package io.github.coda45.hexadence.net;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.world.ArenaCollision;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.SpawnPoint;
import io.netty.channel.embedded.EmbeddedChannel;

class GameServerJoinTest {

    private static GameServer serverWithSpawns(int spawns) {
        List<SpawnPoint> points = new java.util.ArrayList<>();

        for (int i = 0; i < spawns; i++) {
            points.add(new SpawnPoint(1f + i, 1f + i));
        }

        return new GameServer(
            new ArenaDefinition(
                new ArenaCollision(new float[10][10]),
                points,
                List.of()
            ),
            "test_map",
            0
        );
    }

    @Test
    void fourPlayersJoinWithDistinctIds() {
        GameServer server = serverWithSpawns(4);
        Set<Integer> ids = new HashSet<>();

        for (int i = 0; i < GameServer.MAX_PLAYERS; i++) {
            int id = server.join(new EmbeddedChannel());

            assertTrue(id >= 0);
            ids.add(id);
        }

        assertEquals(4, ids.size());
    }

    @Test
    void fifthPlayerIsRefusedAndDisconnected() {
        GameServer server = serverWithSpawns(4);

        for (int i = 0; i < GameServer.MAX_PLAYERS; i++) {
            server.join(new EmbeddedChannel());
        }

        EmbeddedChannel extra = new EmbeddedChannel();

        assertEquals(-1, server.join(extra));
        assertFalse(extra.isOpen());
    }

    @Test
    void capacityIsLimitedByTheNumberOfSpawnPoints() {
        GameServer server = serverWithSpawns(2);

        assertTrue(server.join(new EmbeddedChannel()) >= 0);
        assertTrue(server.join(new EmbeddedChannel()) >= 0);
        assertEquals(-1, server.join(new EmbeddedChannel()));
    }

    @Test
    void leavingFreesASeatForANewPlayer() {
        GameServer server = serverWithSpawns(2);
        int first = server.join(new EmbeddedChannel());
        server.join(new EmbeddedChannel());

        server.leave(first);

        assertTrue(server.join(new EmbeddedChannel()) >= 0);
    }

    @Test
    void aNewPlayerNeverReusesAnEarlierPlayersId() {
        GameServer server = serverWithSpawns(2);
        int first = server.join(new EmbeddedChannel());

        server.leave(first);

        assertTrue(server.join(new EmbeddedChannel()) != first);
    }

    @Test
    void acceptsOnlyItsOwnMapId() {
        GameServer server = serverWithSpawns(1);

        assertTrue(server.usesMap("test_map"));
        assertFalse(server.usesMap("other_map"));
        assertFalse(server.usesMap(null));
    }

    @Test
    void rejectsANullMapId() {
        assertThrows(
            NullPointerException.class,
            () -> new GameServer(
                new ArenaDefinition(
                    new ArenaCollision(new float[10][10]),
                    List.of(new SpawnPoint(1f, 1f)),
                    List.of()
                ),
                null,
                0
            )
        );
    }

    @Test
    void arenaWithoutPlayerSpawnsIsRejected() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new ArenaDefinition(
                new ArenaCollision(new float[10][10]),
                List.<SpawnPoint>of(),
                List.of()
            )
        );
    }
}
