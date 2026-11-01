package io.github.coda45.hexadence.entity;

import java.util.HashSet;
import java.util.Set;

import io.github.coda45.hexadence.power.Power;

// Stores one stable projectile identity and its mutable authoritative motion.
// ArenaWorld owns movement, collision, and removal.
public class Projectile {

    private final int id;
    private final Power power;
    private int ownerPlayerId;
    private float directionX;
    private float directionY;
    private float currentSpeed;
    private float travelLimit;
    private final Set<Integer> completedVortexSourceIds = new HashSet<>();

    private float x;
    private float y;
    private float distanceTraveled;

    // The direction must be normalized so each update travels the requested distance.
    public Projectile(
        int id,
        float x,
        float y,
        float directionX,
        float directionY,
        float travelLimit,
        Power power,
        int ownerPlayerId
    ) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.directionX = directionX;
        this.directionY = directionY;
        this.currentSpeed = power.speed();
        this.travelLimit = travelLimit;
        this.power = power;
        this.ownerPlayerId = ownerPlayerId;
    }

    // Client replicas do not advance projectiles, so their per-cast limit is
    // not currently replicated. Keep this constructor for snapshot restore.
    public Projectile(
        int id,
        float x,
        float y,
        float directionX,
        float directionY,
        Power power,
        int ownerPlayerId
    ) {
        this(
            id,
            x,
            y,
            directionX,
            directionY,
            power.range(),
            power,
            ownerPlayerId
        );
    }

    // Moves by a world-unit distance and records distance traveled, including the spawn step.
    public void update(float distance) {
        x += directionX * distance;
        y += directionY * distance;
        distanceTraveled += distance;
    }

    // Movement imposed by a field changes the path without consuming the
    // projectile's original straight-line range budget.
    public void updateWithoutConsumingRange(float distance) {
        x += directionX * distance;
        y += directionY * distance;
    }

    // Applies one acceleration sample to the projectile's actual velocity.
    // Unlike ordinary steering, this can slow, reverse, or accelerate it.
    public void accelerateToward(
        float targetX,
        float targetY,
        float acceleration,
        float deltaSeconds
    ) {
        float dx = targetX - x;
        float dy = targetY - y;
        float targetDistance = (float) Math.sqrt(dx * dx + dy * dy);

        if (targetDistance <= 0.0001f || acceleration <= 0f) {
            return;
        }

        float velocityX = directionX * currentSpeed
            + dx / targetDistance * acceleration * deltaSeconds;
        float velocityY = directionY * currentSpeed
            + dy / targetDistance * acceleration * deltaSeconds;
        float velocityLength = (float) Math.sqrt(
            velocityX * velocityX + velocityY * velocityY
        );

        if (velocityLength <= 0.0001f) {
            currentSpeed = 0f;
            return;
        }

        directionX = velocityX / velocityLength;
        directionY = velocityY / velocityLength;
        currentSpeed = velocityLength;
    }

    public Power getPower() {
        return power;
    }

    public float getRemainingTravel() {
        return Math.max(0f, travelLimit - distanceTraveled);
    }

    public boolean hasReachedTravelLimit() {
        return distanceTraveled >= travelLimit;
    }

    public int getId() {
        return id;
    }

    // Client-side snapshot restoration only.
    public void restorePosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void restoreState(
        float x,
        float y,
        float directionX,
        float directionY,
        int ownerPlayerId
    ) {
        this.x = x;
        this.y = y;
        this.directionX = directionX;
        this.directionY = directionY;
        this.currentSpeed = power.speed();
        this.ownerPlayerId = ownerPlayerId;
    }

    public boolean hasCompletedVortexOrbit(int sourceId) {
        return completedVortexSourceIds.contains(sourceId);
    }

    public void orbitTo(
        float x,
        float y,
        float tangentX,
        float tangentY,
        float orbitDistance
    ) {
        this.x = x;
        this.y = y;
        directionX = tangentX;
        directionY = tangentY;
        // Orbit distance does not consume the original range; it is added as
        // bonus range so the released projectile still travels just as far.
        travelLimit += orbitDistance;
    }

    public void releaseFromVortex(
        int sourceId,
        float tangentX,
        float tangentY,
        int newOwnerPlayerId
    ) {
        completedVortexSourceIds.add(sourceId);
        directionX = tangentX;
        directionY = tangentY;
        currentSpeed = power.speed();
        ownerPlayerId = newOwnerPlayerId;
    }

    public float getDistanceTraveled() {
        return distanceTraveled;
    }

    public int getOwnerPlayerId() {
        return ownerPlayerId;
    }

    public float getDirectionX() {
        return directionX;
    }

    public float getDirectionY() {
        return directionY;
    }

    public float getCurrentSpeed() {
        return currentSpeed;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }
}
