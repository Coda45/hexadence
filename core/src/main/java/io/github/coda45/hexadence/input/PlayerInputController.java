package io.github.coda45.hexadence.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import io.github.coda45.hexadence.entity.Player;

// Converts keyboard state and mouse input into commands for the simulation.
public class PlayerInputController extends InputAdapter {

    private final Vector2 mouseWorld = new Vector2();
    // Keep a click until the next simulation step, even if a render frame has no step.
    // Multiple clicks before that step count as one request.
    private boolean emberPressed;
    private boolean waterPressed;
    private boolean lightningPressed;

    public PlayerCommand sample(
        Viewport viewport,
        Player player
    ) {

        float moveX = 0f;
        float moveY = 0f;
        boolean castEmber =
            emberPressed
            || Gdx.input.isButtonPressed(Input.Buttons.LEFT);

        boolean castWater =
            waterPressed
            || Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

        boolean castLightning =
            lightningPressed
            || Gdx.input.isKeyPressed(Input.Keys.Q);

        // Consume once so catch-up steps don't repeat the same shot.
        emberPressed = false;
        waterPressed = false;
        lightningPressed = false;

        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            moveY += 1f;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            moveY -= 1f;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            moveX -= 1f;
        }

        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            moveX += 1f;
        }

        mouseWorld.set(
            Gdx.input.getX(),
            Gdx.input.getY()
        );

        // Account for the camera and viewport before comparing mouse and player positions.
        viewport.unproject(mouseWorld);

        float aimX = mouseWorld.x - player.getCastX();
        float aimY = mouseWorld.y - player.getCastY();

        return new PlayerCommand(moveX, moveY, aimX, aimY,
            castEmber, castWater, castLightning
        );
    }
    
    public void clearLatchedActions() {
        emberPressed = false;
        waterPressed = false;
        lightningPressed = false;
    }

    @Override
    public boolean touchDown(
        int screenX,
        int screenY,
        int pointer,
        int button
    ) {
        if (button == Input.Buttons.LEFT) {
            emberPressed = true;
        }

        if (button == Input.Buttons.RIGHT) {
            waterPressed = true;
        }

        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.Q) {
            lightningPressed = true;
        }

        return false;
    }
}
