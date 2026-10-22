package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.HexadenceGame;
import io.github.coda45.hexadence.net.GameServer;
import io.github.coda45.hexadence.net.LobbyConnection;
import io.github.coda45.hexadence.net.ServerAddress;
import io.github.coda45.hexadence.world.ArenaMaps;

// The host's lobby, using a Scene2D table layout. Opening it creates a room
// on the server and shows its code; START begins the match for everyone in it.
public class HostLobbyScreen implements Screen {

    private static final String START_TEXT = "[ START BATTLE ]";
    private static final String LEAVE_TEXT = "[ LEAVE ]";
    private static final String HEADER_TEXT = "HOST LOBBY";
    private static final String HOST_SLOT_TEXT = "[HOST]";
    private static final String PLAYER_SLOT_TEXT = "[CHAMPION]";
    private static final String OPEN_SLOT_TEXT = "+ OPEN PLAYER SLOT +";
    private static final int MAX_PLAYERS = GameServer.MAX_PLAYERS;
    private static final float MENU_WIDTH = 640f;
    private static final float MENU_HEIGHT = 360f;

    private final HexadenceGame game;
    private final BitmapFont font;
    private final Texture image;
    private final Stage stage;
    private final LobbyConnection connection;

    // Updated every frame from the connection.
    private final Label roomLabel;
    private final Label playerLabel;
    private final Label[] slotLabels = new Label[MAX_PLAYERS];
    private final Label.LabelStyle whiteText;
    private final Label.LabelStyle goldText;

    // Set by button listeners and handled after stage.act(), so the
    // stage is never disposed while it is still processing input.
    private boolean startRequested;
    private boolean leaveRequested;

    public HostLobbyScreen(HexadenceGame game) {
        this.game = game;
        image = new Texture("host_lobbytest2.png");
        font = new BitmapFont();
        font.getData().setScale(1.5f);

        stage = new Stage(new FitViewport(MENU_WIDTH, MENU_HEIGHT));

        // Styles built in code, so no skin file is needed.
        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.font = font;
        buttonStyle.fontColor = Color.WHITE;
        buttonStyle.overFontColor = Color.GOLD;

        whiteText = new Label.LabelStyle(font, Color.WHITE);
        goldText = new Label.LabelStyle(font, Color.GOLD);
        Label.LabelStyle cyanText = new Label.LabelStyle(font, Color.CYAN);

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(image));

        roomLabel = new Label("", cyanText);
        playerLabel = new Label("", whiteText);

        root.add(new Label(HEADER_TEXT, whiteText)).padBottom(8).row();
        root.add(roomLabel).padBottom(8).row();
        root.add(playerLabel).padBottom(4).row();

        // Player slots: the host first, then everyone who joined, then open slots.
        for (int i = 0; i < MAX_PLAYERS; i++) {
            slotLabels[i] = new Label("", whiteText);
            root.add(slotLabels[i]).row();
        }

        TextButton startButton = new TextButton(START_TEXT, buttonStyle);
        TextButton leaveButton = new TextButton(LEAVE_TEXT, buttonStyle);

        startButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                startRequested = true;
            }
        });

        leaveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leaveRequested = true;
            }
        });

        root.add(startButton).padTop(10).row();
        root.add(leaveButton).padTop(4);

        stage.addActor(root);

        // Ask the server for a room right away; the code appears when it answers.
        connection = LobbyConnection.createRoom(
            ServerAddress.fromSettings(),
            ArenaMaps.MULTIPLAYER
        );
        updateLabels();
    }

    @Override
    public void show() {
        // Route mouse and keyboard events to this screen's buttons.
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        // Keyboard shortcuts kept from the original screen.
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leaveRequested = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            startRequested = true;
        }

        connection.poll();
        updateLabels();

        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        stage.act(delta);
        stage.draw();

        if (startRequested) {
            // The server answers with GameStarted, which switches every
            // player in the room, including this one, below.
            connection.requestStart();
            startRequested = false;
        }

        if (connection.status() == LobbyConnection.Status.STARTED) {
            game.setScreen(new ArenaScreen(connection.handOff()));
            // Switching screens hides this screen but does not dispose it.
            dispose();
        } else if (leaveRequested) {
            game.setScreen(new MainMenuScreen(game));
            dispose();
        }
    }

    private void updateLabels() {
        String code = connection.roomCode();
        int players = connection.playerCount();

        roomLabel.setText(code == null ? "ROOM CODE: ...." : "ROOM CODE: " + code);

        playerLabel.setText(switch (connection.status()) {
            case CONNECTING -> "CONNECTING TO SERVER...";
            case UNREACHABLE -> "COULD NOT REACH THE SERVER";
            case REJECTED -> LobbyConnection.describe(connection.rejection());
            case DISCONNECTED, ROOM_CLOSED -> "LOST CONNECTION TO THE SERVER";
            case IN_LOBBY, STARTED ->
                "CHAMPIONS ASSEMBLED (" + players + "/" + connection.maxPlayers() + ")";
        });

        for (int i = 0; i < MAX_PLAYERS; i++) {
            Label slot = slotLabels[i];

            boolean filled = i < players;
            Label.LabelStyle style = filled ? goldText : whiteText;

            slot.setText(!filled ? OPEN_SLOT_TEXT : i == 0 ? HOST_SLOT_TEXT : PLAYER_SLOT_TEXT);

            // setStyle re-lays out the table, so only call it on a change.
            if (slot.getStyle() != style) {
                slot.setStyle(style);
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        // Stop sending input to this stage once another screen is active.
        Gdx.input.setInputProcessor(null);
    }

    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        // Leaves the room, unless the match started and the arena took over.
        connection.close();
        stage.dispose();
        font.dispose();
        image.dispose();
    }
}
