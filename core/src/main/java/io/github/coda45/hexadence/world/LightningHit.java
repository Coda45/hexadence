package io.github.coda45.hexadence.world;

import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;

// Internal gameplay result, entity references never enter presentation events.
record LightningHit(
    Kind kind,
    float distance,
    Zone zone,
    TargetDummy dummy
) {
    enum Kind {
        NONE,
        TERRAIN,
        PUDDLE,
        DUMMY
    }

    static LightningHit none() {
        return new LightningHit(
            Kind.NONE,
            CombatGeometry.NO_HIT,
            null,
            null
        );
    }

    static LightningHit terrain(float distance) {
        return new LightningHit(
            Kind.TERRAIN,
            distance,
            null,
            null
        );
    }

    static LightningHit puddle(
        float distance,
        Zone zone
    ) {
        return new LightningHit(
            Kind.PUDDLE,
            distance,
            zone,
            null
        );
    }

    static LightningHit dummy(
        float distance,
        TargetDummy dummy
    ) {
        return new LightningHit(
            Kind.DUMMY,
            distance,
            null,
            dummy
        );
    }

    boolean isCloserThan(LightningHit other) {
        return distance < other.distance;
    }
}
