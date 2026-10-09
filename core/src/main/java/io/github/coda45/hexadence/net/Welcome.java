package io.github.coda45.hexadence.net;

// First message the server sends after a client connects. It tells the client
// which player in the snapshots is the one it controls.
public record Welcome(int playerId) {
}