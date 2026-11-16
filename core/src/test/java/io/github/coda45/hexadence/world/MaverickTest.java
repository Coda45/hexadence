package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.entity.Maverick;
import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.net.SnapshotFactory;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.ZoneKinds;

// The Maverick circles the middle of the arena and rains the zone power that
// was cast most from the eye of the storm. Its zones' damage and kills are
// its own, never a player's.
class MaverickTest {

    private static final float EPSILON = 0.001f;
    private static final int ONE_SECOND = Math.round(1f / ArenaWorld.STEP);

    private static final PlayerCommand CAST_FIREBALL =
        new PlayerCommand(0f, 0f, 1f, 0f, true, false, false);
    private static final PlayerCommand CAST_EMBER_CIRCLE =
        new PlayerCommand(0f, 0f, 1f, 0f, false, true, false);
    private static final PlayerCommand CAST_WATER_PUDDLE =
        new PlayerCommand(0f, 0f, 1f, 0f, false, false, true);

    @Test
    void onlyZonesCastFromInsideTheEyeAreCounted() {
        ArenaWorld world = worldWithPlayerAt(7f, 6f);

        world.step(CAST_EMBER_CIRCLE);
        // Refused by its cooldown.
        world.step(CAST_EMBER_CIRCLE);
        // Not a zone.
        world.step(CAST_FIREBALL);

        assertEquals(1, world.getProjectiles().size());
        assertEquals(1, world.getEyeCasts().count(Powers.EMBER_CIRCLE));
        assertEquals(0, world.getEyeCasts().count(Powers.FIREBALL));

        // Between the pillars, but past the edge of the circle.
        ArenaWorld outside = worldWithPlayerAt(4.5f, 3.5f);

        outside.step(CAST_EMBER_CIRCLE);

        assertEquals(1, outside.getZones().size());
        assertEquals(0, outside.getEyeCasts().count(Powers.EMBER_CIRCLE));
    }

    @Test
    void mostCastPowerIsSelectedAndTiesGoToTheLatestCast() {
        ZoneCastTally tally = new ZoneCastTally();

        // Fire is the default when nothing was cast from the eye.
        assertSame(Powers.EMBER_CIRCLE, tally.select());

        tally.record(Powers.WATER_PUDDLE);
        tally.record(Powers.NULL_ZONE);
        tally.record(Powers.NULL_ZONE);
        assertSame(Powers.NULL_ZONE, tally.select());

        tally.record(Powers.WATER_PUDDLE);
        // More recent than both, but not tied for the lead.
        tally.record(Powers.STONE_WALL);
        assertSame(Powers.WATER_PUDDLE, tally.select());

        tally.clear();
        assertSame(Powers.EMBER_CIRCLE, tally.select());
    }

    @Test
    void spawnsOnceCastingTheFavoriteAndLeavesWithTheMatch() {
        ArenaWorld world = worldWithPlayerAt(7f, 6f);
        assertNull(world.getMaverick());

        world.step(CAST_WATER_PUDDLE);
        world.spawnMaverick();
        Maverick maverick = world.getMaverick();

        // A later cast from the eye changes nothing.
        world.step(CAST_EMBER_CIRCLE);
        world.spawnMaverick();

        assertSame(maverick, world.getMaverick());
        assertSame(Powers.WATER_PUDDLE, maverick.getPower());

        world.resetForNewMatch();

        assertNull(world.getMaverick());
        assertEquals(0, world.getEyeCasts().count(Powers.WATER_PUDDLE));
    }

    @Test
    void circlesTheMiddleOnceEveryOrbit() {
        ArenaWorld world = worldWithPlayerAt(2f, 2f);
        world.spawnMaverick();
        Maverick maverick = world.getMaverick();
        StormEye eye = world.getStormEye();

        float startX = maverick.getX();
        float startY = maverick.getY();
        int lapSteps = Math.round(Maverick.ORBIT_SECONDS / ArenaWorld.STEP);

        for (int i = 1; i <= lapSteps; i++) {
            world.step(PlayerCommand.IDLE);

            assertEquals(
                Maverick.ORBIT_RADIUS,
                distance(eye.centerX(), eye.centerY(), maverick.getX(), maverick.getY()),
                EPSILON
            );

            // Half a lap in, it is on the far side of the middle.
            if (i == lapSteps / 2) {
                assertEquals(2f * eye.centerX() - startX, maverick.getX(), 0.01f);
                assertEquals(2f * eye.centerY() - startY, maverick.getY(), 0.01f);
            }
        }

        assertEquals(startX, maverick.getX(), 0.01f);
        assertEquals(startY, maverick.getY(), 0.01f);
    }

