package io.github.coda45.hexadence.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import io.github.coda45.hexadence.entity.PlayerColor;
import io.github.coda45.hexadence.render.animation.FacingDirection;
import io.github.coda45.hexadence.power.Power;

public class GameTextures {

    private final Texture waterBolt;
    private final Texture voidBolt;
    private final Texture zoneCircle;
    private final Texture targetDummy;

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

    // Void has no art yet, so its projectile is a small generated circle,
    // the same trick createZoneCircle() uses.
    private static final int VOID_BOLT_SIZE = 8;

    private final Texture lightningSheet;
    private final List<Animation<TextureRegion>> lightningAnimations;
    private final Texture chargedPuddleSheet;
    private final List<Animation<TextureRegion>> chargedPuddleAnimations;
    private final Texture fireballSheet;
    private final Animation<TextureRegion> fireballAnimation;
    private final TextureRegion waterBoltRegion;
    private final TextureRegion voidBoltRegion;


    // Directional sheet rows follow FacingDirection order, columns are frames.
    private final EnumMap<
        PlayerColor,
        PlayerAnimationSet
    > playerAnimationSets =
        new EnumMap<>(PlayerColor.class);





    public GameTextures() {
        for (PlayerColor color : PlayerColor.values()) {
        String folder     = "sprites/player/" + color.assetFolder();
        String idlePath   = folder + "/idle.png";
        String movePath   = folder + "/move.png";
        Texture idleSheet = loadPixelArt(idlePath);
        Texture moveSheet = loadPixelArt(movePath);

        playerAnimationSets.put(
            color,
            new PlayerAnimationSet(
                idleSheet,
                moveSheet,
                createPlayerIdleAnimations(idleSheet, idlePath),
                createPlayerMoveAnimations(moveSheet, movePath)
            )
        );
    }
        fireballSheet = loadPixelArt("spells/fireball.png");
        fireballAnimation = createFireballAnimation(fireballSheet);
        lightningSheet = loadPixelArt("spells/Lightning.png");
        lightningAnimations = createLightningAnimations(lightningSheet);
        chargedPuddleSheet = loadPixelArt("spells/chargedPuddle.png");
        chargedPuddleAnimations = createChargedPuddleAnimations(chargedPuddleSheet);
        waterBolt = loadPixelArt("spells/water_bolt.png");
        waterBoltRegion = new TextureRegion(waterBolt);
        voidBolt = createVoidBolt();
        voidBoltRegion = new TextureRegion(voidBolt);
        zoneCircle = createZoneCircle();
        targetDummy = loadPixelArt("sprites/target_dummy.png");
    }

    private EnumMap<FacingDirection, Animation<TextureRegion>> createPlayerMoveAnimations(
        Texture sheet,
        String path
    ) {
        FacingDirection[] directions = FacingDirection.values();

        EnumMap<FacingDirection, Animation<TextureRegion>> 
            animations = new EnumMap<>(FacingDirection.class);

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

        for (int row = 0; row < directions.length; row++) {
            Animation<TextureRegion> animation =
                new Animation<>(
                    MOVE_FRAME_DURATION,
                    split[row]
                );

            animation.setPlayMode(Animation.PlayMode.LOOP);
            animations.put(directions[row], animation);
        }

        return animations;
    }

    private EnumMap<FacingDirection, Animation<TextureRegion>> createPlayerIdleAnimations(
        Texture sheet,
        String path
    ) {
        FacingDirection[] directions =
            FacingDirection.values();

        EnumMap<FacingDirection, Animation<TextureRegion>> 
            animations = new EnumMap<>(FacingDirection.class);

        boolean invalidDimensions =
            sheet.getWidth() < PLAYER_FRAME_WIDTH
            || sheet.getWidth() % PLAYER_FRAME_WIDTH != 0
            || sheet.getHeight()
                != PLAYER_FRAME_HEIGHT
                    * directions.length;

        if (invalidDimensions) {
            sheet.dispose();

            throw new IllegalStateException(
                "Expected eight rows of 24x36 "
                    + "idle frames in: "
                    + path
            );
        }

        TextureRegion[][] split =
            TextureRegion.split(
                sheet,
                PLAYER_FRAME_WIDTH,
                PLAYER_FRAME_HEIGHT
            );

        for (int row = 0; row < directions.length; row++) {
            Animation<TextureRegion> animation =
                new Animation<>(
                    IDLE_FRAME_DURATION,
                    split[row]
                );

            animation.setPlayMode(Animation.PlayMode.LOOP);
            animations.put(directions[row], animation);
        }

        return animations;
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
                    + "spells/Lightning.png"
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
                    + "spells/chargedPuddle.png"
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
        PlayerColor color,
        FacingDirection direction,
        boolean moving,
        float stateTime
    ) {
        PlayerAnimationSet animations = playerAnimationSets.get(color);

        if (animations == null) {
            throw new IllegalArgumentException(
                "No animations loaded for color: " + color
            );
        }

        return animations.getFrame(
            direction,
            moving,
            stateTime
        );
    }

    // Projectile art is chosen by element, so a new power gets matching art
    // without code changes here. Give a power its own art by checking for it
    // above the element switch.
    public TextureRegion getProjectileFrame(
        Power power,
        float stateTime
    ) {
        return switch (power.element()) {
            case WATER -> waterBoltRegion;
            case VOID -> voidBoltRegion;

            // Elements without projectile art yet borrow the fireball.
            default -> fireballAnimation.getKeyFrame(stateTime);
        };
    }

    // A small solid dark-purple circle, standing in for Void art until some exists.
    private Texture createVoidBolt() {
        Pixmap pixmap =
            new Pixmap(VOID_BOLT_SIZE, VOID_BOLT_SIZE, Pixmap.Format.RGBA8888);

        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        pixmap.setColor(0.22f, 0.05f, 0.33f, 1f);
        pixmap.fillCircle(
            VOID_BOLT_SIZE / 2,
            VOID_BOLT_SIZE / 2,
            VOID_BOLT_SIZE / 2
        );

        Texture texture = new Texture(pixmap);
        texture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        pixmap.dispose();
        return texture;
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

    public Texture getTargetDummy() {
        return targetDummy;
    }

    private record PlayerAnimationSet(
        Texture idleSheet,
        Texture moveSheet,
        EnumMap<
            FacingDirection,
            Animation<TextureRegion>
        > idleAnimations,
        EnumMap<
            FacingDirection,
            Animation<TextureRegion>
        > moveAnimations
    ) {

        private TextureRegion getFrame(
            FacingDirection direction,
            boolean moving,
            float stateTime
        ) {
            EnumMap<
                FacingDirection,
                Animation<TextureRegion>
            > animations =
                moving
                    ? moveAnimations
                    : idleAnimations;

            return animations
                .get(direction)
                .getKeyFrame(stateTime);
        }

        private void dispose() {
            idleSheet.dispose();
            moveSheet.dispose();
        }
    }

    public void dispose() {
        for (PlayerAnimationSet animations : playerAnimationSets.values()) {
            animations.dispose();
        }
        waterBolt.dispose();
        voidBolt.dispose();
        lightningSheet.dispose();
        chargedPuddleSheet.dispose();
        zoneCircle.dispose();
        targetDummy.dispose();
        fireballSheet.dispose();
    }
}
