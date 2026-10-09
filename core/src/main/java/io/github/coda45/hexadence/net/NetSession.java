package io.github.coda45.hexadence.net;

import io.github.coda45.hexadence.world.ArenaDefinition;

// One player's connection to a game, either as the host or as a joiner.
//
// Host:   starts a GameServer inside this process, then connects to it like
//         any other player. Up to three other players may then join.
// Joiner: connects to a host's address.
//
// Either way the game talks only to client(), so it does not care which
// kind of session it is in. Create one with host() or join(), and call
// close() when leaving the game.
public final class NetSession {

    // Connect to our own server by address, not by name, to avoid a
    // localhost lookup that could pick an address the server isn't using.
    private static final String LOCAL_ADDRESS = "127.0.0.1";

    private final GameServer server;   // null for a joiner
    private final NetClient client;

    private NetSession(GameServer server, NetClient client) {
        this.server = server;
        this.client = client;
    }

    // Start hosting. The definition comes from the host's own loaded map.
    public static NetSession host(ArenaDefinition definition, int port)
        throws InterruptedException {

        GameServer server = new GameServer(definition, port);
        server.start();

        NetClient client = new NetClient(LOCAL_ADDRESS, port);

        try {
            client.connect();
        } catch (Exception e) {
            // Without a working local connection the host cannot play,
            // so do not leave the server running with nobody using it.
            server.stop();
            throw e;
        }

        return new NetSession(server, client);
    }

    // Join a game someone else is hosting.
    public static NetSession join(String hostAddress, int port)
        throws InterruptedException {

        NetClient client = new NetClient(hostAddress, port);
        client.connect();

        return new NetSession(null, client);
    }

    public NetClient client() {
        return client;
    }

    public boolean isHost() {
        return server != null;
    }

    // Disconnects. If this player is the host, the server stops too, which
    // ends the game for everyone who joined.
    public void close() {
        client.close();

        if (server != null) {
            server.stop();
        }
    }
}