package io.github.coda45.hexadence.world;

import io.github.coda45.hexadence.entity.Maverick;

// A player died. Drives the kill feed.
//
// killerPlayerId is the player credited with the kill, Maverick.SOURCE_ID
// when a Maverick zone did it, or NO_KILLER when nobody else gets credit
// (the victim's own power, for example).
public record PlayerKilledEvent(
    int victimPlayerId,
    int killerPlayerId
) implements WorldEvent {

    public static final int NO_KILLER = -1;

    public boolean hasKiller() {
        return killerPlayerId != NO_KILLER && killerPlayerId != victimPlayerId;
    }

    public boolean killedByMaverick() {
        return killerPlayerId == Maverick.SOURCE_ID;
    }
}
