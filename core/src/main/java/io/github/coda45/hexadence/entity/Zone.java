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
    private final float x;
    private final float y;
    private final float radius;

    private float remainingSeconds;
    // Who gets credit for damage this zone deals.
    private int damageOwnerPlayerId;
    // Set by a CHARGE reaction; cleared when the charge discharges.
    private boolean electrified;

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
    }

    // A zone with its kind's own size and lifetime.
    public static Zone create(
        int id,
        ZoneKind kind,
        float x,
        float y,
        int ownerPlayerId
    ) {
        return new Zone(id, kind, x, y, kind.radius(), kind.duration(), ownerPlayerId);
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

    public boolean isElectrified() {
        return electrified;
    }

    public void electrify(int ownerPlayerId) {
        electrified = true;
        damageOwnerPlayerId = ownerPlayerId;
    }

    // Keeps existing tests and client-side code usable.
    public void electrify() {
        electrify(damageOwnerPlayerId);
    }

    public void discharge() {
        electrified = false;
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
    }

    public boolean hasExpired() {
        return remainingSeconds <= 0f;
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
