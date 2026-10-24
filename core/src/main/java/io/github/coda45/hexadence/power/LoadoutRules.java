package io.github.coda45.hexadence.power;

import java.util.HashSet;
import java.util.Set;

// What makes a loadout legal to bring into a match. The lobby checks this
// before a player can lock in, and the server checks it again, so a modified
// game cannot bring an illegal loadout.
public final class LoadoutRules {

    // The design rule is one BASIC power and two ZONE powers. While the game has
    // too few zone powers for that, any three different powers are allowed.
    // Flip to true once at least two zone powers exist.
    public static final boolean REQUIRE_ONE_BASIC_TWO_ZONES = false;

    private LoadoutRules() {
    }

    // Why this loadout is not allowed, in words for the lobby screen, or null
    // if it is allowed.
    public static String problem(Loadout loadout) {
        return problem(loadout, REQUIRE_ONE_BASIC_TWO_ZONES);
    }

    // The same check with the basic/zone rule switched on or off, so tests can
    // cover the strict rule before the game turns it on.
    static String problem(Loadout loadout, boolean requireOneBasicTwoZones) {
        Set<Power> distinct = new HashSet<>(loadout.slots());

        if (distinct.size() != Loadout.SIZE) {
            return "PICK 3 DIFFERENT POWERS";
        }

        for (Power power : loadout.slots()) {
            if (!Powers.ALL.contains(power)) {
                return "UNKNOWN POWER: " + power.name();
            }
        }

        Weapon weapon = loadout.get(0).weapon();

        for (Power power : loadout.slots()) {
            if (power.weapon() != weapon) {
                return "ONE WEAPON PER LOADOUT";
            }
        }

        if (requireOneBasicTwoZones) {
            long basics = loadout.slots().stream()
                .filter(power -> power.form() == PowerForm.BASIC)
                .count();

            if (basics != 1) {
                return "PICK 1 BASIC + 2 ZONE POWERS";
            }
        }

        return null;
    }

    public static boolean isAllowed(Loadout loadout) {
        return problem(loadout) == null;
    }
}
