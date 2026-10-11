package io.github.coda45.hexadence.entity;

// Stationary combat test target. Its position centers the circular hit area.
public class TargetDummy {

    public static final float RADIUS = 0.3f;

    private static final float MAX_HEALTH = 100f;

    private final int id;
    private final float x;
    private final float y;

    private float health = MAX_HEALTH;
    private float respawnRemaining = -1f;

    public TargetDummy(int id, float x, float y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public boolean hasRespawnPending() {
        return respawnRemaining >= 0f;
    }

    public void beginRespawn(float delaySeconds) {
        if (!isAlive() && !hasRespawnPending()) {
            respawnRemaining = delaySeconds;
        }
    }

    public void updateRespawn(float deltaSeconds) {
        if (!hasRespawnPending()) {
            return;
        }

        respawnRemaining -= deltaSeconds;

        if (respawnRemaining <= 0f) {
            health = MAX_HEALTH;
            respawnRemaining = -1f;
        }
    }

    public void takeDamage(float amount) {
        health = Math.max(0f, health - amount);
    }

    // Overwrites health with authoritative replicated state on clients.
    public void setHealth(float health) {
        this.health = Math.min(MAX_HEALTH, health);
    }

    public boolean isAlive() {
        return health > 0f;
    }

    public int getId() {
        return id;
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
