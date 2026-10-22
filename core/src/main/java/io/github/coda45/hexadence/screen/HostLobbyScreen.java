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

// Displays the prototype host lobby using a Scene2D table layout.
public class HostLobbyScreen implements Screen {

    private static final String START_TEXT = "[ START BATTLE ]";
    private static final String LEAVE_TEXT = "[ LEAVE ]";
    private static final String HEADER_TEXT = "HOST LOBBY";
    private static final String ROOM_TEXT = "ROOM CODE: 4837";
    private static final String PLAYER_TEXT = "CHAMPIONS ASSEMBLED (1/4)";
    private static final String HOST_SLOT_TEXT = "[HOST]";
    private static final String OPEN_SLOT_TEXT = "+ OPEN PLAYER SLOT +";
    private static final int MAX_PLAYERS = 4;
    private static final float MENU_WIDTH = 640f;
    private static final float MENU_HEIGHT = 360f;

    private final HexadenceGame game;
    private final BitmapFont font;
    private final Texture image;
    private final Stage stage;

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

        Label.LabelStyle whiteText = new Label.LabelStyle(font, Color.WHITE);
        Label.LabelStyle goldText = new Label.LabelStyle(font, Color.GOLD);
        Label.LabelStyle cyanText = new Label.LabelStyle(font, Color.CYAN);

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(new TextureRegionDrawable(image));

        root.add(new Label(HEADER_TEXT, whiteText)).padBottom(8).row();
        root.add(new Label(ROOM_TEXT, cyanText)).padBottom(8).row();
        root.add(new Label(PLAYER_TEXT, whiteText)).padBottom(4).row();

        // Player slots: the host first, then open slots.
        root.add(new Label(HOST_SLOT_TEXT, goldText)).row();
        for (int i = 1; i < MAX_PLAYERS; i++) {
            root.add(new Label(OPEN_SLOT_TEXT, whiteText)).row();
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

        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        stage.act(delta);
        stage.draw();

        if (startRequested) {
            game.setScreen(new ArenaScreen());
            // Switching screens hides this screen but does not dispose it.
            dispose();
        } else if (leaveRequested) {
            game.setScreen(new MainMenuScreen(game));
            dispose();
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
        stage.dispose();
        font.dispose();
        image.dispose();
    }
}