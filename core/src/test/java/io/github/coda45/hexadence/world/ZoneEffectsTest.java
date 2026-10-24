package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.zone.Reactions;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Zone effects and the newest-on-top rule.
class ZoneEffectsTest {

    private static final float EPSILON = 0.01f;
    private static final int ONE_SECOND = Math.round(1f / ArenaWorld.STEP);

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
    void coveredChargeDoesNotDischarge() {
        ArenaWorld world = world();
        TargetDummy dummy = world.getDummies().get(0);

        Zone charged = addZone(world, 1, ZoneKinds.PUDDLE, dummy.getX(), dummy.getY());
        charged.electrify(Zone.NO_PLAYER);
        addZone(world, 2, ZoneKinds.QUICKSAND, dummy.getX(), dummy.getY());

        stepIdle(world, 1);

        assertTrue(charged.isElectrified());
        assertEquals(dummy.getMaxHealth(), dummy.getHealth(), EPSILON);
    }

    @Test
    void uncoveredChargeDischargesOnce() {
        ArenaWorld world = world();
        TargetDummy dummy = world.getDummies().get(0);

        Zone charged = addZone(world, 1, ZoneKinds.PUDDLE, dummy.getX(), dummy.getY());
        charged.electrify(Zone.NO_PLAYER);

        stepIdle(world, 2);

        assertFalse(charged.isElectrified());
        assertEquals(
            dummy.getMaxHealth() - Reactions.CHARGE_DISCHARGE_DAMAGE,
            dummy.getHealth(),
            EPSILON
        );
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
