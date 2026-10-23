package io.github.coda45.hexadence.entity;

import io.github.coda45.hexadence.power.Power;

// Stores one stable projectile identity and its simulation state.
// ArenaWorld owns movement, collision, and removal.
public class Projectile {

    private final int id;
    private final Power power;
    private final int ownerPlayerId;
    private final float directionX;
    private final float directionY;

    private float x;
    private float y;
    private float distanceTraveled;

    // The direction must be normalized so each update travels the requested distance.
    public Projectile(
        int id,
        float x,
        float y,
        float directionX,
        float directionY,
        Power power,
        int ownerPlayerId
    ) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.directionX = directionX;
        this.directionY = directionY;
        this.power = power;
        this.ownerPlayerId = ownerPlayerId;
    }

    // Moves by a world-unit distance and records distance traveled, including the spawn step.
    public void update(float distance) {
        x += directionX * distance;
        y += directionY * distance;
        distanceTraveled += distance;
    }

    public Power getPower() {
        return power;
    }

    public boolean hasReachedMaxRange(float maxRange) {
        return distanceTraveled >= maxRange;
    }

    public int getId() {
        return id;
    }

    // Client-side snapshot restoration only.
    public void restorePosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getDistanceTraveled() {
        return distanceTraveled;
    }

    public int getOwnerPlayerId() {
        return ownerPlayerId;
    }

    public float getDirectionX() {
        return directionX;
    }

    public float getDirectionY() {
        return directionY;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}
