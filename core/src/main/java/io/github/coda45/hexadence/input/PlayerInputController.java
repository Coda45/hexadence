package io.github.coda45.hexadence.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public class PlayerInputController {

    public PlayerCommand sample() {

        float moveX = 0f;
        float moveY = 0f;

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

        return new PlayerCommand(moveX, moveY);
    }
}