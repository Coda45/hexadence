package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaWorld;

/** Draws world state and owns drawing resources. The TiledMap stays owned by ArenaMap. */
public class WorldRenderer {

    private final OrthogonalTiledMapRenderer mapRenderer;
    private final SpriteBatch batch;
    private final Texture playerTexture;
    private final Texture projectileTexture;

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

    public void dispose() {
        mapRenderer.dispose();
        batch.dispose();
        playerTexture.dispose();
        projectileTexture.dispose();
    }
}
