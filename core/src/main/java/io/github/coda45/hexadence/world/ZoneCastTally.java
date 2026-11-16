package io.github.coda45.hexadence.world;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;

// Counts the zone powers cast from the eye of the storm during a match, so
// the Maverick can pick the favorite when it appears.
public final class ZoneCastTally {

    // What the Maverick casts when nobody cast a zone from the eye.
    public static final Power DEFAULT_POWER = Powers.EMBER_CIRCLE;

    // Ordered by each power's latest cast, oldest first, for breaking ties.
    private final Map<Power, Integer> counts = new LinkedHashMap<>();

    public void record(Power power) {
        int count = count(power);

        counts.remove(power);
        counts.put(power, count + 1);
    }

    public int count(Power power) {
        return counts.getOrDefault(power, 0);
    }

    // The most-cast power. Tied powers go to whichever was cast most recently.
    public Power select() {
        Power selected = DEFAULT_POWER;

        for (Power power : counts.keySet()) {
            if (count(power) >= count(selected)) {
                selected = power;
            }
        }

        return selected;
    }

    public void clear() {
        counts.clear();
    }
}
