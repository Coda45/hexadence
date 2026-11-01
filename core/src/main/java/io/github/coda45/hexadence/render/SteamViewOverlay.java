package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

// Local-only presentation shown while this client is standing in Steam.
// It owns no gameplay state and never affects visibility on the server.
public final class SteamViewOverlay {

    private static final float MAX_OPACITY = 0.9f;
    private static final float FADE_SECONDS = 0.18f;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ScreenViewport viewport = new ScreenViewport(camera);
    private final SpriteBatch batch = new SpriteBatch();
    private final Texture pixelTexture;

    private float opacity;

    public SteamViewOverlay() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixelTexture = new Texture(pixmap);
        pixmap.dispose();
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void render(float deltaSeconds, boolean insideSteam) {
        float opacityStep = MAX_OPACITY * deltaSeconds / FADE_SECONDS;
        opacity = MathUtils.clamp(
            opacity + (insideSteam ? opacityStep : -opacityStep),
            0f,
            MAX_OPACITY
        );

        if (opacity <= 0f) {
            return;
        }

        viewport.apply();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(1f, 1f, 1f, opacity);
        batch.draw(
            pixelTexture,
            0f,
            0f,
            viewport.getWorldWidth(),
            viewport.getWorldHeight()
        );
        batch.setColor(Color.WHITE);
        batch.end();
    }

    public void dispose() {
        batch.dispose();
        pixelTexture.dispose();
    }
}
