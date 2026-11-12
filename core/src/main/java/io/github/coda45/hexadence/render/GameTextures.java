package io.github.coda45.hexadence.render;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import io.github.coda45.hexadence.entity.PlayerColor;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.render.animation.FacingDirection;

public class GameTextures {

    private final Texture waterBolt;
    private final Texture voidBolt;
    private final Texture zoneCircle;
    private final Texture zoneRing;
    private final Texture targetDummy;
    private final Texture rockShard;
    private final Texture beamOfLight;
    private final Texture crystalBeam;
    private final Texture stoneWallSheet;
    private final Texture crystalWallSheet;
    private final Texture wearLinesSheet;
    private final Texture vortexSheet;

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
    private static final int LIGHT_BEAM_FRAME_WIDTH = 96;
    private static final int LIGHT_BEAM_FRAME_HEIGHT = 24;
    private static final int LIGHT_BEAM_FRAME_COUNT = 8;
    private static final float LIGHT_BEAM_FRAME_DURATION = 0.1f;
    private static final int CRYSTAL_BEAM_FRAME_WIDTH = 144;
    private static final int CRYSTAL_BEAM_FRAME_HEIGHT = 24;
    private static final int CRYSTAL_BEAM_FRAME_COUNT = 8;
    private static final float CRYSTAL_BEAM_FRAME_DURATION = 0.1f;
    private static final int STONE_WALL_FRAME_SIZE = 24;
    private static final int STONE_WALL_FALLBACK_FRAME_COUNT = 6;
    private static final float STONE_WALL_FRAME_DURATION = 0.06f;
    private static final int WALL_WEAR_FRAME_COUNT = 3;

    private final Texture lightningSheet;
    private final List<Animation<TextureRegion>> lightningAnimations;
    private final Texture fireballSheet;
    private final Animation<TextureRegion> fireballAnimation;
    private final TextureRegion waterBoltRegion;
    private final TextureRegion voidBoltRegion;
    private final Animation<TextureRegion> rockShardAnimation;
    private final Animation<TextureRegion> beamOfLightAnimation;
    private final Animation<TextureRegion> crystalBeamAnimation;
    private final Animation<TextureRegion> stoneWallAnimation;
    private final Animation<TextureRegion> crystalWallAnimation;
    private final TextureRegion[] wallWearFrames;
    private final Animation<TextureRegion> vortexAnimation;


    // Directional sheet rows follow FacingDirection order, columns are frames.
    private final EnumMap<PlayerColor, PlayerAnimationSet>
        playerAnimationSets = new EnumMap<>(PlayerColor.class);

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
        waterBolt = loadPixelArt("spells/water_bolt.png");
        waterBoltRegion = new TextureRegion(waterBolt);

        rockShard = loadPixelArt("spells/rock_shard.png");
        rockShardAnimation =
            createProjectileAnimation(
                rockShard,
                "spells/rock_shard.png"
            );

        beamOfLight = loadOptionalBeamSheet(
            "spells/beam_of_light.png",
            LIGHT_BEAM_FRAME_WIDTH,
            LIGHT_BEAM_FRAME_HEIGHT,
            LIGHT_BEAM_FRAME_COUNT,
            new Color(1f, 0.88f, 0.35f, 0.8f)
        );
        beamOfLightAnimation = createBeamOfLightAnimation(beamOfLight);
        crystalBeam = loadRequiredSheet(
            "spells/crystal_beam.png",
            CRYSTAL_BEAM_FRAME_WIDTH,
            CRYSTAL_BEAM_FRAME_HEIGHT,
            CRYSTAL_BEAM_FRAME_COUNT
        );
        crystalBeamAnimation = createCrystalBeamAnimation(crystalBeam);

        stoneWallSheet = loadOptionalFixedFrameSheet(
            "spells/stone_wall.png",
            STONE_WALL_FRAME_SIZE,
            STONE_WALL_FRAME_SIZE,
            STONE_WALL_FALLBACK_FRAME_COUNT,
            new Color(0.48f, 0.35f, 0.24f, 1f)
        );
        stoneWallAnimation = createStoneWallAnimation(stoneWallSheet);
        crystalWallSheet = loadRequiredHorizontalSheet(
            "spells/crystal_wall.png",
            STONE_WALL_FRAME_SIZE,
            STONE_WALL_FRAME_SIZE
        );
        crystalWallAnimation = createStoneWallAnimation(crystalWallSheet);
        wearLinesSheet = loadRequiredSheet(
            "effects/wear_lines.png",
            STONE_WALL_FRAME_SIZE,
            STONE_WALL_FRAME_SIZE,
            WALL_WEAR_FRAME_COUNT
        );
        wallWearFrames = TextureRegion.split(
            wearLinesSheet,
            STONE_WALL_FRAME_SIZE,
            STONE_WALL_FRAME_SIZE
        )[0];
        vortexSheet = loadOptionalFixedFrameSheet(
            "spells/Vortex.png",
            48,
            48,
            8,
            new Color(0.45f, 0.8f, 1f, 0.8f)
        );
        vortexAnimation = createVortexAnimation(vortexSheet);

