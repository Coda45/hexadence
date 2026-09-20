package io.github.coda45.hexadence.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;

public class ArenaMap {

    public static final float TILE_SIZE = 24f;
    private final int width;
    private final int height;

    private final TiledMap tiledMap;

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

    public void dispose() {
        tiledMap.dispose();
    }
}