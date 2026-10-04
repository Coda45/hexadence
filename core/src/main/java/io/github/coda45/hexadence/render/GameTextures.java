package io.github.coda45.hexadence.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import io.github.coda45.hexadence.render.animation.FacingDirection;
import io.github.coda45.hexadence.spell.SpellType;

public class GameTextures {

    private final Texture waterBolt;
    private final Texture zoneCircle;

    private static final int PLAYER_FRAME_WIDTH = 24;
    private static final int PLAYER_FRAME_HEIGHT = 36;
    private static final float MOVE_FRAME_DURATION = 0.1f;
    private static final float IDLE_FRAME_DURATION = 0.15f;
    private static final int PLAYER_MOVE_FRAME_COUNT = 6;

    private static final int FIREBALL_FRAME_SIZE = 24;
    private static final int FIREBALL_FRAME_COUNT = 8;
    private static final float FIREBALL_FRAME_DURATION = 0.12f;

    private static final int LIGHTNING_FRAME_WIDTH = 192;
    private static final int LIGHTNING_FRAME_HEIGHT = 24;
    private static final int LIGHTNING_FRAME_COUNT = 4;
    private static final float LIGHTNING_FRAME_DURATION = 0.05f;
    private static final int LIGHTNING_VARIANT_COUNT = 8;
    private static final int CHARGED_PUDDLE_FRAME_SIZE = 48;
    private static final int CHARGED_PUDDLE_FRAME_COUNT = 8;
    private static final float CHARGED_PUDDLE_FRAME_DURATION = 0.08f;

    private final Texture lightningSheet;
    private final List<Animation<TextureRegion>> lightningAnimations;
    private final Texture chargedPuddleSheet;
    private final List<Animation<TextureRegion>> chargedPuddleAnimations;
    private final Texture fireballSheet;
    private final Animation<TextureRegion> fireballAnimation;
    private final TextureRegion waterBoltRegion;
    private final Texture playerIdleSheet;

    // Directional sheet rows follow FacingDirection order, columns are frames.
    private final EnumMap<FacingDirection, Animation<TextureRegion>> playerIdleAnimations =
        new EnumMap<>(FacingDirection.class);

    private final Texture playerMoveSheet;

    private final EnumMap<FacingDirection, Animation<TextureRegion>> playerMoveAnimations =
        new EnumMap<>(FacingDirection.class);

    public GameTextures() {
        playerIdleSheet = loadPixelArt("sprites/idle.png");
        playerMoveSheet = loadPixelArt("sprites/move.png");
        loadPlayerMoveAnimations(
            playerMoveSheet,
            "sprites/move.png"
        );
        loadPlayerIdleAnimations(
            playerIdleSheet,
            "sprites/idle.png"
        );
        fireballSheet = loadPixelArt("spells/fireball.png");
        fireballAnimation = createFireballAnimation(fireballSheet);
        lightningSheet = loadPixelArt("spells/lightning.png");
        lightningAnimations = createLightningAnimations(lightningSheet);
        chargedPuddleSheet = loadPixelArt("spells/chargedpuddle.png");
        chargedPuddleAnimations = createChargedPuddleAnimations(chargedPuddleSheet);
        waterBolt = loadPixelArt("spells/water_bolt.png");
        waterBoltRegion = new TextureRegion(waterBolt);
        zoneCircle = createZoneCircle();
    }

    private void loadPlayerMoveAnimations(
        Texture sheet,
        String path
    ) {
        FacingDirection[] directions = FacingDirection.values();

        boolean invalidDimensions =
            sheet.getWidth()
                != PLAYER_FRAME_WIDTH
                    * PLAYER_MOVE_FRAME_COUNT
            || sheet.getHeight()
                != PLAYER_FRAME_HEIGHT
                    * directions.length;

        if (invalidDimensions) {
            sheet.dispose();

            throw new IllegalStateException(
                "Expected eight rows of six 24x36 "
                    + "movement frames in: "
                    + path
            );
        }

        TextureRegion[][] split =
            TextureRegion.split(
                sheet,
                PLAYER_FRAME_WIDTH,
                PLAYER_FRAME_HEIGHT
            );

        for (
            int row = 0;
            row < directions.length;
            row++
        ) {
            Animation<TextureRegion> animation =
                new Animation<>(
                    MOVE_FRAME_DURATION,
                    split[row]
                );

            animation.setPlayMode(
                Animation.PlayMode.LOOP
            );

            playerMoveAnimations.put(
                directions[row],
                animation
            );
        }
    }

    private void loadPlayerIdleAnimations(
        Texture sheet,
        String path
    ) {
        FacingDirection[] directions =  FacingDirection.values();

        boolean invalidDimensions =
            sheet.getWidth() < PLAYER_FRAME_WIDTH
            || sheet.getWidth() % PLAYER_FRAME_WIDTH != 0
            || sheet.getHeight()
                != PLAYER_FRAME_HEIGHT * directions.length;

        if (invalidDimensions) {
            sheet.dispose();

            throw new IllegalStateException(
                "Expected eight rows of 24x36 idle frames in: "
                    + path
            );
        }

        TextureRegion[][] split =
            TextureRegion.split(
                sheet,
                PLAYER_FRAME_WIDTH,
                PLAYER_FRAME_HEIGHT
            );

        for (
            int row = 0;
            row < directions.length;
            row++
        ) {
            Animation<TextureRegion> animation =
                new Animation<>(
                    IDLE_FRAME_DURATION,
                    split[row]
                );

            animation.setPlayMode(
                Animation.PlayMode.LOOP
            );

            playerIdleAnimations.put(
                directions[row],
                animation
            );
        }
    }

