package io.github.coda45.hexadence.world;

import java.util.Objects;

import io.github.coda45.hexadence.power.Power;

// Ends the persistent presentation for one caster's held beam immediately
// when the binding is released, the caster dies, or their power changes.
public record BeamChannelEndEvent(
    int ownerPlayerId,
    Power power
) implements WorldEvent {

    public BeamChannelEndEvent {
        Objects.requireNonNull(power);
    }
}
