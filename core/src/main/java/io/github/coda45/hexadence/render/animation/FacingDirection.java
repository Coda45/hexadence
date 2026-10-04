package io.github.coda45.hexadence.render.animation;

public enum FacingDirection {
    NORTH,
    NORTHEAST,
    EAST,
    SOUTHEAST,
    SOUTH,
    SOUTHWEST,
    WEST,
    NORTHWEST;

    // Cosine boundaries split normalized aim into eight 45-degree sectors.
    private static final float CARDINAL_X_THRESHOLD = (float) Math.cos(Math.PI / 8.0);
    private static final float DIAGONAL_X_THRESHOLD = (float) Math.cos(3.0 * Math.PI / 8.0);

    public static FacingDirection fromAim(
        float aimX,
        float aimY
    ) {
        if (Math.abs(aimX) >= CARDINAL_X_THRESHOLD) {
            return aimX >= 0f
                ? EAST
                : WEST;
        }

        if (Math.abs(aimX) >= DIAGONAL_X_THRESHOLD) {
            if (aimX >= 0f) {
                return aimY >= 0f
                    ? NORTHEAST
                    : SOUTHEAST;
            } else {
                return aimY >= 0f
                    ? NORTHWEST
                    : SOUTHWEST;
            }
        }

        return aimY >= 0f
            ? NORTH
            : SOUTH;
    }
}
