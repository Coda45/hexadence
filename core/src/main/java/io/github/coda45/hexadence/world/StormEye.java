package io.github.coda45.hexadence.world;

// The "eye of the storm": a fixed circle around the middle of the arena.
// Zone casts made from inside it decide what the Maverick will cast.
public record StormEye(
    float centerX,
    float centerY,
    float radius
) {

    // The eye's area as a share of the open floor between the pillars'
    // inner faces. Raise it for a bigger eye.
    public static final float EYE_AREA_FRACTION = 0.75f;

    /*
     * The pillars are the free-standing blocks of the collision grid. The eye
     * is centered between them, and its area is EYE_AREA_FRACTION of the
     * rectangle of floor they enclose.
     *
     * A map without pillars on all four sides of that center has no such
     * rectangle, so its eye has radius 0 and nothing can be cast from inside
     * it.
     */
    public static StormEye locate(ArenaCollision collision) {
        boolean[][] pillar = collision.findPillarTiles();
        float outerLeft = pillar.length;
        float outerBottom = pillar[0].length;
        float outerRight = 0f;
        float outerTop = 0f;

        for (int x = 0; x < pillar.length; x++) {
            for (int y = 0; y < pillar[0].length; y++) {
                if (pillar[x][y]) {
                    outerLeft = Math.min(outerLeft, x);
                    outerBottom = Math.min(outerBottom, y);
                    outerRight = Math.max(outerRight, x + 1f);
                    outerTop = Math.max(outerTop, y + 1f);
                }
            }
        }

        // Without pillars this is the middle of the map.
        float centerX = (outerLeft + outerRight) / 2f;
        float centerY = (outerBottom + outerTop) / 2f;

        // The inner faces: the nearest pillar edge on each side of the center.
        float innerLeft = Float.NEGATIVE_INFINITY;
        float innerBottom = Float.NEGATIVE_INFINITY;
        float innerRight = Float.POSITIVE_INFINITY;
        float innerTop = Float.POSITIVE_INFINITY;

        for (int x = 0; x < pillar.length; x++) {
            for (int y = 0; y < pillar[0].length; y++) {
                if (!pillar[x][y]) {
                    continue;
                }

                if (x + 1f <= centerX) {
                    innerLeft = Math.max(innerLeft, x + 1f);
                } else if (x >= centerX) {
                    innerRight = Math.min(innerRight, x);
                }

                if (y + 1f <= centerY) {
                    innerBottom = Math.max(innerBottom, y + 1f);
                } else if (y >= centerY) {
                    innerTop = Math.min(innerTop, y);
                }
            }
        }

        float openArea = (innerRight - innerLeft) * (innerTop - innerBottom);
        float radius = Float.isFinite(openArea)
            ? (float) Math.sqrt(EYE_AREA_FRACTION * openArea / Math.PI)
            : 0f;

        return new StormEye(centerX, centerY, radius);
    }

    public boolean contains(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;

        return radius > 0f && dx * dx + dy * dy <= radius * radius;
    }
}
