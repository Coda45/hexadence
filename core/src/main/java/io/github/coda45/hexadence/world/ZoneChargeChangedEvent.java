package io.github.coda45.hexadence.world;

public record ZoneChargeChangedEvent(
    int zoneId,
    boolean electrified
) implements WorldEvent {

    public ZoneChargeChangedEvent {
        if (zoneId < 0) {
            throw new IllegalArgumentException(
                "Zone event requires an assigned ID"
            );
        }
    }
}