package io.github.coda45.hexadence.world;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;

record LightningHit(
    Kind kind,
    float distance,
    Zone zone,
    TargetDummy dummy,
    Player player
) {
    enum Kind {
        NONE,
        TERRAIN,
        PUDDLE,
        DUMMY,
        PLAYER
    }

    static LightningHit none() {
        return new LightningHit(
            Kind.NONE,
            CombatGeometry.NO_HIT,
            null,
            null,
            null
        );
    }

    static LightningHit terrain(float distance) {
        return new LightningHit(
            Kind.TERRAIN,
            distance,
            null,
            null,
            null
        );
    }

    static LightningHit puddle(float distance, Zone zone) {
        return new LightningHit(
            Kind.PUDDLE,
            distance,
            zone,
            null,
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
            dummy,
            null
        );
    }

    static LightningHit player(
        float distance,
        Player player
    ) {
        return new LightningHit(
            Kind.PLAYER,
            distance,
            null,
            null,
            player
        );
    }

    boolean isCloserThan(LightningHit other) {
        return distance < other.distance;
    }
}
