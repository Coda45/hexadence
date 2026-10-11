package io.github.coda45.hexadence.world;

// Immutable simulation facts that presentation may consume once.
public sealed interface WorldEvent
    permits LightningStrikeEvent,
            ZoneChargeChangedEvent {
}
