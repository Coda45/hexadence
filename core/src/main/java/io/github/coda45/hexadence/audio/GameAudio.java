package io.github.coda45.hexadence.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

// Owns application-lifetime audio settings and the arena music stream. Spell
// effects remain ArenaScreen-owned so their short-lived resources cannot leak
// into menus or later matches.
public final class GameAudio implements Disposable {

    private static final String ARENA_MUSIC_PATH = "tracks/Arena_Loop.mp3";

    private final AudioSettings settings = new AudioSettings();
    private Music arenaMusic;
    private boolean musicUnavailable;

    public AudioSettings settings() {
        return settings;
    }

    public float sfxVolume() {
        return settings.sfxVolume();
    }

    public void setSfxVolume(float volume) {
        settings.setSfxVolume(volume);
    }

    public float musicVolume() {
        return settings.musicVolume();
    }

    public void setMusicVolume(float volume) {
        settings.setMusicVolume(volume);

        if (arenaMusic != null) {
            arenaMusic.setVolume(settings.musicVolume());
        }
    }

    public void saveSettings() {
        settings.save();
    }

    public void playArenaMusic() {
        Music music = arenaMusic();

        if (music == null) {
            return;
        }

        music.setLooping(true);
        music.setVolume(settings.musicVolume());

        if (!music.isPlaying()) {
            music.play();
        }
    }

    public void stopArenaMusic() {
        if (arenaMusic != null) {
            arenaMusic.stop();
        }
    }

    private Music arenaMusic() {
        if (arenaMusic != null || musicUnavailable) {
            return arenaMusic;
        }

        try {
            arenaMusic = Gdx.audio.newMusic(
                Gdx.files.internal(ARENA_MUSIC_PATH)
            );
        } catch (GdxRuntimeException exception) {
            musicUnavailable = true;
            System.err.println(
                "Arena music is unavailable at " + ARENA_MUSIC_PATH + ": "
                    + exception.getMessage()
            );
        }

        return arenaMusic;
    }

    @Override
    public void dispose() {
        settings.save();

        if (arenaMusic != null) {
            arenaMusic.dispose();
            arenaMusic = null;
        }
    }
}
