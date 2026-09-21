package io.github.coda45.hexadence;

import com.badlogic.gdx.Game;

import io.github.coda45.hexadence.screen.MainMenuScreen;

// Selects the initial screen and releases the active screen at shutdown.
public class HexadenceGame extends Game {
    @Override
    public void create() {
        setScreen(new MainMenuScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        // Game.dispose() hides the active screen; it does not dispose that screen.
        if (getScreen() != null) {
            getScreen().dispose();
        }
    }
}
