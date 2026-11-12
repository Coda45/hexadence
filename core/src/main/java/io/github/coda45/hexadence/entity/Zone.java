package io.github.coda45.hexadence.entity;

import java.util.Objects;

import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneKinds;

// One zone in the arena: a circle of some ZoneKind that lasts a while.
// What the kind does (damage, slow, reactions) is handled by ArenaWorld.
public class Zone {

    public static final int UNASSIGNED_ID = -1;
    public static final int NO_PLAYER = -1;

    private final int id;
    private final ZoneKind kind;
    private float x;
    private float y;
    private final float radius;

    private float remainingSeconds;
    // Who gets credit for damage this zone deals.
    private int damageOwnerPlayerId;
    private float health;
    // Set once, by a reaction that tips a derived zone in a direction;
    // zero for a zone that stays put.
    private float velocityX;
    private float velocityY;

    private Zone(
        int id,
        ZoneKind kind,
        float x,
        float y,
        float radius,
        float duration,
        int damageOwnerPlayerId
    ) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind);
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.remainingSeconds = duration;
        this.damageOwnerPlayerId = damageOwnerPlayerId;
        this.health = kind.maxHealth();
    }

    // A zone with its kind's own size and lifetime.
    public static Zone create(
        int id,
        ZoneKind kind,
        float x,
        float y,
        int ownerPlayerId
    ) {
        return new Zone(
            id,
            kind,
            x,
            y,
            kind.radius(),
            kind.duration(),
            ownerPlayerId
        );
    }

    // A zone with an explicit size and lifetime, as a snapshot gives them.
    public static Zone create(
        int id,
        ZoneKind kind,
        float x,
        float y,
        float radius,
        float duration,
        int ownerPlayerId
    ) {
        return new Zone(id, kind, x, y, radius, duration, ownerPlayerId);
    }

    public static Zone createPuddle(float x, float y) {
        return createPuddle(UNASSIGNED_ID, x, y, NO_PLAYER);
    }

    public static Zone createPuddle(
        int id,
        float x,
        float y,
        int ownerPlayerId
    ) {
        return create(id, ZoneKinds.PUDDLE, x, y, ownerPlayerId);
    }

    public static Zone createSteam(
        int id,
        float x,
        float y,
        int ownerPlayerId
    ) {
        return create(id, ZoneKinds.STEAM, x, y, ownerPlayerId);
    }

    public int getId() {
        return id;
    }

    public int getDamageOwnerPlayerId() {
        return damageOwnerPlayerId;
    }

    public void setDamageOwnerPlayerId(int playerId) {
        damageOwnerPlayerId = playerId;
    }

    public void update(float deltaSeconds) {
        remainingSeconds = Math.max(0f, remainingSeconds - deltaSeconds);
        x += velocityX * deltaSeconds;
        y += velocityY * deltaSeconds;
    }

    // Tipped in this direction, at the kind's drift speed. Set once, when a
    // reaction creates the zone; has no effect on a kind with no drift.
    public void setVelocity(float velocityX, float velocityY) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    public boolean hasExpired() {
        return remainingSeconds <= 0f;
    }

    public boolean isDestructible() {
        return kind.maxHealth() > 0f;
    }

    public float getHealth() {
        return health;
    }

    public float getHealthFraction() {
        return isDestructible() ? health / kind.maxHealth() : 1f;
    }

    public void takeDamage(float amount) {
        if (isDestructible() && amount > 0f) {
            health = Math.max(0f, health - amount);
        }
    }

    public boolean hasBeenDestroyed() {
        return isDestructible() && health <= 0f;
    }

    // Clients follow the host's position for a zone that drifts, since they
    // never simulate zone velocity themselves.
    public void restorePosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void restoreHealth(float value) {
        health = Math.max(0f, Math.min(kind.maxHealth(), value));
    }

    public ZoneKind getKind() {
        return kind;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getRadius() {
        return radius;
    }

}
