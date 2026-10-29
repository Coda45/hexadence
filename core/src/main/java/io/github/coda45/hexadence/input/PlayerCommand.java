package io.github.coda45.hexadence.input;

import java.util.ArrayList;
import java.util.List;

/*
 * Intent for one simulation step. Movement and aim may be unnormalized;
 * aim is an offset from the cast point to the current mouse position.
 *
 * Each CastRequest asks to use one loadout slot and preserves the cursor
 * offset from the moment that cast was sampled. Which power is in each slot
 * is the player's server-owned Loadout, so the command never names a power.
 */
public record PlayerCommand(
    float moveX,
    float moveY,
    float aimX,
    float aimY,
    List<CastRequest> casts
) {

    public PlayerCommand {
        casts = List.copyOf(casts);
    }

    public static final PlayerCommand IDLE = new PlayerCommand(
        0f, 0f,
        0f, 0f,
        List.of()
    );

    // Compatibility constructor for local code and tests that express the
    // three current bindings as booleans. Every requested slot uses the aim
    // offset supplied in this command.
    public PlayerCommand(
        float moveX,
        float moveY,
        float aimX,
        float aimY,
        boolean castSlot1,
        boolean castSlot2,
        boolean castSlot3
    ) {
        this(
            moveX,
            moveY,
            aimX,
            aimY,
            requestsFor(
                aimX,
                aimY,
                castSlot1,
                castSlot2,
                castSlot3
            )
        );
    }

    public boolean castSlot1() {
        return castFor(0) != null;
    }

    public boolean castSlot2() {
        return castFor(1) != null;
    }

    public boolean castSlot3() {
        return castFor(2) != null;
    }

    // Slots are numbered from 0, matching Loadout.get().
    public CastRequest castFor(int slot) {
        for (CastRequest cast : casts) {
            if (cast.slot() == slot) {
                return cast;
            }
        }

        return null;
    }

    private static List<CastRequest> requestsFor(
        float aimX,
        float aimY,
        boolean castSlot1,
        boolean castSlot2,
        boolean castSlot3
    ) {
        List<CastRequest> requests = new ArrayList<>(3);

        if (castSlot1) {
            requests.add(new CastRequest(0, aimX, aimY));
        }
        if (castSlot2) {
            requests.add(new CastRequest(1, aimX, aimY));
        }
        if (castSlot3) {
            requests.add(new CastRequest(2, aimX, aimY));
        }

        return requests;
    }
}
