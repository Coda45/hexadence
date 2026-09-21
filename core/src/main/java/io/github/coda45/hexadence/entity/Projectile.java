package io.github.coda45.hexadence.entity;

public class Projectile {

    private float x;
    private float y;

    private final float directionX;
    private final float directionY;

    public Projectile(
        float x,
        float y,
        float directionX,
        float directionY
    ) {
        this.x = x;
        this.y = y;

        this.directionX = directionX;
        this.directionY = directionY;
    }

    public void move(float distance) {
        x += directionX * distance;
        y += directionY * distance;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}