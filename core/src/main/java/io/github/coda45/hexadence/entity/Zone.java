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

    private boolean electrified;

    private Zone(
        Type type,
        float x,
        float y,
        float radius,
        float duration
    ) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        remainingSeconds = duration;
    }

    public static Zone createPuddle(float x, float y) {
        return new Zone(
            Type.PUDDLE,
            x,
            y,
            PUDDLE_RADIUS,
            PUDDLE_DURATION
        );
    }

    public static Zone createSteam(float x, float y) {
        return new Zone(
            Type.STEAM,
            x,
            y,
            STEAM_RADIUS,
            STEAM_DURATION
        );
    }

    public boolean isElectrified() {
        return electrified;
    }

    public void electrify() {
        if (type == Type.PUDDLE) {
            electrified = true;
        }
    }

    public void discharge() {
        electrified = false;
    }

    public void update(float deltaSeconds) {
        remainingSeconds =
            Math.max(0f, remainingSeconds - deltaSeconds);
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