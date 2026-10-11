package io.github.coda45.hexadence.entity;

public class Zone {

    public enum Type {
        PUDDLE,
        STEAM
    }

    public static final int UNASSIGNED_ID = -1;
    public static final int NO_PLAYER = -1;

    private static final float PUDDLE_RADIUS = 0.8f;
    private static final float PUDDLE_DURATION = 6f;
    private static final float STEAM_RADIUS = 1f;
    private static final float STEAM_DURATION = 3f;

    private final int id;
    private final Type type;
    private final float x;
    private final float y;
    private final float radius;

    private float remainingSeconds;
    private int damageOwnerPlayerId;
    private boolean electrified;

    private Zone(
        int id,
        Type type,
        float x,
        float y,
        float radius,
        float duration,
        int damageOwnerPlayerId
    ) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.remainingSeconds = duration;
        this.damageOwnerPlayerId = damageOwnerPlayerId;
    }

    public static Zone createPuddle(float x, float y) {
        return createPuddle(UNASSIGNED_ID, x, y, NO_PLAYER);
    }

    public static Zone createPuddle(
        float x,
        float y,
        int ownerPlayerId
    ) {
        return createPuddle(UNASSIGNED_ID, x, y, ownerPlayerId);
    }

    public static Zone createPuddle(
        int id,
        float x,
        float y,
        int ownerPlayerId
    ) {
        return new Zone(
            id,
            Type.PUDDLE,
            x,
            y,
            PUDDLE_RADIUS,
            PUDDLE_DURATION,
            ownerPlayerId
        );
    }

    public static Zone createSteam(float x, float y) {
        return createSteam(UNASSIGNED_ID, x, y, NO_PLAYER);
    }

    public static Zone createSteam(
        float x,
        float y,
        int ownerPlayerId
    ) {
        return createSteam(UNASSIGNED_ID, x, y, ownerPlayerId);
    }

    public static Zone createSteam(
        int id,
        float x,
        float y,
        int ownerPlayerId
    ) {
        return new Zone(
            id,
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
        }
    }

    // Keeps existing tests and client-side code usable.
    public void electrify() {
        electrify(damageOwnerPlayerId);
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

    public void discharge() {
        if (electrified) {
            electrified = false;
        }
    }

    public void update(float deltaSeconds) {
        remainingSeconds = Math.max(0f, remainingSeconds - deltaSeconds);
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
