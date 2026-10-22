package io.github.coda45.hexadence.net;

// Where the dedicated server is. The game connects here for HOST GAME and
// JOIN GAME.
//
// Defaults to the team's VPS. To test against a server on your own machine
// (./gradlew server:run), start the game with
//   -Dhexadence.server=localhost
// A port can be added after a colon: -Dhexadence.server=localhost:7800
public record ServerAddress(String host, int port) {

    public static final String DEFAULT = "40.160.225.76:7777";

    public static ServerAddress fromSettings() {
        return parse(System.getProperty("hexadence.server", DEFAULT));
    }

    // Accepts "host" or "host:port".
    public static ServerAddress parse(String text) {
        String trimmed = text.trim();
        int colon = trimmed.lastIndexOf(':');

        if (colon < 0) {
            return new ServerAddress(trimmed, GameServer.DEFAULT_PORT);
        }

        return new ServerAddress(
            trimmed.substring(0, colon),
            Integer.parseInt(trimmed.substring(colon + 1))
        );
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
