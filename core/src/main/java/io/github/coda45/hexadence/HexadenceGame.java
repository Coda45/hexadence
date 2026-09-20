package io.github.coda45.hexadence;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;

import io.github.coda45.hexadence.screen.ArenaScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class HexadenceGame extends Game {
    private SpriteBatch batch;
    private Texture image;

    @Override
    public void create() {
        setScreen(new ArenaScreen());
        batch = new SpriteBatch();
        image = new Texture("libgdx.png");
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        batch.begin();
        batch.draw(image, 140, 210);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        image.dispose();
    }
}
