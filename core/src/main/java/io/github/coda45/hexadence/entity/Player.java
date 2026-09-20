package io.github.coda45.hexadence.entity;

public class Player {
    
    private float x;
    private float y;

    // Player footprint
    public static final float HALF_WIDTH = 0.275f;
    public static final float HALF_HEIGHT = 0.225f;

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

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
