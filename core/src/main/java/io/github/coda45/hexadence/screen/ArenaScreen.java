package io.github.coda45.hexadence.screen;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import io.github.coda45.hexadence.entity.Player;


public class ArenaScreen implements Screen {
    
    private final OrthographicCamera camera;
    private final SpriteBatch batch;
    private final Player player;

    public ArenaScreen() {
        camera = new OrthographicCamera();
        batch = new SpriteBatch();

        player = new Player(0f, 0f);
    }

    @Override 
    public void render(float delta) {
        // Update will go here later
        // Render "
    }

    @Override 
    public void resize(int width, int height) {

    }

    @Override 
    public void dispose() {
        batch.dispose();
    }

    @Override public void show() {}
    @Override public void hide() {}
    @Override public void pause() {}
    @Override public void resume() {}
}
