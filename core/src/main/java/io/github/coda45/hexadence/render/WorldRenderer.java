package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.world.ArenaMap;
import io.github.coda45.hexadence.world.ArenaWorld;

public class WorldRenderer {

    private final OrthogonalTiledMapRenderer mapRenderer;
    private final SpriteBatch batch;
    private final Texture playerTexture;

    public WorldRenderer(ArenaMap arenaMap) {

        float unitScale = 1f / ArenaMap.TILE_SIZE;

        mapRenderer = new OrthogonalTiledMapRenderer(
            arenaMap.getTiledMap(),
            unitScale
        );

        batch = new SpriteBatch();

        playerTexture = new Texture("sprites/wizard.png");

        playerTexture.setFilter(
            Texture.TextureFilter.Nearest,
            Texture.TextureFilter.Nearest
        );
    }

    public void render(
        ArenaWorld world,
        WorldCamera worldCamera
    ) {

        // ----- Map -----

        mapRenderer.setView(worldCamera.getCamera());
        mapRenderer.render();

        // ----- Entities -----

        batch.setProjectionMatrix(
            worldCamera.getCamera().combined
        );

        batch.begin();

        Player player = world.getPlayer();

        float width =
            playerTexture.getWidth() / ArenaMap.TILE_SIZE;

        float height =
            playerTexture.getHeight() / ArenaMap.TILE_SIZE;

        batch.draw(
            playerTexture,
            player.getX() - width / 2f,
            player.getY(),
            width,
            height
        );

        batch.end();
    }

    public void dispose() {
        mapRenderer.dispose();
        batch.dispose();
        playerTexture.dispose();
    }
}