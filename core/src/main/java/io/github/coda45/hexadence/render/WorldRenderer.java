package io.github.coda45.hexadence.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.power.Element;
import io.github.coda45.hexadence.render.animation.ChargedPuddleVisual;
import io.github.coda45.hexadence.render.animation.FacingDirection;
import io.github.coda45.hexadence.render.animation.LightningStrikeVisual;
import io.github.coda45.hexadence.render.animation.VoidTrailVisual;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.LightningStrikeEvent;
import io.github.coda45.hexadence.world.WorldEvent;
import io.github.coda45.hexadence.world.ZoneChargeChangedEvent;

// Draws world state and owns drawing resources. The TiledMap stays owned by ArenaMap.
public class WorldRenderer {

    private static final float ZONE_FLASH_DURATION = 0.12f;
    private static final int RING_FLAME_COUNT = 8;
    private static final float RING_FLAME_SIZE = 0.35f;

    private final OrthogonalTiledMapRenderer mapRenderer;
    private final SpriteBatch batch;
    private final GameTextures textures;
    private final Texture pixelTexture;
    private final int[] groundLayers;
    private final int[] lowerTerrainLayers;
    private final int[] foregroundLayers;
    private final List<LightningStrikeVisual> lightningStrikes = new ArrayList<>();
    private final Map<Zone, ChargedPuddleVisual> chargedPuddleVisuals = new IdentityHashMap<>();
    private final Map<Integer, Float> zoneFlashRemaining = new HashMap<>();
    private final Map<Integer, VoidTrailVisual> voidTrails = new HashMap<>();

    private float projectileAnimationTime;
    private float playerMoveTime;
    private float playerSpriteHeight;

    public WorldRenderer(ArenaMap arenaMap) {

        float unitScale = 1f / ArenaMap.TILE_SIZE;

        mapRenderer = new OrthogonalTiledMapRenderer(
            arenaMap.getTiledMap(),
            unitScale
        );

        groundLayers = new int[] {
            requireLayerIndex(
                arenaMap,
                ArenaMap.GROUND_LAYER_NAME
            )
        };

        lowerTerrainLayers = new int[] {
            requireLayerIndex(
                arenaMap,
                ArenaMap.DECORATION_GROUND_LAYER_NAME
            ),
            requireLayerIndex(
                arenaMap,
                ArenaMap.WALLS_LAYER_NAME
            )
        };

        foregroundLayers = new int[] {
            requireLayerIndex(
                arenaMap,
                ArenaMap.DECORATION_OVERLAY_LAYER_NAME
            )
        };

        batch = new SpriteBatch();

        textures = new GameTextures();

        // Tint and stretch one white pixel to draw health bars.
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixelTexture = new Texture(pixmap);
        // Texture has its own copy; the temporary pixel data can be released now.
        pixmap.dispose();
    }

