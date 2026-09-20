package io.github.coda45.hexadence.world;

import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;

public class ArenaMap {

    public static final float TILE_SIZE = 24f;

    private final TiledMap tiledMap;

    public ArenaMap() {
        tiledMap = new TmxMapLoader().load("maps/dungeon_map.tmx");
    }

    public TiledMap getTiledMap() {
        return tiledMap;
    }

    public void dispose() {
        tiledMap.dispose();
    }
}