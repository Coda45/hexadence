package io.github.coda45.hexadence.screen;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.ScreenUtils;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.input.PlayerInputController;
import io.github.coda45.hexadence.net.NetSession;
import io.github.coda45.hexadence.render.HudRenderer;
import io.github.coda45.hexadence.render.WorldCamera;
import io.github.coda45.hexadence.render.WorldRenderer;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaMaps;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.StepResult;
import io.github.coda45.hexadence.world.WorldEvent;

// Coordinates input, fixed simulation steps, camera updates, and drawing.
public class ArenaScreen implements Screen {

    private final ArenaMap arenaMap;
    private final ArenaWorld world;
    // Null means normal single-player play.
    private final NetSession session;

    private final PlayerInputController inputController;
    private final WorldCamera worldCamera;
    private final WorldRenderer renderer;
    private final HudRenderer hudRenderer;

    private long lastAppliedTick = -1;
    private float accumulator;

    // Multiplayer: a match on the dedicated server. The lobby hands over a
    // session whose match has already started.
    public ArenaScreen(NetSession session) {
        this(ArenaMaps.MULTIPLAYER, session);
    }

    // Single-player on a local map, such as the training grounds.
    public ArenaScreen(String localMapPath) {
        this(localMapPath, null);
    }

    private ArenaScreen(
        String mapPath,
        NetSession session
    ) {
        worldCamera = new WorldCamera();
        inputController = new PlayerInputController();

        arenaMap = new ArenaMap(mapPath);
        ArenaDefinition definition =
            arenaMap.createDefinition();

        world = new ArenaWorld(definition);
        this.session = session;

        // The server decides who is in the match; drop the local player 0.
        if (session != null) {
            world.removePlayer(0);
        }

        renderer = new WorldRenderer(arenaMap);
        hudRenderer = new HudRenderer();
    }

    // Single-player: player 0. Multiplayer: the player the server assigned us,
    // or null until the Welcome and the first snapshot arrive.
    private Player localPlayer() {
        if (session == null) {
            return world.getPlayer();
        }

        return world.getPlayers().get(session.client().getPlayerId());
    }

    @Override
    public void render(float delta) {

        // Clamp cosmetic playback separately from simulation catch-up after a stall.
        float presentationDelta =
            Math.min(delta, 0.1f);

        delta = Math.min(delta, 0.25f);

        accumulator += delta;

        // Preserve events from every fixed step until this frame is drawn once.
        List<WorldEvent> frameEvents =
            new ArrayList<>();

        // Run whole 60 Hz steps and keep leftover time for the next frame.
        while (accumulator >= ArenaWorld.STEP) {

            if (session == null) {
                PlayerCommand command =
                    inputController.sample(
                    worldCamera.getViewport(),
                    world.getPlayer()
                    );
                
                StepResult result = world.step(command);
                frameEvents.addAll(result.events());
            } else {
                sendLocalInput();
            }

            accumulator -= ArenaWorld.STEP;
        }

        if (session != null) {
            applyHostState(frameEvents);
        }

        // Draw the latest completed step; positions are not interpolated between steps.
        Player player = localPlayer();

        if (player != null) {
            worldCamera.follow(
                player.getX(),
                player.getY(),
                arenaMap.getWorldWidth(),
                arenaMap.getWorldHeight()
            );
        }

        ScreenUtils.clear(
            0.05f,
            0.05f,
            0.07f,
            1f
        );

        worldCamera.getViewport().apply();

        renderer.render(
            world,
            worldCamera,
            presentationDelta,
            frameEvents
        );

        if (player != null) {
            hudRenderer.render(player);
        }
    }

    private void resetFrameState() {
        accumulator = 0f;
        inputController.clearLatchedActions();
    }

    @Override
    public void resize(int width, int height) {
        worldCamera.resize(width, height);
        hudRenderer.resize(width, height);
    }

    @Override
    public void dispose() {
        hudRenderer.dispose();
        renderer.dispose();
        arenaMap.dispose();
    }

    @Override
    public void show() {
        resetFrameState();
        Gdx.input.setInputProcessor(inputController);
    }

    @Override
    public void hide() {
        resetFrameState();

        if (session != null) {
            session.close();
        }
        
        // A replacement screen may already have installed its processor.
        if (Gdx.input.getInputProcessor() == inputController) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void pause() {
        resetFrameState();
    }

    @Override
    public void resume() {
        resetFrameState();
    }

    private void sendLocalInput() {
        Player player = localPlayer();

        // Still waiting for Welcome + first snapshot.
        if (player == null) {
            return;
        }

        session.client().send(
            inputController.sample(worldCamera.getViewport(), player)
        );
    }

    private void applyHostState(
        List<WorldEvent> frameEvents
    ) {
        var snapshot = session.client().getLatestSnapshot();

        if (snapshot != null && snapshot.tick() != lastAppliedTick) {
            world.applySnapshot(snapshot);
            lastAppliedTick = snapshot.tick();
        }

        frameEvents.addAll(
            session.client().drainEvents()
        );
    }
}
