package io.github.coda45.hexadence.net;

// Client to server, sent when the client has sent nothing else for a few
// seconds. In the lobby no input flows, and without this the server's read
// timeout would treat a waiting player as a dead connection.
public record KeepAlive() {
}
