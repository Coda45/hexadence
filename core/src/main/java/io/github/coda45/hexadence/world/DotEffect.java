package io.github.coda45.hexadence.world;

// One active damage-over-time instance on a player or dummy, started by a
// hit from a power like Void Rot. A later hit from the same power replaces
// this with a fresh instance instead of stacking (see ArenaWorld.applyDotIfAny).
final class DotEffect {

    private final float damagePerTick;
    private final float tickSeconds;
    private final int attackerPlayerId;

    private float tickTimer;
    private float remainingSeconds;

    DotEffect(
        float damagePerTick,
        float tickSeconds,
        float durationSeconds,
        int attackerPlayerId
    ) {
        this.damagePerTick = damagePerTick;
        this.tickSeconds = tickSeconds;
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

    float damagePerTick() {
        return damagePerTick;
    }

    int attackerPlayerId() {
        return attackerPlayerId;
    }
}