    public void render(
        ArenaWorld world,
        WorldCamera worldCamera,
        float presentationDelta,
        List<WorldEvent> frameEvents
    ) {
        updateZoneFlashes(presentationDelta);
        consumeEvents(frameEvents);
        // TMX order: Ground, DecorationGround, Walls, DecorationOverlay, Spawns.

        projectileAnimationTime += presentationDelta;

        updateChargedPuddleVisuals(
            world,
            presentationDelta
        );

        updateVoidTrails(
            world,
            presentationDelta
        );

        mapRenderer.setView(worldCamera.getCamera());
        // Ground is the bottommost map layer.
        mapRenderer.render(groundLayers);

        batch.setProjectionMatrix(
            worldCamera.getCamera().combined
        );

        batch.begin();

        // Ground zones, oldest first, so the newest is drawn on top.
        for (Zone zone : world.getZones()) {
            if (!zone.getKind().coversPlayers()) {
                renderZone(zone);

                ChargedPuddleVisual visual =
                    chargedPuddleVisuals.get(zone);

                if (
                    visual != null
                    && visual.isPlaying()
                ) {
                    renderChargedPuddle(visual);
                }
            }
        }

        batch.end();

        mapRenderer.render(lowerTerrainLayers);

        batch.begin();

        // Dummies, player, projectiles, and their world-space health bars.
        for (TargetDummy dummy : world.getDummies()) {
            renderDummy(dummy);
        }

        // One shared walk timer runs while anyone is moving.
        // Standing players ignore it and show their idle frame.
        if (world.getPlayers().values().stream()
                .anyMatch(
                    player ->
                        player.isAlive()
                        && player.isMoving()
                )
        ) {
            playerMoveTime += presentationDelta;
        } else {
            playerMoveTime = 0f;
        }

        for (Player player : world.getPlayers().values()){
            if (!player.isAlive()) {
                continue;
            }
            FacingDirection facing =
                FacingDirection.fromAim(
                    player.getAimX(),
                    player.getAimY()
                );

            TextureRegion playerRegion =
                textures.getPlayerFrame(
                    player.getColor(),
                    facing,
                    player.isMoving(),
                    playerMoveTime
                );

            float width =  playerRegion.getRegionWidth()  / ArenaMap.TILE_SIZE;
            float height = playerRegion.getRegionHeight() / ArenaMap.TILE_SIZE;
            playerSpriteHeight = height;

            // Anchor the wizard at its feet; projectiles below are centered on their positions.
            batch.draw(
                playerRegion,
                player.getX() - width / 2f,
                player.getY(),
                width,
                height
            );
        }   

        for (Projectile projectile : world.getProjectiles()) {
            TextureRegion projectileRegion =
                textures.getProjectileFrame(
                    projectile.getPower(),
                    projectileAnimationTime
                );

            if (projectile.getPower().element() == Element.VOID) {
                VoidTrailVisual trail = voidTrails.get(projectile.getId());

                if (trail != null) {
                    renderVoidTrail(trail, projectileRegion);
                }
            }

            float projectileWidth =
                projectileRegion.getRegionWidth()
                    / ArenaMap.TILE_SIZE;

            float projectileHeight =
                projectileRegion.getRegionHeight()
                    / ArenaMap.TILE_SIZE;

            float rotationDegrees =
                MathUtils.atan2(
                    projectile.getDirectionY(),
                    projectile.getDirectionX()
                ) * MathUtils.radiansToDegrees;

            float left =   projectile.getX() - projectileWidth  / 2f;
            float bottom = projectile.getY() - projectileHeight / 2f;

            batch.draw(
                projectileRegion,
                left,
                bottom,
                projectileWidth / 2f,
                projectileHeight / 2f,
                projectileWidth,
                projectileHeight,
                1f,
                1f,
                rotationDegrees
            );
        }

        renderLightningStrikes();

        batch.end();

        // Foreground art covers all entities and the dummy health bars.
        mapRenderer.render(foregroundLayers);

        batch.begin();
        
        for (Player player : world.getPlayers().values()) {
            if (!player.isAlive()) {
                continue;
            }
            renderPlayerHealthBar(player, playerSpriteHeight);
        }

        // Cloud-like zones (steam) are drawn over players.
        for (Zone zone : world.getZones()) {
            if (zone.getKind().coversPlayers()) {
                renderZone(zone);
            }
        }

        batch.end();

        updateLightningStrikes(presentationDelta);
    }

    private void consumeEvents(
        List<WorldEvent> frameEvents
    ) {
        for (WorldEvent event : frameEvents) {
            if (event instanceof LightningStrikeEvent strike) {
                int variantIndex =
                    MathUtils.random(
                        textures.getLightningVariantCount() - 1
                    );

                lightningStrikes.add(
                    new LightningStrikeVisual(
                        strike,
                        variantIndex
                    )
                );
            } else if (event instanceof ZoneChargeChangedEvent changed) {
                zoneFlashRemaining.put(
                    changed.zoneId(),
                    ZONE_FLASH_DURATION
                );
            }
        }
    }

