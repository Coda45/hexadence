package io.github.coda45.hexadence.world;

// A simulation-owned obstacle that can block rectangular actors and swept
// projectiles. Implementations may be grid-backed terrain or floating shapes.
public interface WorldCollider {

    boolean overlaps(
        float x,
        float y,
        float halfWidth,
        float halfHeight
    );

    float raycast(
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float footprintHalfWidth,
        float footprintHalfHeight
    );
}
