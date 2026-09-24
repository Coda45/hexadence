package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;

import io.github.coda45.hexadence.HexadenceGame;

// Starts the arena on Enter or a click/touch.
public class MainMenuScreen implements Screen {

    private final HexadenceGame game;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GlyphLayout layout;
    private final Texture image;

    public MainMenuScreen(HexadenceGame game) {
        this.game = game;
        batch = new SpriteBatch();
        image = new Texture("title_screen.png");
        font = new BitmapFont();
        font.getData().setScale(1.5f);
        layout = new GlyphLayout();
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.justTouched()) {
            game.setScreen(new ArenaScreen());
            // Switching screens hides the menu but does not release its drawing resources.
            dispose();
            return;
        }

        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        batch.begin();
        // Draw the title image at its native size in the menu's pixel coordinates.
        batch.draw(image, 0, 0);
        drawCentered("Press ENTER or click to start", 180);
        batch.end();
    }

    private void drawCentered(String text, float y) {
        layout.setText(font, text);
        font.draw(batch, layout, (Gdx.graphics.getWidth() - layout.width) / 2f, y);
    }

    @Override public void resize(int width, int height) {}
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