    private void updateZoneFlashes(float deltaSeconds) {
        Iterator<Map.Entry<Integer, Float>> iterator =
            zoneFlashRemaining.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Float> entry = iterator.next();

            float remaining = entry.getValue() - deltaSeconds;

            if (remaining <= 0f) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private void updateLightningStrikes(
        float presentationDelta
    ) {
        Iterator<LightningStrikeVisual> iterator = lightningStrikes.iterator();

        while (iterator.hasNext()) {
            LightningStrikeVisual strike = iterator.next();

            strike.update(presentationDelta);

            if (
                strike.isFinished(
                    textures.getLightningAnimationDuration()
                )
            ) {
                iterator.remove();
            }
        }
    }

    private void renderLightningStrikes() {
        for (
            LightningStrikeVisual visual
                : lightningStrikes
        ) {
            renderLightningStrike(visual);
        }
    }

    private void renderLightningStrike(
        LightningStrikeVisual visual
    ) {
        LightningStrikeEvent strike = visual.getStrike();

        float deltaX = strike.endX() - strike.startX();
        float deltaY = strike.endY() - strike.startY();

        float strikeLength = (float) Math.sqrt(
            deltaX * deltaX + deltaY * deltaY
        );

        if (strikeLength <= 0.0001f) {
            return;
        }

        TextureRegion frame =
            textures.getLightningFrame(
                visual.getVariantIndex(),
                visual.getStateTime()
            );

        float visualHeight =
            frame.getRegionHeight()
                / ArenaMap.TILE_SIZE;

        float rotationDegrees =
            MathUtils.atan2(deltaY, deltaX)
                * MathUtils.radiansToDegrees;

        int visiblePixelWidth =
            Math.max(
                1,
                Math.min(
                    frame.getRegionWidth(),
                    (int) Math.ceil(
                        strikeLength
                            * ArenaMap.TILE_SIZE
                    )
                )
            );

        batch.draw(
            frame.getTexture(),

            // Place the unrotated image with its left-center
            // directly on the authoritative cast point.
            strike.startX(),
            strike.startY() - visualHeight / 2f,

            // Rotate around that left-center point.
            0f,
            visualHeight / 2f,

            // The destination ends at the exact collision distance.
            strikeLength,
            visualHeight,

            1f,
            1f,
            rotationDegrees,

            // Draw only the visible portion of the selected frame.
            frame.getRegionX(),
            frame.getRegionY(),
            visiblePixelWidth,
            frame.getRegionHeight(),

            false,
            false
        );
    }

    private void renderChargedPuddle(
        ChargedPuddleVisual visual
    ) {
        Zone zone = visual.getZone();

        TextureRegion frame =
            textures.getChargedPuddleFrame(
                visual.getVariantIndex(),
                visual.getStateTime()
            );

        float diameter = zone.getRadius() * 2f;

        batch.setColor(Color.WHITE);

        batch.draw(
            frame,
            zone.getX() - zone.getRadius(),
            zone.getY() - zone.getRadius(),
            diameter,
            diameter
        );
    }

    private void updateChargedPuddleVisuals(
        ArenaWorld world,
        float presentationDelta
    ) {
        Iterator<
            Map.Entry<Zone, ChargedPuddleVisual>
        > iterator =
            chargedPuddleVisuals
                .entrySet()
                .iterator();

        // Discharge, expiration, or replacement immediately ends the overlay.
        while (iterator.hasNext()) {
            Map.Entry<Zone, ChargedPuddleVisual> entry =
                iterator.next();

            Zone zone = entry.getKey();

            boolean zoneStillExists = world.getZones().contains(zone);

            if (
                !zoneStillExists
                || !zone.isElectrified()
            ) {
                iterator.remove();
                continue;
            }

            ChargedPuddleVisual visual = entry.getValue();

            visual.update(
                presentationDelta,
                textures.getChargedPuddleAnimationDuration()
            );

            if (visual.isReadyToPlayAgain()) {
                int variantIndex =
                    MathUtils.random(
                        textures
                            .getChargedPuddleVariantCount()
                            - 1
                    );

                visual.start(variantIndex);
            }
        }

        // A newly charged puddle begins its first variation immediately.
        for (Zone zone : world.getZones()) {
            if (
                !zone.isElectrified()
                || chargedPuddleVisuals.containsKey(zone)
            ) {
                continue;
            }

            int variantIndex =
                MathUtils.random(
                    textures
                        .getChargedPuddleVariantCount()
                        - 1
                );

            chargedPuddleVisuals.put(
                zone,
                new ChargedPuddleVisual(
                    zone,
                    variantIndex
                )
            );
        }
    }

    // Samples each live Void projectile's position for its fading trail, and
    // drops trails for projectiles that are gone.
    private void updateVoidTrails(
        ArenaWorld world,
        float presentationDelta
    ) {
        Set<Integer> present = new HashSet<>();

        for (Projectile projectile : world.getProjectiles()) {
            if (projectile.getPower().element() != Element.VOID) {
                continue;
            }

            present.add(projectile.getId());

            VoidTrailVisual trail =
                voidTrails.computeIfAbsent(
                    projectile.getId(),
                    id -> new VoidTrailVisual()
                );

            trail.update(
                presentationDelta,
                projectile.getX(),
                projectile.getY()
            );
        }

        voidTrails.keySet().retainAll(present);
    }

    private void renderVoidTrail(
        VoidTrailVisual trail,
        TextureRegion region
    ) {
        float width = region.getRegionWidth() / ArenaMap.TILE_SIZE;
        float height = region.getRegionHeight() / ArenaMap.TILE_SIZE;

        for (VoidTrailVisual.Sample sample : trail.getSamples()) {
            float alpha = Math.max(
                0f,
                1f - sample.getAge() / VoidTrailVisual.getTrailSeconds()
            );

            batch.setColor(0.2f, 0.05f, 0.3f, alpha * 0.6f);

            batch.draw(
                region,
                sample.getX() - width / 2f,
                sample.getY() - height / 2f,
                width,
                height
            );
        }

        batch.setColor(Color.WHITE);
    }

    private void renderZone(Zone zone) {
        float diameter = zone.getRadius() * 2f;
        float left = zone.getX() - zone.getRadius();
        float bottom = zone.getY() - zone.getRadius();

        if (zoneFlashRemaining.containsKey(zone.getId())) {
            // Brief yellow hit/damage feedback.
            batch.setColor(0.9f, 0.95f, 0.2f, 0.75f);
        } else if (zone.isElectrified()) {
            // Persistent muted teal charged state.
            batch.setColor(0.25f, 0.68f, 0.72f, 0.68f);
        } else {
            // The zone kind's own color, from its definition in ZoneKinds.
            int tint = zone.getKind().tint();

            batch.setColor(
                ((tint >>> 24) & 0xFF) / 255f,
                ((tint >>> 16) & 0xFF) / 255f,
                ((tint >>> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f
            );
        }

        // A ring zone (hollow center) is drawn with a hole; others are a
        // plain filled circle.
        Texture zoneSprite = zone.getKind().innerRadius() > 0f
            ? textures.getZoneRing()
            : textures.getZoneCircle();

        batch.draw(
            zoneSprite,
            left,
            bottom,
            diameter,
            diameter
        );

        batch.setColor(Color.WHITE);

        if (zone.getKind().innerRadius() > 0f) {
            renderRingFlames(zone);
        }
    }

    // Flame shapes spaced evenly around a ring zone's band, reusing the
    // fireball animation so a ring reads as fire without new art.
    private void renderRingFlames(Zone zone) {
        float midRadius =
            (zone.getKind().innerRadius() + zone.getRadius()) / 2f;

        for (int i = 0; i < RING_FLAME_COUNT; i++) {
            float angle = i * 360f / RING_FLAME_COUNT;
            float flameX = zone.getX() + midRadius * MathUtils.cosDeg(angle);
            float flameY = zone.getY() + midRadius * MathUtils.sinDeg(angle);

            TextureRegion flame = textures.getFireballFrame(
                projectileAnimationTime + i * 0.05f
            );

            batch.draw(
                flame,
                flameX - RING_FLAME_SIZE / 2f,
                flameY - RING_FLAME_SIZE / 2f,
                RING_FLAME_SIZE,
                RING_FLAME_SIZE
            );
        }
    }

    private void renderPlayerHealthBar(
        Player player,
        float spriteHeight
    ) {
        float barWidth = 1.2f;
        float barHeight = 0.1f;

        float left =
            player.getX() - barWidth / 2f;

        float bottom =
            player.getY() + spriteHeight + 0.12f;

        float healthFraction =
            player.getHealth() / player.getMaxHealth();

        batch.setColor(Color.BLACK);
        batch.draw(
            pixelTexture,
            left,
            bottom,
            barWidth,
            barHeight
        );

        batch.setColor(Color.LIME);
        batch.draw(
            pixelTexture,
            left,
            bottom,
            barWidth * healthFraction,
            barHeight
        );

        batch.setColor(Color.WHITE);
    }

    // Center the sprite on the circular hit area; dead dummies remain gray.
    private void renderDummy(TargetDummy dummy) {
        Texture sprite = textures.getTargetDummy();
        float width = sprite.getWidth() / ArenaMap.TILE_SIZE;
        float height = sprite.getHeight() / ArenaMap.TILE_SIZE;
        float spriteLeft = dummy.getX() - width / 2f;
        float spriteBottom = dummy.getY() - height / 2f;
        float diameter = TargetDummy.RADIUS * 2f;

        batch.setColor(dummy.isAlive() ? Color.WHITE : Color.DARK_GRAY);
        batch.draw(
            sprite,
            spriteLeft,
            spriteBottom,
            width,
            height
        );
        // Batch tint carries into later draws, so reset it after drawing the sprite.
        batch.setColor(Color.WHITE);

        if (dummy.isAlive()) {
            float barWidth = diameter;
            float barHeight = 0.08f;
            float left = dummy.getX() - barWidth / 2f;
            float barY = spriteBottom + height + 0.15f;
            float healthFraction = dummy.getHealth() / dummy.getMaxHealth();

            batch.setColor(Color.BLACK);
            batch.draw(pixelTexture, left, barY, barWidth, barHeight);

            batch.setColor(Color.LIME);
            batch.draw(pixelTexture, left, barY, barWidth * healthFraction, barHeight);

            batch.setColor(Color.WHITE);
        }
    }

    private static int requireLayerIndex(
        ArenaMap arenaMap,
        String layerName
    ) {
        int index =
            arenaMap.getTiledMap()
                .getLayers()
                .getIndex(layerName);

        if (index < 0) {
            throw new IllegalStateException(
                "TMX map is missing required layer: "
                    + layerName
            );
        }

        return index;
    }

    public void dispose() {
        mapRenderer.dispose();
        batch.dispose();
        textures.dispose();
        
        pixelTexture.dispose();
    }

    
}
