package io.github.coda45.hexadence.input;

/*
 * Intent for one simulation step. Movement and aim may be unnormalized;
 * aim is a direction from the cast point, not a mouse position.
 *
 * The cast flags ask to use the power in each loadout slot (slot 1 = left
 * mouse, slot 2 = right mouse, slot 3 = Q). They are requests: a power on
 * cooldown, or a dead player, casts nothing. Which power is in each slot is
 * the player's Loadout, which the server already knows, so the command never
 * names a specific power.
 */
public record PlayerCommand(
    float moveX,
    float moveY,
    float aimX,
    float aimY,
    boolean castSlot1,
    boolean castSlot2,
    boolean castSlot3
) {

    public static final PlayerCommand IDLE = new PlayerCommand(
        0f, 0f,
        0f, 0f,
        false, false, false
    );

    // Slots numbered from 0, matching Loadout.get().
    public boolean casts(int slot) {
        return switch (slot) {
            case 0 -> castSlot1;
            case 1 -> castSlot2;
            case 2 -> castSlot3;
            default -> false;
        };
    }
}
