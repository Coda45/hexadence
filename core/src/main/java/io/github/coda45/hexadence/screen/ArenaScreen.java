package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.input.PlayerInputController;
import io.github.coda45.hexadence.render.WorldCamera;


public class ArenaScreen implements Screen {

    private final WorldCamera worldCamera;
    private final SpriteBatch batch;
    private final PlayerInputController inputController;

    private final Player player;

    public ArenaScreen() {
        worldCamera = new WorldCamera();
        batch = new SpriteBatch();
        inputController = new PlayerInputController();

        player = new Player(0f, 0f);
    }

    @Override
    public void render(float delta) {
        PlayerCommand command = inputController.sample();

        batch.setProjectionMatrix(
            worldCamera.getCamera().combined
        );
        // rendering here
    }

    @Override
    public void resize(int width, int height) {
        worldCamera.resize(width, height);
    }

    @Override
    public void dispose() {
        batch.dispose();
    }

    @Override public void show() {}
    @Override public void hide() {}
    @Override public void pause() {}
    @Override public void resume() {}
}
