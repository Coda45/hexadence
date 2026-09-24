package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import io.github.coda45.hexadence.spell.SpellType;

public class GameTextures {

    private final Texture player;
    private final Texture emberBolt;
    private final Texture waterBolt;
    private final Texture lightningBolt;
    private final Texture zoneCircle;

    public GameTextures() {
        player = loadPixelArt("sprites/wizard.png");
        emberBolt = loadPixelArt("spells/ember_bolt.png");
        waterBolt = loadPixelArt("spells/water_bolt.png");
        lightningBolt = loadPixelArt("spells/lightning_bolt.png");
        zoneCircle = createZoneCircle();
    }

    private Texture loadPixelArt(String path) {
        Texture texture = new Texture(path);

        texture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        return texture;
    }

    public Texture getPlayer() {
        return player;
    }

    public Texture getProjectile(SpellType spellType) {
        return switch (spellType) {
            case EMBER -> emberBolt;
            case WATER -> waterBolt;
            case LIGHTNING -> lightningBolt;
        };
    }

    private Texture createZoneCircle() {
        int size = 33;

        Pixmap pixmap =
            new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Transparent square canvas.
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // White circle that WorldRenderer can tint.
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fillCircle(
            size / 2,
            size / 2,
            size / 2
        );

        Texture texture = new Texture(pixmap);
        texture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        pixmap.dispose();
        return texture;
    }

    public Texture getZoneCircle() {
        return zoneCircle;
    }

    public void dispose() {
        player.dispose();
        emberBolt.dispose();
        waterBolt.dispose();
        lightningBolt.dispose();
        zoneCircle.dispose();
    }
}