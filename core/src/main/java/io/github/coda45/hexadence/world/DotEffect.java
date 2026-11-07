package io.github.coda45.hexadence.world;

// One active damage-over-time instance on a player or dummy, started by a
// hit from a power like Void Rot. A later hit from the same power adds a
// stack (up to the power's cap) and restarts the duration, instead of
// stacking a second independent instance (see ArenaWorld.applyDotIfAny).
final class DotEffect {

    private final float baseDamagePerTick;
    private final float tickSeconds;
    private final float durationSeconds;
    private final int maxStacks;

    private int stacks;
    private int attackerPlayerId;
    private float tickTimer;
    private float remainingSeconds;

    DotEffect(
        float baseDamagePerTick,
        float tickSeconds,
        float durationSeconds,
        int maxStacks,
        int attackerPlayerId
    ) {
        this.baseDamagePerTick = baseDamagePerTick;
        this.tickSeconds = tickSeconds;
        this.durationSeconds = durationSeconds;
        this.maxStacks = maxStacks;
        this.attackerPlayerId = attackerPlayerId;
        this.stacks = 1;
        this.tickTimer = tickSeconds;
        this.remainingSeconds = durationSeconds;
    }

    // Adds another stack, capped at maxStacks, and restarts the duration and
    // tick timer as a fresh application would. Attribution moves to whoever
    // landed this latest hit.
    void reapply(int attackerPlayerId) {
        if (stacks < maxStacks) {
            stacks++;
        }

        this.attackerPlayerId = attackerPlayerId;
        this.tickTimer = tickSeconds;
        this.remainingSeconds = durationSeconds;
    }

    // Advances by one step; returns true the instant a tick lands.
    boolean update(float deltaSeconds) {
        remainingSeconds -= deltaSeconds;
        tickTimer -= deltaSeconds;

        if (tickTimer > 0f) {
            return false;
        }

        tickTimer += tickSeconds;
        return true;
    }

    boolean hasExpired() {
        return remainingSeconds <= 0f;
    }

    // Base damage per tick multiplied by the current stack count.
    float damagePerTick() {
        return baseDamagePerTick * stacks;
    }

    int attackerPlayerId() {
        return attackerPlayerId;
    }
}
