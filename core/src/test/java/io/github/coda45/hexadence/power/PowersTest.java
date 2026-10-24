package io.github.coda45.hexadence.power;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.zone.ZoneKinds;

// Checks the power definitions follow the rules, so a mistake in Powers
// fails a test instead of showing up in a match.
class PowersTest {

    @Test
    void everyPowerCanBeFoundByItsId() {
        for (Power power : Powers.ALL) {
            assertSame(power, Powers.byId(power.id()));
        }
    }

    @Test
    void idsAreUniqueAndFitInOneByte() {
        Set<Integer> ids = new HashSet<>();

        for (Power power : Powers.ALL) {
            assertTrue(power.id() >= 1 && power.id() <= 255, power + " has an out-of-range ID");
            assertTrue(ids.add(power.id()), power + " reuses an ID");
        }
    }

    @Test
    void eachElementHasAtMostOneBasicAndOneZonePerWeapon() {
        Set<String> seen = new HashSet<>();

        for (Power power : Powers.ALL) {
            String key = power.weapon() + " " + power.element() + " " + power.form();

            assertTrue(seen.add(key), "More than one " + key + " power: " + power);
        }
    }

    @Test
    void zoneCreatingPowersAreZoneForm() {
        for (Power power : Powers.ALL) {
            if (power.behavior().createsZone()) {
                assertEquals(PowerForm.ZONE, power.form(), power.name());
            }
        }
    }

    @Test
    void defaultLoadoutUsesRegisteredPowers() {
        for (Power power : Loadout.DEFAULT.slots()) {
            assertTrue(Powers.ALL.contains(power), power + " is not in Powers.ALL");
        }
    }

    @Test
    void unknownIdFindsNothing() {
        assertEquals(null, Powers.byId(254));
    }

    @Test
    void definitionsMissingWhatTheirBehaviorNeedsAreRejected() {
        // A thrown zone with no zone(...)
        assertThrows(IllegalStateException.class, () ->
            Power.define(200, "Broken Zone")
                .element(Element.WATER).form(PowerForm.ZONE)
                .behavior(Behavior.THROWN_ZONE)
                .cooldown(1f).speed(5f).range(5f).radius(0.1f)
                .build()
        );

        // A projectile with no speed
        assertThrows(IllegalStateException.class, () ->
            Power.define(201, "Stuck Bolt")
                .element(Element.FIRE).form(PowerForm.BASIC)
                .behavior(Behavior.PROJECTILE)
                .cooldown(1f).range(5f).radius(0.1f)
                .build()
        );

        // A zone power placing a derived zone
        assertThrows(IllegalStateException.class, () ->
            Power.define(203, "Steam Thrower")
                .element(Element.WATER).form(PowerForm.ZONE)
                .behavior(Behavior.THROWN_ZONE)
                .cooldown(1f).speed(5f).range(5f).radius(0.1f)
                .zone(ZoneKinds.STEAM)
                .build()
        );

        // A zone power placing another element's zone
        assertThrows(IllegalStateException.class, () ->
            Power.define(204, "Wrong Puddle")
                .element(Element.FIRE).form(PowerForm.ZONE)
                .behavior(Behavior.THROWN_ZONE)
                .cooldown(1f).speed(5f).range(5f).radius(0.1f)
                .zone(ZoneKinds.PUDDLE)
                .build()
        );

        // A beam that sets a zone
        assertThrows(IllegalStateException.class, () ->
            Power.define(202, "Zone Beam")
                .element(Element.LIGHT).form(PowerForm.BASIC)
                .behavior(Behavior.BEAM)
                .cooldown(1f).range(5f).radius(0.1f)
                .zone(ZoneKinds.PUDDLE)
                .build()
        );
    }

    @Test
    void loadoutsHoldExactlyThreePowers() {
        assertThrows(IllegalArgumentException.class, () ->
            new Loadout(List.of(Powers.FIREBALL))
        );
        assertFalse(Loadout.DEFAULT.slots().isEmpty());
    }
}
