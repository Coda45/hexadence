package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaWorld;

/** Draws world state and owns drawing resources. The TiledMap stays owned by ArenaMap. */
public class WorldRenderer {

    private final OrthogonalTiledMapRenderer mapRenderer;
    private final SpriteBatch batch;
    private final Texture playerTexture;
    private final Texture projectileTexture;
    private final Texture pixelTexture; //Target dummies testing only

    public WorldRenderer(ArenaMap arenaMap) {

        float unitScale = 1f / ArenaMap.TILE_SIZE;

        mapRenderer = new OrthogonalTiledMapRenderer(
            arenaMap.getTiledMap(),
            unitScale
        );

        batch = new SpriteBatch();

        playerTexture = new Texture("sprites/wizard.png");

        projectileTexture = new Texture("spells/ember_bolt.png");

        projectileTexture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        playerTexture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );

        //Target dummies testing only
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        pixelTexture = new Texture(pixmap);
        pixmap.dispose();
        //End target dummies
    }

    public void render(
        ArenaWorld world,
        WorldCamera worldCamera
    ) {

        // These indices depend on TMX order: Ground, DecorationGround, DecorationOverlay.

        mapRenderer.setView(worldCamera.getCamera());
        mapRenderer.render(new int[] { 0, 1 });

        batch.setProjectionMatrix(
            worldCamera.getCamera().combined
        );

        batch.begin();

        //Target dummies testing only
        for (TargetDummy dummy : world.getDummies()) {
            renderDummy(dummy);
        }
        //End target dummies

        Player player = world.getPlayer();

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

        for (Projectile projectile : world.getProjectiles()) {

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

        // Foreground art covers both the player and projectiles.
        mapRenderer.render(new int[] { 2 });
    }

    //Target dummies testing only
    private void renderDummy(TargetDummy dummy) {
        float diameter = TargetDummy.RADIUS * 2f;
        float left = dummy.getX() - TargetDummy.RADIUS;
        float bottom = dummy.getY() - TargetDummy.RADIUS;

        batch.setColor(dummy.isAlive() ? Color.FIREBRICK : Color.DARK_GRAY);
        batch.draw(pixelTexture, left, bottom, diameter, diameter);
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
    //End target dummies

    public void dispose() {
        mapRenderer.dispose();
        batch.dispose();
        playerTexture.dispose();
        projectileTexture.dispose();
        
        //Target dummies testing only
        pixelTexture.dispose();
        //End target dummies
    }

    
}
