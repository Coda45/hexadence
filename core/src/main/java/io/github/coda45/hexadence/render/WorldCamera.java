package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class WorldCamera {

    private static final float WORLD_WIDTH = 26.6667f;
    private static final float WORLD_HEIGHT = 15f;

    private final OrthographicCamera camera;
    private final FitViewport viewport;

    public WorldCamera() {
        camera = new OrthographicCamera();

        viewport = new FitViewport(
            WORLD_WIDTH,
            WORLD_HEIGHT,
            camera
        );
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public Viewport getViewport() {
        return viewport;
    }
}