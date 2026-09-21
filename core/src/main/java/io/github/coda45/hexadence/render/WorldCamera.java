package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class WorldCamera {
    // VIEW w x h measured in tiles
    private static final float VIEW_WIDTH = 640f / 24f;
    private static final float VIEW_HEIGHT = 360f / 24f;

    private final OrthographicCamera camera;
    private final FitViewport viewport;

    public WorldCamera() {
        camera = new OrthographicCamera();

        viewport = new FitViewport(
            VIEW_WIDTH,
            VIEW_HEIGHT,
            camera
        );
    }
    
    public void follow(float targetX, float targetY, float mapWidth, float mapHeight) {
        float halfViewWidth = VIEW_WIDTH / 2f;
        float halfViewHeight = VIEW_HEIGHT / 2f;

        float cameraX = MathUtils.clamp(
            targetX,
            halfViewWidth,
            mapWidth - halfViewWidth
        );

        float cameraY = MathUtils.clamp(
            targetY,
            halfViewHeight,
            mapHeight - halfViewHeight
        );

        camera.position.set(cameraX, cameraY, 0f);
        camera.update();
    }

    public void resize(int width, int height) {
        viewport.update(width, height, false);
        camera.update();
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public Viewport getViewport() {
        return viewport;
    }
}