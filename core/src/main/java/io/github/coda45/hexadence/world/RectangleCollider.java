package io.github.coda45.hexadence.world;

// One axis-aligned floating obstacle in world coordinates.
public record RectangleCollider(
    float centerX,
    float centerY,
    float halfWidth,
    float halfHeight
) implements WorldCollider {

    @Override
    public boolean overlaps(
        float x,
        float y,
        float otherHalfWidth,
        float otherHalfHeight
    ) {
        return Math.abs(x - centerX) <= halfWidth + otherHalfWidth
            && Math.abs(y - centerY) <= halfHeight + otherHalfHeight;
    }

    @Override
    public float raycast(
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float footprintHalfWidth,
        float footprintHalfHeight
    ) {
        return CombatGeometry.rayAabbEntryDistance(
            originX,
            originY,
            directionX,
            directionY,
            maxDistance,
            centerX - halfWidth - footprintHalfWidth,
            centerY - halfHeight - footprintHalfHeight,
            centerX + halfWidth + footprintHalfWidth,
            centerY + halfHeight + footprintHalfHeight
        );
    }
}
