package io.github.coda45.hexadence;

import com.badlogic.gdx.Game;

import io.github.coda45.hexadence.audio.GameAudio;
import io.github.coda45.hexadence.screen.MainMenuScreen;

// Selects the initial screen and releases the active screen at shutdown.
public class HexadenceGame extends Game {

    private GameAudio audio;

    @Override
    public void create() {
        audio = new GameAudio();
        setScreen(new MainMenuScreen(this));
    }

    public GameAudio audio() {
        return audio;
    }

    @Override
    public void dispose() {
        super.dispose();
        // Game.dispose() hides the active screen; it does not dispose that screen.
        if (getScreen() != null) {
            getScreen().dispose();
        }

        if (audio != null) {
            audio.dispose();
        }
    }
}
