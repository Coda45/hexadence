package io.github.coda45.hexadence.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Owns client audio resources and the presentation-only cue map exported by
// docs/hexadence-spell-sound-matches.json.
final class SpellSoundLibrary implements Disposable {

    record LoopCue(List<SoundClip> variants, float overlapSeconds) {
    }

    record PlayingSound(Sound sound, long soundId, float durationSeconds) {
    }

    record SoundClip(Sound sound, float durationSeconds) {
    }

    private static final float MIN_CLIP_SECONDS = 0.05f;

    private final Map<String, SoundClip> loadedClips = new HashMap<>();
    private final Map<Power, List<SoundClip>> castSounds =
        new IdentityHashMap<>();
    private final Map<Power, List<SoundClip>> impactSounds =
        new IdentityHashMap<>();
    private final Map<ZoneKind, LoopCue> fieldSounds =
        new IdentityHashMap<>();
    private boolean enabled = true;

    SpellSoundLibrary() {
        registerCast(
            Powers.FIREBALL,
            "fire/DSGNMisc_SKILL RELEASE-Flame Ball_HY_PC-004.wav"
        );
        registerCast(
            Powers.EMBER_CIRCLE,
            numbered("MAGSpel_CAST-Appear Firewall_HY_PC-", 6)
        );
        registerCast(
            Powers.WATER_SHOTGUN,
            "DSGNMisc_PROJECTILE-Water Bolt_HY_PC-001.wav"
        );
        registerCast(
            Powers.ROCK_SHARDS,
            numbered("DSGNMisc_SKILL IMPACT-Pebbles_HY_PC-", 6)
        );
        registerCast(
            Powers.STONE_WALL,
            "DSGNImpt_EXPLOSION-Mecha Engine Impact_HY_PC-001.wav"
        );
        registerCast(
            Powers.LIGHTNING_BOLT,
            "storm/lightning_spell_impact_1.wav"
        );
        registerCast(
            Powers.VORTEX,
            numbered("storm/WHSH_MOVEMENT-Wind Sweep Swish_HY_PC-", 6)
        );
        registerCast(
            Powers.VOID_ROT,
            numbered("void/DSGNMisc_MOVEMENT-Noise Decay_HY_PC-", 6)
        );

        registerImpact(
            Powers.FIREBALL,
            "fire/fire_spell_impact_5.wav"
        );
        registerImpact(
            Powers.NULL_ZONE,
            "void/DSGNImpt_EXPLOSION-Bass Hit_HY_PC-002.wav"
        );

        registerField(
            ZoneKinds.VORTEX,
            1.6f,
            numbered("DSGNMisc_PROJECTILE-Hollow Tornado_HY_PC-", 6)
        );
        registerField(
            ZoneKinds.INFERNO,
            1.45f,
            "fire/DSGNMisc_SKILL RELEASE-Flame Ball_HY_PC-005.wav"
        );
        // The JSON also maps Charged Puddle, but the current simulation has
        // no Charged Puddle ZoneKind to observe. Keep that cue dormant rather
        // than attaching it to the unrelated Hailstorm interaction.
    }

    boolean hasCast(Power power) {
        List<SoundClip> clips = castSounds.get(power);
        return clips != null && !clips.isEmpty();
    }

    void playCast(Power power, float volume) {
        playOneShot(castSounds.get(power), volume);
    }

    void playImpact(Power power, float volume) {
        playOneShot(impactSounds.get(power), volume);
    }

    LoopCue fieldCue(ZoneKind kind) {
        return fieldSounds.get(kind);
    }

    PlayingSound playField(LoopCue cue, float volume) {
        SoundClip clip = randomClip(cue == null ? null : cue.variants());

        if (clip == null) {
            return null;
        }

        long soundId = enabled && volume > 0f
            ? clip.sound().play(MathUtils.clamp(volume, 0f, 1f))
            : -1L;
        return new PlayingSound(
            clip.sound(),
            soundId,
            clip.durationSeconds()
        );
    }

    void stop(PlayingSound playing) {
        if (playing != null && playing.soundId() >= 0L) {
            playing.sound().stop(playing.soundId());
        }
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void registerCast(Power power, String... paths) {
        castSounds.put(power, load(paths));
    }

    private void registerImpact(Power power, String... paths) {
        impactSounds.put(power, load(paths));
    }

    private void registerField(
        ZoneKind kind,
        float overlapSeconds,
        String... paths
    ) {
        List<SoundClip> clips = load(paths);

        if (!clips.isEmpty()) {
            fieldSounds.put(kind, new LoopCue(clips, overlapSeconds));
        }
    }

    private List<SoundClip> load(String... paths) {
        List<SoundClip> clips = new ArrayList<>(paths.length);

        for (String path : paths) {
            SoundClip clip = loadClip(path);

            if (clip != null) {
                clips.add(clip);
            }
        }

        return List.copyOf(clips);
    }

    private SoundClip loadClip(String relativePath) {
        SoundClip existing = loadedClips.get(relativePath);

        if (existing != null) {
            return existing;
        }

        String path = "sounds/" + relativePath;
        FileHandle file = Gdx.files.internal(path);

        try {
            SoundClip clip = new SoundClip(
                Gdx.audio.newSound(file),
                wavDurationSeconds(file)
            );
            loadedClips.put(relativePath, clip);
            return clip;
        } catch (GdxRuntimeException exception) {
            System.err.println(
                "Skipping unavailable spell sound " + path + ": "
                    + exception.getMessage()
            );
            return null;
        }
    }

    private void playOneShot(List<SoundClip> clips, float volume) {
        SoundClip clip = randomClip(clips);

        if (!enabled || volume <= 0f || clip == null) {
            return;
        }

        clip.sound().play(MathUtils.clamp(volume, 0f, 1f));
    }

    private static SoundClip randomClip(List<SoundClip> clips) {
        if (clips == null || clips.isEmpty()) {
            return null;
        }

        return clips.get(MathUtils.random(clips.size() - 1));
    }

    private static String[] numbered(String prefix, int count) {
        String[] paths = new String[count];

        for (int index = 0; index < count; index++) {
            paths[index] = prefix + String.format("%03d", index + 1) + ".wav";
        }

        return paths;
    }

    private static float wavDurationSeconds(FileHandle file) {
        byte[] bytes = file.readBytes();

        if (bytes.length < 12) {
            return MIN_CLIP_SECONDS;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int byteRate = 0;
        int dataBytes = 0;
        int offset = 12;

        while (offset + 8 <= bytes.length) {
            String chunkId = new String(
                bytes,
                offset,
                4,
                StandardCharsets.US_ASCII
            );
            int chunkSize = buffer.getInt(offset + 4);
            int chunkData = offset + 8;

            if (chunkSize < 0 || chunkData + (long) chunkSize > bytes.length) {
                break;
            }

            if ("fmt ".equals(chunkId) && chunkSize >= 12) {
                byteRate = buffer.getInt(chunkData + 8);
            } else if ("data".equals(chunkId)) {
                dataBytes = chunkSize;
            }

            if (byteRate > 0 && dataBytes > 0) {
                return Math.max(
                    MIN_CLIP_SECONDS,
                    dataBytes / (float) byteRate
                );
            }

            offset = chunkData + chunkSize + (chunkSize & 1);
        }

        return MIN_CLIP_SECONDS;
    }

    @Override
    public void dispose() {
        for (SoundClip clip : loadedClips.values()) {
            clip.sound().dispose();
        }

        loadedClips.clear();
        castSounds.clear();
        impactSounds.clear();
        fieldSounds.clear();
    }
}
