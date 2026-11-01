package io.github.coda45.hexadence.power;

import java.util.HashSet;
import java.util.Set;

// What makes a loadout legal to bring into a match. The lobby checks this
// before a player can lock in, and the server checks it again, so a modified
// game cannot bring an illegal loadout.
public final class LoadoutRules {

    // A legal build mixes both forms: either one BASIC and two ZONE powers,
    // or two BASIC and one ZONE power.
    public static final int MIN_BASIC_POWERS = 1;
    public static final int MAX_BASIC_POWERS = 2;

    private LoadoutRules() {
    }

    // Why this loadout is not allowed, in words for the lobby screen, or null
    // if it is allowed.
    public static String problem(Loadout loadout) {
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

        long basics = loadout.slots().stream()
            .filter(power -> power.form() == PowerForm.BASIC)
            .count();

        if (basics < MIN_BASIC_POWERS || basics > MAX_BASIC_POWERS) {
            return "PICK 1 BASIC + 2 ZONES OR 2 BASICS + 1 ZONE";
        }

        return null;
    }

    public static boolean isAllowed(Loadout loadout) {
        return problem(loadout) == null;
    }
}
