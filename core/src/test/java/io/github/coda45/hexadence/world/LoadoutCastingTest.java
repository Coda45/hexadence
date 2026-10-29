package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Behavior;
import io.github.coda45.hexadence.power.Element;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.PowerForm;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Casting goes through the player's loadout: a slot casts whatever power is
// in it, with that power's own numbers.
class LoadoutCastingTest {

    private static final float EPSILON = 0.0001f;

    private static final PlayerCommand CAST_SLOT_1_RIGHT =
        new PlayerCommand(0f, 0f, 1f, 0f, true, false, false);

    @Test
    void slotCastsWhicheverPowerTheLoadoutHolds() {
        ArenaWorld world = worldWithDummyAt(4f, 2.75f);
        Player player = world.getPlayer();

        // Put the beam in slot 1 instead of the fireball.
        player.setLoadout(new Loadout(List.of(
            Powers.LIGHTNING_BOLT,
            Powers.WATER_PUDDLE,
            Powers.FIREBALL
        )));

        float healthBefore = world.getDummies().get(0).getHealth();
        world.step(CAST_SLOT_1_RIGHT);

        // A beam hits instantly and spawns no projectile.
        assertTrue(world.getProjectiles().isEmpty());
        assertEquals(
            healthBefore - Powers.LIGHTNING_BOLT.damage(),
            world.getDummies().get(0).getHealth(),
            EPSILON
        );
    }

    @Test
    void cooldownUsesTheSlotsPower() {
        ArenaWorld world = worldWithDummyAt(9f, 9f);
        Player player = world.getPlayer();

        world.step(CAST_SLOT_1_RIGHT);

        assertEquals(1, world.getProjectiles().size());
        assertEquals(
            Powers.FIREBALL.cooldownSeconds(),
            player.getCooldownRemaining(0),
            EPSILON
        );
        assertFalse(player.isSlotReady(0));
        assertTrue(player.isSlotReady(1));

        // Holding the button during the cooldown casts nothing more.
        world.step(CAST_SLOT_1_RIGHT);
        assertEquals(1, world.getProjectiles().size());
    }

    @Test
    void thrownZoneStopsAtTheCursorTarget() {
        ArenaWorld world = worldWithDummyAt(9f, 9f);
        Player player = world.getPlayer();
        player.setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        world.step(new PlayerCommand(
            0f, 0f,
            3f, 0f,
            true, false, false
        ));

        advanceUntilNoProjectiles(world);

        assertEquals(1, world.getZones().size());
        assertEquals(player.getCastX() + 3f, world.getZones().get(0).getX(), EPSILON);
        assertEquals(player.getCastY(), world.getZones().get(0).getY(), EPSILON);
        assertEquals(
            Powers.WATER_PUDDLE.zoneKind().radius(),
            world.getZones().get(0).getRadius(),
            EPSILON
        );
    }

    @Test
    void thrownZoneClampsTargetsBeyondMaximumRange() {
        ArenaWorld world = worldWithDummyAt(19f, 9f);
        Player player = world.getPlayer();
        player.setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        world.step(new PlayerCommand(
            0f, 0f,
            20f, 0f,
            true, false, false
        ));
        advanceUntilNoProjectiles(world);

        assertEquals(
            player.getCastX() + Powers.WATER_PUDDLE.range(),
            world.getZones().get(0).getX(),
            EPSILON
        );
    }

    @Test
    void thrownZoneRangeClampIsCircularForDiagonalTargets() {
        ArenaWorld world = worldWithDummyAt(19f, 9f);
        Player player = world.getPlayer();
        player.setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        world.step(new PlayerCommand(
            0f, 0f,
            20f, 20f,
            true, false, false
        ));
        advanceUntilNoProjectiles(world);

        float dx = world.getZones().get(0).getX() - player.getCastX();
        float dy = world.getZones().get(0).getY() - player.getCastY();

        assertEquals(
            Powers.WATER_PUDDLE.range(),
            (float) Math.hypot(dx, dy),
            EPSILON
        );
    }