    @Test
    void castsOutwardAtHalfThePowersCooldown() {
        ArenaWorld world = worldWithPlayerAt(2f, 2f);
        world.spawnMaverick();
        StormEye eye = world.getStormEye();

        // Ember Circle has a 5 s cooldown, so a cast every 2.5 s.
        int interval = Math.round(
            Powers.EMBER_CIRCLE.cooldownSeconds() / 2f / ArenaWorld.STEP
        );

        // The first cast comes one interval after it appears.
        Set<Integer> zoneIds = stepCollectingMaverickZones(world, interval - 2);
        assertEquals(0, zoneIds.size());

        zoneIds.addAll(stepCollectingMaverickZones(world, 4));
        assertEquals(1, zoneIds.size());

        // On the line from the middle through the Maverick.
        Zone zone = newestMaverickZone(world);
        assertSame(ZoneKinds.EMBER_CIRCLE, zone.getKind());
        assertEquals(
            Maverick.ORBIT_RADIUS + Maverick.CAST_DISTANCE,
            distance(eye.centerX(), eye.centerY(), zone.getX(), zone.getY()),
            EPSILON
        );

        zoneIds.addAll(stepCollectingMaverickZones(world, 2 * interval));
        assertEquals(3, zoneIds.size());
    }

    @Test
    void thrownPowerIsPlacedWithoutAProjectile() {
        ArenaWorld world = worldWithPuddleMaverick();

        while (newestMaverickZone(world) == null) {
            world.step(PlayerCommand.IDLE);
        }

        assertSame(ZoneKinds.PUDDLE, newestMaverickZone(world).getKind());
        assertTrue(world.getProjectiles().isEmpty());
    }

    @Test
    void killByAMaverickZoneIsCreditedToTheMaverick() {
        ArenaWorld world = worldWithPlayerAt(2f, 2f);
        Player victim = world.getPlayer();
        Player bystander = world.getPlayers().get(world.addPlayer());
        world.spawnMaverick();
        Maverick maverick = world.getMaverick();

        float healthBefore = victim.getHealth();
        List<WorldEvent> events = new ArrayList<>();

        // Keep the victim in the Maverick's newest zone until it kills them.
        for (int i = 0; i < 60 * ONE_SECOND && victim.isAlive(); i++) {
            standInNewestMaverickZone(world, victim);
            events.addAll(world.step(PlayerCommand.IDLE).events());
        }

        assertFalse(victim.isAlive());
        assertEquals(1, victim.getDeaths());
        assertEquals(1, maverick.getKills());
        assertEquals(healthBefore, maverick.getDamageDealt(), EPSILON);

        // Nobody else gets anything for it.
        assertEquals(0, bystander.getKills());
        assertEquals(0f, bystander.getDamageDealt(), EPSILON);

        PlayerKilledEvent killed = events.stream()
            .filter(PlayerKilledEvent.class::isInstance)
            .map(PlayerKilledEvent.class::cast)
            .findFirst()
            .orElseThrow();

        assertEquals(new PlayerKilledEvent(0, Maverick.SOURCE_ID), killed);
        assertTrue(killed.killedByMaverick());
        assertFalse(new PlayerKilledEvent(1, 0).killedByMaverick());
    }

    @Test
    void nothingIsCastOrCountedOnceTheClockHasRunOut() {
        ArenaWorld world = worldWithPlayerAt(2f, 2f);
        Player victim = world.getPlayer();
        world.spawnMaverick();
        Maverick maverick = world.getMaverick();

        Set<Integer> zoneIds = new LinkedHashSet<>();

        while (zoneIds.isEmpty()) {
            zoneIds.addAll(stepCollectingMaverickZones(world, 1));
        }

        world.setStatsFrozen(true);

        float healthBefore = victim.getHealth();
        float x = maverick.getX();

        for (int i = 0; i < 6 * ONE_SECOND; i++) {
            standInNewestMaverickZone(world, victim);
            zoneIds.addAll(stepCollectingMaverickZones(world, 1));
        }

        // The zone still burns and the Maverick still circles.
        assertTrue(victim.getHealth() < healthBefore);
        assertTrue(Math.abs(maverick.getX() - x) > EPSILON);

        assertEquals(1, zoneIds.size());
        assertEquals(0f, maverick.getDamageDealt(), EPSILON);
    }

