package io.github.coda45.hexadence.render.animation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// A short fading trail of recent positions behind a moving Void projectile.
public final class VoidTrailVisual {

    private static final float TRAIL_SECONDS = 0.25f;
    private static final float SAMPLE_INTERVAL_SECONDS = 0.03f;

    public static final class Sample {

        private final float x;
        private final float y;
        private float age;

        private Sample(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public float getAge() {
            return age;
        }
    }

    private final List<Sample> samples = new ArrayList<>();
    private float timeSinceLastSample = SAMPLE_INTERVAL_SECONDS;

    public void update(float deltaSeconds, float x, float y) {
        timeSinceLastSample += deltaSeconds;

        Iterator<Sample> iterator = samples.iterator();

        while (iterator.hasNext()) {
            Sample sample = iterator.next();
            sample.age += deltaSeconds;

            if (sample.age >= TRAIL_SECONDS) {
                iterator.remove();
            }
        }

        if (timeSinceLastSample >= SAMPLE_INTERVAL_SECONDS) {
            samples.add(new Sample(x, y));
            timeSinceLastSample = 0f;
        }
    }

    public List<Sample> getSamples() {
        return samples;
    }

    public static float getTrailSeconds() {
        return TRAIL_SECONDS;
    }
}
