package io.github.coda45.hexadence.entity;

import io.github.coda45.hexadence.power.Power;

// A server-controlled caster that appears late in a match. It circles the
// middle of the arena and rains one zone power outward. It has no body:
// players walk through it, and only its zones hurt.
//
// When it appears and what it casts are decided by GameRoom and ArenaWorld.
public class Maverick {

    // Stands in for a player ID wherever damage is credited (a zone's owner,
    // a kill's killer), so the Maverick's zones credit no player.
    public static final int SOURCE_ID = -2;

    // Distance from the middle of the arena to the Maverick, in tiles.
    public static final float ORBIT_RADIUS = 1.5f;
    // Seconds to circle the middle once.
    public static final float ORBIT_SECONDS = 8f;
    // How far outward from the Maverick its zones land, in tiles.
    public static final float CAST_DISTANCE = 4f;
    // Its time between casts, as a share of the power's cooldown.
    public static final float CAST_INTERVAL_FRACTION = 0.5f;

    private final float centerX;
    private final float centerY;
    private final Power power;

    // Radians around the center, counterclockwise from due east.
    private float angle;
    private float secondsUntilCast;

    private int kills;
    private float damageDealt;

    public Maverick(float centerX, float centerY, Power power) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.power = power;
        this.secondsUntilCast = getCastIntervalSeconds();
    }

    // Moves along the circle at a steady speed.
    public void update(float deltaSeconds) {
        angle += (float) (2.0 * Math.PI) * deltaSeconds / ORBIT_SECONDS;
    }

    // Counts down to the next cast. Returns true when one is due, and starts
    // the countdown to the one after.
    public boolean advanceCastTimer(float deltaSeconds) {
        secondsUntilCast -= deltaSeconds;

        if (secondsUntilCast > 0f) {
            return false;
        }

        secondsUntilCast += getCastIntervalSeconds();
        return true;
    }

    public float getCastIntervalSeconds() {
        return power.cooldownSeconds() * CAST_INTERVAL_FRACTION;
    }

    public Power getPower() {
        return power;
    }

    public float getAngle() {
        return angle;
    }

    public float getX() {
        return centerX + getDirectionX() * ORBIT_RADIUS;
    }

    public float getY() {
        return centerY + getDirectionY() * ORBIT_RADIUS;
    }

    // Straight out from the middle of the arena, normalized. The Maverick
    // casts this way.
    public float getDirectionX() {
        return (float) Math.cos(angle);
    }

    public float getDirectionY() {
        return (float) Math.sin(angle);
    }

    public float getSecondsUntilCast() {
        return secondsUntilCast;
    }

    public void recordKill() {
        kills++;
    }

    public int getKills() {
        return kills;
    }

    public void recordDamageDealt(float amount) {
        damageDealt += amount;
    }

    public float getDamageDealt() {
        return damageDealt;
    }

    // Clients copy the server's Maverick instead of moving their own.
    public void restoreState(float angle, float secondsUntilCast) {
        this.angle = angle;
        this.secondsUntilCast = secondsUntilCast;
    }
}
