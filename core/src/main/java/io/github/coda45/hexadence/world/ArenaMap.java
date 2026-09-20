package io.github.coda45.hexadence.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;

public class ArenaMap {

    public static final float TILE_SIZE = 24f;
    private final int width;
    private final int height;

    private final TiledMap tiledMap;
    private final ArenaCollision collision; //Collision map

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
        boolean[][] blocked = new boolean[width][height];

        // DecorationOverlay is drawn above the player and is purely visual, so it's excluded.
        markBlockedTiles(blocked, "Ground");
        markBlockedTiles(blocked, "DecorationGround");

        return new ArenaCollision(blocked);
    }

    private void markBlockedTiles(boolean[][] blocked, String layerName) {
        TiledMapTileLayer layer = (TiledMapTileLayer) tiledMap.getLayers().get(layerName);
        if (layer == null) {
            return;
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                if (cell == null || cell.getTile() == null) {
                    continue;
                }

                boolean isBlocked = cell.getTile().getProperties()
                    .get("blocked", false, Boolean.class);

                if (isBlocked) {
                    blocked[x][y] = true;
                }
            }
        }
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