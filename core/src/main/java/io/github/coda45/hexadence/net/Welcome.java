package io.github.coda45.hexadence.net;

// Accepts the client into a room. Tells it which player in authoritative
// snapshots it controls and the room's code, which is how the host learns
// the code to share.
public record Welcome(
    int protocolVersion,
    int playerId,
    String roomCode
) {
}
