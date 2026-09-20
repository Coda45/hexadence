package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class WorldCamera {
    // VIEW w x h measured in tiles
    private static final float VIEW_WIDTH = 26.6667f;
    private static final float VIEW_HEIGHT = 15f;

    private final OrthographicCamera camera;
    private final FitViewport viewport;

    public WorldCamera() {
        camera = new OrthographicCamera();

        viewport = new FitViewport(
            VIEW_WIDTH,
            VIEW_HEIGHT,
            camera
        );
        // Center camera using half of map w x h for now
        camera.position.set(15f, 10f, 0f);
        camera.update();
    }
    
    public void follow(float targetX, float targetY, float mapWidth, float mapHeight) {
        float halfWidth = VIEW_WIDTH / 2f;
        float halfHeight = VIEW_HEIGHT / 2f;

        float cameraX = MathUtils.clamp(
            targetX,
            halfWidth,
            mapWidth - halfWidth
        );

        float cameraY = MathUtils.clamp(
            targetY,
            halfHeight,
            mapHeight - halfHeight
        );

        camera.position.set(cameraX, cameraY, 0f);
        camera.update();
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