        voidBolt = loadPixelArt("spells/void_rot.png");
        voidBoltRegion = new TextureRegion(voidBolt);
        zoneCircle = createZoneCircle();
        zoneRing = createZoneRing();
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
    private Animation<TextureRegion> createProjectileAnimation(
        Texture sheet,
        String path
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
                    + path
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

    private Animation<TextureRegion> createVortexAnimation(Texture sheet) {
        TextureRegion[][] split = TextureRegion.split(sheet, 48, 48);

        Animation<TextureRegion> animation = new Animation<>(0.1f, split[0]);
        animation.setPlayMode(Animation.PlayMode.LOOP);
        return animation;
    }

    private Animation<TextureRegion> createBeamOfLightAnimation(Texture sheet) {
        TextureRegion[][] split = TextureRegion.split(
            sheet,
            LIGHT_BEAM_FRAME_WIDTH,
            LIGHT_BEAM_FRAME_HEIGHT
        );

        Animation<TextureRegion> animation = new Animation<>(
            LIGHT_BEAM_FRAME_DURATION,
            split[0]
        );
        animation.setPlayMode(Animation.PlayMode.LOOP);
        return animation;
    }

    private Animation<TextureRegion> createCrystalBeamAnimation(Texture sheet) {
        TextureRegion[][] split = TextureRegion.split(
            sheet,
            CRYSTAL_BEAM_FRAME_WIDTH,
            CRYSTAL_BEAM_FRAME_HEIGHT
        );

        Animation<TextureRegion> animation = new Animation<>(
            CRYSTAL_BEAM_FRAME_DURATION,
            split[0]
        );
        animation.setPlayMode(Animation.PlayMode.LOOP);
        return animation;
    }

    private Animation<TextureRegion> createStoneWallAnimation(Texture sheet) {
        TextureRegion[][] split = TextureRegion.split(
            sheet,
            STONE_WALL_FRAME_SIZE,
            STONE_WALL_FRAME_SIZE
        );

        Animation<TextureRegion> animation = new Animation<>(
            STONE_WALL_FRAME_DURATION,
            split[0]
        );
        animation.setPlayMode(Animation.PlayMode.NORMAL);
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

    public TextureRegion getBeamFrame(
        Power power,
        int variantIndex,
        float stateTime,
        boolean crystalSplit
    ) {
        if (crystalSplit) {
            return crystalBeamAnimation.getKeyFrame(stateTime, true);
        }

        if (power == Powers.BEAM_OF_LIGHT) {
            return beamOfLightAnimation.getKeyFrame(stateTime, true);
        }

        return getLightningFrame(variantIndex, stateTime);
    }

    public float getBeamAnimationDuration(Power power, boolean crystalSplit) {
        if (crystalSplit) {
            return crystalBeamAnimation.getAnimationDuration();
        }

        return power == Powers.BEAM_OF_LIGHT
            ? beamOfLightAnimation.getAnimationDuration()
            : getLightningAnimationDuration();
    }

    private Texture loadPixelArt(String path) {
        Texture texture = new Texture(path);

        texture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        return texture;
    }

    // New content art may still be in progress. A missing or malformed sheet
    // should look temporary in-game, not prevent the arena from opening.
    private Texture loadOptionalBeamSheet(
        String path,
        int frameWidth,
        int frameHeight,
        int frameCount,
        Color fallbackColor
    ) {
        Texture texture = tryLoadPixelArt(path);

        if (
            texture != null
            && texture.getHeight() == frameHeight
            && texture.getWidth() == frameWidth * frameCount
        ) {
            return texture;
        }

        discardInvalidTexture(path, texture);
        return createBeamPlaceholder(
            frameWidth,
            frameHeight,
            frameCount,
            fallbackColor
        );
    }

    private Texture loadRequiredSheet(
        String path,
        int frameWidth,
        int frameHeight,
        int frameCount
    ) {
        Texture texture = loadPixelArt(path);
        int expectedWidth = frameWidth * frameCount;

        if (
            texture.getWidth() == expectedWidth
                && texture.getHeight() == frameHeight
        ) {
            return texture;
        }

        int actualWidth = texture.getWidth();
        int actualHeight = texture.getHeight();
        texture.dispose();
        throw new IllegalStateException(
            "Expected " + frameCount + " horizontal "
                + frameWidth + "x" + frameHeight + " frames in " + path
                + ", but found " + actualWidth + "x" + actualHeight
        );
    }

    private Texture loadRequiredHorizontalSheet(
        String path,
        int frameWidth,
        int frameHeight
    ) {
        Texture texture = loadPixelArt(path);

        if (
            texture.getHeight() == frameHeight
                && texture.getWidth() >= frameWidth
                && texture.getWidth() % frameWidth == 0
        ) {
            return texture;
        }

        int actualWidth = texture.getWidth();
        int actualHeight = texture.getHeight();
        texture.dispose();
        throw new IllegalStateException(
            "Expected horizontal " + frameWidth + "x" + frameHeight
                + " frames in " + path + ", but found "
                + actualWidth + "x" + actualHeight
        );
    }

    private Texture loadOptionalFixedFrameSheet(
        String path,
        int frameWidth,
        int frameHeight,
        int fallbackFrameCount,
        Color fallbackColor
    ) {
        Texture texture = tryLoadPixelArt(path);

        if (
            texture != null
            && texture.getHeight() == frameHeight
            && texture.getWidth() >= frameWidth
            && texture.getWidth() % frameWidth == 0
        ) {
            return texture;
        }

        discardInvalidTexture(path, texture);
        return createPlaceholderSheet(
            frameWidth,
            frameHeight,
            fallbackFrameCount,
            fallbackColor
        );
    }

    private Texture tryLoadPixelArt(String path) {
        if (!Gdx.files.internal(path).exists()) {
            return null;
        }

        try {
            return loadPixelArt(path);
        } catch (RuntimeException exception) {
            Gdx.app.error(
                "GameTextures",
                "Could not load optional art " + path + "; using a placeholder.",
                exception
            );
            return null;
        }
    }

    private void discardInvalidTexture(String path, Texture texture) {
        if (texture != null) {
            texture.dispose();
        }

        Gdx.app.log(
            "GameTextures",
            "Optional art " + path + " has unsupported dimensions; using a placeholder."
        );
    }

    private Texture createBeamPlaceholder(
        int frameWidth,
        int frameHeight,
        int frameCount,
        Color color
    ) {
        int width = frameWidth * frameCount;
        Pixmap pixmap = new Pixmap(width, frameHeight, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        for (int frame = 0; frame < frameCount; frame++) {
            int left = frame * frameWidth;
            pixmap.setColor(color.r, color.g, color.b, color.a);
            pixmap.fillRectangle(
                left,
                frameHeight / 2 - 2,
                frameWidth,
                5
            );
            pixmap.setColor(Color.WHITE);
            pixmap.fillRectangle(
                left,
                frameHeight / 2,
                frameWidth,
                1
            );
        }

        return textureFromPixmap(pixmap);
    }

    private Texture createPlaceholderSheet(
        int frameWidth,
        int frameHeight,
        int frameCount,
        Color color
    ) {
        Pixmap pixmap = new Pixmap(
            frameWidth * frameCount,
            frameHeight,
            Pixmap.Format.RGBA8888
        );
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(color);

        int radius = Math.max(2, Math.min(frameWidth, frameHeight) / 2 - 2);

        for (int frame = 0; frame < frameCount; frame++) {
            pixmap.fillCircle(
                frame * frameWidth + frameWidth / 2,
                frameHeight / 2,
                radius
            );
        }

        return textureFromPixmap(pixmap);
    }

    private Texture textureFromPixmap(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
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
        if (power == Powers.VORTEX) {
            return vortexAnimation.getKeyFrame(stateTime);
        }

        return switch (power.element()) {
            case WATER -> waterBoltRegion;
            case VOID -> voidBoltRegion;
            case STONE ->
                rockShardAnimation.getKeyFrame(stateTime);

            case LIGHT -> fireballAnimation.getKeyFrame(stateTime);

            default -> fireballAnimation.getKeyFrame(stateTime);
        };
    }

    // The same flame animation, for flame shapes drawn elsewhere (e.g.
    // around a ring zone) rather than on a traveling projectile.
    public TextureRegion getFireballFrame(float stateTime) {
        return fireballAnimation.getKeyFrame(stateTime);
    }

    public float getFireballAnimationDuration() {
        return fireballAnimation.getAnimationDuration();
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

    private Texture createZoneRing() {
        int size = 33;

        Pixmap pixmap =
            new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // Transparent square canvas.
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        // White ring that WorldRenderer can tint. The hole matches Ember
        // Circle's innerRadius-to-radius ratio (half the outer radius).
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fillCircle(
            size / 2,
            size / 2,
            size / 2
        );

        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fillCircle(
            size / 2,
            size / 2,
            size / 4
        );

        Texture texture = new Texture(pixmap);
        texture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        pixmap.dispose();
        return texture;
    }

    public Texture getZoneRing() {
        return zoneRing;
    }

    public TextureRegion getStoneWallFrame(float stateTime) {
        return stoneWallAnimation.getKeyFrame(stateTime, false);
    }

    public TextureRegion getCrystalWallFrame(float stateTime) {
        return crystalWallAnimation.getKeyFrame(stateTime, false);
    }

    public TextureRegion getWallWearFrame(int damageStage) {
        if (damageStage < 1 || damageStage > wallWearFrames.length) {
            throw new IllegalArgumentException(
                "Wall wear damage stage must be between 1 and "
                    + wallWearFrames.length
            );
        }

        return wallWearFrames[damageStage - 1];
    }

    public TextureRegion getVortexFrame(float stateTime) {
        return vortexAnimation.getKeyFrame(stateTime);
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
        rockShard.dispose();
        beamOfLight.dispose();
        crystalBeam.dispose();
        stoneWallSheet.dispose();
        crystalWallSheet.dispose();
        wearLinesSheet.dispose();
        vortexSheet.dispose();
        lightningSheet.dispose();
        zoneCircle.dispose();
        zoneRing.dispose();
        targetDummy.dispose();
        fireballSheet.dispose();
    }
}
