package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import io.github.coda45.hexadence.HexadenceGame;

// Displays explicit Play and Exit controls using lightweight text buttons.
public class HostLobbyScreen implements Screen {

    private final HexadenceGame game;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final Texture image;
    private static final String START_TEXT = "[ START BATTLE ]";
    private static final String LEAVE_TEXT = "[ LEAVE ]";
    private static final String TITLE_TEXT = "HEXADENCE";
    private static final String HEADER_TEXT = "HOST LOBBY";
    private static final String ROOM_TEXT = "ROOM CODE: 4837";
    private static final String PLAYER_TEXT = "CHAMPIONS ASSEMBLED (1/4)";
    private static final float BUTTON_PADDING = 12f;
    //private static final float BUTTON_SPACING = 50f;
    //private static final String PLAYER_COUNT = "PLAYERS: 1 / 4";
    private static final String MAP_TEXT = "ARENA: CRYSTAL NEXUS";
    private final Rectangle startBounds = new Rectangle();
    private final Rectangle leaveBounds = new Rectangle();
    private static final float MENU_WIDTH = 640f;
    private static final float MENU_HEIGHT = 360f;

    private final OrthographicCamera camera;
    private final FitViewport viewport;
    private final Vector2 pointer = new Vector2();

    public HostLobbyScreen(HexadenceGame game) {
        this.game = game;
        batch = new SpriteBatch();
        image = new Texture("host_lobbytest2.png");
        font = new BitmapFont();
        font.getData().setScale(1.5f);
        layout = new GlyphLayout();
        camera = new OrthographicCamera();

        viewport = new FitViewport(
            MENU_WIDTH,
            MENU_HEIGHT,
            camera
        );

        viewport.update(
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight(),
            true
        );
    }

    @Override
    public void render(float delta) {
        float startY = 45f;

        float leaveY = 15f;

        updateButtonBounds(
            startBounds,
            START_TEXT,
            startY
        );

        updateButtonBounds(
            leaveBounds,
            LEAVE_TEXT,
            leaveY
        );

        pointer.set(
            Gdx.input.getX(),
            Gdx.input.getY()
        );

        viewport.unproject(pointer);

        float pointerX = pointer.x;
        float pointerY = pointer.y;

        boolean clicked =
            Gdx.input.justTouched();

        boolean playRequested =
            Gdx.input.isKeyJustPressed(
                Input.Keys.ENTER
            )
            || (
                clicked
                && startBounds.contains(
                    pointerX,
                    pointerY
                )
            );

        if (playRequested) {
            startGame();
            return;
        }

        boolean exitRequested =
            Gdx.input.isKeyJustPressed(
                Input.Keys.ESCAPE
            )
            || (
                clicked
                && leaveBounds.contains(
                    pointerX,
                    pointerY
                )
            );

        if (exitRequested) {
            game.setScreen(new MainMenuScreen(game));
            dispose();
            return;
        }

        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);

        viewport.apply();
            batch.setProjectionMatrix(
            camera.combined
        );

        batch.begin();

        // Draw the title image at its native size.
        batch.draw(
            image,
            0f,
            0f,
            MENU_WIDTH,
            MENU_HEIGHT
        );
        
        layout.setText(font, TITLE_TEXT);

        font.draw(
            batch,
            TITLE_TEXT,
            (MENU_WIDTH - layout.width) / 2f,
            340f
        );
        layout.setText(font, HEADER_TEXT);

        font.draw(
            batch,
            HEADER_TEXT,
            (MENU_WIDTH - layout.width) / 2f,
            310f
        );
        
        layout.setText(font, ROOM_TEXT);
        
        font.setColor(Color.CYAN);
        
        font.draw(
            batch,
            ROOM_TEXT,
            (MENU_WIDTH - layout.width) / 2f,
            275f
        );

        font.setColor(Color.WHITE);
        
        layout.setText(font, PLAYER_TEXT);

        font.draw(
            batch,
            PLAYER_TEXT,
            (MENU_WIDTH - layout.width) / 2f,
            230f
        );


        font.setColor(Color.GOLD);
        
        font.draw(
            batch,
            "[HOST]",
            220f,
            180f
        );

        font.setColor(Color.WHITE);

        font.draw(
            batch,
            "+ OPEN PLAYER SLOT +",
            220f,
            145f
        );

        font.draw(
            batch,
            "+ OPEN PLAYER SLOT +",
            220f,
            110f
        );

        font.draw(
            batch,
            "+ OPEN PLAYER SLOT +",
            220f,
            75f
        );

        layout.setText(font, MAP_TEXT);

        font.draw(
            batch,
            MAP_TEXT,
            (MENU_WIDTH - layout.width) / 2f,
            95f
        );

        drawButton(
            START_TEXT,
            startY,
            startBounds.contains(
                pointerX,
                pointerY
            )
        );

        drawButton(
            LEAVE_TEXT,
            leaveY,
            leaveBounds.contains(
                pointerX,
                pointerY
            )
        );

        batch.end();
    }

    private void updateButtonBounds(
        Rectangle bounds,
        String text,
        float baselineY
    ) {
        layout.setText(font, text);

        float textX = (MENU_WIDTH - layout.width) / 2f;

        bounds.set(
            textX - BUTTON_PADDING,
            baselineY
                - layout.height
                - BUTTON_PADDING,
            layout.width
                + BUTTON_PADDING * 2f,
            layout.height
                + BUTTON_PADDING * 2f
        );
    }

    private void drawButton(
        String text,
        float baselineY,
        boolean hovered
    ) {
        font.setColor(
            hovered
                ? Color.GOLD
                : Color.WHITE
        );

        layout.setText(font, text);

        float textX =
            (MENU_WIDTH - layout.width) / 2f;

        font.draw(
            batch,
            layout,
            textX,
            baselineY
        );

        font.setColor(Color.WHITE);
    }

    private void startGame() {
        game.setScreen(new ArenaScreen());

        // Switching screens hides this menu but does not dispose it.
        dispose();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height,true);
    }
    @Override public void show() {}
    @Override public void hide() {}
    @Override public void pause() {}
    @Override public void resume() {}

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        image.dispose();
    }
}

