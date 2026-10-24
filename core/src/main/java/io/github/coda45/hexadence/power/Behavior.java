package io.github.coda45.hexadence.power;

// How a power physically works in the arena. Most powers reuse one of these,
// so a new power usually needs only a definition in Powers, not new code.
//
// Each behavior reads different fields from its Power:
//   PROJECTILE   damage, speed, range, radius (size of the projectile)
//   BEAM         damage, range, radius (half the beam's width); hits instantly
//   THROWN_ZONE  like PROJECTILE, then leaves its zone (a ZoneKind) where
//                it stops
//
// A power that needs a mechanic none of these cover gets a new value here
// and a matching case in ArenaWorld.cast().
public enum Behavior {
    PROJECTILE,
    BEAM,
    THROWN_ZONE;

    // True for behaviors that move through the world as a Projectile.
    public boolean travels() {
        return this == PROJECTILE || this == THROWN_ZONE;
    }

    public boolean createsZone() {
        return this == THROWN_ZONE;
    }
}
