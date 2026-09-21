package io.github.coda45.hexadence.entity;

// Stores a projectile's world position, fixed direction, and age. ArenaWorld handles removal.
public class Projectile {

    private static final float LIFETIME_SECONDS = 2f;

    private float x;
    private float y;
    private float ageSeconds;

    private final float directionX;
    private final float directionY;

    // The direction must be normalized so each update travels the requested distance.
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

    // Moves by a world-unit distance and ages by simulation time, including the spawn step.
    public void update(float distance, float deltaSeconds) {
        x += directionX * distance;
        y += directionY * distance;
        ageSeconds += deltaSeconds;
    }

    public boolean hasExpired() {
        return ageSeconds >= LIFETIME_SECONDS;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}
