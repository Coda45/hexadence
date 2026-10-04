package io.github.coda45.hexadence.world;

import java.util.Objects;

public record LightningStrikeEvent(
    float startX,
    float startY,
    float endX,
    float endY,
    EndReason endReason
) implements WorldEvent {

    public enum EndReason {
        RANGE_LIMIT,
        TERRAIN,
        PUDDLE,
        DUMMY
    }

    public LightningStrikeEvent {
        Objects.requireNonNull(endReason);
    }
}