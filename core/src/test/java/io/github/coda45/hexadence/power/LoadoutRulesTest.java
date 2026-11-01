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
    void allowsEitherMixedOneTwoComposition() {
        Loadout twoBasics = new Loadout(List.of(
            Powers.FIREBALL, Powers.LIGHTNING_BOLT, Powers.WATER_PUDDLE
        ));
        Loadout threeBasics = new Loadout(List.of(
            Powers.FIREBALL, Powers.LIGHTNING_BOLT, Powers.ROCK_SHARDS
        ));
        Loadout threeZones = new Loadout(List.of(
            Powers.WATER_PUDDLE, Powers.STONE_WALL, Powers.VORTEX
        ));

        assertNull(LoadoutRules.problem(Loadout.DEFAULT));
        assertNull(LoadoutRules.problem(twoBasics));
        assertNotNull(LoadoutRules.problem(threeBasics));
        assertNotNull(LoadoutRules.problem(threeZones));
    }
}
