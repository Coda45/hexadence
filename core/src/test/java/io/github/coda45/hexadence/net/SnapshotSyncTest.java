package io.github.coda45.hexadence.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.spell.SpellType;
import io.github.coda45.hexadence.world.ArenaCollision;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.SpawnPoint;
import io.github.coda45.hexadence.world.StepResult;

// Server world -> SnapshotFactory -> WorldSnapshot -> client world.
class SnapshotSyncTest {

    private static final float EPSILON = 0.0001f;

    private static ArenaDefinition definition() {
        return new ArenaDefinition(
            new ArenaCollision(new float[10][10]),
            List.of(new SpawnPoint(2f, 3f), new SpawnPoint(6f, 6f)),
            List.of(new SpawnPoint(8f, 8f))
        );
    }

    // Same setup GameServer uses: no local player, only joined players.
    private static ArenaWorld serverWorld(int players) {
        ArenaWorld world = new ArenaWorld(definition());
        world.removePlayer(0);

        for (int i = 0; i < players; i++) {
            world.addPlayer();
        }
        return world;
    }

    private static PlayerCommand command(
        float moveX, float moveY, float aimX, float aimY, boolean ember
    ) {
        return new PlayerCommand(moveX, moveY, aimX, aimY, ember, false, false);
    }

    @Test
    void clientWorldMirrorsPlayerPositionAimAndMoving() {
        ArenaWorld server = serverWorld(2);
        List<Integer> ids = new ArrayList<>(server.getPlayers().keySet());

        StepResult result = server.step(Map.of(
            ids.get(0), command(1f, 0f, 0f, 1f, false),
            ids.get(1), command(0f, 0f, -1f, 0f, false)
        ));

        ArenaWorld client = new ArenaWorld(definition());
        client.applySnapshot(SnapshotFactory.capture(server, result));

        assertEquals(2, client.getPlayers().size());

        for (int id : ids) {
            Player expected = server.getPlayers().get(id);
            Player actual = client.getPlayers().get(id);

            assertNotNull(actual, "player " + id);
            assertEquals(expected.getX(), actual.getX(), EPSILON);
            assertEquals(expected.getY(), actual.getY(), EPSILON);
            assertEquals(expected.getAimX(), actual.getAimX(), EPSILON);
            assertEquals(expected.getAimY(), actual.getAimY(), EPSILON);
            assertEquals(expected.isMoving(), actual.isMoving());
            assertEquals(expected.getHealth(), actual.getHealth(), EPSILON);
        }

        assertTrue(server.getPlayers().get(ids.get(0)).isMoving());
        assertFalse(server.getPlayers().get(ids.get(1)).isMoving());
    }

    @Test
    void snapshotCarriesTheServerTick() {
        ArenaWorld server = serverWorld(1);
        StepResult result = null;

        for (int i = 0; i < 5; i++) {
            result = server.step(Map.of());
        }

        WorldSnapshot snapshot = SnapshotFactory.capture(server, result);

        assertEquals(result.tick(), snapshot.tick());
    }

    @Test
    void clientDropsAPlayerWhoLeftTheServer() {
        ArenaWorld server = serverWorld(2);
        int leaver = server.getPlayers().keySet().iterator().next();

        ArenaWorld client = new ArenaWorld(definition());
        client.applySnapshot(SnapshotFactory.capture(server, server.step(Map.of())));
        assertEquals(2, client.getPlayers().size());

        server.removePlayer(leaver);
        client.applySnapshot(SnapshotFactory.capture(server, server.step(Map.of())));

        assertEquals(1, client.getPlayers().size());
        assertFalse(client.getPlayers().containsKey(leaver));
    }

    @Test
    void clientMirrorsProjectileAndSpellCooldown() {
        ArenaWorld server = serverWorld(1);
        int id = server.getPlayers().keySet().iterator().next();

        StepResult result = server.step(Map.of(
            id, command(0f, 0f, 1f, 0f, true)
        ));

        assertEquals(1, server.getProjectiles().size());

        ArenaWorld client = new ArenaWorld(definition());
        client.applySnapshot(SnapshotFactory.capture(server, result));

        assertEquals(1, client.getProjectiles().size());
        assertEquals(
            server.getProjectiles().get(0).getId(),
            client.getProjectiles().get(0).getId()
        );
        assertEquals(
            server.getProjectiles().get(0).getOwnerPlayerId(),
            client.getProjectiles().get(0).getOwnerPlayerId()
        );

        float serverCooldown =
            server.getPlayers().get(id).getCooldownRemaining(SpellType.EMBER);

        assertTrue(serverCooldown > 0f);
        assertEquals(
            serverCooldown,
            client.getPlayers().get(id).getCooldownRemaining(SpellType.EMBER),
            EPSILON
        );
    }

    @Test
    void clientMirrorsDummyHealthAfterADummyIsHit() {
        ArenaWorld server = serverWorld(1);
        ArenaWorld client = new ArenaWorld(definition());

        client.applySnapshot(SnapshotFactory.capture(server, server.step(Map.of())));
        assertEquals(1, client.getDummies().size());

        server.getDummies().get(0).setHealth(10f);
        client.applySnapshot(SnapshotFactory.capture(server, server.step(Map.of())));

        assertEquals(10f, client.getDummies().get(0).getHealth(), EPSILON);
    }

    @Test
    void snapshotListsCannotBeModified() {
        WorldSnapshot snapshot = SnapshotFactory.capture(
            serverWorld(1),
            new StepResult(1, List.of())
        );

        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.players().clear()
        );
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.projectiles().add(null)
        );
    }

    @Test
    void snapshotIsNotAffectedByLaterChangesToTheSourceList() {
        List<WorldSnapshot.DummyState> source = new ArrayList<>();
        source.add(new WorldSnapshot.DummyState(1, 0f, 0f, 100f));

        WorldSnapshot snapshot = new WorldSnapshot(
            1L, List.of(), source, List.of(), List.of()
        );

        source.clear();

        assertEquals(1, snapshot.dummies().size());
    }
}
