package io.github.coda45.hexadence;

import com.badlogic.gdx.Game;

import io.github.coda45.hexadence.screen.MainMenuScreen;

public class HexadenceGame extends Game {
    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        if (getScreen() != null) {
            getScreen().dispose();
        }
    }
}
