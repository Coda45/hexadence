package io.github.coda45.hexadence.world;

// Tile collision in world units, backed by a nonempty rectangular grid indexed [x][y].
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

    // Tests every tile covered by a rectangular footprint centered at (x, y).
    public boolean overlaps(float x, float y, float halfWidth, float halfHeight){
        // Floor also maps negative coordinates outside the arena correctly.
        // The maximum edges are inclusive: an edge on a tile boundary includes that tile.
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

    // Returns the final {x, y} after the same axis checks as resolveMovement.
    // ArenaWorld uses resolveMovement instead because Player.move takes offsets.
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
     * Returns {allowedDx, allowedDy}. Each axis is accepted in full or rejected;
     * testing Y from the accepted X position lets the player slide along walls.
     * Only destinations are checked, so large moves can skip over blocked tiles.
     */
    public float[] resolveMovement(float x, float y, float dx, float dy, float halfWidth, float halfHeight) {
        float allowedDx = overlaps(x + dx, y, halfWidth, halfHeight) ? 0f : dx;
        float newX = x + allowedDx;

        float allowedDy = overlaps(newX, y + dy, halfWidth, halfHeight) ? 0f : dy;

        return new float[] { allowedDx, allowedDy };
    }
}
