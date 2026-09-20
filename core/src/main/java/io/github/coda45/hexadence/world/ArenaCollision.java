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
}