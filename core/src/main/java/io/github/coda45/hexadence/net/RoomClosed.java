package io.github.coda45.hexadence.net;

// Server to the players left in a lobby: the host left, so the room no
// longer exists. The server closes the connection right after this.
public record RoomClosed() {
}
