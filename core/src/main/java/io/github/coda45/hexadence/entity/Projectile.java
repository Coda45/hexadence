package io.github.coda45.hexadence.entity;

import io.github.coda45.hexadence.spell.SpellType;

// Stores a projectile's world position, fixed direction, and distance traveled. ArenaWorld handles removal.
public class Projectile {

    private float x;
    private float y;
    private float distanceTraveled;
    private final SpellType spellType;
    private final int ownerPlayerId;

    private final float directionX;
    private final float directionY;

    // The direction must be normalized so each update travels the requested distance.
    public Projectile(
        float x,
        float y,
        float directionX,
        float directionY,
        SpellType spellType,
        int ownerPlayerId
    ) {
        this.x = x;
        this.y = y;
        this.directionX = directionX;
        this.directionY = directionY;
        this.spellType = spellType;
        this.ownerPlayerId = ownerPlayerId;
    }

    // Moves by a world-unit distance and records distance traveled, including the spawn step.
    public void update(float distance) {
        x += directionX * distance;
        y += directionY * distance;
        distanceTraveled += distance;
    }

    public SpellType getSpellType() {
        return spellType;
    }

    public boolean hasReachedMaxRange(float maxRange) {
        return distanceTraveled >= maxRange;
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
