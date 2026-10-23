package io.github.coda45.hexadence.power;

import java.util.List;
import java.util.Objects;

// The three powers a player brings into a match, one per slot. Slot 1 is cast
// with the left mouse button, slot 2 with the right, slot 3 with Q.
//
// The intended rule is one BASIC power and two ZONE powers, chosen in the
// lobby. Until lobby selection exists, everyone gets DEFAULT, which keeps the
// three powers the game has so far.
public record Loadout(List<Power> slots) {

    public static final int SIZE = 3;

    public static final Loadout DEFAULT = new Loadout(List.of(
        Powers.FIREBALL,
        Powers.WATER_PUDDLE,
        Powers.LIGHTNING_BOLT
    ));

    public Loadout {
        slots = List.copyOf(slots);

        if (slots.size() != SIZE) {
            throw new IllegalArgumentException(
                "A loadout has exactly " + SIZE + " powers, not " + slots.size()
            );
        }

        slots.forEach(Objects::requireNonNull);
    }

    // Slots are numbered from 0 in code (0 = left mouse, 1 = right, 2 = Q).
    public Power get(int slot) {
        return slots.get(slot);
    }
}
