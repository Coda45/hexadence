package io.github.coda45.hexadence.zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.power.Powers;

// Checks the reaction table follows the design rules, so a mistake in
// Reactions or ZoneKinds fails a test instead of showing up in a match.
class ReactionsTest {

    // Raise to "exactly 2" once the interaction matrix is final.
    private static final int MAX_REACTIONS_PER_BASIC = 2;

    @Test
    void eachCellAppearsOnce() {
        Set<String> cells = new HashSet<>();

        for (Reactions.Reaction r : Reactions.ALL) {
            assertTrue(
                cells.add(r.weapon() + " " + r.cast() + " on " + r.field()),
                "Duplicate cell: " + r
            );
        }
    }

    @Test
    void noElementReactsWithItsOwnField() {
        for (Reactions.Reaction r : Reactions.ALL) {
            assertNotEquals(r.cast(), r.field(), "Diagonal cell filled: " + r);
        }
    }

    @Test
    void eachBasicReactsWithAtMostTwoFields() {
        Map<String, Integer> counts = new HashMap<>();

        for (Reactions.Reaction r : Reactions.ALL) {
            counts.merge(r.weapon() + " " + r.cast(), 1, Integer::sum);
        }

        counts.forEach((basic, count) ->
            assertTrue(
                count <= MAX_REACTIONS_PER_BASIC,
                basic + " has " + count + " reactions"
            )
        );
    }

    @Test
    void transformsProduceRegisteredDerivedZones() {
        for (Reactions.Reaction r : Reactions.ALL) {
            if (r.result() == Reactions.Result.TRANSFORM) {
                assertTrue(r.into().isDerived(), r + " must produce a derived zone");
                assertTrue(ZoneKinds.ALL.contains(r.into()), r.into() + " is not in ZoneKinds.ALL");
            }
        }
    }

    @Test
    void everyDerivedZoneComesFromSomeReaction() {
        for (ZoneKind kind : ZoneKinds.ALL) {
            if (!kind.isDerived()) {
                continue;
            }

            boolean produced = Reactions.ALL.stream()
                .anyMatch(r -> kind.equals(r.into()));

            assertTrue(produced, kind + " is never produced by a reaction");
        }
    }

    @Test
    void currentGameReactionsStillWork() {
        Reactions.Reaction steam = Reactions.find(Powers.FIREBALL, ZoneKinds.PUDDLE);
        assertEquals(Reactions.Result.TRANSFORM, steam.result());
        assertSame(ZoneKinds.STEAM, steam.into());

        Reactions.Reaction charge = Reactions.find(Powers.LIGHTNING_BOLT, ZoneKinds.PUDDLE);
        assertEquals(Reactions.Result.CHARGE, charge.result());
    }

    @Test
    void lightningTransformsEmberCircleIntoInferno() {
        Reactions.Reaction inferno = Reactions.find(Powers.LIGHTNING_BOLT, ZoneKinds.EMBER_CIRCLE);
        assertEquals(Reactions.Result.TRANSFORM, inferno.result());
        assertSame(ZoneKinds.INFERNO, inferno.into());
    }

    @Test
    void derivedZonesNeverReact() {
        for (ZoneKind kind : ZoneKinds.ALL) {
            if (kind.isDerived()) {
                assertNull(Reactions.find(Powers.FIREBALL, kind), kind.name());
                assertNull(Reactions.find(Powers.LIGHTNING_BOLT, kind), kind.name());
            }
        }
    }

    @Test
    void zonePowersDoNotReact() {
        assertNull(Reactions.find(Powers.WATER_PUDDLE, ZoneKinds.PUDDLE));
    }

    @Test
    void zoneIdsAreUniqueAndFindable() {
        Set<Integer> ids = new HashSet<>();

        for (ZoneKind kind : ZoneKinds.ALL) {
            assertTrue(kind.id() >= 1 && kind.id() <= 255, kind + " has an out-of-range ID");
            assertTrue(ids.add(kind.id()), kind + " reuses an ID");
            assertSame(kind, ZoneKinds.byId(kind.id()));
        }
    }
}
