package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.ScreenUtils;

import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.input.PlayerInputController;
import io.github.coda45.hexadence.render.WorldCamera;
import io.github.coda45.hexadence.render.WorldRenderer;
import io.github.coda45.hexadence.world.ArenaWorld;


public class ArenaScreen implements Screen {

    private final WorldCamera worldCamera;
    private final PlayerInputController inputController;

    private final ArenaWorld world;
    private final WorldRenderer renderer;

    public ArenaScreen() {
        
        worldCamera = new WorldCamera();
        inputController = new PlayerInputController();
        world = new ArenaWorld();
        renderer = new WorldRenderer(world.getArenaMap());
    }

    @Override
    public void render(float delta) {

        PlayerCommand command = inputController.sample();

        world.update(command, delta);

        ScreenUtils.clear(
            0.05f,
            0.05f,
            0.07f,
            1f
        );

        worldCamera.getViewport().apply();

        renderer.render(
            world,
            worldCamera
        );
    }

    @Override
    public void resize(int width, int height) {
        worldCamera.resize(width, height);
    }

    @Override
    public void dispose() {
        renderer.dispose();
        world.dispose();
    }

    @Override public void show() {}
    @Override public void hide() {}
    @Override public void pause() {}
    @Override public void resume() {}
}
