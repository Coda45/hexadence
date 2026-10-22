package io.github.coda45.hexadence.net;

// Sent instead of Welcome when the server will not let a client in, just
// before it closes the connection, so the screen can say why.
public record JoinRejected(Reason reason) {

    public enum Reason {
        ROOM_NOT_FOUND,
        ROOM_FULL,
        GAME_IN_PROGRESS,
        SERVER_FULL,
        WRONG_VERSION,
        WRONG_MAP
    }
}
