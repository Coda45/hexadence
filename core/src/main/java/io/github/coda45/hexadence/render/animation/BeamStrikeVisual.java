package io.github.coda45.hexadence.render.animation;

import io.github.coda45.hexadence.world.BeamStrikeEvent;

public final class BeamStrikeVisual {

    private BeamStrikeEvent strike;
    private float stateTime;
    private final int variantIndex;

    public BeamStrikeVisual(BeamStrikeEvent strike, int variantIndex) {
        this.strike = strike;
        this.variantIndex = variantIndex;
    }

    public void update(float deltaSeconds) {
        stateTime += deltaSeconds;
    }

    // A held beam keeps one visual and moves its endpoints on each damage
    // sample instead of leaving overlapping animations behind.
    public void refresh(BeamStrikeEvent latestStrike) {
        strike = latestStrike;
    }

    public int getVariantIndex() {
        return variantIndex;
    }

    public boolean isFinished(float animationDuration) {
        return stateTime >= animationDuration;
    }

    public BeamStrikeEvent getStrike() {
        return strike;
    }

    public float getStateTime() {
        return stateTime;
    }
}
