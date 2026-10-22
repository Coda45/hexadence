package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.HexadenceGame;
import io.github.coda45.hexadence.net.LobbyConnection;
import io.github.coda45.hexadence.net.RoomCodes;
import io.github.coda45.hexadence.net.ServerAddress;
import io.github.coda45.hexadence.world.ArenaMaps;

// The joining player's lobby, laid out like HostLobbyScreen. The player types
// the host's room code and presses JOIN; once in the room it waits for the
// host to start the match.
public class JoinLobbyScreen implements Screen {

    private static final String JOIN_TEXT = "[ JOIN GAME ]";
    private static final String LEAVE_TEXT = "[ LEAVE ]";
    private static final String HEADER_TEXT = "JOIN LOBBY";
    private static final String MAP_TEXT = "ARENA: CRYSTAL NEXUS";
    private static final String ENTER_CODE_TEXT = "TYPE THE HOST'S ROOM CODE";
    private static final float MENU_WIDTH = 640f;
    private static final float MENU_HEIGHT = 360f;

    private final HexadenceGame game;
    private final BitmapFont font;
    private final Texture image;
    private final Stage stage;

    private final Label codeLabel;
    private final Label statusLabel;
    private final TextButton joinButton;

    private final StringBuilder typedCode = new StringBuilder();
    // Null while the player is typing a code; set once JOIN is pressed.
    private LobbyConnection connection;
    // Why the last attempt ended, shown until the player tries again.
    private String lastProblem = "";

    // Set by listeners and handled after stage.act(), so the stage is
    // never disposed while it is still processing input.
    private boolean joinRequested;
    private boolean leaveRequested;

    public JoinLobbyScreen(HexadenceGame game) {
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

        Label.LabelStyle whiteText = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle goldText = new Label.LabelStyle(font, Color.GOLD);
        Label.LabelStyle cyanText = new Label.LabelStyle(font, Color.CYAN);

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(image));

        codeLabel = new Label("", cyanText);
        statusLabel = new Label("", goldText);
        joinButton = new TextButton(JOIN_TEXT, buttonStyle);
        TextButton leaveButton = new TextButton(LEAVE_TEXT, buttonStyle);

        root.add(new Label(HEADER_TEXT, whiteText)).padBottom(8).row();
        root.add(codeLabel).padBottom(8).row();
        root.add(statusLabel).padBottom(8).row();
        root.add(new Label(MAP_TEXT, whiteText)).padBottom(4).row();
        root.add(joinButton).padTop(10).row();
        root.add(leaveButton).padTop(4);

        stage.addActor(root);
        
        joinButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                joinRequested = true;
            }
        });

        leaveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                leaveRequested = true;
            }
        });

        // With no text field focused, the stage sends typed keys to its
        // root, which is where stage listeners live.
        stage.addListener(new InputListener() {
            @Override
            public boolean keyTyped(InputEvent event, char character) {
                if (
                    connection == null
                    && typedCode.length() < RoomCodes.LENGTH
                    && RoomCodes.isCodeCharacter(character)
                ) {
                    typedCode.append(Character.toUpperCase(character));
                    lastProblem = "";
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (
                    keycode == Input.Keys.BACKSPACE
                    && connection == null
                    && typedCode.length() > 0
                ) {
                    typedCode.setLength(typedCode.length() - 1);
                    return true;
                }
                return false;
            }
        });

        updateLabels();
    }

    @Override
    public void show() {
        // Route mouse and keyboard events to this screen.
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            leaveRequested = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            joinRequested = true;
        }

        if (connection != null) {
            connection.poll();
            endFailedAttempt();
        }

        updateLabels();

        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        stage.act(delta);
        stage.draw();

        if (joinRequested) {
            joinRequested = false;
            tryJoin();
        }

        if (
            connection != null
            && connection.status() == LobbyConnection.Status.STARTED
        ) {
            game.setScreen(new ArenaScreen(connection.handOff()));
            // Switching screens hides this screen but does not dispose it.
            dispose();
        } else if (leaveRequested) {
            game.setScreen(new MainMenuScreen(game));
            dispose();
        }
    }

    private void tryJoin() {
        if (connection != null || typedCode.length() < RoomCodes.LENGTH) {
            return;
        }

        lastProblem = "";
        connection = LobbyConnection.joinRoom(
            ServerAddress.fromSettings(),
            ArenaMaps.MULTIPLAYER,
            typedCode.toString()
        );
    }

    // If the attempt ended badly, remember why and let the player try again.
    private void endFailedAttempt() {
        String problem = switch (connection.status()) {
            case UNREACHABLE -> "COULD NOT REACH THE SERVER";
            case REJECTED -> LobbyConnection.describe(connection.rejection());
            case ROOM_CLOSED -> "THE HOST LEFT THE LOBBY";
            case DISCONNECTED -> "LOST CONNECTION TO THE SERVER";
            case CONNECTING, IN_LOBBY, STARTED -> null;
        };

        if (problem != null) {
            lastProblem = problem;
            connection.close();
            connection = null;
        }
    }

    private void updateLabels() {
        StringBuilder shown = new StringBuilder(typedCode);

        while (shown.length() < RoomCodes.LENGTH) {
            shown.append('_');
        }

        codeLabel.setText("ROOM CODE: " + shown);

        if (connection == null) {
            statusLabel.setText(lastProblem.isEmpty() ? ENTER_CODE_TEXT : lastProblem);
            joinButton.setVisible(true);
            return;
        }

        joinButton.setVisible(false);

        statusLabel.setText(switch (connection.status()) {
            case IN_LOBBY -> "CONNECTED - WAITING FOR HOST TO START ("
                + connection.playerCount() + "/" + connection.maxPlayers() + ")";
            case STARTED -> "STARTING...";
            default -> "CONNECTING...";
        });
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
        if (connection != null) {
            connection.close();
        }
        stage.dispose();
        font.dispose();
        image.dispose();
    }
}