    @Test
    void ordinaryProjectileStillUsesItsFullRange() {
        ArenaWorld world = worldWithDummyAt(19f, 9f);

        world.step(CAST_SLOT_1_RIGHT);

        for (int i = 0; i < 10; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertFalse(world.getProjectiles().isEmpty());
        assertTrue(world.getProjectiles().get(0).getDistanceTraveled() > 1f);
    }

    @Test
    void blockedTargetStillLaunchesThrownZoneAndSettlesBeforeWall() {
        float[][] blocked = new float[20][10];
        blocked[4][2] = 1f;
        ArenaCollision collision = new ArenaCollision(blocked);
        ArenaWorld world = new ArenaWorld(new ArenaDefinition(
            collision,
            new SpawnPoint(1f, 2f),
            List.of()
        ));
        Player player = world.getPlayer();
        player.setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        world.step(new PlayerCommand(
            0f, 0f,
            3f, 0f,
            true, false, false
        ));

        assertFalse(world.getProjectiles().isEmpty());
        assertTrue(player.getCooldownRemaining(0) > 0f);

        advanceUntilNoProjectiles(world);

        assertEquals(1, world.getZones().size());
        assertEquals(
            4f - Powers.WATER_PUDDLE.radius(),
            world.getZones().get(0).getX(),
            EPSILON
        );
    }

    @Test
    void thrownZoneKeepsEndpointWhenOnlyTheLargerZoneOverlapsWall() {
        float[][] blocked = new float[20][10];
        blocked[5][2] = 1f;
        ArenaCollision collision = new ArenaCollision(blocked);
        ArenaWorld world = new ArenaWorld(new ArenaDefinition(
            collision,
            new SpawnPoint(1f, 2f),
            List.of()
        ));
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        // The small projectile itself fits at x=4.5, so the zone remains
        // centered there even though the larger puddle overlaps the wall.
        world.step(new PlayerCommand(
            0f, 0f,
            3.5f, 0f,
            true, false, false
        ));
        advanceUntilNoProjectiles(world);

        assertEquals(1, world.getZones().size());
        assertEquals(4.5f, world.getZones().get(0).getX(), EPSILON);
    }

    @Test
    void thrownZoneIsCenteredWhereItsProjectileTouchesWall() {
        float[][] blocked = new float[20][10];
        blocked[4][2] = 1f;
        ArenaCollision collision = new ArenaCollision(blocked);
        ArenaWorld world = new ArenaWorld(new ArenaDefinition(
            collision,
            new SpawnPoint(1f, 2f),
            List.of()
        ));
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        // The selected point is clear beyond the wall, so the cast is legal,
        // but the projectile hits the wall first.
        world.step(new PlayerCommand(
            0f, 0f,
            6f, 0f,
            true, false, false
        ));
        advanceUntilNoProjectiles(world);

        assertEquals(1, world.getZones().size());
        assertEquals(
            4f - Powers.WATER_PUDDLE.radius(),
            world.getZones().get(0).getX(),
            EPSILON
        );
    }

    @Test
    void thrownZoneCannotTunnelThroughBlockedTileCorner() {
        float[][] blocked = new float[20][10];
        blocked[4][4] = 1f;
        ArenaCollision collision = new ArenaCollision(blocked);
        ArenaWorld world = new ArenaWorld(new ArenaDefinition(
            collision,
            new SpawnPoint(1f, 2f),
            List.of()
        ));
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        // This path grazes the bottom-left corner of the blocked tile.
        world.step(new PlayerCommand(
            0f, 0f,
            6f, 2.5f,
            true, false, false
        ));
        advanceUntilNoProjectiles(world);

        assertEquals(1, world.getZones().size());
        assertTrue(world.getZones().get(0).getX() < 4f);
        assertTrue(world.getZones().get(0).getY() < 4f);
    }

    @Test
    void placedZoneAppearsImmediatelyAtTheTarget() {
        Power placedPuddle = placedPuddle();
        ArenaWorld world = worldWithDummyAt(19f, 9f);
        Player player = world.getPlayer();
        player.setLoadout(new Loadout(List.of(
            placedPuddle,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        world.step(new PlayerCommand(
            0f, 0f,
            3f, 0f,
            true, false, false
        ));

        assertTrue(world.getProjectiles().isEmpty());
        assertEquals(1, world.getZones().size());
        assertEquals(player.getCastX() + 3f, world.getZones().get(0).getX(), EPSILON);
    }

    @Test
    void placedZoneAtBlockedTargetUsesProjectileLikeTerrainContact() {
        float[][] blocked = new float[20][10];
        blocked[4][2] = 1f;
        ArenaCollision collision = new ArenaCollision(blocked);
        ArenaWorld world = new ArenaWorld(new ArenaDefinition(
            collision,
            new SpawnPoint(1f, 2f),
            List.of()
        ));
        Player player = world.getPlayer();
        player.setLoadout(new Loadout(List.of(
            placedPuddle(),
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        world.step(new PlayerCommand(
            0f, 0f,
            3.5f, 0f,
            true, false, false
        ));

        assertTrue(world.getProjectiles().isEmpty());
        assertEquals(1, world.getZones().size());
        assertEquals(4f, world.getZones().get(0).getX(), EPSILON);
        assertTrue(player.getCooldownRemaining(0) > 0f);
    }

    private static Power placedPuddle() {
        return Power.define(200, "Placed Puddle")
            .element(Element.WATER)
            .form(PowerForm.ZONE)
            .behavior(Behavior.PLACED_ZONE)
            .cooldown(1f)
            .range(5f)
            .zone(ZoneKinds.PUDDLE)
            .build();
    }

    private static void advanceUntilNoProjectiles(ArenaWorld world) {
        for (int i = 0; i < 600 && !world.getProjectiles().isEmpty(); i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertTrue(world.getProjectiles().isEmpty());
    }

    private static ArenaWorld worldWithDummyAt(float x, float y) {
        return new ArenaWorld(new ArenaDefinition(
            new ArenaCollision(new float[20][10]),
            new SpawnPoint(1f, 2f),
            List.of(new SpawnPoint(x, y))
        ));
    }
}
