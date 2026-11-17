package io.github.coda45.hexadence.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.math.MathUtils;

// Client-owned, persistent mix settings. They never enter simulation state or
// the network protocol.
public final class AudioSettings {

    private static final String PREFERENCES_NAME = "hexadence-audio";
    private static final String SFX_VOLUME_KEY = "sfxVolume";
    private static final String MUSIC_VOLUME_KEY = "musicVolume";
    private static final float DEFAULT_VOLUME = 0.5f;

    private final Preferences preferences;
    private float sfxVolume;
    private float musicVolume;

    AudioSettings() {
        preferences = Gdx.app.getPreferences(PREFERENCES_NAME);
        sfxVolume = readVolume(SFX_VOLUME_KEY);
        musicVolume = readVolume(MUSIC_VOLUME_KEY);
    }

    public float sfxVolume() {
        return sfxVolume;
    }

    public float musicVolume() {
        return musicVolume;
    }

    public void setSfxVolume(float volume) {
        sfxVolume = storeVolume(SFX_VOLUME_KEY, volume);
    }

    public void setMusicVolume(float volume) {
        musicVolume = storeVolume(MUSIC_VOLUME_KEY, volume);
    }

    public void save() {
        preferences.flush();
    }

    private float readVolume(String key) {
        return MathUtils.clamp(
            preferences.getFloat(key, DEFAULT_VOLUME),
            0f,
            1f
        );
    }

    private float storeVolume(String key, float volume) {
        float clamped = MathUtils.clamp(volume, 0f, 1f);
        preferences.putFloat(key, clamped);
        return clamped;
    }
}
