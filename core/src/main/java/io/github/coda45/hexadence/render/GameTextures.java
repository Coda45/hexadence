package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Texture;

import io.github.coda45.hexadence.spell.SpellType;

public class GameTextures {

    private final Texture player;
    private final Texture emberBolt;
    private final Texture waterBolt;
    private final Texture lightningBolt;

    public GameTextures() {
        player = loadPixelArt("sprites/wizard.png");
        emberBolt = loadPixelArt("spells/ember_bolt.png");
        waterBolt = loadPixelArt("spells/water_bolt.png");
        lightningBolt = loadPixelArt("spells/lightning_bolt.png");
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

    public void dispose() {
        player.dispose();
        emberBolt.dispose();
        waterBolt.dispose();
        lightningBolt.dispose();
    }
}