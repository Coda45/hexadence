package io.github.coda45.hexadence.net;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.LoadoutRules;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.StepResult;
import io.github.coda45.hexadence.world.WorldEvent;
import io.netty.channel.Channel;

// One match: its own arena and up to four players, identified by a room code.
//
// A room starts in the LOBBY state, where players gather, draft their three
// powers and lock them in, and the world does not advance. Once everyone is
// locked in, the host (the player who created the room) can send StartGame to
// switch it to PLAYING: each player's loadout is applied and it ticks at 60 Hz.
// Loadouts cannot change after that.
//
// A match lasts a fixed time. When the clock runs out the stats are frozen,
// but play goes on for a few seconds while clients show GAME OVER and fade
// to black; nothing done then counts. After that every player gets a
// MatchEnded with the frozen stats, and the room goes back to LOBBY with the
// same players and code, ready for a rematch. If the host left during the
// match there is nobody to start one, so the room closes instead.
//
// Threading: only touched from GameServer's game thread, so no locks.
final class GameRoom {

    enum State { LOBBY, PLAYING, CLOSED }

    private static final int MAX_PENDING_EVENTS_PER_CLIENT = 64;
    static final float DEFAULT_MATCH_SECONDS = 180f;
    // How long play continues after the clock runs out.
    static final float DEFAULT_ENDING_SECONDS = 3f;

    private final String code;
    private final String mapId;
    private final ArenaWorld world;
    private final int capacity;
    private final int matchTicks;
    private final int endingTicks;

    private final Map<Integer, Channel> channels = new LinkedHashMap<>();
    private final Map<Integer, GameServer.PendingCommand> pending = new LinkedHashMap<>();
    private final Map<Integer, Deque<WorldEvent>> pendingEvents = new LinkedHashMap<>();
    // Each player's drafted powers, and whether they have locked them in.
    private final Map<Integer, Draft> drafts = new LinkedHashMap<>();

    private record Draft(Loadout loadout, boolean locked) {
    }

    private State state = State.LOBBY;
    private int hostPlayerId = -1;
    // Simulation steps left on the match clock.
    private int ticksRemaining;
    // After the clock runs out: the frozen stats, and steps left until they
    // are sent.
    private boolean ending;
    private int endingTicksRemaining;
    private List<MatchEnded.PlayerResult> frozenResults = List.of();

    GameRoom(String code, ArenaDefinition definition, String mapId) {
        this(code, definition, mapId, DEFAULT_MATCH_SECONDS, DEFAULT_ENDING_SECONDS);
    }

    GameRoom(
        String code,
        ArenaDefinition definition,
        String mapId,
        float matchSeconds,
        float endingSeconds
    ) {
        this.code = Objects.requireNonNull(code);
        this.mapId = Objects.requireNonNull(mapId);
        this.world = new ArenaWorld(definition);
        this.matchTicks = Math.max(1, Math.round(matchSeconds / ArenaWorld.STEP));
        this.endingTicks = Math.max(1, Math.round(endingSeconds / ArenaWorld.STEP));

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
        drafts.put(id, new Draft(Loadout.DEFAULT, false));

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

        broadcast(new LobbyState(channels.size(), capacity, lockedCount()));
    }

    void leave(int playerId) {
        if (!channels.containsKey(playerId)) {
            return;   // already removed, e.g. when the room closed
        }

        channels.remove(playerId);
        pending.remove(playerId);
        pendingEvents.remove(playerId);
        drafts.remove(playerId);
        world.removePlayer(playerId);

        log("Player " + playerId + " left");

        if (state == State.LOBBY && playerId == hostPlayerId) {
            close("the host left the lobby");
        } else {
            broadcastLobbyState();
        }
    }

    // The room is over: tell everyone else and disconnect them.
    private void close(String reason) {
        state = State.CLOSED;

        List<Channel> others = new ArrayList<>(channels.values());
        channels.clear();
        pending.clear();
        pendingEvents.clear();
        drafts.clear();

        for (Channel channel : others) {
            channel.writeAndFlush(new RoomClosed()).addListener(
                future -> channel.close()
            );
        }

        log("Closed because " + reason);
    }

