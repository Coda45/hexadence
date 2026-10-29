package io.github.coda45.hexadence.world;

// Pure cast-target geometry. It owns no world state and trusts no client range.
final class Targeting {

    private static final double MIN_DISTANCE = 0.0001;

    private Targeting() {
    }

    static CastTarget resolveOffset(
        float originX,
        float originY,
        float offsetX,
        float offsetY,
        float maxRange,
        float fallbackDirectionX,
        float fallbackDirectionY
    ) {
        double requestedDistance = Math.hypot(
            (double) offsetX,
            (double) offsetY
        );

        if (requestedDistance <= MIN_DISTANCE) {
            return new CastTarget(
                fallbackDirectionX,
                fallbackDirectionY,
                0f,
                originX,
                originY
            );
        }

        float directionX = (float) (offsetX / requestedDistance);
        float directionY = (float) (offsetY / requestedDistance);
        float distance = (float) Math.min(requestedDistance, maxRange);

        return new CastTarget(
            directionX,
            directionY,
            distance,
            originX + directionX * distance,
            originY + directionY * distance
        );
    }
}
