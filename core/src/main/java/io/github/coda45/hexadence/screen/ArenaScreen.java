package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.input.PlayerInputController;
import io.github.coda45.hexadence.render.WorldCamera;
import io.github.coda45.hexadence.world.ArenaWorld;


public class ArenaScreen implements Screen {

    private final WorldCamera worldCamera;
    private final SpriteBatch batch;
    private final PlayerInputController inputController;

    private final ArenaWorld world;

    public ArenaScreen() {
        worldCamera = new WorldCamera();
        batch = new SpriteBatch();
        inputController = new PlayerInputController();

        world = new ArenaWorld();
    }

    @Override
    public void render(float delta) {

        PlayerCommand command = inputController.sample();

        world.update(command, delta);

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
