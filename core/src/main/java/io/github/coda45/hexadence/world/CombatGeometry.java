package io.github.coda45.hexadence.world;

public final class CombatGeometry {

    public static final float NO_HIT =
        Float.POSITIVE_INFINITY;
    private static final float DIRECTION_EPSILON = 0.000001f;

    private CombatGeometry() {
    }

    // Returns the distance from the ray origin to the circle's near edge.
    // Direction must be normalized - a miss returns NO_HIT.
    public static float rayCircleEntryDistance(
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float centerX,
        float centerY,
        float radius
    ) {
        float offsetX = centerX - originX;
        float offsetY = centerY - originY;

        float centerDistanceSquared =
            offsetX * offsetX + offsetY * offsetY;

        float radiusSquared = radius * radius;

        if (centerDistanceSquared <= radiusSquared) {
            return 0f;
        }

        float projection =
            offsetX * directionX
                + offsetY * directionY;

        if (projection < 0f) {
            return NO_HIT;
        }

        float perpendicularSquared =
            centerDistanceSquared
                - projection * projection;

        if (perpendicularSquared > radiusSquared) {
            return NO_HIT;
        }

        float halfChord =
            (float) Math.sqrt(
                Math.max(
                    0f,
                    radiusSquared - perpendicularSquared
                )
            );

        float entryDistance = projection - halfChord;

        if (
            entryDistance < 0f
            || entryDistance > maxDistance
        ) {
            return NO_HIT;
        }

        return entryDistance;
    }
    // Returns where a normalized ray enters an axis-aligned rectangle.
    // A miss or a rectangle beyond maxDistance returns NO_HIT.
    public static float rayAabbEntryDistance(
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float minX,
        float minY,
        float maxX,
        float maxY
    ) {
        float nearDistance = 0f;
        float farDistance = maxDistance;

        if (Math.abs(directionX) < DIRECTION_EPSILON) {
            if (originX < minX || originX > maxX) {
                return NO_HIT;
            }
        } else {
            float firstX = (minX - originX) / directionX;
            float secondX = (maxX - originX) / directionX;

            if (firstX > secondX) {
                float swap = firstX;
                firstX = secondX;
                secondX = swap;
            }

            nearDistance =  Math.max(nearDistance, firstX);
            farDistance = Math.min(farDistance, secondX);

            if (nearDistance > farDistance) {
                return NO_HIT;
            }
        }

        if (Math.abs(directionY) < DIRECTION_EPSILON) {
            if (originY < minY || originY > maxY) {
                return NO_HIT;
            }
        } else {
            float firstY =
                (minY - originY) / directionY;

            float secondY =
                (maxY - originY) / directionY;

            if (firstY > secondY) {
                float swap = firstY;
                firstY = secondY;
                secondY = swap;
            }

            nearDistance =  Math.max(nearDistance, firstY);
            farDistance = Math.min(farDistance, secondY);

            if (nearDistance > farDistance) {
                return NO_HIT;
            }
        }

        return nearDistance;
    }
}
