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

    private static final int DEFAULT_PORT = 7777;

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

    public ArenaScreen() {
        this(
            System.getProperty(
                "hexadence.map",
                ArenaMaps.MULTIPLAYER
            ),
            true
        );
    }

    public ArenaScreen(String localMapPath) {
        this(localMapPath, false);
    }

    private ArenaScreen(
        String mapPath,
        boolean networkingEnabled
    ) {
        worldCamera = new WorldCamera();
        inputController = new PlayerInputController();

        arenaMap = new ArenaMap(mapPath);
        ArenaDefinition definition =
            arenaMap.createDefinition();

        world = new ArenaWorld(definition);

        session = networkingEnabled
            ? openSession(definition, mapPath)
            : null;

        if (session != null) {
            world.removePlayer(0);
        }

        renderer = new WorldRenderer(arenaMap);
        hudRenderer = new HudRenderer();
    }

    // Reads -Dhexadence.host=true or -Dhexadence.join=host[:port].
    // Neither set: returns null and the game is single-player.
    private static NetSession openSession(
        ArenaDefinition definition,
        String mapId
    ) {
        int port = Integer.getInteger("hexadence.port", DEFAULT_PORT);
        String join = System.getProperty("hexadence.join");

        try {
            if (Boolean.getBoolean("hexadence.host")) {
                return NetSession.host(definition, mapId, port);
            }

            if (join != null) {
                int colon = join.lastIndexOf(':');
                String address = colon < 0 ? join : join.substring(0, colon);
                int joinPort = colon < 0
                    ? port
                    : Integer.parseInt(join.substring(colon + 1));

                return NetSession.join(address, mapId, joinPort);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while connecting", e);
        }

        return null;
    }

    // Single-player: player 0. Multiplayer: the player the host assigned us,
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
