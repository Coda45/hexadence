package io.github.coda45.hexadence.net;

import java.util.concurrent.CompletableFuture;

import io.github.coda45.hexadence.power.Loadout;

// What a lobby screen needs from the network, in one place: start connecting,
// then call poll() once per frame and read status().
//
// It has no libGDX code, so its logic is the same whichever screen uses it,
// and it can be tested without a window.
public final class LobbyConnection {

    public enum Status {
        CONNECTING,       // opening the connection or waiting for Welcome
        UNREACHABLE,      // could not connect to the server at all
        REJECTED,         // the server said no; see rejection()
        IN_LOBBY,         // in a room, waiting for the host to start
        STARTED,          // the match began; hand the session to ArenaScreen
        ROOM_CLOSED,      // the host left the lobby
        DISCONNECTED      // the connection dropped
    }

    private CompletableFuture<NetSession> pending;
    private NetSession session;
    private boolean unreachable;
    private boolean handedOff;

    private LobbyConnection(CompletableFuture<NetSession> pending) {
        this.pending = pending;
    }

    public static LobbyConnection createRoom(ServerAddress server, String mapId) {
        return new LobbyConnection(NetSession.createRoom(server, mapId));
    }

    public static LobbyConnection joinRoom(
        ServerAddress server,
        String mapId,
        String roomCode
    ) {
        return new LobbyConnection(NetSession.joinRoom(server, mapId, roomCode));
    }

    // Picks up the finished connection attempt, if it finished. Call from the
    // render thread once per frame.
    public void poll() {
        if (pending == null || !pending.isDone()) {
            return;
        }

        try {
            session = pending.join();
        } catch (RuntimeException e) {
            unreachable = true;
        }

        pending = null;
    }

    public Status status() {
        if (unreachable) {
            return Status.UNREACHABLE;
        }

        if (session == null) {
            return Status.CONNECTING;
        }

        NetClient client = session.client();

        // Checked in this order because a rejection or closed room is
        // followed by a disconnect, and the more specific reason is the
        // useful one to show.
        if (client.getRejection() != null) {
            return Status.REJECTED;
        }

        if (client.isRoomClosed()) {
            return Status.ROOM_CLOSED;
        }

        if (client.isGameStarted()) {
            return Status.STARTED;
        }

        if (client.isDisconnected()) {
            return Status.DISCONNECTED;
        }

        if (client.getRoomCode() == null) {
            return Status.CONNECTING;
        }

        return Status.IN_LOBBY;
    }

    public JoinRejected.Reason rejection() {
        return session == null ? null : session.client().getRejection();
    }

    // Null until the room is joined.
    public String roomCode() {
        return session == null ? null : session.client().getRoomCode();
    }

    public int playerCount() {
        LobbyState state = lobbyState();
        return state == null ? 0 : state.playerCount();
    }

    public int maxPlayers() {
        LobbyState state = lobbyState();
        return state == null ? GameServer.MAX_PLAYERS : state.maxPlayers();
    }

    // How many players have locked in their powers.
    public int lockedCount() {
        LobbyState state = lobbyState();
        return state == null ? 0 : state.lockedCount();
    }

    // True when every player in the room has locked in, so the host can start.
    public boolean everyoneLocked() {
        LobbyState state = lobbyState();
        return state != null && state.everyoneLocked();
    }

    // Sends this player's powers, locked in or not. The server ignores a
    // loadout the rules do not allow, so check LoadoutRules first.
    public void selectLoadout(Loadout loadout, boolean locked) {
        if (status() == Status.IN_LOBBY) {
            session.client().selectLoadout(loadout, locked);
        }
    }

    private LobbyState lobbyState() {
        return session == null ? null : session.client().getLobbyState();
    }

    // Host only: asks the server to start the match.
    public void requestStart() {
        if (status() == Status.IN_LOBBY) {
            session.client().requestStart();
        }
    }

    // Gives the open session to the arena. After this, close() leaves the
    // connection alone; the arena closes it when the player leaves.
    public NetSession handOff() {
        if (status() != Status.STARTED) {
            throw new IllegalStateException("The match has not started");
        }

        handedOff = true;
        return session;
    }

    // Leaves the lobby. Safe to call at any time, including while still
    // connecting: a connection that finishes later is closed as soon as it does.
    public void close() {
        if (handedOff) {
            return;
        }

        if (pending != null) {
            pending.thenAccept(NetSession::close);
            pending = null;
        }

        if (session != null) {
            session.close();
            session = null;
        }
    }

    // Player-facing text for why the server said no.
    public static String describe(JoinRejected.Reason reason) {
        if (reason == null) {
            return "";
        }

        return switch (reason) {
            case ROOM_NOT_FOUND -> "NO ROOM WITH THAT CODE";
            case ROOM_FULL -> "THAT ROOM IS FULL";
            case GAME_IN_PROGRESS -> "THAT MATCH HAS ALREADY STARTED";
            case SERVER_FULL -> "SERVER IS FULL, TRY AGAIN LATER";
            case WRONG_VERSION, WRONG_MAP -> "GAME VERSION DOES NOT MATCH THE SERVER";
        };
    }
}
