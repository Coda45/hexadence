package io.github.coda45.hexadence.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class SpellSoundAssetTest {

    private static final List<String> SOUND_FILES = List.of(
        "DSGNImpt_EXPLOSION-Mecha Engine Impact_HY_PC-001.wav",
        "DSGNMisc_MOVEMENT-Watery Laser_HY_PC-001.wav",
        "DSGNMisc_MOVEMENT-Watery Laser_HY_PC-002.wav",
        "DSGNMisc_MOVEMENT-Watery Laser_HY_PC-003.wav",
        "DSGNMisc_MOVEMENT-Watery Laser_HY_PC-004.wav",
        "DSGNMisc_MOVEMENT-Watery Laser_HY_PC-005.wav",
        "DSGNMisc_MOVEMENT-Watery Laser_HY_PC-006.wav",
        "DSGNMisc_PROJECTILE-Hollow Tornado_HY_PC-001.wav",
        "DSGNMisc_PROJECTILE-Hollow Tornado_HY_PC-002.wav",
        "DSGNMisc_PROJECTILE-Hollow Tornado_HY_PC-003.wav",
        "DSGNMisc_PROJECTILE-Hollow Tornado_HY_PC-004.wav",
        "DSGNMisc_PROJECTILE-Hollow Tornado_HY_PC-005.wav",
        "DSGNMisc_PROJECTILE-Hollow Tornado_HY_PC-006.wav",
        "DSGNMisc_PROJECTILE-Water Bolt_HY_PC-001.wav",
        "DSGNMisc_SKILL IMPACT-Pebbles_HY_PC-001.wav",
        "DSGNMisc_SKILL IMPACT-Pebbles_HY_PC-002.wav",
        "DSGNMisc_SKILL IMPACT-Pebbles_HY_PC-003.wav",
        "DSGNMisc_SKILL IMPACT-Pebbles_HY_PC-004.wav",
        "DSGNMisc_SKILL IMPACT-Pebbles_HY_PC-005.wav",
        "DSGNMisc_SKILL IMPACT-Pebbles_HY_PC-006.wav",
        "fire/DSGNMisc_SKILL RELEASE-Flame Ball_HY_PC-004.wav",
        "fire/DSGNMisc_SKILL RELEASE-Flame Ball_HY_PC-005.wav",
        "fire/fire_spell_impact_5.wav",
        "MAGSpel_CAST-Appear Firewall_HY_PC-001.wav",
        "MAGSpel_CAST-Appear Firewall_HY_PC-002.wav",
        "MAGSpel_CAST-Appear Firewall_HY_PC-003.wav",
        "MAGSpel_CAST-Appear Firewall_HY_PC-004.wav",
        "MAGSpel_CAST-Appear Firewall_HY_PC-005.wav",
        "MAGSpel_CAST-Appear Firewall_HY_PC-006.wav",
        "storm/lightning_spell_impact_1.wav",
        "storm/WHSH_MOVEMENT-Wind Sweep Swish_HY_PC-001.wav",
        "storm/WHSH_MOVEMENT-Wind Sweep Swish_HY_PC-002.wav",
        "storm/WHSH_MOVEMENT-Wind Sweep Swish_HY_PC-003.wav",
        "storm/WHSH_MOVEMENT-Wind Sweep Swish_HY_PC-004.wav",
        "storm/WHSH_MOVEMENT-Wind Sweep Swish_HY_PC-005.wav",
        "storm/WHSH_MOVEMENT-Wind Sweep Swish_HY_PC-006.wav",
        "void/DSGNImpt_EXPLOSION-Bass Hit_HY_PC-002.wav",
        "void/DSGNMisc_MOVEMENT-Noise Decay_HY_PC-001.wav",
        "void/DSGNMisc_MOVEMENT-Noise Decay_HY_PC-002.wav",
        "void/DSGNMisc_MOVEMENT-Noise Decay_HY_PC-003.wav",
        "void/DSGNMisc_MOVEMENT-Noise Decay_HY_PC-004.wav",
        "void/DSGNMisc_MOVEMENT-Noise Decay_HY_PC-005.wav",
        "void/DSGNMisc_MOVEMENT-Noise Decay_HY_PC-006.wav"
    );

    @Test
    void spellSoundsUseLibgdxCompatiblePcmWavFormat() throws IOException {
        Path soundDirectory = findSoundDirectory();

        for (String name : SOUND_FILES) {
            byte[] wav = Files.readAllBytes(soundDirectory.resolve(name));
            WavFormat format = readFormat(wav);

            assertEquals(1, format.code(), name + " must use PCM encoding");
            assertTrue(
                format.bitsPerSample() == 8 || format.bitsPerSample() == 16,
                name + " must use 8-bit or 16-bit PCM for libGDX"
            );
            assertEquals(48_000, format.sampleRate(), name + " must use 48 kHz PCM");
        }
    }

    private static Path findSoundDirectory() {
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path fromRoot = workingDirectory.resolve("assets/sounds");

        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }

        Path fromCore = workingDirectory.resolve("../assets/sounds").normalize();
        assertTrue(Files.isDirectory(fromCore), "Could not find assets/sounds");
        return fromCore;
    }

    private static WavFormat readFormat(byte[] wav) {
        assertTrue(wav.length >= 12, "WAV header is truncated");
        assertEquals("RIFF", ascii(wav, 0));
        assertEquals("WAVE", ascii(wav, 8));

        int offset = 12;

        while (offset + 8 <= wav.length) {
            String chunkId = ascii(wav, offset);
            int chunkSize = littleEndianInt(wav, offset + 4);
            int contents = offset + 8;

            if ("fmt ".equals(chunkId)) {
                assertTrue(chunkSize >= 16, "WAV fmt chunk is truncated");
                return new WavFormat(
                    littleEndianShort(wav, contents),
                    littleEndianInt(wav, contents + 4),
                    littleEndianShort(wav, contents + 14)
                );
            }

            offset = contents + chunkSize + (chunkSize & 1);
        }

        throw new AssertionError("WAV has no fmt chunk");
    }

    private static String ascii(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getInt();
    }

    private static int littleEndianShort(byte[] bytes, int offset) {
        return Short.toUnsignedInt(
            ByteBuffer.wrap(bytes, offset, 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getShort()
        );
    }

    private record WavFormat(int code, int sampleRate, int bitsPerSample) {
    }
}
