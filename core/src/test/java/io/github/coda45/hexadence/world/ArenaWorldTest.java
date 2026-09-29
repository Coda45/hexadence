package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.input.PlayerCommand;

class ArenaWorldTest {

    private static final float EPSILON = 0.0001f;

    @Test
    void createsWorldFromPlainArenaDefinition() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(2f, 3f),
            List.of(
                new SpawnPoint(5f, 6f),
                new SpawnPoint(7f, 8f)
            )
        );

        assertEquals(2f, world.getPlayer().getX(), EPSILON);
        assertEquals(3f, world.getPlayer().getY(), EPSILON);
        assertEquals(2, world.getDummies().size());
    }

    @Test
    void movesPlayerUsingOneFixedSimulationStep() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(2f, 3f),
            List.of()
        );

        PlayerCommand moveRight = new PlayerCommand(
            1f, 0f,
            0f, 0f,
            false, false, false
        );

        world.step(moveRight);

        float expectedX = 2f + 5f * ArenaWorld.STEP;

        assertEquals(
            expectedX,
            world.getPlayer().getX(),
            EPSILON
        );

        assertEquals(
            3f,
            world.getPlayer().getY(),
            EPSILON
        );
    }

    @Test
    void projectileDoesNotTunnelThroughThinObstacle() {
        float[][] blockedHeights = new float[10][10];

        // A thin obstacle at the bottom of tile (3, 3).
        blockedHeights[3][3] = 0.01f;

        ArenaDefinition definition =
            new ArenaDefinition(
                new ArenaCollision(blockedHeights),
                new SpawnPoint(2.87f, 2.25f),
                List.of()
            );

        ArenaWorld world = new ArenaWorld(definition);

        PlayerCommand castLightningDiagonally =
            new PlayerCommand(
                0f, 0f,
                1f, 1f,
                false, false, true
            );

        world.step(castLightningDiagonally);

        assertTrue(
            world.getProjectiles().isEmpty(),
            "Projectile should collide with an obstacle crossed during movement"
        );
    }

    @Test
    void terrainCollisionTakesPriorityOverDummyHit() {
        float[][] blockedHeights = new float[10][10];

        // Full wall occupying tile (3, 3).
        blockedHeights[3][3] = 1f;

        ArenaDefinition definition =
            new ArenaDefinition(
                new ArenaCollision(blockedHeights),
                new SpawnPoint(2.79f, 2.25f),
                List.of(
                    new SpawnPoint(2.9f, 3f)
                )
            );

        ArenaWorld world = new ArenaWorld(definition);

        float healthBefore =
            world.getDummies().get(0).getHealth();

        PlayerCommand castEmberRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                true, false, false
            );

        world.step(castEmberRight);

        assertTrue(
            world.getProjectiles().isEmpty(),
            "Terrain should remove the projectile"
        );

        assertEquals(
            healthBefore,
            world.getDummies().get(0).getHealth(),
            EPSILON,
            "A projectile stopped by terrain must not damage the dummy"
        );
    }
    
    @Test
    void deadPlayerCannotAimMoveOrCast() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(2f, 3f),
            List.of()
        );

        world.getPlayer().takeDamage(
            world.getPlayer().getMaxHealth()
        );

        PlayerCommand command =
            new PlayerCommand(
                1f, 0f,
                0f, 1f,
                true, true, true
            );

        world.step(command);

        assertFalse(world.getPlayer().isAlive());

        assertEquals(
            2f,
            world.getPlayer().getX(),
            EPSILON
        );

        assertEquals(
            3f,
            world.getPlayer().getY(),
            EPSILON
        );

        // The original default aim points right.
        assertEquals(
            1f,
            world.getPlayer().getAimX(),
            EPSILON
        );

        assertEquals(
            0f,
            world.getPlayer().getAimY(),
            EPSILON
        );

        assertTrue(
            world.getProjectiles().isEmpty(),
            "A dead player must not cast spells"
        );
    }

    private ArenaWorld createEmptyWorld(
        SpawnPoint playerSpawn,
        List<SpawnPoint> dummySpawns
    ) {
        float[][] emptyCollisionGrid =
            new float[10][10];

        ArenaDefinition definition =
            new ArenaDefinition(
                new ArenaCollision(emptyCollisionGrid),
                playerSpawn,
                dummySpawns
            );

        return new ArenaWorld(definition);
    }
}