package io.github.coda45.hexadence.entity;

/** A projectile's center and fixed travel direction, both in world coordinates. */
public class Projectile {

    private float x;
    private float y;

    private final float directionX;
    private final float directionY;

    /** The direction must be normalized so move(distance) travels the requested distance. */
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
