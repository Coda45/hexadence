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
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.power.Element;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.render.animation.BeamStrikeVisual;
import io.github.coda45.hexadence.render.animation.ChargedPuddleVisual;
import io.github.coda45.hexadence.render.animation.FacingDirection;
import io.github.coda45.hexadence.render.animation.VoidTrailVisual;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.BeamChannelEndEvent;
import io.github.coda45.hexadence.world.BeamStrikeEvent;
import io.github.coda45.hexadence.world.WorldEvent;
import io.github.coda45.hexadence.world.ZoneChargeChangedEvent;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Draws world state and owns drawing resources. The TiledMap stays owned by ArenaMap.
public class WorldRenderer {

    private static final float ZONE_FLASH_DURATION = 0.12f;
    private static final float DAMAGE_FLASH_DURATION = 0.1f;
    private static final int RING_FLAME_COUNT = 8;
    private static final float RING_FLAME_SIZE = 0.75f;
    private static final float RING_FLAME_ROTATION = -90f;

    private final OrthogonalTiledMapRenderer mapRenderer;
    private final SpriteBatch batch;
    private final GameTextures textures;
    private final Texture pixelTexture;
    private final ShaderProgram whiteFlashShader;
    private final int[] groundLayers;
    private final int[] lowerTerrainLayers;
    private final int[] foregroundLayers;
    private final List<BeamStrikeVisual> beamStrikes = new ArrayList<>();
    private final Map<Zone, ChargedPuddleVisual> chargedPuddleVisuals = new IdentityHashMap<>();
    private final Map<Integer, Float> zoneFlashRemaining = new HashMap<>();
    private final Map<Integer, Float> stoneWallAnimationTimes = new HashMap<>();
    private final Map<Integer, Float> crystalWallAnimationTimes = new HashMap<>();
    private final Map<Integer, VoidTrailVisual> voidTrails = new HashMap<>();
    private final Map<Integer, Float> previousPlayerHealth = new HashMap<>();
    private final Map<Integer, Float> previousDummyHealth = new HashMap<>();
    private final Map<Integer, Float> playerDamageFlashes = new HashMap<>();
    private final Map<Integer, Float> dummyDamageFlashes = new HashMap<>();

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
            ),
            requireLayerIndex(
                arenaMap,
                ArenaMap.DECORATION_GROUND_LAYER_NAME
            )
        };

        lowerTerrainLayers = new int[] {
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

        whiteFlashShader = createWhiteFlashShader();
    }

    private static ShaderProgram createWhiteFlashShader() {
        String vertexShader =
            "attribute vec4 a_position;\n"
                + "attribute vec4 a_color;\n"
                + "attribute vec2 a_texCoord0;\n"
                + "uniform mat4 u_projTrans;\n"
                + "varying vec4 v_color;\n"
                + "varying vec2 v_texCoords;\n"
                + "void main() {\n"
                + "  v_color = a_color;\n"
                + "  v_color.a = v_color.a * (255.0 / 254.0);\n"
                + "  v_texCoords = a_texCoord0;\n"
                + "  gl_Position = u_projTrans * a_position;\n"
                + "}\n";

        String fragmentShader =
            "#ifdef GL_ES\n"
                + "precision mediump float;\n"
                + "#endif\n"
                + "varying vec4 v_color;\n"
                + "varying vec2 v_texCoords;\n"
                + "uniform sampler2D u_texture;\n"
                + "void main() {\n"
                + "  float alpha = texture2D(u_texture, v_texCoords).a * v_color.a;\n"
                + "  gl_FragColor = vec4(1.0, 1.0, 1.0, alpha);\n"
                + "}\n";

        ShaderProgram shader = new ShaderProgram(vertexShader, fragmentShader);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException(
                "Could not compile the damage-flash shader: " + log
            );
        }
        return shader;
    }

    public void render(
        ArenaWorld world,
        WorldCamera worldCamera,
        float presentationDelta,
        List<WorldEvent> frameEvents
    ) {
        updateDamageFlashes(world, presentationDelta);
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

        updateStoneWallAnimations(world, presentationDelta);
        updateCrystalWallAnimations(world, presentationDelta);

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

        for (Map.Entry<Integer, Player> entry : world.getPlayers().entrySet()) {
            int playerId = entry.getKey();
            Player player = entry.getValue();

            if (!player.isAlive() && !playerDamageFlashes.containsKey(playerId)) {
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
            drawEntitySprite(
                playerRegion,
                player.getX() - width / 2f,
                player.getY(),
                width,
                height,
                playerDamageFlashes.containsKey(playerId),
                Color.WHITE
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

            float rotationDegrees = projectile.getPower() == Powers.VORTEX
                ? 0f
                : MathUtils.atan2(
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

        renderBeamStrikes(world);

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

        updateBeamStrikes(presentationDelta);
    }

    private void consumeEvents(
        List<WorldEvent> frameEvents
    ) {
        Set<Integer> sampledLightOwners = new HashSet<>();
        Set<Long> refreshedSplitBranches = new HashSet<>();

        for (WorldEvent event : frameEvents) {
            if (event instanceof BeamStrikeEvent strike) {
                if (strike.power() == Powers.BEAM_OF_LIGHT) {
                    if (strike.branchIndex() == 0) {
                        sampledLightOwners.add(strike.ownerPlayerId());
                    } else {
                        refreshedSplitBranches.add(beamBranchKey(
                            strike.ownerPlayerId(),
                            strike.branchIndex()
                        ));
                    }

                    BeamStrikeVisual active = findHeldBeam(
                        strike.ownerPlayerId(),
                        strike.power(),
                        strike.branchIndex()
                    );

                    if (active != null) {
                        active.refresh(strike);
                    } else {
                        beamStrikes.add(new BeamStrikeVisual(strike, 0));
                    }

                    continue;
                }

                int variantIndex = strike.power() == Powers.LIGHTNING_BOLT
                    ? MathUtils.random(textures.getLightningVariantCount() - 1)
                    : 0;

                beamStrikes.add(
                    new BeamStrikeVisual(
                        strike,
                        variantIndex
                    )
                );
            } else if (event instanceof BeamChannelEndEvent ended) {
                beamStrikes.removeIf(visual ->
                    visual.getStrike().ownerPlayerId() == ended.ownerPlayerId()
                        && visual.getStrike().power().equals(ended.power())
                );
            } else if (event instanceof ZoneChargeChangedEvent changed) {
                zoneFlashRemaining.put(
                    changed.zoneId(),
                    ZONE_FLASH_DURATION
                );
            }
        }

        beamStrikes.removeIf(visual -> {
            BeamStrikeEvent strike = visual.getStrike();
            return strike.power() == Powers.BEAM_OF_LIGHT
                && strike.branchIndex() > 0
                && sampledLightOwners.contains(strike.ownerPlayerId())
                && !refreshedSplitBranches.contains(beamBranchKey(
                    strike.ownerPlayerId(),
                    strike.branchIndex()
                ));
        });
    }

    private BeamStrikeVisual findHeldBeam(
        int ownerPlayerId,
        Power power,
        int branchIndex
    ) {
        for (BeamStrikeVisual visual : beamStrikes) {
            BeamStrikeEvent strike = visual.getStrike();

            if (
                strike.ownerPlayerId() == ownerPlayerId
                && strike.power().equals(power)
                && strike.branchIndex() == branchIndex
            ) {
                return visual;
            }
        }

        return null;
    }

    private static long beamBranchKey(int ownerPlayerId, int branchIndex) {
        return ((long) ownerPlayerId << 32) | (branchIndex & 0xFFFFFFFFL);
    }

    private void updateDamageFlashes(
        ArenaWorld world,
        float deltaSeconds
    ) {
        updateFlashTimers(playerDamageFlashes, deltaSeconds);
        updateFlashTimers(dummyDamageFlashes, deltaSeconds);

        Set<Integer> livePlayerIds = new HashSet<>();
        for (Map.Entry<Integer, Player> entry : world.getPlayers().entrySet()) {
            int id = entry.getKey();
            float health = entry.getValue().getHealth();
            Float previous = previousPlayerHealth.put(id, health);
            livePlayerIds.add(id);

            if (
                previous != null
                    && health < previous
                    && !playerDamageFlashes.containsKey(id)
            ) {
                playerDamageFlashes.put(id, DAMAGE_FLASH_DURATION);
            }
        }
        previousPlayerHealth.keySet().retainAll(livePlayerIds);
        playerDamageFlashes.keySet().retainAll(livePlayerIds);

        Set<Integer> liveDummyIds = new HashSet<>();
        for (TargetDummy dummy : world.getDummies()) {
            int id = dummy.getId();
            float health = dummy.getHealth();
            Float previous = previousDummyHealth.put(id, health);
            liveDummyIds.add(id);

            if (
                previous != null
                    && health < previous
                    && !dummyDamageFlashes.containsKey(id)
            ) {
                dummyDamageFlashes.put(id, DAMAGE_FLASH_DURATION);
            }
        }
        previousDummyHealth.keySet().retainAll(liveDummyIds);
        dummyDamageFlashes.keySet().retainAll(liveDummyIds);
    }

    private static void updateFlashTimers(
        Map<Integer, Float> flashes,
        float deltaSeconds
    ) {
        flashes.replaceAll((id, remaining) -> remaining - deltaSeconds);
        flashes.values().removeIf(remaining -> remaining <= 0f);
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

    private void updateStoneWallAnimations(
        ArenaWorld world,
        float deltaSeconds
    ) {
        Set<Integer> liveWallIds = new HashSet<>();

        for (Zone zone : world.getZones()) {
            if (zone.getKind() != ZoneKinds.STONE_WALL) {
                continue;
            }

            liveWallIds.add(zone.getId());
            stoneWallAnimationTimes.merge(zone.getId(), deltaSeconds, Float::sum);
        }

        stoneWallAnimationTimes.keySet().retainAll(liveWallIds);
    }

    private void updateCrystalWallAnimations(
        ArenaWorld world,
        float deltaSeconds
    ) {
        Set<Integer> liveCrystalIds = new HashSet<>();

        for (Zone zone : world.getZones()) {
            if (zone.getKind() != ZoneKinds.CRYSTAL_WALL) {
                continue;
            }

            liveCrystalIds.add(zone.getId());
            crystalWallAnimationTimes.merge(zone.getId(), deltaSeconds, Float::sum);
        }

        crystalWallAnimationTimes.keySet().retainAll(liveCrystalIds);
    }

    private void updateBeamStrikes(
        float presentationDelta
    ) {
        Iterator<BeamStrikeVisual> iterator = beamStrikes.iterator();

        while (iterator.hasNext()) {
            BeamStrikeVisual strike = iterator.next();

            strike.update(presentationDelta);

            if (
                strike.getStrike().power() != Powers.BEAM_OF_LIGHT
                &&
                strike.isFinished(
                    textures.getBeamAnimationDuration(
                        strike.getStrike().power(),
                        strike.getStrike().branchIndex() > 0
                    )
                )
            ) {
                iterator.remove();
            }
        }
    }

    private void renderBeamStrikes(ArenaWorld world) {
        for (
            BeamStrikeVisual visual
                : beamStrikes
        ) {
            renderBeamStrike(world, visual);
        }
    }

    private void renderBeamStrike(
        ArenaWorld world,
        BeamStrikeVisual visual
    ) {
        BeamStrikeEvent strike = visual.getStrike();

        float deltaX = strike.endX() - strike.startX();
        float deltaY = strike.endY() - strike.startY();

        float strikeLength = (float) Math.sqrt(
            deltaX * deltaX + deltaY * deltaY
        );

        if (strikeLength <= 0.0001f) {
            return;
        }

        float drawStartX = strike.startX();
        float drawStartY = strike.startY();

        // Damage, collision length, and crystal origin remain the latest
        // authoritative sample. Presentation alone follows the owner's latest
        // 60 Hz aim between damage ticks.
        if (strike.power() == Powers.BEAM_OF_LIGHT) {
            Player owner = world.getPlayers().get(strike.ownerPlayerId());

            if (owner != null && owner.isAlive()) {
                float aimX = owner.getAimX();
                float aimY = owner.getAimY();
                float aimLength = (float) Math.sqrt(aimX * aimX + aimY * aimY);

                if (aimLength > 0.0001f) {
                    float directionX = aimX / aimLength;
                    float directionY = aimY / aimLength;

                    if (strike.branchIndex() == 0) {
                        drawStartX = owner.getCastX();
                        drawStartY = owner.getCastY();
                    } else {
                        float splitAngle = strike.branchIndex() == 1
                            ? -22.5f
                            : 22.5f;
                        float cos = MathUtils.cosDeg(splitAngle);
                        float sin = MathUtils.sinDeg(splitAngle);
                        float rotatedX = directionX * cos - directionY * sin;
                        float rotatedY = directionX * sin + directionY * cos;
                        directionX = rotatedX;
                        directionY = rotatedY;
                    }

                    deltaX = directionX * strikeLength;
                    deltaY = directionY * strikeLength;
                }
            }
        }

        TextureRegion frame = textures.getBeamFrame(
            strike.power(),
            visual.getVariantIndex(),
            visual.getStateTime(),
            strike.branchIndex() > 0
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
                        frame.getRegionWidth()
                            * strikeLength
                            / (strike.branchIndex() > 0
                                ? Powers.BEAM_OF_LIGHT_CRYSTAL_RANGE
                                : strike.power().range())
                    )
                )
            );

        batch.draw(
            frame.getTexture(),

            // Place the unrotated image with its left-center on the current
            // presentation anchor (or the authoritative branch origin).
            drawStartX,
            drawStartY - visualHeight / 2f,

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

        if (zone.getKind() == ZoneKinds.STONE_WALL) {
            renderStoneWall(zone);
            batch.setColor(Color.WHITE);
            return;
        }

        if (zone.getKind() == ZoneKinds.CRYSTAL_WALL) {
            renderCrystalWall(zone);
            batch.setColor(Color.WHITE);
            return;
        }

        if (zone.getKind() == ZoneKinds.VORTEX) {
            TextureRegion frame = textures.getVortexFrame(projectileAnimationTime);
            batch.setColor(Color.WHITE);
            batch.draw(frame, left, bottom, diameter, diameter);
            return;
        }
        


        // A ring zone (hollow center) is drawn with a hole; others are a
        // plain filled circle.
        Texture zoneSprite = zone.getKind().innerRadius() > 0f
            ? textures.getZoneRing()
            : textures.getZoneCircle();

        if (zone.getKind() == ZoneKinds.NULL_ZONE) {

            batch.setColor(0.08f, 0.02f, 0.12f, 0.10f);

            for (int i = 0; i < 10; i++) {

                float offsetX =
                    MathUtils.sin(
                        projectileAnimationTime * 0.8f + i
                    ) * 0.25f;

                float offsetY =
                    MathUtils.cos(
                        projectileAnimationTime * 0.7f + i
                    ) * 0.25f;

                float smokeRadius =
                    zone.getRadius() * (
                        1.2f + i * 0.08f
                    );

                batch.draw(
                    textures.getZoneCircle(),
                    zone.getX() - smokeRadius + offsetX,
                    zone.getY() - smokeRadius + offsetY,
                    smokeRadius * 2f,
                    smokeRadius * 2f
                );
            }

            // Restore the zone's original tint before drawing it.
            int tint = zone.getKind().tint();

            batch.setColor(
                ((tint >>> 24) & 0xFF) / 255f,
                ((tint >>> 16) & 0xFF) / 255f,
                ((tint >>> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f
            );
        }

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

    private void renderStoneWall(Zone zone) {
        TextureRegion frame = textures.getStoneWallFrame(
            stoneWallAnimationTimes.getOrDefault(zone.getId(), 0f)
        );
        float width = zone.getKind().solidHalfLength() * 2f;
        float height = zone.getKind().solidHalfThickness() * 2f;
        float left = zone.getX() - width / 2f;
        float bottom = zone.getY() - height / 2f;

        batch.setColor(Color.WHITE);
        batch.draw(frame, left, bottom, width, height);
        renderStoneWallWear(zone, left, bottom, width, height);
    }

    private void renderCrystalWall(Zone zone) {
        TextureRegion frame = textures.getCrystalWallFrame(
            crystalWallAnimationTimes.getOrDefault(zone.getId(), 0f)
        );
        float width = zone.getKind().solidHalfLength() * 2f;
        float height = zone.getKind().solidHalfThickness() * 2f;

        batch.setColor(Color.WHITE);
        batch.draw(
            frame,
            zone.getX() - width / 2f,
            zone.getY() - height / 2f,
            width,
            height
        );
        renderStoneWallWear(zone, zone.getX() - width / 2f,
            zone.getY() - height / 2f, width, height);
    }

    // Damage selects progressively heavier frames from wear_lines.png rather
    // than advancing them as a time-based animation.
    private void renderStoneWallWear(
        Zone zone,
        float left,
        float bottom,
        float width,
        float height
    ) {
        float healthFraction = zone.getHealthFraction();
        int stage = healthFraction <= 0.25f ? 3
            : healthFraction <= 0.5f ? 2
            : healthFraction <= 0.75f ? 1
            : 0;

        if (stage == 0) {
            return;
        }

        batch.setColor(Color.WHITE);
        batch.draw(
            textures.getWallWearFrame(stage),
            left,
            bottom,
            width,
            height
        );
    }

    // Flame shapes spaced evenly around a ring zone's band, reusing the
    // fireball animation so a ring reads as fire without new art.
    private void renderRingFlames(Zone zone) {
        float midRadius =
            (zone.getKind().innerRadius() + zone.getRadius()) / 2f;
        float phaseStep =
            textures.getFireballAnimationDuration() / RING_FLAME_COUNT;

        for (int i = 0; i < RING_FLAME_COUNT; i++) {
            float angle = i * 360f / RING_FLAME_COUNT;
            float flameX = zone.getX() + midRadius * MathUtils.cosDeg(angle);
            float flameY = zone.getY() + midRadius * MathUtils.sinDeg(angle);

            TextureRegion flame = textures.getFireballFrame(
                projectileAnimationTime + i * phaseStep
            );

            batch.draw(
                flame,
                flameX - RING_FLAME_SIZE / 2f,
                flameY - RING_FLAME_SIZE / 2f,
                RING_FLAME_SIZE / 2f,
                RING_FLAME_SIZE / 2f,
                RING_FLAME_SIZE,
                RING_FLAME_SIZE,
                1f,
                1f,
                RING_FLAME_ROTATION
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

    private void drawEntitySprite(
        TextureRegion sprite,
        float left,
        float bottom,
        float width,
        float height,
        boolean flashing,
        Color normalTint
    ) {
        batch.setColor(flashing ? Color.WHITE : normalTint);
        if (flashing) {
            batch.setShader(whiteFlashShader);
        }
        batch.draw(sprite, left, bottom, width, height);
        if (flashing) {
            batch.setShader(null);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawEntitySprite(
        Texture sprite,
        float left,
        float bottom,
        float width,
        float height,
        boolean flashing,
        Color normalTint
    ) {
        batch.setColor(flashing ? Color.WHITE : normalTint);
        if (flashing) {
            batch.setShader(whiteFlashShader);
        }
        batch.draw(sprite, left, bottom, width, height);
        if (flashing) {
            batch.setShader(null);
        }
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

        drawEntitySprite(
            sprite,
            spriteLeft,
            spriteBottom,
            width,
            height,
            dummyDamageFlashes.containsKey(dummy.getId()),
            dummy.isAlive() ? Color.WHITE : Color.DARK_GRAY
        );

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
        whiteFlashShader.dispose();
        
        pixelTexture.dispose();
    }

    
}
