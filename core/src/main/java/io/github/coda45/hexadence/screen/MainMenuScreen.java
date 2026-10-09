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
public class MainMenuScreen implements Screen {

    private final HexadenceGame game;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final Texture image;
    private static final String PLAY_TEXT = "[ PLAY ]";
    private static final String EXIT_TEXT = "[ EXIT ]";
    private static final float BUTTON_PADDING = 12f;
    private static final float BUTTON_SPACING = 50f;
    private final Rectangle playBounds = new Rectangle();
    private final Rectangle exitBounds = new Rectangle();
    private static final float MENU_WIDTH = 640f;
    private static final float MENU_HEIGHT = 360f;

    private final OrthographicCamera camera;
    private final FitViewport viewport;
    private final Vector2 pointer = new Vector2();

    public MainMenuScreen(HexadenceGame game) {
        this.game = game;
        batch = new SpriteBatch();
        image = new Texture("title_screen.png");
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
        float playY = MENU_HEIGHT * 0.45f;

        float exitY = playY - BUTTON_SPACING;

        updateButtonBounds(
            playBounds,
            PLAY_TEXT,
            playY
        );

        updateButtonBounds(
            exitBounds,
            EXIT_TEXT,
            exitY
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
                && playBounds.contains(
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
                && exitBounds.contains(
                    pointerX,
                    pointerY
                )
            );

        if (exitRequested) {
            Gdx.app.exit();
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

        drawButton(
            PLAY_TEXT,
            playY,
            playBounds.contains(
                pointerX,
                pointerY
            )
        );

        drawButton(
            EXIT_TEXT,
            exitY,
            exitBounds.contains(
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
        game.setScreen(
            new HostLobbyScreen(game)
        );

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
