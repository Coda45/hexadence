package io.github.coda45.hexadence.render.animation;

import io.github.coda45.hexadence.world.LightningStrikeEvent;

public final class LightningStrikeVisual {

    private final LightningStrikeEvent strike;
    private float stateTime;
    private final int variantIndex;

    public LightningStrikeVisual(
        LightningStrikeEvent strike,
        int variantIndex
    ) {
        this.strike = strike;
        this.variantIndex = variantIndex;
    }

    public void update(float deltaSeconds) {
        stateTime += deltaSeconds;
    }

    public int getVariantIndex() {
        return variantIndex;
    }

    public boolean isFinished(
        float animationDuration
    ) {
        return stateTime >= animationDuration;
    }

    public LightningStrikeEvent getStrike() {
        return strike;
    }

    public float getStateTime() {
        return stateTime;
    }
}