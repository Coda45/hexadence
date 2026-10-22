package io.github.coda45.hexadence.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaMaps;
import io.github.coda45.hexadence.world.TmxArenaLoader;

// Entry point for the dedicated server: a GameServer with no window and no
// local player, meant to run on the VPS. Every player connects as a client
// (the same path as -Dhexadence.join).
//
// Settings, each optional:
//   port   -Dhexadence.port=7777,  or the HEXADENCE_PORT environment variable
//   map    -Dhexadence.map=maps/dungeon_map.tmx
//   assets -Dhexadence.assets=path/to/assets, only needed when the map is
//          not bundled in the jar
//
// The map ID must match the one clients use, which is the map's path, so
// clients and server must run the same build of the game.
public final class ServerLauncher {

    private ServerLauncher() {
    }

    public static void main(String[] args) throws InterruptedException {
        int port = readPort();
        String mapPath = System.getProperty("hexadence.map", ArenaMaps.MULTIPLAYER);
        Path assetsFolder = Path.of(System.getProperty("hexadence.assets", "."));

        ArenaDefinition definition = TmxArenaLoader.load(
            mapPath,
            path -> open(path, assetsFolder)
        );

        GameServer server = new GameServer(definition, mapPath, port);
        server.start();
        System.out.println("Map: " + mapPath);

        // systemctl stop and Ctrl+C both arrive here; close sockets cleanly.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down");
            server.stop();
        }, "shutdown"));

        server.awaitClose();
    }

    private static int readPort() {
        String fromEnv = System.getenv("HEXADENCE_PORT");
        int fallback = fromEnv == null || fromEnv.isBlank()
            ? GameServer.DEFAULT_PORT
            : Integer.parseInt(fromEnv.trim());

        return Integer.getInteger("hexadence.port", fallback);
    }

    // The server jar bundles the maps, so look on the classpath first, then
    // in the assets folder (for running from an IDE or the repository).
    private static InputStream open(String path, Path assetsFolder)
        throws IOException {

        InputStream bundled = ServerLauncher.class
            .getClassLoader()
            .getResourceAsStream(path);

        if (bundled != null) {
            return bundled;
        }

        Path file = assetsFolder.resolve(path);

        if (Files.isRegularFile(file)) {
            return Files.newInputStream(file);
        }

        throw new IOException(
            path + " is not in the jar or in " + assetsFolder.toAbsolutePath()
        );
    }
}
