package io.github.coda45.hexadence.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.StepResult;
import io.github.coda45.hexadence.world.WorldEvent;
import io.netty.channel.Channel;

// One match: its own arena and up to four players, identified by a room code.
//
// A room starts in the LOBBY state, where players gather and the world does
// not advance. The host (the player who created the room) sends StartGame to
// switch it to PLAYING, after which it ticks at 60 Hz like the old
// single-game server did.
//
// Threading: only touched from GameServer's game thread, so no locks.
final class GameRoom {

    enum State { LOBBY, PLAYING, CLOSED }

    private static final int MAX_PENDING_EVENTS_PER_CLIENT = 64;

    private final String code;
    private final String mapId;
    private final ArenaWorld world;
    private final int capacity;

    private final Map<Integer, Channel> channels = new LinkedHashMap<>();
    private final Map<Integer, GameServer.PendingCommand> pending = new LinkedHashMap<>();
    private final Map<Integer, Deque<WorldEvent>> pendingEvents = new LinkedHashMap<>();

    private State state = State.LOBBY;
    private int hostPlayerId = -1;

    GameRoom(String code, ArenaDefinition definition, String mapId) {
        this.code = Objects.requireNonNull(code);
        this.mapId = Objects.requireNonNull(mapId);
        this.world = new ArenaWorld(definition);

        // ArenaWorld always creates player 0 for single-player. A room has
        // no local player; every player here is a connected client.
        world.removePlayer(0);

        this.capacity = Math.min(
            GameServer.MAX_PLAYERS,
            world.getPlayerCapacity()
        );
    }

    // ---- Joining and leaving ----

    // Why a new player cannot join right now, or null if they can.
    JoinRejected.Reason joinProblem() {
        if (state != State.LOBBY) {
            return JoinRejected.Reason.GAME_IN_PROGRESS;
        }

        if (channels.size() >= capacity) {
            return JoinRejected.Reason.ROOM_FULL;
        }

        return null;
    }

    // Adds a player. Call only when joinProblem() is null. The first player
    // to join is the host.
    int join(Channel channel) {
        int id = world.addPlayer();

        channels.put(id, channel);
        pending.put(id, new GameServer.PendingCommand());
        pendingEvents.put(id, new ArrayDeque<>());

        if (hostPlayerId < 0) {
            hostPlayerId = id;
        }

        log("Player " + id + " joined from " + channel.remoteAddress()
            + (id == hostPlayerId ? " (host)" : ""));
        return id;
    }

    // Tells everyone in the lobby how many players are in it. Called after
    // a join has been answered with Welcome, so the new player gets the
    // Welcome first.
    void broadcastLobbyState() {
        if (state != State.LOBBY) {
            return;
        }

        broadcast(new LobbyState(channels.size(), capacity));
    }

    void leave(int playerId) {
        if (!channels.containsKey(playerId)) {
            return;   // already removed, e.g. when the room closed
        }

        channels.remove(playerId);
        pending.remove(playerId);
        pendingEvents.remove(playerId);
        world.removePlayer(playerId);

        log("Player " + playerId + " left");

        if (state == State.LOBBY && playerId == hostPlayerId) {
            close();
        } else {
            broadcastLobbyState();
        }
    }

    // The host left the lobby: tell everyone else and disconnect them.
    private void close() {
        state = State.CLOSED;

        List<Channel> others = new ArrayList<>(channels.values());
        channels.clear();
        pending.clear();
        pendingEvents.clear();

        for (Channel channel : others) {
            channel.writeAndFlush(new RoomClosed()).addListener(
                future -> channel.close()
            );
        }

        log("Closed because the host left the lobby");
    }

    // ---- Lobby to match ----

    // Starts the match if the host asked and it has not started yet.
    void start(int playerId) {
        if (state != State.LOBBY) {
            return;
        }

        if (playerId != hostPlayerId) {
            log("Ignored START from player " + playerId + ", who is not the host");
            return;
        }

        state = State.PLAYING;
        broadcast(new GameStarted());
        log("Match started with " + channels.size() + " player(s)");
    }

    // ---- Input ----

    void receive(int playerId, PlayerCommand command) {
        if (state != State.PLAYING) {
            return;   // input only matters once the match is running
        }

        GameServer.PendingCommand slot = pending.get(playerId);

        if (slot != null) {
            slot.merge(command);
        }
    }

    // ---- The simulation loop ----

    void tick() {
        if (state != State.PLAYING) {
            return;
        }

        Map<Integer, PlayerCommand> commands = new LinkedHashMap<>();

        for (Map.Entry<Integer, GameServer.PendingCommand> entry : pending.entrySet()) {
            commands.put(entry.getKey(), entry.getValue().take());
        }

        StepResult result = world.step(commands);

        if (channels.isEmpty()) {
            return;
        }

        queueEvents(result);

        WorldSnapshot snapshot = SnapshotFactory.capture(world, result);

        for (Map.Entry<Integer, Channel> entry : channels.entrySet()) {
            int playerId = entry.getKey();
            Channel channel = entry.getValue();

            // Snapshots are replaceable, so do not queue them for a slow client.
            if (!channel.isActive() || !channel.isWritable()) {
                continue;
            }

            // Send current persistent state first.
            channel.write(snapshot);

            // Then send every reliable event that client has not received.
            Deque<WorldEvent> events = pendingEvents.get(playerId);

            if (events != null) {
                while (!events.isEmpty()) {
                    channel.write(events.removeFirst());
                }
            }

            channel.flush();
        }
    }

    private void queueEvents(StepResult result) {
        for (WorldEvent event : result.events()) {
            for (Map.Entry<Integer, Channel> entry : channels.entrySet()) {
                int playerId = entry.getKey();
                Channel channel = entry.getValue();

                Deque<WorldEvent> events = pendingEvents.get(playerId);

                if (events == null) {
                    continue;
                }

                if (events.size() >= MAX_PENDING_EVENTS_PER_CLIENT) {
                    log("Disconnecting player " + playerId
                        + ": reliable event queue overflow");
                    channel.close();
                    continue;
                }

                events.addLast(event);
            }
        }
    }

    // ---- Helpers ----

    private void broadcast(Object message) {
        for (Channel channel : channels.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(message);
            }
        }
    }

    private void log(String message) {
        System.out.println("[room " + code + "] " + message);
    }

    // ---- Queries ----

    String code() {
        return code;
    }

    State state() {
        return state;
    }

    int playerCount() {
        return channels.size();
    }

    int capacity() {
        return capacity;
    }

    boolean usesMap(String requestedMapId) {
        return mapId.equals(requestedMapId);
    }

    // True when the server should forget this room.
    boolean isFinished() {
        return state == State.CLOSED || channels.isEmpty();
    }
}
