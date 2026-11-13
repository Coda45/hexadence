package io.github.coda45.hexadence.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;

// Player state in world units. (x, y) centers the collision footprint;
// the renderer also uses it as the bottom-center of the sprite.
public class Player {
    
    private float x;
    private float y;
    private float aimX = 1f;
    private float aimY = 0f;
    private boolean moving;
    private float knockbackVelocityX;
    private float knockbackVelocityY;

    private static final float MAX_HEALTH = 100f;
    // At or below this much health, health bars turn red.
    public static final float LOW_HEALTH = 20f;
    private float health = MAX_HEALTH;
    private float respawnRemaining = -1f;
    private int kills;
    private int deaths;
    // Damage this player has dealt to other players this match.
    private float damageDealt;
    // Seconds of spawn protection left; 0 when unprotected.
    private float spawnShieldRemaining;
    private PlayerColor color;

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

    // The three powers this player brings, and each slot's remaining cooldown.
    private Loadout loadout = Loadout.DEFAULT;
    private final float[] cooldowns = new float[Loadout.SIZE];

    public Player(float x, float y) {
        this(x, y, PlayerColor.CYAN);
    }

    public Player(
        float x,
        float y,
        PlayerColor color
    ) {
        this.x = x;
        this.y = y;
        this.color = Objects.requireNonNull(color);
    }

    public void move(float dx, float dy) {
        moving = dx * dx + dy * dy > 0.000001f;
        x += dx;
        y += dy;
    }

    public boolean isMoving() {
        return moving;
    }

    public void addKnockbackVelocity(float velocityX, float velocityY) {
        knockbackVelocityX += velocityX;
        knockbackVelocityY += velocityY;
    }

    public float getKnockbackVelocityX() {
        return knockbackVelocityX;
    }

    public float getKnockbackVelocityY() {
        return knockbackVelocityY;
    }

    public void dampenKnockback(float multiplier) {
        knockbackVelocityX *= multiplier;
        knockbackVelocityY *= multiplier;

        if (knockbackVelocityX * knockbackVelocityX < 0.0001f) {
            knockbackVelocityX = 0f;
        }
        if (knockbackVelocityY * knockbackVelocityY < 0.0001f) {
            knockbackVelocityY = 0f;
        }
    }

    public PlayerColor getColor() {
        return color;
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

    public Loadout getLoadout() {
        return loadout;
    }

    // Changing powers resets every cooldown.
    public void setLoadout(Loadout loadout) {
        this.loadout = Objects.requireNonNull(loadout);
        resetCooldowns();
    }

    // The power in a slot, numbered from 0.
    public Power getPower(int slot) {
        return loadout.get(slot);
    }

    public void updateCooldowns(float deltaSeconds) {
        for (int slot = 0; slot < cooldowns.length; slot++) {
            cooldowns[slot] = Math.max(0f, cooldowns[slot] - deltaSeconds);
        }
    }

    public boolean isSlotReady(int slot) {
        return cooldowns[slot] <= 0f;
    }

    public void startCooldown(int slot) {
        cooldowns[slot] = loadout.get(slot).cooldownSeconds();
    }

    public float getCooldownRemaining(int slot) {
        return cooldowns[slot];
    }

    private void resetCooldowns() {
        for (int slot = 0; slot < cooldowns.length; slot++) {
            cooldowns[slot] = 0f;
        }
    }

    public void takeDamage(float amount) {
        health = Math.max(0f, health - amount);

        if (!isAlive()) {
            moving = false;
            knockbackVelocityX = 0f;
            knockbackVelocityY = 0f;
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

    public boolean isLowHealth() {
        return isAlive() && health <= LOW_HEALTH;
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

    public boolean hasRespawnPending() {
        return respawnRemaining >= 0f;
    }

    public float getRespawnRemaining() {
        return respawnRemaining;
    }

    public void beginRespawn(float delaySeconds) {
        if (!isAlive() && !hasRespawnPending()) {
            respawnRemaining = delaySeconds;
        }
    }

    public void updateRespawn(
        float deltaSeconds,
        float spawnX,
        float spawnY
    ) {
        if (!hasRespawnPending()) {
            return;
        }

        respawnRemaining -= deltaSeconds;

        if (respawnRemaining > 0f) {
            return;
        }

        health = MAX_HEALTH;
        respawnRemaining = -1f;
        aimX = 1f;
        aimY = 0f;
        setPosition(spawnX, spawnY);
        resetCooldowns();
    }

    public void recordKill() {
        kills++;
    }

    public void recordDeath() {
        deaths++;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public void recordDamageDealt(float amount) {
        damageDealt += Math.max(0f, amount);
    }

    public float getDamageDealt() {
        return damageDealt;
    }

    // ---- Spawn shield ----
    // A freshly respawned player cannot be damaged for a few seconds, so they
    // cannot be spawn camped. Casting anything drops the shield early.

    public void startSpawnShield(float seconds) {
        spawnShieldRemaining = Math.max(0f, seconds);
    }

    public void dropSpawnShield() {
        spawnShieldRemaining = 0f;
    }

    public void updateSpawnShield(float deltaSeconds) {
        spawnShieldRemaining = Math.max(0f, spawnShieldRemaining - deltaSeconds);
    }

    public boolean hasSpawnShield() {
        return spawnShieldRemaining > 0f;
    }

    public float getSpawnShieldRemaining() {
        return spawnShieldRemaining;
    }

    // Puts this player back to how they entered the match: full health at
    // the given spawn, no stats, no cooldowns, no shield. Keeps the loadout.
    public void resetForNewMatch(float spawnX, float spawnY) {
        health = MAX_HEALTH;
        respawnRemaining = -1f;
        kills = 0;
        deaths = 0;
        damageDealt = 0f;
        spawnShieldRemaining = 0f;
        aimX = 1f;
        aimY = 0f;
        setPosition(spawnX, spawnY);
        resetCooldowns();
    }

    // Overwrites this player with state received from the host (clients only).
    public void restoreState(
        float x,
        float y,
        float aimX,
        float aimY,
        boolean moving,
        float health,
        float respawnRemaining,
        int kills,
        int deaths,
        float spawnShieldRemaining,
        PlayerColor color
    ) {
        this.x = x;
        this.y = y;
        this.aimX = aimX;
        this.aimY = aimY;
        this.moving = moving;
        this.health = Math.max(0f, Math.min(MAX_HEALTH, health));
        this.respawnRemaining = respawnRemaining;
        this.kills = kills;
        this.deaths = deaths;
        this.spawnShieldRemaining = Math.max(0f, spawnShieldRemaining);
        this.color = Objects.requireNonNull(color);
    }

    // Clients only: copies the server's loadout and cooldowns.
    public void restoreSlot(int slot, Power power, float cooldownRemaining) {
        if (!loadout.get(slot).equals(power)) {
            List<Power> slots = new ArrayList<>(loadout.slots());
            slots.set(slot, power);
            loadout = new Loadout(slots);
        }

        cooldowns[slot] = cooldownRemaining;
    }
    
    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
        moving = false;
        knockbackVelocityX = 0f;
        knockbackVelocityY = 0f;
    }
}
