package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.spell.SpellType;

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

        PlayerCommand castEmberDiagonally =
            new PlayerCommand(
                0f, 0f,
                1f, 1f,
                true, false, false
            );

        world.step(castEmberDiagonally);

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

    @Test
    void lightningDamagesNearestDummyRegardlessOfListOrder() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of(
                // Deliberately put the farther dummy first.
                new SpawnPoint(6f, 1.75f),
                new SpawnPoint(3f, 1.75f)
            )
        );

        float fartherHealth =
            world.getDummies().get(0).getHealth();

        float nearerHealth =
            world.getDummies().get(1).getHealth();

        PlayerCommand castLightningRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                false, false, true
            );

        world.step(castLightningRight);

        assertEquals(
            fartherHealth,
            world.getDummies().get(0).getHealth(),
            EPSILON
        );

        assertEquals(
            nearerHealth
                - SpellType.LIGHTNING.getDamage(),
            world.getDummies().get(1).getHealth(),
            EPSILON
        );

        assertTrue(
            world.getProjectiles().isEmpty(),
            "Lightning should not create a projectile"
        );
    }

    @Test
    void assignsDifferentSpawnsAndReusesReleasedSpawn() {
        ArenaWorld world = createEmptyWorld(
            List.of(
                new SpawnPoint(1f, 1f),
                new SpawnPoint(3f, 1f),
                new SpawnPoint(5f, 1f)
            ),
            List.of()
        );

        int secondId = world.addPlayer();
        int thirdId = world.addPlayer();

        assertEquals(
            3f,
            world.getPlayers().get(secondId).getX(),
            EPSILON
        );

        assertEquals(
            5f,
            world.getPlayers().get(thirdId).getX(),
            EPSILON
        );

        world.removePlayer(secondId);

        int replacementId = world.addPlayer();

        assertEquals(
            3f,
            world.getPlayers().get(replacementId).getX(),
            EPSILON
        );
    }

    @Test
    void deadPlayerRespawnsAtAssignedSpawn() {
        ArenaWorld world = createEmptyWorld(
            List.of(
                new SpawnPoint(1f, 1f),
                new SpawnPoint(4f, 2f)
            ),
            List.of()
        );

        int targetId = world.addPlayer();
        Player target = world.getPlayers().get(targetId);

        target.setPosition(7f, 7f);
        target.takeDamage(target.getMaxHealth());

        for (int i = 0; i < 60; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertFalse(target.isAlive());

        for (int i = 0; i < 140; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertTrue(target.isAlive());
        assertEquals(target.getMaxHealth(), target.getHealth(), EPSILON);
        assertEquals(4f, target.getX(), EPSILON);
        assertEquals(2f, target.getY(), EPSILON);
    }

    @Test
    void lethalProjectileRecordsKillAndDeath() {
        ArenaWorld world = createEmptyWorld(
            List.of(
                new SpawnPoint(1f, 1f),
                new SpawnPoint(3f, 1f)
            ),
            List.of()
        );

        Player attacker = world.getPlayer();

        int targetId = world.addPlayer();
        Player target = world.getPlayers().get(targetId);

        // Leave exactly one Ember hit worth of health.
        target.takeDamage(
            target.getMaxHealth()
                - SpellType.EMBER.getDamage()
        );

        world.step(
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                true, false, false
            )
        );

        for (int i = 0; i < 20; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertFalse(target.isAlive());
        assertEquals(1, attacker.getKills());
        assertEquals(1, target.getDeaths());
    }

    @Test
    void wallStopsLightningBeforeDummy() {
        float[][] blockedHeights =
            new float[10][10];

        blockedHeights[3][1] = 1f;

        ArenaWorld world =
            new ArenaWorld(
                new ArenaDefinition(
                    new ArenaCollision(blockedHeights),
                    new SpawnPoint(1.5f, 1f),
                    List.of(
                        new SpawnPoint(5f, 1.75f)
                    )
                )
            );

        float healthBefore =
            world.getDummies().get(0).getHealth();

        PlayerCommand castLightningRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                false, false, true
            );

        StepResult result =
            world.step(castLightningRight);

        assertEquals(
            healthBefore,
            world.getDummies().get(0).getHealth(),
            EPSILON
        );
        assertEquals(1, result.events().size());

        LightningStrikeEvent strike =
            assertInstanceOf(
                LightningStrikeEvent.class,
                result.events().get(0)
            );

        assertEquals(
            LightningStrikeEvent.EndReason.TERRAIN,
            strike.endReason()
        );

        assertEquals(1.5f, strike.startX(), EPSILON);
        assertEquals(1.75f, strike.startY(), EPSILON);

        // The wall starts at x = 3 and is expanded by the
        // Lightning collision half-width of 0.1.
        assertEquals(2.9f, strike.endX(), EPSILON);
        assertEquals(1.75f, strike.endY(), EPSILON);
    }

    @Test
    void puddleStopsLightningAndBecomesElectrified() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of(
                new SpawnPoint(5f, 1.75f)
            )
        );

        Zone puddle =
            Zone.createPuddle(3f, 1.75f);

        world.getZones().add(puddle);

        float healthBefore =
            world.getDummies().get(0).getHealth();

        PlayerCommand castLightningRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                false, false, true
            );

        world.step(castLightningRight);

        assertTrue(puddle.isElectrified());

        assertEquals(
            healthBefore,
            world.getDummies().get(0).getHealth(),
            EPSILON
        );
    }

    @Test
    void unobstructedLightningEventEndsAtMaximumRange() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of()
        );

        PlayerCommand castLightningRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                false, false, true
            );

        StepResult result =
            world.step(castLightningRight);

        assertEquals(1L, result.tick());
        assertEquals(1, result.events().size());

        LightningStrikeEvent strike =
            assertInstanceOf(
                LightningStrikeEvent.class,
                result.events().get(0)
            );

        assertEquals(
            LightningStrikeEvent.EndReason.RANGE_LIMIT,
            strike.endReason()
        );

        assertEquals(1f, strike.startX(), EPSILON);
        assertEquals(1.75f, strike.startY(), EPSILON);

        assertEquals(
            1f + SpellType.LIGHTNING.getRange(),
            strike.endX(),
            EPSILON
        );

        assertEquals(1.75f, strike.endY(), EPSILON);
    }

    @Test
    void lightningOnCooldownDoesNotEmitAnotherEvent() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of()
        );

        PlayerCommand castLightningRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                false, false, true
            );

        StepResult firstResult =
            world.step(castLightningRight);

        StepResult secondResult =
            world.step(castLightningRight);

        assertEquals(1L, firstResult.tick());
        assertEquals(2L, secondResult.tick());

        assertEquals(1, firstResult.events().size());

        assertTrue(
            secondResult.events().isEmpty(),
            "A rejected cooldown cast must not create presentation"
        );
    }

    @Test
    void emberDamagesAnotherPlayerButNotItsCaster() {
        ArenaWorld world = createEmptyWorld(
            List.of(
                new SpawnPoint(1f, 1f),
                new SpawnPoint(3f, 1f)
            ),
            List.of()
        );

        Player caster = world.getPlayer();

        int targetId = world.addPlayer();
        Player target = world.getPlayers().get(targetId);
        target.setPosition(3f, 1f);

        float casterHealthBefore = caster.getHealth();
        float targetHealthBefore = target.getHealth();

        PlayerCommand castEmberRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                true, false, false
            );

        world.step(castEmberRight);

        for (int i = 0; i < 20; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(casterHealthBefore, caster.getHealth(), EPSILON);
        assertEquals(targetHealthBefore - SpellType.EMBER.getDamage(), target.getHealth(), EPSILON);
    }

    @Test
    void lightningDamagesNearestOtherPlayer() {
        ArenaWorld world = createEmptyWorld(
            List.of(
                new SpawnPoint(1f, 1f),
                new SpawnPoint(3f, 1f)
            ),
            List.of()
        );

        int targetId = world.addPlayer();
        Player target = world.getPlayers().get(targetId);
        target.setPosition(3f, 1f);

        float healthBefore = target.getHealth();

        StepResult result =
            world.step(
                new PlayerCommand(
                    0f, 0f,
                    1f, 0f,
                    false, false, true
                )
            );

        assertEquals(
            healthBefore - SpellType.LIGHTNING.getDamage(),
            target.getHealth(),
            EPSILON
        );

        LightningStrikeEvent strike =
            assertInstanceOf(
                LightningStrikeEvent.class,
                result.events().get(0)
            );

        assertEquals(LightningStrikeEvent.EndReason.PLAYER, strike.endReason());
    }

    private ArenaWorld createEmptyWorld(
        List<SpawnPoint> playerSpawns,
        List<SpawnPoint> dummySpawns
    ) {
        return new ArenaWorld(
            new ArenaDefinition(
                new ArenaCollision(new float[10][10]),
                playerSpawns,
                dummySpawns
            )
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