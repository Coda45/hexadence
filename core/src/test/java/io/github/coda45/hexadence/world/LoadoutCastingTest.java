package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Powers;

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
    void thrownZoneLeavesItsDefinedZone() {
        ArenaWorld world = worldWithDummyAt(9f, 9f);
        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.WATER_PUDDLE,
            Powers.FIREBALL,
            Powers.LIGHTNING_BOLT
        )));

        world.step(CAST_SLOT_1_RIGHT);

        // Let it fly to the end of its range.
        int steps = (int) Math.ceil(
            Powers.WATER_PUDDLE.range() / Powers.WATER_PUDDLE.speed() / ArenaWorld.STEP
        ) + 5;

        for (int i = 0; i < steps; i++) {
            world.step(PlayerCommand.IDLE);
        }

        assertEquals(1, world.getZones().size());
        assertEquals(
            Powers.WATER_PUDDLE.zoneRadius(),
            world.getZones().get(0).getRadius(),
            EPSILON
        );
    }

    private static ArenaWorld worldWithDummyAt(float x, float y) {
        return new ArenaWorld(new ArenaDefinition(
            new ArenaCollision(new float[20][10]),
            new SpawnPoint(1f, 2f),
            List.of(new SpawnPoint(x, y))
        ));
    }
}
