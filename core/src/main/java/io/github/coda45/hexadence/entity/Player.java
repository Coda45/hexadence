package io.github.coda45.hexadence.entity;

public class Player {
    
    private float x;
    private float y;
    private float aimX = 1f;
    private float aimY = 0f;

    // Player footprint
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
