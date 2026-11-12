package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Zone effects and the newest-on-top rule.
class ZoneEffectsTest {

    private static final float EPSILON = 0.01f;
    private static final int ONE_SECOND = Math.round(1f / ArenaWorld.STEP);

    @Test
    void steamViewQueryUsesTheLocalPlayersZoneOverlap() {
        ArenaWorld world = world();
        Player player = world.getPlayer();

        assertFalse(world.isPlayerOverlappingZoneKind(player, ZoneKinds.STEAM));
        addZone(
            world,
            1,
            ZoneKinds.STEAM,
            player.getCollisionX(),
            player.getCollisionY()
        );

        assertTrue(world.isPlayerOverlappingZoneKind(player, ZoneKinds.STEAM));
    }

    @Test
    void damageOverTimeHurtsWhoeverStandsInIt() {
        ArenaWorld world = world();
        TargetDummy dummy = world.getDummies().get(0);
        addZone(world, 1, ZoneKinds.LAVA, dummy.getX(), dummy.getY());

        stepIdle(world, ONE_SECOND);

        assertEquals(
            dummy.getMaxHealth() - ZoneKinds.LAVA.damagePerSecond(),
            dummy.getHealth(),
            EPSILON
        );
    }

    @Test
    void emberCircleBurnsItsOwnCasterToo() {
        ArenaWorld world = world();
        Player player = world.getPlayer();
        float ringDistance = midRingDistance();
        Zone circle = addZone(
            world, 1, ZoneKinds.EMBER_CIRCLE,
            player.getCollisionX() + ringDistance, player.getCollisionY()
        );
        circle.setDamageOwnerPlayerId(0);

        stepIdle(world, ONE_SECOND);

        assertEquals(
            player.getMaxHealth() - ZoneKinds.EMBER_CIRCLE.damagePerSecond(),
            player.getHealth(),
            EPSILON
        );
    }

    // The hollow center is a visual only: the whole disk still burns, so
    // standing dead-center is no safer than standing in the ring.
    @Test
    void emberCircleBurnsThroughItsHollowCenterToo() {
        ArenaWorld world = world();
        TargetDummy dummy = world.getDummies().get(0);
        addZone(world, 1, ZoneKinds.EMBER_CIRCLE, dummy.getX(), dummy.getY());

        stepIdle(world, ONE_SECOND);

        assertEquals(
            dummy.getMaxHealth() - ZoneKinds.EMBER_CIRCLE.damagePerSecond(),
            dummy.getHealth(),
            EPSILON
        );
    }

    @Test
    void emberCircleRingBurnsAroundTheHollowCenter() {
        ArenaWorld world = world();
        TargetDummy dummy = world.getDummies().get(0);
        addZone(
            world, 1, ZoneKinds.EMBER_CIRCLE,
            dummy.getX() + midRingDistance(), dummy.getY()
        );

        stepIdle(world, ONE_SECOND);

        assertEquals(
            dummy.getMaxHealth() - ZoneKinds.EMBER_CIRCLE.damagePerSecond(),
            dummy.getHealth(),
            EPSILON
        );
    }

    // Halfway between the hollow center and the outer edge, safely inside
    // the burning ring.
    private static float midRingDistance() {
        return (ZoneKinds.EMBER_CIRCLE.innerRadius() + ZoneKinds.EMBER_CIRCLE.radius()) / 2f;
    }

    @Test
    void slowZoneSlowsMovement() {
        ArenaWorld world = world();
        Player player = world.getPlayer();
        addZone(world, 1, ZoneKinds.QUICKSAND, player.getCollisionX(), player.getCollisionY());

        float startX = player.getX();
        world.step(new PlayerCommand(1f, 0f, 0f, 0f, false, false, false));

        float normalStep = 5f * ArenaWorld.STEP;
        assertEquals(
            normalStep * ZoneKinds.QUICKSAND.speedMultiplier(),
            player.getX() - startX,
            0.0001f
        );
    }

    @Test
    void radiantCircleSpeedsMovement() {
        ArenaWorld world = world();
        Player player = world.getPlayer();
        addZone(world, 1, ZoneKinds.RADIANT_CIRCLE, player.getCollisionX(), player.getCollisionY());

        float startX = player.getX();
        world.step(new PlayerCommand(1f, 0f, 0f, 0f, false, false, false));

        float normalStep = 5f * ArenaWorld.STEP;
        assertEquals(
            normalStep * ZoneKinds.RADIANT_CIRCLE.speedMultiplier(),
            player.getX() - startX,
            0.0001f
        );
    }

