package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Powers;

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
                - Powers.LIGHTNING_BOLT.damage(),
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
                - Powers.FIREBALL.damage()
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
            Zone.createPuddle(123, 3f, 1.75f, Zone.NO_PLAYER);

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
            1f + Powers.LIGHTNING_BOLT.range(),
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
        assertEquals(targetHealthBefore - Powers.FIREBALL.damage(), target.getHealth(), EPSILON);
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
            healthBefore - Powers.LIGHTNING_BOLT.damage(),
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

    @Test
    void waterShotgunFiresPelletsEvenlySpreadAcrossTheCone() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of()
        );

        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.WATER_SHOTGUN,
            Powers.WATER_PUDDLE,
            Powers.LIGHTNING_BOLT
        )));

        PlayerCommand castShotgun = new PlayerCommand(
            0f, 0f,
            1f, 0f,
            true, false, false
        );

        world.step(castShotgun);

        List<Projectile> pellets = world.getProjectiles();
        assertEquals(Powers.WATER_SHOTGUN.pelletCount(), pellets.size());

        float halfSpreadRadians =
            (float) Math.toRadians(Powers.WATER_SHOTGUN.spreadDegrees() / 2f);

        Projectile first = pellets.get(0);
        assertEquals((float) Math.cos(-halfSpreadRadians), first.getDirectionX(), EPSILON);
        assertEquals((float) Math.sin(-halfSpreadRadians), first.getDirectionY(), EPSILON);

        Projectile last = pellets.get(pellets.size() - 1);
        assertEquals((float) Math.cos(halfSpreadRadians), last.getDirectionX(), EPSILON);
        assertEquals((float) Math.sin(halfSpreadRadians), last.getDirectionY(), EPSILON);
    }

    @Test
    void waterShotgunPelletsCanEachDamageTheSameNearbyTarget() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of(new SpawnPoint(1.3f, 1.75f))
        );

        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.WATER_SHOTGUN,
            Powers.WATER_PUDDLE,
            Powers.LIGHTNING_BOLT
        )));

        TargetDummy dummy = world.getDummies().get(0);
        float healthBefore = dummy.getHealth();

        PlayerCommand castShotgun = new PlayerCommand(
            0f, 0f,
            1f, 0f,
            true, false, false
        );

        world.step(castShotgun);

        assertEquals(
            healthBefore
                - Powers.WATER_SHOTGUN.pelletCount() * Powers.WATER_SHOTGUN.damage(),
            dummy.getHealth(),
            EPSILON,
            "A target standing close to the caster can be hit by more than one pellet"
        );

        assertTrue(
            world.getProjectiles().isEmpty(),
            "Every pellet should have hit or been consumed this step"
        );
    }

    @Test
    void voidRotDealsInitialHitThenFourTicksOfDamageOverTime() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of(new SpawnPoint(1.3f, 1.75f))
        );

        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.VOID_ROT,
            Powers.WATER_PUDDLE,
            Powers.LIGHTNING_BOLT
        )));

        TargetDummy dummy = world.getDummies().get(0);
        float healthBeforeCast = dummy.getHealth();

        PlayerCommand castVoidRot = new PlayerCommand(
            0f, 0f,
            1f, 0f,
            true, false, false
        );

        world.step(castVoidRot);

        assertEquals(
            healthBeforeCast - Powers.VOID_ROT.damage(),
            dummy.getHealth(),
            EPSILON,
            "Void Rot should deal its direct hit damage immediately"
        );

        float healthAfterHit = dummy.getHealth();

        for (int i = 0; i < 29; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            healthAfterHit - 2f,
            dummy.getHealth(),
            EPSILON,
            "The first damage-over-time tick should land 0.5s after the hit"
        );

        for (int i = 0; i < 90; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            healthAfterHit - 8f,
            dummy.getHealth(),
            EPSILON,
            "Four ticks of 2 damage should land over the 2-second duration"
        );

        float healthAfterDot = dummy.getHealth();

        for (int i = 0; i < 60; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            healthAfterDot,
            dummy.getHealth(),
            EPSILON,
            "Damage over time should stop once its duration ends"
        );
    }

    @Test
    void voidRotReapplyRefreshesInsteadOfStackingDamageOverTime() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of(new SpawnPoint(1.3f, 1.75f))
        );

        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.VOID_ROT,
            Powers.WATER_PUDDLE,
            Powers.LIGHTNING_BOLT
        )));

        TargetDummy dummy = world.getDummies().get(0);

        PlayerCommand castVoidRot = new PlayerCommand(
            0f, 0f,
            1f, 0f,
            true, false, false
        );

        world.step(castVoidRot);

        for (int i = 0; i < 29; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(100f - 4f - 2f, dummy.getHealth(), EPSILON);

        for (int i = 0; i < 14; i++) {
            world.step(PlayerCommand.IDLE);
        }

        world.step(castVoidRot);

        float healthAfterSecondHit = dummy.getHealth();

        assertEquals(100f - 4f - 2f - 4f, healthAfterSecondHit, EPSILON);

        for (int i = 0; i < 15; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            healthAfterSecondHit,
            dummy.getHealth(),
            EPSILON,
            "Reapplying Void Rot should refresh its timer, not stack a second one"
        );

        for (int i = 0; i < 14; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            healthAfterSecondHit - 2f,
            dummy.getHealth(),
            EPSILON,
            "The refreshed timer's first tick lands 0.5s after the second hit"
        );
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