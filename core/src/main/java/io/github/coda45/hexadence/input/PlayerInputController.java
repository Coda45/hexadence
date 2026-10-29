package io.github.coda45.hexadence.input;

import java.util.ArrayList;
import java.util.List;

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
    // Slot 1 = left mouse, slot 2 = right mouse, slot 3 = Q.
    private boolean slot1Pressed;
    private boolean slot2Pressed;
    private boolean slot3Pressed;
    private int slot1ScreenX;
    private int slot1ScreenY;
    private int slot2ScreenX;
    private int slot2ScreenY;
    private int slot3ScreenX;
    private int slot3ScreenY;

    public PlayerCommand sample(
        Viewport viewport,
        Player player
    ) {

        float moveX = 0f;
        float moveY = 0f;
        boolean slot1WasPressed = slot1Pressed;
        boolean slot2WasPressed = slot2Pressed;
        boolean slot3WasPressed = slot3Pressed;
        boolean castSlot1 =
            slot1WasPressed
            || Gdx.input.isButtonPressed(Input.Buttons.LEFT);

        boolean castSlot2 =
            slot2WasPressed
            || Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

        boolean castSlot3 =
            slot3WasPressed
            || Gdx.input.isKeyPressed(Input.Keys.Q);

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

        int currentScreenX = Gdx.input.getX();
        int currentScreenY = Gdx.input.getY();
        Vector2 currentTarget = targetOffset(
            viewport,
            player,
            currentScreenX,
            currentScreenY
        );

        float aimX = currentTarget.x;
        float aimY = currentTarget.y;
        List<CastRequest> casts = new ArrayList<>(3);

        if (castSlot1) {
            casts.add(castRequest(
                0,
                viewport,
                player,
                slot1WasPressed ? slot1ScreenX : currentScreenX,
                slot1WasPressed ? slot1ScreenY : currentScreenY
            ));
        }
        if (castSlot2) {
            casts.add(castRequest(
                1,
                viewport,
                player,
                slot2WasPressed ? slot2ScreenX : currentScreenX,
                slot2WasPressed ? slot2ScreenY : currentScreenY
            ));
        }
        if (castSlot3) {
            casts.add(castRequest(
                2,
                viewport,
                player,
                slot3WasPressed ? slot3ScreenX : currentScreenX,
                slot3WasPressed ? slot3ScreenY : currentScreenY
            ));
        }

        // Consume once so catch-up steps don't repeat the same press. A held
        // binding may produce a new request with the current target next tick.
        slot1Pressed = false;
        slot2Pressed = false;
        slot3Pressed = false;

        return new PlayerCommand(moveX, moveY, aimX, aimY, casts);
    }

    private CastRequest castRequest(
        int slot,
        Viewport viewport,
        Player player,
        int screenX,
        int screenY
    ) {
        Vector2 target = targetOffset(
            viewport,
            player,
            screenX,
            screenY
        );

        return new CastRequest(slot, target.x, target.y);
    }

    private Vector2 targetOffset(
        Viewport viewport,
        Player player,
        int screenX,
        int screenY
    ) {
        mouseWorld.set(screenX, screenY);

        // Account for the camera and viewport before comparing mouse and player positions.
        viewport.unproject(mouseWorld);
        mouseWorld.sub(player.getCastX(), player.getCastY());

        return mouseWorld;
    }
    
    public void clearLatchedActions() {
        slot1Pressed = false;
        slot2Pressed = false;
        slot3Pressed = false;
    }

    @Override
    public boolean touchDown(
        int screenX,
        int screenY,
        int pointer,
        int button
    ) {
        if (button == Input.Buttons.LEFT) {
            slot1Pressed = true;
            slot1ScreenX = screenX;
            slot1ScreenY = screenY;
        }

        if (button == Input.Buttons.RIGHT) {
            slot2Pressed = true;
            slot2ScreenX = screenX;
            slot2ScreenY = screenY;
        }

        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.Q) {
            slot3Pressed = true;
            slot3ScreenX = Gdx.input.getX();
            slot3ScreenY = Gdx.input.getY();
        }

        return false;
    }
}
