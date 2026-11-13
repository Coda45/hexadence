package io.github.coda45.hexadence.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

// Covers the whole window, letterbox bars included, with black at some
// opacity. Used to fade out of a match and into the results screen.
//
// It resets the GL viewport to the full window, so apply your own viewport
// again before drawing anything after it.
public final class ScreenFade {

    private final ShapeRenderer shapes = new ShapeRenderer();
    // Maps the unit square onto the full window.
    private final Matrix4 projection = new Matrix4().setToOrtho2D(0f, 0f, 1f, 1f);

    // 0 draws nothing; 1 is solid black.
    public void draw(float alpha) {
        alpha = Math.max(0f, Math.min(1f, alpha));

        if (alpha <= 0f) {
            return;
        }

        Gdx.gl.glViewport(
            0,
            0,
            Gdx.graphics.getBackBufferWidth(),
            Gdx.graphics.getBackBufferHeight()
        );
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, alpha);
        shapes.rect(0f, 0f, 1f, 1f);
        shapes.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void dispose() {
        shapes.dispose();
    }
}
