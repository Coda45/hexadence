package io.github.coda45.hexadence.world;

import java.util.Objects;

import io.github.coda45.hexadence.power.Power;

// One instantaneous sample of a beam power. Held beams emit these at their
// configured tick interval; presentation can keep adjacent samples visually
// continuous without making rendering authoritative for damage.
public record BeamStrikeEvent(
    int ownerPlayerId,
    Power power,
    float startX,
    float startY,
    float endX,
    float endY,
    EndReason endReason,
    int branchIndex
) implements WorldEvent {

    public enum EndReason {
        RANGE_LIMIT,
        TERRAIN,
        WALL,
        ZONE_REACTION,
        DUMMY,
        PLAYER
    }

    public BeamStrikeEvent {
        Objects.requireNonNull(power);
        Objects.requireNonNull(endReason);

        if (branchIndex < 0 || branchIndex > 2) {
            throw new IllegalArgumentException("Beam branch index must be 0, 1, or 2");
        }
    }

    public BeamStrikeEvent(
        int ownerPlayerId,
        Power power,
        float startX,
        float startY,
        float endX,
        float endY,
        EndReason endReason
    ) {
        this(ownerPlayerId, power, startX, startY, endX, endY, endReason, 0);
    }
}
