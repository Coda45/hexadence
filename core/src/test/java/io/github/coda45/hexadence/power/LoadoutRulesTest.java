package io.github.coda45.hexadence.power;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class LoadoutRulesTest {

    @Test
    void defaultLoadoutIsAllowed() {
        assertNull(LoadoutRules.problem(Loadout.DEFAULT));
    }

    @Test
    void repeatedPowersAreNotAllowed() {
        Loadout twice = new Loadout(List.of(
            Powers.FIREBALL, Powers.FIREBALL, Powers.WATER_PUDDLE
        ));

        assertNotNull(LoadoutRules.problem(twice));
    }

    @Test
    void strictRuleNeedsOneBasicAndTwoZones() {
        // The default has two basics and one zone, so the strict rule refuses it.
        assertNotNull(LoadoutRules.problem(Loadout.DEFAULT, true));
        assertNull(LoadoutRules.problem(Loadout.DEFAULT, false));
    }
}
