package io.github.coda45.hexadence.world;

// An authoritative point target resolved from cursor intent and a power's
// maximum range. Direction is normalized unless distance is zero.
record CastTarget(
    float directionX,
    float directionY,
    float distance,
    float x,
    float y
) {
}