    @Test
    void clientCopiesTheServersMaverickFromSnapshots() {
        ArenaWorld server = worldWithPuddleMaverick();
        ArenaWorld client = worldWithPlayerAt(7f, 6f);

        stepCollectingMaverickZones(server, ONE_SECOND);
        client.applySnapshot(
            SnapshotFactory.capture(server, server.step(PlayerCommand.IDLE))
        );

        Maverick copy = client.getMaverick();
        assertEquals(server.getMaverick().getX(), copy.getX(), EPSILON);
        assertEquals(server.getMaverick().getY(), copy.getY(), EPSILON);
        assertSame(Powers.WATER_PUDDLE, copy.getPower());
        assertEquals(
            server.getMaverick().getSecondsUntilCast(),
            copy.getSecondsUntilCast(),
            EPSILON
        );

        // When the next match starts the server has none, so neither does
        // the client.
        server.resetForNewMatch();
        client.applySnapshot(
            SnapshotFactory.capture(server, server.step(PlayerCommand.IDLE))
        );

        assertNull(client.getMaverick());
    }

    // A 14x12 room whose four pillars enclose a 6x6 floor around (7, 6),
    // giving an eye of radius about 2.93.
    private static ArenaWorld worldWithPlayerAt(float x, float y) {
        float[][] blockedHeights = new float[14][12];
        blockedHeights[3][2] = 1f;
        blockedHeights[10][2] = 1f;
        blockedHeights[3][9] = 1f;
        blockedHeights[10][9] = 1f;

        ArenaWorld world = new ArenaWorld(new ArenaDefinition(
            new ArenaCollision(blockedHeights),
            List.of(new SpawnPoint(x, y), new SpawnPoint(12.5f, 1.5f)),
            List.of()
        ));

        world.getPlayer().setLoadout(new Loadout(List.of(
            Powers.FIREBALL,
            Powers.EMBER_CIRCLE,
            Powers.WATER_PUDDLE
        )));

        return world;
    }

    // The player throws one Water Puddle from the eye, so that is what the
    // Maverick casts. The arena is empty again by the time it appears.
    private static ArenaWorld worldWithPuddleMaverick() {
        ArenaWorld world = worldWithPlayerAt(7f, 6f);

        world.step(CAST_WATER_PUDDLE);
        stepCollectingMaverickZones(world, ONE_SECOND);
        world.getZones().clear();
        world.spawnMaverick();

        return world;
    }

    // Steps the world with no input. Returns the ID of every Maverick zone
    // seen along the way.
    private static Set<Integer> stepCollectingMaverickZones(ArenaWorld world, int steps) {
        Set<Integer> zoneIds = new LinkedHashSet<>();

        for (int i = 0; i < steps; i++) {
            world.step(PlayerCommand.IDLE);

            for (Zone zone : world.getZones()) {
                if (zone.getDamageOwnerPlayerId() == Maverick.SOURCE_ID) {
                    zoneIds.add(zone.getId());
                }
            }
        }

        return zoneIds;
    }

    // Null while there is none.
    private static Zone newestMaverickZone(ArenaWorld world) {
        Zone newest = null;

        for (Zone zone : world.getZones()) {
            if (zone.getDamageOwnerPlayerId() == Maverick.SOURCE_ID) {
                newest = zone;
            }
        }

        return newest;
    }

    // Puts the player's feet on the middle of the newest Maverick zone, if
    // there is one.
    private static void standInNewestMaverickZone(ArenaWorld world, Player player) {
        Zone zone = newestMaverickZone(world);

        if (zone != null) {
            player.setPosition(
                zone.getX() - (player.getCollisionX() - player.getX()),
                zone.getY() - (player.getCollisionY() - player.getY())
            );
        }
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }
}
