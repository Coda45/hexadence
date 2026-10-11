package io.github.coda45.hexadence.entity;

public class Zone {

    public enum Type {
        PUDDLE,
        STEAM
    }

    private static final float PUDDLE_RADIUS = 0.8f;
    private static final float PUDDLE_DURATION = 6f;

    private static final float STEAM_RADIUS = 1f;
    private static final float STEAM_DURATION = 3f;

    private final Type type;
    private final float x;
    private final float y;
    private final float radius;

    private float remainingSeconds;
    public static final int NO_PLAYER = -1;
    private int damageOwnerPlayerId;

    private boolean electrified;
    private static final float CHARGE_FLASH_DURATION = 0.12f;
    private float chargeFlashRemaining;

    private Zone(
        Type type,
        float x,
        float y,
        float radius,
        float duration,
        int damageOwnerPlayerId
    ) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.remainingSeconds = duration;
        this.damageOwnerPlayerId = damageOwnerPlayerId;
    }

    public static Zone createPuddle(float x, float y) {
        return createPuddle(x, y, NO_PLAYER);
    }

    public static Zone createPuddle(
        float x,
        float y,
        int ownerPlayerId
    ) {
        return new Zone(
            Type.PUDDLE,
            x,
            y,
            PUDDLE_RADIUS,
            PUDDLE_DURATION,
            ownerPlayerId
        );
    }

    public static Zone createSteam(float x, float y) {
        return createSteam(x, y, NO_PLAYER);
    }

    public static Zone createSteam(
        float x,
        float y,
        int ownerPlayerId
    ) {
        return new Zone(
            Type.STEAM,
            x,
            y,
            STEAM_RADIUS,
            STEAM_DURATION,
            ownerPlayerId
        );
    }

    public boolean isElectrified() {
        return electrified;
    }

    public void electrify(int ownerPlayerId) {
        if (type == Type.PUDDLE) {
            electrified = true;
            damageOwnerPlayerId = ownerPlayerId;
            chargeFlashRemaining = CHARGE_FLASH_DURATION;
        }
    }

    // Keeps existing tests and client-side code usable.
    public void electrify() {
        electrify(damageOwnerPlayerId);
    }

    public int getDamageOwnerPlayerId() {
        return damageOwnerPlayerId;
    }

    public void setDamageOwnerPlayerId(int playerId) {
        damageOwnerPlayerId = playerId;
    }

    public void discharge() {
        if (electrified) {
            electrified = false;
            chargeFlashRemaining = CHARGE_FLASH_DURATION;
        }
    }

    public boolean isChargeFlashVisible() {
        return chargeFlashRemaining > 0f;
    }

    public float getChargeFlashRemaining() {
        return chargeFlashRemaining;
    }

    // Used when restoring authoritative network state.
    public void setChargeFlashRemaining(float seconds) {
        chargeFlashRemaining = Math.max(0f, seconds);
    }

    public void update(float deltaSeconds) {
        remainingSeconds =
            Math.max(0f, remainingSeconds - deltaSeconds);

        chargeFlashRemaining =
            Math.max(0f, chargeFlashRemaining - deltaSeconds);
    }

    public boolean hasExpired() {
        return remainingSeconds <= 0f;
    }

    public Type getType() {
        return type;
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