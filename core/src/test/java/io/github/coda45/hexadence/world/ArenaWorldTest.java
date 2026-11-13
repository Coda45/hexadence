package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.MathUtils;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.net.WorldSnapshot;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.ZoneKinds;

class ArenaWorldTest {

    private static final float EPSILON = 0.0001f;

    @Test
    void crystalWallSplitsLightBeamIntoTwoDamagingBranches() {
        float castY = 4.75f;
        float branchDistance = 3f;
        float branchX = 3f + MathUtils.cosDeg(22.5f) * branchDistance;
        float branchOffsetY = MathUtils.sinDeg(22.5f) * branchDistance;
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 4f),
            List.of(
                new SpawnPoint(branchX, castY - branchOffsetY),
                new SpawnPoint(branchX, castY + branchOffsetY)
            )
        );
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.BEAM_OF_LIGHT,
            Powers.WATER_PUDDLE,
            Powers.STONE_WALL
        )));
        Zone crystal = Zone.create(
            99,
            ZoneKinds.CRYSTAL_WALL,
            3f,
            castY,
            Zone.NO_PLAYER
        );
        world.getZones().add(crystal);

        StepResult result = world.step(
            new PlayerCommand(0f, 0f, 1f, 0f, true, false, false)
        );

        assertEquals(
            100f - Powers.BEAM_OF_LIGHT.damage()
                * Powers.BEAM_OF_LIGHT_CRYSTAL_DAMAGE_MULTIPLIER,
            world.getDummies().get(0).getHealth(),
            EPSILON
        );
        assertEquals(
            100f - Powers.BEAM_OF_LIGHT.damage()
                * Powers.BEAM_OF_LIGHT_CRYSTAL_DAMAGE_MULTIPLIER,
            world.getDummies().get(1).getHealth(),
            EPSILON
        );
        List<BeamStrikeEvent> strikes = result.events().stream()
            .filter(BeamStrikeEvent.class::isInstance)
            .map(BeamStrikeEvent.class::cast)
            .toList();
        assertEquals(3, strikes.size());
        assertTrue(strikes.stream().anyMatch(strike -> strike.branchIndex() == 1));
        assertTrue(strikes.stream().anyMatch(strike -> strike.branchIndex() == 2));
        assertEquals(
            ZoneKinds.CRYSTAL_WALL.maxHealth(),
            crystal.getHealth(),
            EPSILON
        );
    }

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
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.FIREBALL,
            Powers.WATER_PUDDLE,
            Powers.LIGHTNING_BOLT
        )));

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

        BeamStrikeEvent strike =
            assertInstanceOf(
                BeamStrikeEvent.class,
                result.events().get(0)
            );

        assertEquals(
            BeamStrikeEvent.EndReason.TERRAIN,
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
    void lightningNoLongerReactsWithAPuddle() {
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

        assertTrue(world.getZones().contains(puddle));

        assertEquals(
            healthBefore - Powers.LIGHTNING_BOLT.damage(),
            world.getDummies().get(0).getHealth(),
            EPSILON,
            "Storm no longer reacts with Water, so lightning passes through and hits the dummy"
        );
    }

    @Test
    void lightningTransformsRadiantCircleIntoDrought() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of(
                new SpawnPoint(5f, 1.75f)
            )
        );

        Zone radiantCircle =
            Zone.create(123, ZoneKinds.RADIANT_CIRCLE, 3f, 1.75f, Zone.NO_PLAYER);

        world.getZones().add(radiantCircle);

        float healthBefore =
            world.getDummies().get(0).getHealth();

        PlayerCommand castLightningRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                false, false, true
            );

        world.step(castLightningRight);

        assertTrue(world.getZones().stream().anyMatch(
            zone -> zone.getKind() == ZoneKinds.DROUGHT
        ));

        assertEquals(
            healthBefore,
            world.getDummies().get(0).getHealth(),
            EPSILON,
            "The beam should stop at the reaction instead of reaching the dummy"
        );
    }

    @Test
    void infernoDriftsInTheLightningsDirectionAfterItIsCreated() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of()
        );

        Zone emberCircle =
            Zone.create(123, ZoneKinds.EMBER_CIRCLE, 3f, 1.75f, Zone.NO_PLAYER);

        world.getZones().add(emberCircle);

        PlayerCommand castLightningRight =
            new PlayerCommand(
                0f, 0f,
                1f, 0f,
                false, false, true
            );

        world.step(castLightningRight);

        Zone inferno = world.getZones().stream()
            .filter(zone -> zone.getKind() == ZoneKinds.INFERNO)
            .findFirst()
            .orElseThrow();

        // It has not drifted yet: the step that creates it only sets its
        // velocity, and movement is applied at the start of the next step.
        assertEquals(3f, inferno.getX(), EPSILON);
        assertEquals(1.75f, inferno.getY(), EPSILON);

        int idleSteps = 10;

        for (int i = 0; i < idleSteps; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            3f + ZoneKinds.INFERNO.driftSpeed() * ArenaWorld.STEP * idleSteps,
            inferno.getX(),
            EPSILON
        );
        assertEquals(1.75f, inferno.getY(), EPSILON);
    }

    // The screen draws from host snapshots, so a drifting zone the client
    // already knows about must follow the host's position, not stay where
    // it was first seen.
    @Test
    void applyingSnapshotMovesAnExistingZoneToTheHostsPosition() {
        ArenaWorld world = createEmptyWorld(
            new SpawnPoint(1f, 1f),
            List.of()
        );
        Zone inferno =
            Zone.create(7, ZoneKinds.INFERNO, 3f, 1.75f, Zone.NO_PLAYER);
        world.getZones().add(inferno);

        world.applySnapshot(new WorldSnapshot(
            1L,
            WorldSnapshot.NO_TIMER,
            WorldSnapshot.NOT_ENDING,
            List.of(),
            List.of(),
            List.of(),
            List.of(new WorldSnapshot.ZoneState(
                7,
                ZoneKinds.INFERNO,
                4.5f,
                2.25f,
                inferno.getRadius(),
                inferno.getHealth(),
                Zone.NO_PLAYER
            ))
        ));

        Zone restored = world.getZones().get(0);
        assertEquals(inferno, restored);
        assertEquals(4.5f, restored.getX(), EPSILON);
        assertEquals(2.25f, restored.getY(), EPSILON);
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

        BeamStrikeEvent strike =
            assertInstanceOf(
                BeamStrikeEvent.class,
                result.events().get(0)
            );

        assertEquals(
            BeamStrikeEvent.EndReason.RANGE_LIMIT,
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

        BeamStrikeEvent strike =
            assertInstanceOf(
                BeamStrikeEvent.class,
                result.events().get(0)
            );

        assertEquals(BeamStrikeEvent.EndReason.PLAYER, strike.endReason());
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
    void voidRotReapplyAddsAStackAndRefreshesDuration() {
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
            "Reapplying Void Rot should refresh its timer, not land an extra tick immediately"
        );

        for (int i = 0; i < 14; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            healthAfterSecondHit - 4f,
            dummy.getHealth(),
            EPSILON,
            "The refreshed timer's first tick lands 0.5s after the second hit, now at 2 stacks (4 damage)"
        );
    }

    @Test
    void voidRotStacksDamageUpToACapThenStopsGrowing() {
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

        // Reapply once per tick interval (0.5s, matching Void Rot's own
        // cooldown) until the stack count has reached and then exceeded
        // its cap.
        for (int reapply = 0; reapply < Powers.VOID_ROT_MAX_STACKS; reapply++) {
            for (int i = 0; i < 30; i++) {
                world.step(PlayerCommand.IDLE);
            }

            world.step(castVoidRot);
        }

        float healthBeforeFinalTick = dummy.getHealth();

        for (int i = 0; i < 30; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            healthBeforeFinalTick
                - Powers.VOID_ROT.dotDamagePerTick() * Powers.VOID_ROT_MAX_STACKS,
            dummy.getHealth(),
            EPSILON,
            "Damage per tick should stop growing once stacks reach the cap"
        );
    }

    @Test
    void killCountsDamageDealtAndReportsTheKill() {
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
        target.takeDamage(target.getMaxHealth() - Powers.FIREBALL.damage());

        List<WorldEvent> events = new ArrayList<>();
        events.addAll(world.step(fireRight()).events());

        for (int i = 0; i < 20; i++) {
            events.addAll(world.step(PlayerCommand.IDLE).events());
        }

        assertFalse(target.isAlive());
        assertEquals(Powers.FIREBALL.damage(), attacker.getDamageDealt(), EPSILON);
        assertEquals(0f, target.getDamageDealt(), EPSILON);
        assertTrue(
            events.contains(new PlayerKilledEvent(targetId, 0)),
            "The kill feed needs an event naming victim and killer"
        );
    }

    @Test
    void frozenStatsIgnoreKillsButFightingGoesOn() {
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
        target.takeDamage(target.getMaxHealth() - Powers.FIREBALL.damage());

        world.setStatsFrozen(true);
        List<WorldEvent> events = new ArrayList<>(world.step(fireRight()).events());

        for (int i = 0; i < 20; i++) {
            events.addAll(world.step(PlayerCommand.IDLE).events());
        }

        // The hit still lands and kills...
        assertFalse(target.isAlive());
        assertTrue(target.hasRespawnPending());
        // ...but nothing is recorded for the results.
        assertEquals(0, attacker.getKills());
        assertEquals(0, target.getDeaths());
        assertEquals(0f, attacker.getDamageDealt(), EPSILON);
        assertFalse(
            events.stream().anyMatch(event -> event instanceof PlayerKilledEvent),
            "Kills after the clock runs out stay out of the kill feed"
        );

        world.resetForNewMatch();
        assertFalse(world.areStatsFrozen());
    }

    @Test
    void respawnedPlayerIsShieldedUntilItExpires() {
        ArenaWorld world = createEmptyWorld(
            List.of(
                new SpawnPoint(1f, 1f),
                new SpawnPoint(3f, 1f)
            ),
            List.of()
        );

        int targetId = world.addPlayer();
        Player target = world.getPlayers().get(targetId);

        assertFalse(target.hasSpawnShield(), "Entering the match is not a respawn");

        killAndRespawn(world, target);
        assertTrue(target.hasSpawnShield());

        // A hit during the shield does nothing.
        world.step(fireRight());

        for (int i = 0; i < 20; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(target.getMaxHealth(), target.getHealth(), EPSILON);
        assertEquals(0f, world.getPlayer().getDamageDealt(), EPSILON);

        // Once it runs out, the same hit lands.
        for (int i = 0; i < (int) (ArenaWorld.SPAWN_SHIELD_SECONDS / ArenaWorld.STEP); i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertFalse(target.hasSpawnShield());

        world.step(fireRight());

        for (int i = 0; i < 20; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(
            target.getMaxHealth() - Powers.FIREBALL.damage(),
            target.getHealth(),
            EPSILON
        );
    }

    @Test
    void castingDropsTheSpawnShield() {
        ArenaWorld world = createEmptyWorld(
            List.of(
                new SpawnPoint(1f, 1f),
                new SpawnPoint(3f, 1f)
            ),
            List.of()
        );

        int targetId = world.addPlayer();
        Player target = world.getPlayers().get(targetId);

        killAndRespawn(world, target);
        assertTrue(target.hasSpawnShield());

        // The respawned player fires straight up, away from everyone.
        world.step(Map.of(
            targetId,
            new PlayerCommand(0f, 0f, 0f, 1f, true, false, false)
        ));

        assertFalse(target.hasSpawnShield());
    }

    @Test
    void resetForNewMatchClearsStatsAndArena() {
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
        target.takeDamage(target.getMaxHealth() - Powers.FIREBALL.damage());

        world.step(fireRight());

        // Long enough for the kill and for the cooldown to end.
        for (int i = 0; i < 40; i++) {
            world.step(PlayerCommand.IDLE);
        }

        // Leave a projectile in flight too.
        world.step(fireRight());
        assertFalse(world.getProjectiles().isEmpty());

        world.resetForNewMatch();

        assertTrue(world.getProjectiles().isEmpty());
        assertTrue(world.getZones().isEmpty());
        assertEquals(0, attacker.getKills());
        assertEquals(0f, attacker.getDamageDealt(), EPSILON);
        assertEquals(0, target.getDeaths());
        assertTrue(target.isAlive());
        assertFalse(target.hasRespawnPending());
        assertEquals(target.getMaxHealth(), target.getHealth(), EPSILON);
        assertEquals(3f, target.getX(), EPSILON);
        assertEquals(1f, target.getY(), EPSILON);
        assertTrue(attacker.isSlotReady(0));
    }

    // Player 0 fires slot 0 to the right.
    private static PlayerCommand fireRight() {
        return new PlayerCommand(0f, 0f, 1f, 0f, true, false, false);
    }

    // Kills a player outright and steps until they are back.
    private static void killAndRespawn(ArenaWorld world, Player player) {
        player.takeDamage(player.getMaxHealth());

        for (int i = 0; i < 400 && !player.isAlive(); i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertTrue(player.isAlive(), "Player should have respawned");
    }

    private ArenaWorld createEmptyWorld(
        List<SpawnPoint> playerSpawns,
        List<SpawnPoint> dummySpawns
    ) {
        ArenaWorld world = new ArenaWorld(
            new ArenaDefinition(
                new ArenaCollision(new float[10][10]),
                playerSpawns,
                dummySpawns
            )
        );
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.FIREBALL,
            Powers.WATER_PUDDLE,
            Powers.LIGHTNING_BOLT
        )));
        return world;
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

        ArenaWorld world = new ArenaWorld(definition);
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.FIREBALL,
            Powers.WATER_PUDDLE,
            Powers.LIGHTNING_BOLT
        )));
        return world;
    }
}
