package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

// Follows the player with a fixed world view; FitViewport adds bars when aspect ratios differ.
public class WorldCamera {
    // A 640 x 360 reference view, converted from pixels to world units (one unit per tile).
    // The actual desktop window size is set in Lwjgl3Launcher.
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
    
    // Keeps the view inside the map. Assumes the map is at least as large as the view.
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
        // Preserve the follow position instead of recentering on the viewport.
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