    private List<Animation<TextureRegion>>
        createLightningAnimations(Texture sheet) {

        TextureRegion[][] split =
            TextureRegion.split(
                sheet,
                LIGHTNING_FRAME_WIDTH,
                LIGHTNING_FRAME_HEIGHT
            );

        if (
            split.length != LIGHTNING_VARIANT_COUNT
        ) {
            sheet.dispose();

            throw new IllegalStateException(
                "Expected eight rows of Lightning animations in: "
                    + "spells/lightning.png"
            );
        }

        List<Animation<TextureRegion>> animations = new ArrayList<>(split.length);

        for (TextureRegion[] row : split) {
            if (
                row.length
                    != LIGHTNING_FRAME_COUNT
            ) {
                sheet.dispose();

                throw new IllegalStateException(
                    "Expected four 192x24 frames per "
                        + "Lightning variation"
                );
            }

            Animation<TextureRegion> animation =
                new Animation<>(
                    LIGHTNING_FRAME_DURATION,
                    row
                );

            animation.setPlayMode(
                Animation.PlayMode.NORMAL
            );

            animations.add(animation);
        }

        return List.copyOf(animations);
    }

    private Animation<TextureRegion> createFireballAnimation(
        Texture sheet
    ) {
        TextureRegion[][] split =
            TextureRegion.split(
                sheet,
                FIREBALL_FRAME_SIZE,
                FIREBALL_FRAME_SIZE
            );

        if (
            split.length != 1
            || split[0].length != FIREBALL_FRAME_COUNT
        ) {
            sheet.dispose();

            throw new IllegalStateException(
                "Expected eight horizontal 24x24 frames in: "
                    + "spells/fireball.png"
            );
        }

        Animation<TextureRegion> animation =
            new Animation<>(
                FIREBALL_FRAME_DURATION,
                split[0]
            );

        animation.setPlayMode(Animation.PlayMode.LOOP);

        return animation;
    }

    public TextureRegion getLightningFrame(
        int variantIndex,
        float stateTime
    ) {
        return lightningAnimations
            .get(variantIndex)
            .getKeyFrame(stateTime, false);
    }

    public int getLightningVariantCount() {
        return lightningAnimations.size();
    }

    public float getLightningAnimationDuration() {
        return lightningAnimations
            .get(0)
            .getAnimationDuration();
    }

    private List<Animation<TextureRegion>>
        createChargedPuddleAnimations(Texture sheet) {

        boolean invalidDimensions =
            sheet.getWidth()
                != CHARGED_PUDDLE_FRAME_SIZE
                    * CHARGED_PUDDLE_FRAME_COUNT
            || sheet.getHeight()
                < CHARGED_PUDDLE_FRAME_SIZE
            || sheet.getHeight()
                % CHARGED_PUDDLE_FRAME_SIZE != 0;

        if (invalidDimensions) {
            sheet.dispose();

            throw new IllegalStateException(
                "Expected rows of eight 48x48 frames in: "
                    + "spells/chargedpuddle.png"
            );
        }

        TextureRegion[][] split =
            TextureRegion.split(
                sheet,
                CHARGED_PUDDLE_FRAME_SIZE,
                CHARGED_PUDDLE_FRAME_SIZE
            );

        // Each row becomes one variation selected when an overlay starts.
        List<Animation<TextureRegion>> animations = new ArrayList<>(split.length);

        for (TextureRegion[] row : split) {
            Animation<TextureRegion> animation =
                new Animation<>(
                    CHARGED_PUDDLE_FRAME_DURATION,
                    row
                );

            animation.setPlayMode(
                Animation.PlayMode.NORMAL
            );

            animations.add(animation);
        }

        return List.copyOf(animations);
    }

    public TextureRegion getChargedPuddleFrame(
        int variantIndex,
        float stateTime
    ) {
        return chargedPuddleAnimations
            .get(variantIndex)
            .getKeyFrame(stateTime, false);
    }

    public int getChargedPuddleVariantCount() {
        return chargedPuddleAnimations.size();
    }

    public float getChargedPuddleAnimationDuration() {
        return chargedPuddleAnimations
            .get(0)
            .getAnimationDuration();
    }

    private Texture loadPixelArt(String path) {
        Texture texture = new Texture(path);

        texture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        return texture;
    }

    public TextureRegion getPlayerFrame(
        FacingDirection direction,
        boolean moving,
        float stateTime
    ) {
        EnumMap<
            FacingDirection,
            Animation<TextureRegion>
        > animations =
            moving
                ? playerMoveAnimations
                : playerIdleAnimations;

        return animations
            .get(direction)
            .getKeyFrame(stateTime);
    }

    public TextureRegion getProjectileFrame(
        SpellType spellType,
        float stateTime
    ) {
        return switch (spellType) {
            case EMBER ->
                fireballAnimation.getKeyFrame(stateTime);

            case WATER ->
                waterBoltRegion;

            case LIGHTNING ->
                throw new IllegalArgumentException(
                    "Lightning does not use a projectile frame"
                );
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
        playerIdleSheet.dispose();
        waterBolt.dispose();
        lightningSheet.dispose();
        chargedPuddleSheet.dispose();
        zoneCircle.dispose();
        fireballSheet.dispose();
        playerMoveSheet.dispose();
    }
}
