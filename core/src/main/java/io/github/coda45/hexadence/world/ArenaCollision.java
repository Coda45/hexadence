package io.github.coda45.hexadence.world;

// Tile collision in world units, backed by a nonempty rectangular grid indexed [x][y].
public class ArenaCollision {

    private final float[][] blockedHeights;

    public ArenaCollision(float[][] blockedHeights) {
        this.blockedHeights = blockedHeights;
    }

    private float getBlockedHeight(
        int tileX,
        int tileY
    ) {
        // Outside the map remains completely blocked.
        if (
            tileX < 0
            || tileY < 0
            || tileX >= blockedHeights.length
            || tileY >= blockedHeights[0].length
        ) {
            return 1f;
        }

        return blockedHeights[tileX][tileY];
    }

    // Tests every tile covered by a rectangular footprint centered at (x, y).
    public boolean overlaps(
        float x,
        float y,
        float halfWidth,
        float halfHeight
    ) {
        float entityLeft = x - halfWidth;
        float entityRight = x + halfWidth;
        float entityBottom = y - halfHeight;
        float entityTop = y + halfHeight;

        int minTileX = (int) Math.floor(entityLeft);
        int maxTileX = (int) Math.floor(entityRight);
        int minTileY = (int) Math.floor(entityBottom);
        int maxTileY = (int) Math.floor(entityTop);

        for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
            for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
                float blockedHeight =
                    getBlockedHeight(tileX, tileY);

                if (blockedHeight <= 0f) {
                    continue;
                }

                float blockLeft = tileX;
                float blockRight = tileX + 1f;
                float blockBottom = tileY;
                float blockTop = tileY + blockedHeight;

                boolean intersects =
                    entityRight >= blockLeft
                    && entityLeft <= blockRight
                    && entityTop >= blockBottom
                    && entityBottom <= blockTop;

                if (intersects) {
                    return true;
                }
            }
        }

        return false;
    }

    // Returns the distance to the first blocked tile touched by a swept square
    // footprint. A zero half-size is a point ray. Direction must be normalized;
    // no collision returns NO_HIT.
    public float raycastTerrain(
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float footprintHalfSize
    ) {
        float endX = originX + directionX * maxDistance;
        float endY = originY + directionY * maxDistance;

        int minTileX = (int) Math.floor(
            Math.min(originX, endX) - footprintHalfSize
        );
        int maxTileX = (int) Math.floor(
            Math.max(originX, endX) + footprintHalfSize
        );
        int minTileY = (int) Math.floor(
            Math.min(originY, endY) - footprintHalfSize
        );
        int maxTileY = (int) Math.floor(
            Math.max(originY, endY) + footprintHalfSize
        );

        float nearestDistance = CombatGeometry.NO_HIT;

        for (
            int tileX = minTileX;
            tileX <= maxTileX;
            tileX++
        ) {
            for (
                int tileY = minTileY;
                tileY <= maxTileY;
                tileY++
            ) {
                float blockedHeight = getBlockedHeight(tileX, tileY);

                if (blockedHeight <= 0f) {
                    continue;
                }

                float currentLimit = Math.min( maxDistance, nearestDistance);

                float hitDistance =
                    CombatGeometry.rayAabbEntryDistance(
                        originX,
                        originY,
                        directionX,
                        directionY,
                        currentLimit,
                        tileX - footprintHalfSize,
                        tileY - footprintHalfSize,
                        tileX + 1f + footprintHalfSize,
                        tileY + blockedHeight + footprintHalfSize
                    );

                if (hitDistance < nearestDistance) {
                    nearestDistance = hitDistance;
                }
            }
        }

        return nearestDistance;
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
