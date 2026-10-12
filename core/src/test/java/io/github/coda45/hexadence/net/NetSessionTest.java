package io.github.coda45.hexadence.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.world.ArenaCollision;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.SpawnPoint;

// Integration tests over real loopback sockets. Each test uses its own free port.
class NetSessionTest {

    private static final String MAP = "test_map";
    private static final String LOOPBACK = "127.0.0.1";

    private static ArenaDefinition definition() {
        return new ArenaDefinition(
            new ArenaCollision(new float[10][10]),
            List.of(
                new SpawnPoint(2f, 3f),
                new SpawnPoint(6f, 6f),
                new SpawnPoint(4f, 8f),
                new SpawnPoint(8f, 2f)
            ),
            List.of()
        );
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean waitFor(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;

        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    private static int playerCount(NetSession session) {
        WorldSnapshot snapshot = session.client().getLatestSnapshot();

        return snapshot == null ? 0 : snapshot.players().size();
    }

    @Test
    void hostIsReadyAndSeesItselfInTheSnapshot() throws Exception {
        NetSession host = NetSession.host(definition(), MAP, freePort());

        try {
            assertTrue(host.isHost());
            assertTrue(waitFor(() -> host.client().isReady()));
            assertEquals(1, playerCount(host));
        } finally {
            host.close();
        }
    }

    @Test
    void joinerConnectsAndBothSidesSeeTwoPlayers() throws Exception {
        int port = freePort();
        NetSession host = NetSession.host(definition(), MAP, port);
        NetSession joiner = null;

        try {
            joiner = NetSession.join(LOOPBACK, MAP, port);
            NetSession guest = joiner;

            assertFalse(guest.isHost());
            assertTrue(waitFor(() -> guest.client().isReady()));
            assertTrue(waitFor(() -> playerCount(host) == 2));
            assertTrue(waitFor(() -> playerCount(guest) == 2));
            assertNotEquals(
                host.client().getPlayerId(),
                guest.client().getPlayerId()
            );
        } finally {
            if (joiner != null) {
                joiner.close();
            }
            host.close();
        }
    }

    @Test
    void hostSeesTheJoinersMovement() throws Exception {
        int port = freePort();
        NetSession host = NetSession.host(definition(), MAP, port);
        NetSession joiner = NetSession.join(LOOPBACK, MAP, port);

        try {
            assertTrue(waitFor(() -> joiner.client().isReady()));
            int joinerId = joiner.client().getPlayerId();

            assertTrue(waitFor(() -> playerCount(host) == 2));
            float startX = host.client().getLatestSnapshot().players().stream()
                .filter(p -> p.id() == joinerId).findFirst().orElseThrow().x();

            PlayerCommand right = new PlayerCommand(1f, 0f, 1f, 0f, false, false, false);

            assertTrue(waitFor(() -> {
                joiner.client().send(right);

                return host.client().getLatestSnapshot().players().stream()
                    .filter(p -> p.id() == joinerId)
                    .anyMatch(p -> p.x() > startX + 0.05f);
            }));
        } finally {
            joiner.close();
            host.close();
        }
    }

    @Test
    void joinerLeavingRemovesItFromTheHostsSnapshot() throws Exception {
        int port = freePort();
        NetSession host = NetSession.host(definition(), MAP, port);
        NetSession joiner = NetSession.join(LOOPBACK, MAP, port);

        try {
            assertTrue(waitFor(() -> playerCount(host) == 2));

            joiner.close();

            assertTrue(waitFor(() -> playerCount(host) == 1));
        } finally {
            joiner.close();
            host.close();
        }
    }

    @Test
    void joinerWithTheWrongMapIsNeverAdmitted() throws Exception {
        int port = freePort();
        NetSession host = NetSession.host(definition(), MAP, port);
        NetSession joiner = NetSession.join(LOOPBACK, "other_map", port);

        try {
            assertTrue(waitFor(() -> !joiner.client().isConnected()));
            assertFalse(joiner.client().isReady());
            assertEquals(-1, joiner.client().getPlayerId());
            assertTrue(waitFor(() -> host.client().isReady()));
            assertEquals(1, playerCount(host));
        } finally {
            joiner.close();
            host.close();
        }
    }

    @Test
    void fifthPlayerIsRefusedWhenTheRoomIsFull() throws Exception {
        int port = freePort();
        NetSession host = NetSession.host(definition(), MAP, port);
        NetSession a = NetSession.join(LOOPBACK, MAP, port);
        NetSession b = NetSession.join(LOOPBACK, MAP, port);
        NetSession c = NetSession.join(LOOPBACK, MAP, port);
        NetSession extra = null;

        try {
            assertTrue(waitFor(() -> playerCount(host) == 4));

            extra = NetSession.join(LOOPBACK, MAP, port);
            NetSession refused = extra;

            assertTrue(waitFor(() -> !refused.client().isConnected()));
            assertFalse(refused.client().isReady());
            assertEquals(4, playerCount(host));
        } finally {
            if (extra != null) {
                extra.close();
            }
            c.close();
            b.close();
            a.close();
            host.close();
        }
    }

    @Test
    void connectingToAPortWithNoServerFails() throws Exception {
        int port = freePort();

        assertThrows(Exception.class, () -> NetSession.join(LOOPBACK, MAP, port));
    }

    @Test
    void hostingOnAPortAlreadyInUseFails() throws Exception {
        try (ServerSocket taken = new ServerSocket(0)) {
            int port = taken.getLocalPort();

            assertThrows(
                Exception.class,
                () -> NetSession.host(definition(), MAP, port)
            );
        }
    }
}
