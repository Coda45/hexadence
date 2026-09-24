package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaWorld;

// Draws world state and owns drawing resources. The TiledMap stays owned by ArenaMap.
public class WorldRenderer {

    private final OrthogonalTiledMapRenderer mapRenderer;
    private final SpriteBatch batch;
    private final GameTextures textures;
    private final Texture pixelTexture;

    public WorldRenderer(ArenaMap arenaMap) {

        float unitScale = 1f / ArenaMap.TILE_SIZE;

        mapRenderer = new OrthogonalTiledMapRenderer(
            arenaMap.getTiledMap(),
            unitScale
        );

        batch = new SpriteBatch();

        textures = new GameTextures();

        // Tint and stretch one white pixel to draw dummy bodies and health bars.
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixelTexture = new Texture(pixmap);
        // Texture has its own copy; the temporary pixel data can be released now.
        pixmap.dispose();
    }

    public void render(
        ArenaWorld world,
        WorldCamera worldCamera
    ) {

        // TMX order: Ground, DecorationGround, Walls, DecorationOverlay, Spawns.

        mapRenderer.setView(worldCamera.getCamera());
        // Ground is the bottommost map layer.
        mapRenderer.render(new int[] { 0 });

        batch.setProjectionMatrix(
            worldCamera.getCamera().combined
        );

        batch.begin();

        for (Zone zone : world.getZones()) {
            if (zone.getType() == Zone.Type.PUDDLE) {
                renderZone(zone);
            }
        }

        batch.end();

        mapRenderer.render(new int[] { 1, 2 });

        batch.begin();

        // Dummies, player, projectiles, and their world-space health bars.
        for (TargetDummy dummy : world.getDummies()) {
            renderDummy(dummy);
        }

        Player player = world.getPlayer();
        Texture playerTexture = textures.getPlayer();

        float width = playerTexture.getWidth() / ArenaMap.TILE_SIZE;
        float height = playerTexture.getHeight() / ArenaMap.TILE_SIZE;

        // Anchor the wizard at its feet; projectiles below are centered on their positions.
        batch.draw(
            playerTexture,
            player.getX() - width / 2f,
            player.getY(),
            width,
            height
        );

        renderPlayerHealthBar(player, height);

        for (Projectile projectile : world.getProjectiles()) {
            Texture projectileTexture =
                textures.getProjectile(projectile.getSpellType());

            float projectileWidth =
                projectileTexture.getWidth() / ArenaMap.TILE_SIZE;

            float projectileHeight =
                projectileTexture.getHeight() / ArenaMap.TILE_SIZE;

            batch.draw(
                projectileTexture,
                projectile.getX() - projectileWidth / 2f,
                projectile.getY() - projectileHeight / 2f,
                projectileWidth,
                projectileHeight
            );
        }

        batch.end();

        // Foreground art covers all entities and the dummy health bars.
        mapRenderer.render(new int[] { 3 });

        batch.begin();

        for (Zone zone : world.getZones()) {
            if (zone.getType() == Zone.Type.STEAM) {
                renderZone(zone);
            }
        }

        batch.end();
    }

    private void renderZone(Zone zone) {
        float diameter = zone.getRadius() * 2f;
        float left = zone.getX() - zone.getRadius();
        float bottom = zone.getY() - zone.getRadius();

        switch (zone.getType()) {
            case PUDDLE -> {
                if (zone.isElectrified()) {
                    batch.setColor(0.9f, 0.95f, 0.2f, 0.75f);
                } else {
                    batch.setColor(0.2f, 0.55f, 1f, 0.6f);
                }
            }
            case STEAM ->
                batch.setColor(0.8f, 0.8f, 0.8f, 0.85f);
        }

        batch.draw(
            textures.getZoneCircle(),
            left,
            bottom,
            diameter,
            diameter
        );

        batch.setColor(Color.WHITE);
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

    // Draws a square placeholder for the circular hit area; dead dummies remain gray.
    private void renderDummy(TargetDummy dummy) {
        float diameter = TargetDummy.RADIUS * 2f;
        float left = dummy.getX() - TargetDummy.RADIUS;
        float bottom = dummy.getY() - TargetDummy.RADIUS;

        batch.setColor(dummy.isAlive() ? Color.FIREBRICK : Color.DARK_GRAY);
        batch.draw(pixelTexture, left, bottom, diameter, diameter);
        // Batch tint carries into later draws, so reset it after drawing colored shapes.
        batch.setColor(Color.WHITE);

        if (dummy.isAlive()) {
            float barWidth = diameter;
            float barHeight = 0.08f;
            float barY = dummy.getY() + TargetDummy.RADIUS + 0.15f;
            float healthFraction = dummy.getHealth() / dummy.getMaxHealth();

            batch.setColor(Color.BLACK);
            batch.draw(pixelTexture, left, barY, barWidth, barHeight);

            batch.setColor(Color.LIME);
            batch.draw(pixelTexture, left, barY, barWidth * healthFraction, barHeight);

            batch.setColor(Color.WHITE);
        }
    }

    public void dispose() {
        mapRenderer.dispose();
        batch.dispose();
        textures.dispose();
        
        pixelTexture.dispose();
    }

    
}
