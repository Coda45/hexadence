package io.github.coda45.hexadence.entity;

public class TargetDummy {

    public static final float RADIUS = 0.3f;
    private static final float MAX_HEALTH = 50f;

    private final float x;
    private final float y;
    private float health = MAX_HEALTH;

    public TargetDummy(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void takeDamage(float amount) {
        health = Math.max(0f, health - amount);
    }

    public boolean isAlive() {
        return health > 0f;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return MAX_HEALTH;
    }
}