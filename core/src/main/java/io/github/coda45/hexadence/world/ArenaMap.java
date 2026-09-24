package io.github.coda45.hexadence.world;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;

// Loads the Tiled map and converts tile properties into a plain collision grid.
public class ArenaMap {

    // Map pixels per world unit. One 24-pixel tile occupies one world unit.
    public static final float TILE_SIZE = 24f;
    private final int width;
    private final int height;

    private final TiledMap tiledMap;
    private final ArenaCollision collision;

    public ArenaMap() {
        tiledMap = new TmxMapLoader().load("maps/dungeon_map.tmx");
        width = tiledMap.getProperties().get(
            "width",
            Integer.class
        );

        height = tiledMap.getProperties().get(
            "height",
            Integer.class
        );

        collision = buildCollision();
    }

    private ArenaCollision buildCollision() {
        float[][] blockedHeights = new float[width][height];

        // DecorationOverlay is drawn above the player and is purely visual, so it's excluded.
        markBlockedTiles(blockedHeights, "Ground");
        markBlockedTiles(blockedHeights, "DecorationGround");
        markBlockedTiles(blockedHeights, "Walls");
        markBlockedTiles(blockedHeights, "DecorationOverlay");

        return new ArenaCollision(blockedHeights);
    }

    private void markBlockedTiles(
        float[][] blockedHeights,
        String layerName
    ) {
        TiledMapTileLayer layer =
            (TiledMapTileLayer) tiledMap
                .getLayers()
                .get(layerName);

        if (layer == null) {
            return;
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                TiledMapTileLayer.Cell cell =
                    layer.getCell(x, y);

                if (
                    cell == null
                    || cell.getTile() == null
                ) {
                    continue;
                }

                boolean fullyBlocked =
                    cell.getTile().getProperties()
                        .get("blocked", false, Boolean.class);

                boolean halfBlocked =
                    cell.getTile().getProperties()
                        .get("halfBlocked", false, Boolean.class);

                float blockedHeight;

                if (halfBlocked) {
                    blockedHeight = 0.5f;
                } else if (fullyBlocked) {
                    blockedHeight = 1f;
                } else {
                    blockedHeight = 0f;
                }

                // Preserve stronger collision contributed by another layer.
                blockedHeights[x][y] = Math.max(
                    blockedHeights[x][y],
                    blockedHeight
                );
            }
        }
    }

    public SpawnPoint getSpawnPoint(String name) {
        MapLayer spawnLayer =
            tiledMap.getLayers().get("Spawns");

        if (spawnLayer == null) {
            throw new IllegalStateException(
                "TMX map is missing the Spawns object layer"
            );
        }

        MapObject object =
            spawnLayer.getObjects().get(name);

        if (object == null) {
            throw new IllegalStateException(
                "TMX map is missing spawn point: " + name
            );
        }

        float pixelX =
            object.getProperties().get("x", Float.class);

        float pixelY =
            object.getProperties().get("y", Float.class);

        return new SpawnPoint(
            pixelX / TILE_SIZE,
            pixelY / TILE_SIZE
        );
    }

    public float getWorldWidth() {
        return width;
    }

    public float getWorldHeight() {
        return height;
    }

    public TiledMap getTiledMap() {
        return tiledMap;
    }

    public ArenaCollision getCollision() {
        return collision;
    }
    
    public void dispose() {
        tiledMap.dispose();
    }
}
