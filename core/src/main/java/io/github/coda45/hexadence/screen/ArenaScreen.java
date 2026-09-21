package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.ScreenUtils;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.input.PlayerInputController;
import io.github.coda45.hexadence.render.WorldCamera;
import io.github.coda45.hexadence.render.WorldRenderer;
import io.github.coda45.hexadence.world.ArenaWorld;


/** Coordinates input, fixed simulation steps, camera updates, and drawing. */
public class ArenaScreen implements Screen {

    private final WorldCamera worldCamera;
    private final PlayerInputController inputController;

    private final ArenaWorld world;
    private final WorldRenderer renderer;

    private float accumulator;

    public ArenaScreen() {
        
        worldCamera = new WorldCamera();
        inputController = new PlayerInputController();
        world = new ArenaWorld();
        renderer = new WorldRenderer(world.getArenaMap());
    }

    @Override
    public void render(float delta) {

        // Drop excess time after a long stall to limit catch-up work in one frame.
        delta = Math.min(delta, 0.25f);

        accumulator += delta;

        // Run whole 60 Hz steps and keep leftover time for the next frame.
        while (accumulator >= ArenaWorld.STEP) {

            PlayerCommand command =
                inputController.sample(
                worldCamera.getViewport(),
                world.getPlayer()
            );

            world.step(command);

            accumulator -= ArenaWorld.STEP;
        }

        // Draw the latest completed step; positions are not interpolated between steps.
        Player player = world.getPlayer();

        worldCamera.follow(
            player.getX(),
            player.getY(),
            world.getArenaMap().getWorldWidth(),
            world.getArenaMap().getWorldHeight()
        );

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

    @Override public void show() {
        Gdx.input.setInputProcessor(inputController);
    }

    @Override public void hide() {
        // A replacement screen may already have installed its own processor.
        if (Gdx.input.getInputProcessor() == inputController) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override public void pause() {}
    
    @Override public void resume() {}
}
