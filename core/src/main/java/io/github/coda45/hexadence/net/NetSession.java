package io.github.coda45.hexadence.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// One player's connection to a room on the dedicated server.
//
// Create one with createRoom() (HOST GAME) or joinRoom() (JOIN GAME). Both
// connect in the background so the screen keeps drawing while it waits; the
// returned future completes once the connection is open, and the handshake
// result then arrives on client().
//
// The lobby screens hand the session to ArenaScreen when the match starts.
// Whoever holds it last calls close().
public final class NetSession {

    private final NetClient client;

    private NetSession(NetClient client) {
        this.client = client;
    }

    // Asks the server for a new room; this player becomes its host.
    public static CompletableFuture<NetSession> createRoom(
        ServerAddress server,
        String mapId
    ) {
        return open(server, ClientHello.createRoom(mapId));
    }

    // Joins the room with the code another player shared.
    public static CompletableFuture<NetSession> joinRoom(
        ServerAddress server,
        String mapId,
        String roomCode
    ) {
        return open(server, ClientHello.joinRoom(mapId, RoomCodes.normalize(roomCode)));
    }

    private static CompletableFuture<NetSession> open(
        ServerAddress server,
        ClientHello hello
    ) {
        return CompletableFuture.supplyAsync(() -> {
            NetClient client = new NetClient(server.host(), server.port(), hello);

            try {
                client.connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            }

            return new NetSession(client);
        });
    }

    public NetClient client() {
        return client;
    }

    public void close() {
        client.close();
    }
}
