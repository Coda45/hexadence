package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public static final String GROUND_LAYER_NAME = "Ground";
    public static final String DECORATION_GROUND_LAYER_NAME = "DecorationGround";
    public static final String WALLS_LAYER_NAME = "Walls";
    public static final String DECORATION_OVERLAY_LAYER_NAME = "DecorationOverlay";
    public static final String SPAWNS_LAYER_NAME = "Spawns";
    private static final String[] COLLISION_LAYER_NAMES = {
        GROUND_LAYER_NAME,
        DECORATION_GROUND_LAYER_NAME,
        WALLS_LAYER_NAME,
        DECORATION_OVERLAY_LAYER_NAME
    };

    private final TiledMap tiledMap;
    private final ArenaCollision collision;

    public ArenaMap() {
        this("maps/dungeon_map.tmx");
    }

    public ArenaMap(String mapPath) {
        tiledMap = new TmxMapLoader().load(mapPath);

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
        float[][] blockedHeights =
            new float[width][height];

        /*
        * Every listed layer may contribute collision, but only when
        * a tile explicitly has blocked or halfBlocked enabled.
        *
        * DecorationOverlay is included because some foreground wall
        * tiles should restrict how far the player can walk beneath them.
        * Overlay tiles without collision properties remain visual only.
        */
        for (String layerName : COLLISION_LAYER_NAMES) {
            markBlockedTiles(
                blockedHeights,
                layerName
            );
        }

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

    public ArenaDefinition createDefinition() {
        List<SpawnPoint> playerSpawns =
            getSpawnPoints("player");

        if (playerSpawns.isEmpty()) {
            throw new IllegalStateException(
                "TMX map needs at least one player spawn"
            );
        }

        return new ArenaDefinition(
            collision,
            playerSpawns,
            getSpawnPoints("dummy")
        );
    }

    private List<SpawnPoint> getSpawnPoints(
        String namePrefix
    ) {
        MapLayer spawnLayer =
            tiledMap.getLayers().get(SPAWNS_LAYER_NAME);

        if (spawnLayer == null) {
            throw new IllegalStateException(
                "TMX map is missing the Spawns object layer"
            );
        }

        return collectSpawnPoints(
            spawnLayer,
            namePrefix
        );
    }

    static List<SpawnPoint> collectSpawnPoints(
        MapLayer spawnLayer,
        String namePrefix
    ) {
        Pattern namePattern = Pattern.compile(
            Pattern.quote(namePrefix) + "([1-9]\\d*)"
        );

        List<IndexedSpawn> indexedSpawns = new ArrayList<>();

        for (MapObject object : spawnLayer.getObjects()) {
            String name = object.getName();

            if (name == null) {
                continue;
            }

            Matcher matcher = namePattern.matcher(name);

            if (!matcher.matches()) {
                continue;
            }

            int   index  = Integer.parseInt(matcher.group(1));
            float pixelX = object.getProperties().get("x", Float.class);
            float pixelY = object.getProperties().get("y", Float.class);

            indexedSpawns.add(new IndexedSpawn(
                    index,
                    new SpawnPoint(
                        pixelX / TILE_SIZE,
                        pixelY / TILE_SIZE
                    )
                )
            );
        }

        indexedSpawns.sort(
            Comparator.comparingInt(
                IndexedSpawn::index
            )
        );

        for (int i = 1; i < indexedSpawns.size(); i++) {
            if (
                indexedSpawns.get(i - 1).index()
                    == indexedSpawns.get(i).index()
            ) {
                throw new IllegalStateException(
                    "Duplicate "
                        + namePrefix
                        + " spawn number: "
                        + indexedSpawns.get(i).index()
                );
            }
        }

        List<SpawnPoint> result = new ArrayList<>(indexedSpawns.size());

        for (IndexedSpawn indexedSpawn : indexedSpawns) {
            result.add(indexedSpawn.spawn());
        }

        return List.copyOf(result);
    }

    private record IndexedSpawn(
        int index,
        SpawnPoint spawn
    ) {
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