    @Test
    void vortexBouncesPlayersAndOrbitsProjectilesBeforeRelease() {
        ArenaWorld world = world();
        Player player = world.getPlayer();
        Zone vortex = addZone(
            world,
            1,
            ZoneKinds.VORTEX,
            player.getCollisionX() + 0.5f,
            player.getCollisionY()
        );
        vortex.setDamageOwnerPlayerId(0);
        Projectile projectile = new Projectile(
            99,
            vortex.getX() - 1.05f,
            vortex.getY(),
            1f,
            0f,
            Powers.FIREBALL.range(),
            Powers.FIREBALL,
            2
        );
        world.getProjectiles().add(projectile);
        float playerStartX = player.getX();

        world.step(PlayerCommand.IDLE);

        assertTrue(player.getX() < playerStartX);
        float remainingAtCapture = projectile.getRemainingTravel();
        assertEquals(2, projectile.getOwnerPlayerId());

        for (int i = 0; i < 120 && projectile.getOwnerPlayerId() == 2; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(0, projectile.getOwnerPlayerId());
        assertTrue(projectile.getRemainingTravel() > remainingAtCapture + 3f);

        float radialX = projectile.getX() - vortex.getX();
        float radialY = projectile.getY() - vortex.getY();
        float tangentDot = radialX * projectile.getDirectionX()
            + radialY * projectile.getDirectionY();
        float counterClockwiseCross = radialX * projectile.getDirectionY()
            - radialY * projectile.getDirectionX();
        assertEquals(0f, tangentDot, EPSILON);
        assertTrue(counterClockwiseCross > 0f);

        stepIdle(world, 30);
        assertTrue(player.getX() < playerStartX - 0.75f);
    }

    @Test
    void travelingVortexUsesItsAnimationSizedEffectRadius() {
        ArenaWorld world = world();
        Player player = world.getPlayer();
        Projectile travelingVortex = new Projectile(
            100,
            player.getCollisionX() + 1f,
            player.getCollisionY(),
            1f,
            0f,
            Powers.VORTEX.range(),
            Powers.VORTEX,
            0
        );
        Projectile fireball = new Projectile(
            101,
            travelingVortex.getX() - 1.05f,
            travelingVortex.getY(),
            1f,
            0f,
            Powers.FIREBALL.range(),
            Powers.FIREBALL,
            0
        );
        world.getProjectiles().add(travelingVortex);
        world.getProjectiles().add(fireball);
        float playerStartX = player.getX();

        world.step(PlayerCommand.IDLE);

        assertTrue(world.getZones().isEmpty());
        assertTrue(player.getX() < playerStartX);
        float orbitDx = fireball.getX() - travelingVortex.getX();
        float orbitDy = fireball.getY() - travelingVortex.getY();
        assertEquals(
            ZoneKinds.VORTEX.radius(),
            (float) Math.sqrt(orbitDx * orbitDx + orbitDy * orbitDy),
            EPSILON
        );
        assertEquals(0, fireball.getOwnerPlayerId());

        // Landing changes the Vortex from a projectile to a zone, but it is
        // still the same field and must not capture the fireball a second time.
        stepIdle(world, 180);
        assertTrue(fireball.hasCompletedVortexOrbit(-101));
        assertFalse(fireball.hasCompletedVortexOrbit(1));
    }

    @Test
    void singularityUsesFiveGravityBandsForProjectilesAndPlayers() {
        ArenaWorld projectileWorld = world();
        Zone singularity = addZone(
            projectileWorld,
            1,
            ZoneKinds.SINGULARITY,
            7f,
            6f
        );
        Projectile tangentProjectile = new Projectile(
            200,
            singularity.getX(),
            singularity.getY() + ZoneKinds.SINGULARITY.gravityRadius(),
            1f,
            0f,
            Powers.FIREBALL.range(),
            Powers.FIREBALL,
            0
        );
        projectileWorld.getProjectiles().add(tangentProjectile);

        projectileWorld.step(PlayerCommand.IDLE);

        assertTrue(tangentProjectile.getDirectionX() > 0.99f);
        assertTrue(tangentProjectile.getDirectionY() < 0f);
        assertTrue(tangentProjectile.getDirectionY() > -0.02f);

        Projectile trappedProjectile = new Projectile(
            201,
            singularity.getX() + 1f,
            singularity.getY(),
            -1f,
            0f,
            Powers.FIREBALL.range(),
            Powers.FIREBALL,
            0
        );
        projectileWorld.getProjectiles().add(trappedProjectile);

        boolean crossedCenter = false;
        boolean turnedBackTowardCenter = false;

        for (int i = 0; i < 90; i++) {
            projectileWorld.step(PlayerCommand.IDLE);

            if (trappedProjectile.getX() < singularity.getX()) {
                crossedCenter = true;
            }
            if (crossedCenter && trappedProjectile.getDirectionX() > 0f) {
                turnedBackTowardCenter = true;
            }
        }

        assertTrue(projectileWorld.getProjectiles().contains(trappedProjectile));
        assertTrue(crossedCenter);
        assertTrue(turnedBackTowardCenter);
        assertEquals(0f, trappedProjectile.getDistanceTraveled(), EPSILON);

        ArenaWorld playerWorld = world();
        Player player = playerWorld.getPlayer();
        float startX = player.getX();
        addZone(
            playerWorld,
            1,
            ZoneKinds.SINGULARITY,
            player.getCollisionX() + 1.5f,
            player.getCollisionY()
        );

        playerWorld.step(new PlayerCommand(
            -1f, 0f, -1f, 0f, false, false, false
        ));

        assertTrue(
            player.getX() > startX,
            "The 1-2 tile gravity band must overpower movement away from the center"
        );
    }

    @Test
    void singularityExplodesForThirtyDamageAtTheEndOfItsLifetime() {
        ArenaWorld world = new ArenaWorld(new ArenaDefinition(
            new ArenaCollision(new float[20][12]),
            new SpawnPoint(2f, 2f),
            List.of(
                new SpawnPoint(4f, 2.24f),
                new SpawnPoint(8f, 2.24f)
            )
        ));
        addZone(world, 1, ZoneKinds.SINGULARITY, 4f, 2.24f);

        for (int i = 0; i < 200 && !world.getZones().isEmpty(); i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertTrue(world.getZones().isEmpty());
        assertEquals(70f, world.getPlayer().getHealth(), EPSILON);
        assertEquals(70f, world.getDummies().get(0).getHealth(), EPSILON);
        assertEquals(100f, world.getDummies().get(1).getHealth(), EPSILON);
    }

    @Test
    void onlyTheNewestZoneCounts() {
        ArenaWorld world = world();
        TargetDummy dummy = world.getDummies().get(0);

        // Lava first, then quicksand on top: quicksand deals no damage, and
        // it is the one that counts.
        addZone(world, 1, ZoneKinds.LAVA, dummy.getX(), dummy.getY());
        addZone(world, 2, ZoneKinds.QUICKSAND, dummy.getX(), dummy.getY());

        stepIdle(world, ONE_SECOND);

        assertEquals(dummy.getMaxHealth(), dummy.getHealth(), EPSILON);
    }

    @Test
    void transformedZoneBecomesTheNewest() {
        ArenaWorld world = world();
        Player player = world.getPlayer();

        // Puddle ahead of the player, then a newer quicksand somewhere else.
        float puddleX = player.getCastX() + 2f;
        addZone(world, 1, ZoneKinds.PUDDLE, puddleX, player.getCastY());
        addZone(world, 2, ZoneKinds.QUICKSAND, 9f, 9f);

        // Fireball to the right turns the puddle into steam.
        world.step(new PlayerCommand(0f, 0f, 1f, 0f, true, false, false));
        stepIdle(world, ONE_SECOND / 2);

        List<Zone> zones = world.getZones();
        Zone newest = zones.get(zones.size() - 1);

        assertSame(ZoneKinds.STEAM, newest.getKind());
        assertEquals(1, newest.getId());
    }

    @Test
    void projectilesPassOverDerivedZones() {
        ArenaWorld world = world();
        Player player = world.getPlayer();

        addZone(world, 1, ZoneKinds.STEAM, player.getCastX() + 2f, player.getCastY());

        world.step(new PlayerCommand(0f, 0f, 1f, 0f, true, false, false));
        stepIdle(world, 12);

        // Still flying past the steam, and the steam is unchanged.
        assertEquals(1, world.getProjectiles().size());
        assertSame(ZoneKinds.STEAM, world.getZones().get(0).getKind());
    }

    @Test
    void stoneWallOnlyTakesReactionDamageAndAFullCloseShotgunBreaksIt() {
        ArenaWorld stoneWorld = world();
        Player stonePlayer = stoneWorld.getPlayer();
        stonePlayer.setLoadout(new Loadout(List.of(
            Powers.ROCK_SHARDS,
            Powers.WATER_PUDDLE,
            Powers.STONE_WALL
        )));
        Zone immuneWall = addZone(
            stoneWorld,
            1,
            ZoneKinds.STONE_WALL,
            stonePlayer.getCastX() + 1f,
            stonePlayer.getCastY()
        );

        stoneWorld.step(new PlayerCommand(0f, 0f, 1f, 0f, true, false, false));
        stepIdle(stoneWorld, 8);

        assertEquals(
            ZoneKinds.STONE_WALL.maxHealth(),
            immuneWall.getHealth(),
            EPSILON
        );

        ArenaWorld fireWorld = world();
        Player firePlayer = fireWorld.getPlayer();
        Zone fireWall = addZone(
            fireWorld,
            1,
            ZoneKinds.STONE_WALL,
            firePlayer.getCastX() + 1f,
            firePlayer.getCastY()
        );

        fireWorld.step(new PlayerCommand(0f, 0f, 1f, 0f, true, false, false));
        stepIdle(fireWorld, 8);

        assertTrue(fireWorld.getZones().contains(fireWall));
        assertEquals(
            ZoneKinds.STONE_WALL.maxHealth() - Powers.FIREBALL.damage(),
            fireWall.getHealth(),
            EPSILON
        );

        ArenaWorld waterWorld = world();
        Player waterPlayer = waterWorld.getPlayer();
        waterPlayer.setLoadout(new Loadout(List.of(
            Powers.WATER_SHOTGUN,
            Powers.WATER_PUDDLE,
            Powers.STONE_WALL
        )));
        addZone(
            waterWorld,
            1,
            ZoneKinds.STONE_WALL,
            waterPlayer.getCastX() + 1f,
            waterPlayer.getCastY()
        );

        waterWorld.step(new PlayerCommand(0f, 0f, 1f, 0f, true, false, false));
        stepIdle(waterWorld, 8);

        assertFalse(waterWorld.getZones().stream().anyMatch(
            zone -> zone.getKind() == ZoneKinds.STONE_WALL
        ));
        assertTrue(waterWorld.getZones().stream().anyMatch(
            zone -> zone.getKind() == ZoneKinds.QUICKSAND
        ));
    }

    @Test
    void crystalWallHasWallHealthAndBreaksWithoutCreatingAnotherZone() {
        ArenaWorld world = world();
        Player player = world.getPlayer();
        player.setLoadout(new Loadout(List.of(
            Powers.FIREBALL,
            Powers.WATER_PUDDLE,
            Powers.STONE_WALL
        )));
        Zone crystal = addZone(
            world,
            1,
            ZoneKinds.CRYSTAL_WALL,
            player.getCastX() + 1f,
            player.getCastY()
        );

        world.step(new PlayerCommand(0f, 0f, 1f, 0f, true, false, false));
        stepIdle(world, 8);

        assertEquals(
            ZoneKinds.CRYSTAL_WALL.maxHealth() - Powers.FIREBALL.damage(),
            crystal.getHealth(),
            EPSILON
        );

        stepIdle(world, Math.round(Powers.FIREBALL.cooldownSeconds() / ArenaWorld.STEP));
        world.step(new PlayerCommand(0f, 0f, 1f, 0f, true, false, false));
        stepIdle(world, 8);

        assertTrue(world.getZones().isEmpty());
    }

    private static ArenaWorld world() {
        return new ArenaWorld(new ArenaDefinition(
            new ArenaCollision(new float[20][12]),
            new SpawnPoint(2f, 2f),
            List.of(new SpawnPoint(10f, 8f))
        ));
    }

    private static Zone addZone(ArenaWorld world, int id, io.github.coda45.hexadence.zone.ZoneKind kind, float x, float y) {
        Zone zone = Zone.create(id, kind, x, y, Zone.NO_PLAYER);
        world.getZones().add(zone);
        return zone;
    }

    private static void stepIdle(ArenaWorld world, int steps) {
        for (int i = 0; i < steps; i++) {
            world.step(PlayerCommand.IDLE);
        }
    }
}
