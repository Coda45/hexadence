package io.github.coda45.hexadence.entity;

import java.util.EnumMap;

import io.github.coda45.hexadence.spell.SpellType;

// Player state in world units. (x, y) centers the collision footprint;
// the renderer also uses it as the bottom-center of the sprite.
public class Player {
    
    private float x;
    private float y;
    private float aimX = 1f;
    private float aimY = 0f;
    private boolean moving;

    private static final float MAX_HEALTH = 100f;
    private float health = MAX_HEALTH;

    // Collision covers the feet, not the full sprite.
    public static final float HALF_WIDTH = 0.365f;
    public static final float HALF_HEIGHT = 0.225f;
    public static final float COLLISION_OFFSET_X = 0f;
    public static final float COLLISION_OFFSET_Y = 0.24f;

    // Combat covers the visible body. Movement collision only covers the feet.
    public static final float COMBAT_HALF_WIDTH = 0.365f;
    public static final float COMBAT_HALF_HEIGHT = 0.75f;
    public static final float COMBAT_OFFSET_Y = 0.75f;

    // Center of the 36-pixel-tall wizard sprite at 24 pixels per world unit.
    private static final float CAST_OFFSET_Y = 0.75f;

    private final EnumMap<SpellType, Float> cooldowns =
        new EnumMap<>(SpellType.class);

    public Player(float x, float y) {
        this.x = x;
        this.y = y;
        for (SpellType spellType : SpellType.values()) {
            cooldowns.put(spellType, 0f);
        }
    }

    public void move(float dx, float dy) {
        moving = dx * dx + dy * dy > 0.000001f;
        x += dx;
        y += dy;
    }

    public boolean isMoving() {
        return moving;
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

    // Stores a unit direction; ArenaWorld normalizes the aim before calling this.
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

    public void updateCooldowns(float deltaSeconds) {
        for (SpellType spellType : SpellType.values()) {
            float remaining = cooldowns.get(spellType);

            cooldowns.put(
                spellType,
                Math.max(0f, remaining - deltaSeconds)
            );
        }
    }

    public boolean isSpellReady(SpellType spellType) {
        return cooldowns.get(spellType) <= 0f;
    }

    public void startCooldown(SpellType spellType) {
        cooldowns.put(
            spellType,
            spellType.getCooldownSeconds()
        );
    }

    public float getCooldownRemaining(SpellType spellType) {
        return cooldowns.get(spellType);
    }

    public void takeDamage(float amount) {
        health = Math.max(0f, health - amount);

        if (!isAlive()) {
            moving = false;
        }
    }

    public boolean isAlive() {
        return health > 0f;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return MAX_HEALTH;
    }

    public float getCollisionX() {
        return x + COLLISION_OFFSET_X;
    }

    public float getCollisionY() {
        return y + COLLISION_OFFSET_Y;
    }

    public float getCombatCenterX() {
        return x;
    }

    public float getCombatCenterY() {
        return y + COMBAT_OFFSET_Y;
    }

    // Overwrites this player with state received from the host (clients only).
    public void restoreState(
        float x,
        float y,
        float aimX,
        float aimY,
        boolean moving,
        float health
    ) {
        this.x = x;
        this.y = y;
        this.aimX = aimX;
        this.aimY = aimY;
        this.moving = moving;
        this.health = Math.min(MAX_HEALTH, health);
    }

    public void setCooldownRemaining(SpellType spellType, float seconds) {
        cooldowns.put(spellType, seconds);
    }
    
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        moving = false;
    }
}
