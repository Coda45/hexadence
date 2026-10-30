package io.github.coda45.hexadence.power;

// How a power physically works in the arena. Most powers reuse one of these,
// so a new power usually needs only a definition in Powers, not new code.
//
// Each behavior reads different fields from its Power:
//   PROJECTILE   damage, speed, range, radius (size of the projectile), and
//                optionally pellets(...) (more than one projectile per cast)
//                and damageOverTime(...) (a timed effect that starts on hit)
//   BEAM         damage, range, radius (half the beam's width); hits instantly
//   THROWN_ZONE  travels toward a range-clamped cursor target, then leaves its
//                zone where it arrives or first collides
//   PLACED_ZONE  creates its zone immediately at a valid range-clamped target
//
// A power that needs a mechanic none of these cover gets a new value here
// and a matching case in ArenaWorld.cast().
public enum Behavior {
    PROJECTILE,
    BEAM,
    THROWN_ZONE,
    PLACED_ZONE;

    // True for behaviors that move through the world as a Projectile.
    public boolean travels() {
        return this == PROJECTILE || this == THROWN_ZONE;
    }

    public boolean createsZone() {
        return this == THROWN_ZONE || this == PLACED_ZONE;
    }

    public boolean needsProjectileRadius() {
        return this == PROJECTILE || this == THROWN_ZONE || this == BEAM;
    }
}
