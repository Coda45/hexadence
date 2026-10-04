package io.github.coda45.hexadence.render.animation;

import io.github.coda45.hexadence.entity.Zone;

public final class ChargedPuddleVisual {

    private static final float REPLAY_PAUSE_SECONDS = 0.9f;

    private final Zone zone;

    private int variantIndex;
    private float stateTime;
    private float waitRemaining;

    // New visuals play immediately. The wait applies only between repeats.
    private boolean playing = true;

    public ChargedPuddleVisual(
        Zone zone,
        int variantIndex
    ) {
        this.zone = zone;
        this.variantIndex = variantIndex;
    }

    public void update(
        float deltaSeconds,
        float animationDuration
    ) {
        if (playing) {
            stateTime += deltaSeconds;

            if (stateTime >= animationDuration) {
                playing = false;
                stateTime = animationDuration;

                waitRemaining = REPLAY_PAUSE_SECONDS;
            }

            return;
        }

        waitRemaining = Math.max(0f, waitRemaining - deltaSeconds);
    }

    public boolean isReadyToPlayAgain() {
        return !playing && waitRemaining <= 0f;
    }

    public void start(int newVariantIndex) {
        variantIndex = newVariantIndex;
        stateTime = 0f;
        playing = true;
    }

    public Zone getZone() {
        return zone;
    }

    public int getVariantIndex() {
        return variantIndex;
    }

    public float getStateTime() {
        return stateTime;
    }

    public boolean isPlaying() {
        return playing;
    }
}
