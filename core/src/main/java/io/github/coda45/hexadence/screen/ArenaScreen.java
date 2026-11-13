package io.github.coda45.hexadence.screen;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.ScreenUtils;

import io.github.coda45.hexadence.HexadenceGame;
import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.input.PlayerInputController;
import io.github.coda45.hexadence.net.MatchEnded;
import io.github.coda45.hexadence.net.NetSession;
import io.github.coda45.hexadence.render.HudRenderer;
import io.github.coda45.hexadence.render.SteamViewOverlay;
import io.github.coda45.hexadence.render.WorldCamera;
import io.github.coda45.hexadence.render.WorldRenderer;
import io.github.coda45.hexadence.world.ArenaDefinition;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaMaps;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.StepResult;
import io.github.coda45.hexadence.world.WorldEvent;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Coordinates input, fixed simulation steps, camera updates, and drawing.
public class ArenaScreen implements Screen {

    private final HexadenceGame game;
    private final ArenaMap arenaMap;
    private final ArenaWorld world;
    // Null means normal single-player play.
    private final NetSession session;

    private final PlayerInputController inputController;
    private final WorldCamera worldCamera;
    private final WorldRenderer renderer;
    private final HudRenderer hudRenderer;
    private final PauseMenu pauseMenu;
    private final SteamViewOverlay steamViewOverlay;
    private final boolean training;

    private long lastAppliedTick = -1;
    private float accumulator;
    private boolean menuOpen;
    private boolean disposed;
    // Set when the results screen takes over the connection, so hiding this
    // screen does not close it.
    private boolean sessionHandedOff;

    // Multiplayer: a match on the dedicated server. The lobby hands over a
    // session whose match has already started.
    public ArenaScreen(HexadenceGame game, NetSession session) {
        this(game, ArenaMaps.MULTIPLAYER, session);
    }

    // Single-player on a local map, such as the training grounds.
    public ArenaScreen(HexadenceGame game, String localMapPath) {
        this(game, localMapPath, null);
    }

    private ArenaScreen(
        HexadenceGame game,
        String mapPath,
        NetSession session
    ) {
        this.game = game;
        worldCamera = new WorldCamera();
        inputController = new PlayerInputController();

        arenaMap = new ArenaMap(mapPath);
        ArenaDefinition definition =
            arenaMap.createDefinition();

        world = new ArenaWorld(definition);
        this.session = session;
        training = session == null && ArenaMaps.TRAINING.equals(mapPath);

        // The server decides who is in the match; drop the local player 0.
        if (session != null) {
            world.removePlayer(0);
        }

        renderer = new WorldRenderer(arenaMap);
        hudRenderer = new HudRenderer();
        steamViewOverlay = new SteamViewOverlay();
        pauseMenu = new PauseMenu(
            training,
            training ? world.getPlayer().getLoadout() : null
        );
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

        // The server ended the match: show everyone's stats.
        if (session != null) {
            MatchEnded results = session.client().getMatchResults();

            if (results != null) {
                showResults(results);
                return;
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            setMenuOpen(!menuOpen);
        }

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
                PlayerCommand command = menuOpen
                    ? PlayerCommand.IDLE
                    : inputController.sample(
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

        hudRenderer.recordEvents(frameEvents, world.getPlayers(), localPlayerId());

        if (player != null) {
            hudRenderer.render(player, presentationDelta);
        }

        steamViewOverlay.render(
            presentationDelta,
            world.isPlayerOverlappingZoneKind(player, ZoneKinds.STEAM)
        );

        if (menuOpen) {
            pauseMenu.render(presentationDelta);
            applyTrainingLoadoutChange();

            if (pauseMenu.takeResumeRequest()) {
                setMenuOpen(false);
            } else if (pauseMenu.takeQuitRequest()) {
                returnToMainMenu();
            }
        }
    }

    private void applyTrainingLoadoutChange() {
        if (!training) {
            return;
        }

        var loadout = pauseMenu.takeLoadoutChange();

        if (loadout != null) {
            world.getPlayer().setLoadout(loadout);
        }
    }

    private void setMenuOpen(boolean open) {
        menuOpen = open;
        resetFrameState();
        Gdx.input.setInputProcessor(
            menuOpen ? pauseMenu.inputProcessor() : inputController
        );
    }

    private void returnToMainMenu() {
        game.setScreen(new MainMenuScreen(game));
        dispose();
    }

    private void showResults(MatchEnded results) {
        Player player = localPlayer();

        sessionHandedOff = true;
        game.setScreen(new ResultsScreen(
            game,
            session,
            results,
            player == null ? null : player.getLoadout()
        ));
        dispose();
    }

    private int localPlayerId() {
        return session == null ? 0 : session.client().getPlayerId();
    }

    private void resetFrameState() {
        accumulator = 0f;
        inputController.clearLatchedActions();
    }

    @Override
    public void resize(int width, int height) {
        worldCamera.resize(width, height);
        hudRenderer.resize(width, height);
        pauseMenu.resize(width, height);
        steamViewOverlay.resize(width, height);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        disposed = true;
        pauseMenu.dispose();
        steamViewOverlay.dispose();
        hudRenderer.dispose();
        renderer.dispose();
        arenaMap.dispose();
    }

    @Override
    public void show() {
        resetFrameState();
        Gdx.input.setInputProcessor(
            menuOpen ? pauseMenu.inputProcessor() : inputController
        );
    }

    @Override
    public void hide() {
        resetFrameState();

        if (session != null && !sessionHandedOff) {
            session.close();
        }
        
        // A replacement screen may already have installed its processor.
        if (
            Gdx.input.getInputProcessor() == inputController
            || pauseMenu.ownsInputProcessor(Gdx.input.getInputProcessor())
        ) {
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

        PlayerCommand command = menuOpen
            ? PlayerCommand.IDLE
            : inputController.sample(worldCamera.getViewport(), player);

        session.client().send(command);
    }

    private void applyHostState(
        List<WorldEvent> frameEvents
    ) {
        var snapshot = session.client().getLatestSnapshot();

        if (snapshot != null && snapshot.tick() != lastAppliedTick) {
            world.applySnapshot(snapshot);
            lastAppliedTick = snapshot.tick();
            hudRenderer.setMatchTimer(snapshot.matchSecondsRemaining());
        }

        frameEvents.addAll(
            session.client().drainEvents()
        );
    }
}
