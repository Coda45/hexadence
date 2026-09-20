package io.github.coda45.hexadence.world;

public class ArenaCollision {

    private final boolean[][] blocked;

    public ArenaCollision(boolean[][] blocked) {
        this.blocked = blocked;
    }

    public boolean isBlocked(int tileX, int tileY) {

        // Treat outside the map as blocked.
        if (tileX < 0 || tileY < 0
            || tileX >= blocked.length
            || tileY >= blocked[0].length) {

            return true;
        }

        return blocked[tileX][tileY];
    }

    public boolean overlaps(float x, float y, float halfWidth, float halfHeight){
        int minTileX = (int) Math.floor(x - halfWidth);
        int maxTileX = (int) Math.floor(x + halfWidth);
        int minTileY = (int) Math.floor(y - halfHeight);
        int maxTileY = (int) Math.floor(y + halfHeight);

        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int ty = minTileY; ty <= maxTileY; ty++) {
                if (isBlocked(tx, ty)) {
                    return true;
                }
            }
        }

        return false;
    }

     /*
     * Moves a footprint from (x, y) by (dx, dy), resolving X and Y separately
     * so hitting a wall on one axis doesn't stop movement along the other
     * (lets the player slide along walls instead of sticking to them).
     */
    public float[] move(float x, float y, float dx, float dy, float halfWidth, float halfHeight) {
        float newX = x;
        float newY = y;

        if (!overlaps(x + dx, y, halfWidth, halfHeight)) {
            newX = x + dx;
        }

        if (!overlaps(newX, y + dy, halfWidth, halfHeight)) {
            newY = y + dy;
        }

        return new float[] { newX, newY };
    }

    /*
    * Given a proposed move (dx, dy), returns how much of that move is actually
    * allowed — resolved per axis so hitting a wall on one axis doesn't cancel
    * movement along the other. Returns {allowedDx, allowedDy}, meant to be
    * passed straight into Player.move().
    */
    public float[] resolveMovement(float x, float y, float dx, float dy, float halfWidth, float halfHeight) {
        float allowedDx = overlaps(x + dx, y, halfWidth, halfHeight) ? 0f : dx;
        float newX = x + allowedDx;

        float allowedDy = overlaps(newX, y + dy, halfWidth, halfHeight) ? 0f : dy;

        return new float[] { allowedDx, allowedDy };
    }
}