package io.github.coda45.hexadence.net;

// Sent to everyone in a room's lobby whenever a player joins, leaves, or
// locks in or unlocks their powers. The host can start once lockedCount
// equals playerCount.
public record LobbyState(int playerCount, int maxPlayers, int lockedCount) {

    public boolean everyoneLocked() {
        return playerCount > 0 && lockedCount == playerCount;
    }
}
