package io.github.coda45.hexadence.net;

// Sent to everyone in a room's lobby whenever a player joins or leaves.
public record LobbyState(int playerCount, int maxPlayers) {
}
