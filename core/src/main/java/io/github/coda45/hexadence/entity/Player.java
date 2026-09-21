package io.github.coda45.hexadence.entity;

/**
 * Player state in world units. (x, y) centers the collision footprint;
 * the renderer also uses it as the bottom-center of the sprite.
 */
public class Player {
    
    private float x;
    private float y;
    private float aimX = 1f;
    private float aimY = 0f;

    // Collision covers the feet, not the full sprite.
    public static final float HALF_WIDTH = 0.275f;
    public static final float HALF_HEIGHT = 0.225f;

    // Center of the 36-pixel-tall wizard sprite at 24 pixels per world unit.
    private static final float CAST_OFFSET_Y = 0.75f;

    public Player(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void move(float dx, float dy) {
        x += dx;
        y += dy;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getCastX() {
        return x;
    }

    public float getCastY() {
        return y + CAST_OFFSET_Y;
    }

    /** Stores a unit direction; ArenaWorld normalizes the aim before calling this. */
    public void setAim(float aimX, float aimY) {
        this.aimX = aimX;
        this.aimY = aimY;
    }

    public float getAimX() {
        return aimX;
    }

    public float getAimY() {
        return aimY;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