    // ---- Drafting ----

    // A player picked or locked in their powers. Only allowed in the lobby,
    // and only for a loadout the rules allow; anything else is ignored.
    void selectLoadout(int playerId, SelectLoadout selection) {
        if (state != State.LOBBY || !drafts.containsKey(playerId)) {
            return;
        }

        String problem = LoadoutRules.problem(selection.loadout());

        if (problem != null) {
            log("Ignored loadout from player " + playerId + ": " + problem);
            return;
        }

        drafts.put(playerId, new Draft(selection.loadout(), selection.locked()));
        broadcastLobbyState();
    }

    private int lockedCount() {
        int locked = 0;

        for (Draft draft : drafts.values()) {
            if (draft.locked()) {
                locked++;
            }
        }

        return locked;
    }

    // ---- Lobby to match ----

    // Starts the match if the host asked, everyone is locked in, and it has
    // not started yet.
    void start(int playerId) {
        if (state != State.LOBBY) {
            return;
        }

        if (playerId != hostPlayerId) {
            log("Ignored START from player " + playerId + ", who is not the host");
            return;
        }

        if (lockedCount() < channels.size()) {
            log("Ignored START: " + lockedCount() + " of " + channels.size()
                + " players locked in");
            return;
        }

        // Everyone enters with the powers they drafted.
        for (Map.Entry<Integer, Draft> entry : drafts.entrySet()) {
            world.getPlayers().get(entry.getKey()).setLoadout(entry.getValue().loadout());
        }

        state = State.PLAYING;
        ticksRemaining = matchTicks;
        ending = false;
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

        if (ending) {
            endingTicksRemaining--;
        } else {
            ticksRemaining--;

            if (ticksRemaining <= 0) {
                beginEnding();
            }
        }

        if (channels.isEmpty()) {
            return;
        }

        queueEvents(result);

        WorldSnapshot snapshot = SnapshotFactory.capture(
            world,
            result,
            Math.max(0, ticksRemaining) * ArenaWorld.STEP,
            ending
                ? 1f - (float) Math.max(0, endingTicksRemaining) / endingTicks
                : WorldSnapshot.NOT_ENDING
        );

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

        // After this tick's snapshot and events, so they arrive first.
        if (ending && endingTicksRemaining <= 0) {
            endMatch();
        }
    }

    // ---- Match to results ----

    // The clock ran out: lock in the results. Play continues until the
    // ending finishes, but nothing more counts.
    private void beginEnding() {
        ending = true;
        endingTicksRemaining = endingTicks;
        frozenResults = currentResults();
        world.setStatsFrozen(true);
        log("Clock ran out");
    }

    private List<MatchEnded.PlayerResult> currentResults() {
        List<MatchEnded.PlayerResult> results = new ArrayList<>();

        for (Map.Entry<Integer, Player> entry : world.getPlayers().entrySet()) {
            Player player = entry.getValue();

            results.add(new MatchEnded.PlayerResult(
                entry.getKey(),
                player.getColor(),
                player.getKills(),
                player.getDeaths(),
                player.getDamageDealt()
            ));
        }

        return results;
    }

    private void endMatch() {
        ending = false;
        broadcast(new MatchEnded(frozenResults));
        log("Match ended");

        // Without the host nobody can start a rematch.
        if (!channels.containsKey(hostPlayerId)) {
            close("the host left during the match");
            return;
        }

        // Same room, same players: back to drafting for a rematch.
        state = State.LOBBY;
        world.resetForNewMatch();

        for (Integer playerId : channels.keySet()) {
            pending.put(playerId, new GameServer.PendingCommand());
            pendingEvents.get(playerId).clear();

            Draft draft = drafts.get(playerId);
            drafts.put(
                playerId,
                new Draft(draft == null ? Loadout.DEFAULT : draft.loadout(), false)
            );
        }

        broadcastLobbyState();
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
