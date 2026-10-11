package io.github.coda45.hexadence.net;

// Accepts the negotiated protocol version and tells the client which player
// in authoritative snapshots it controls.
public record Welcome(
    int protocolVersion,
    int playerId
) {
}
