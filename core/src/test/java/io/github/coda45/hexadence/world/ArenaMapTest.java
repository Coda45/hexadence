package io.github.coda45.hexadence.world;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;

class ArenaMapTest {

    private static final float EPSILON = 0.0001f;

    @Test
    void discoversAndNumericallySortsPlayerSpawns() {
        MapLayer layer = new MapLayer();

        layer.getObjects().add(
            spawnObject("player2", 48f, 72f)
        );

        layer.getObjects().add(
            spawnObject("dummy1", 240f, 240f)
        );

        layer.getObjects().add(
            spawnObject("player1", 24f, 48f)
        );

        List<SpawnPoint> spawns =
            ArenaMap.collectSpawnPoints(
                layer,
                "player"
            );

        assertEquals(2, spawns.size());

        assertEquals(
            1f,
            spawns.get(0).x(),
            EPSILON
        );

        assertEquals(
            2f,
            spawns.get(0).y(),
            EPSILON
        );

        assertEquals(
            2f,
            spawns.get(1).x(),
            EPSILON
        );

        assertEquals(
            3f,
            spawns.get(1).y(),
            EPSILON
        );
    }

    @Test
    void allowsMapWithNoDummySpawns() {
        MapLayer layer = new MapLayer();

        layer.getObjects().add(
            spawnObject("player1", 24f, 48f)
        );

        List<SpawnPoint> dummies =
            ArenaMap.collectSpawnPoints(
                layer,
                "dummy"
            );

        assertEquals(0, dummies.size());
    }

    private static MapObject spawnObject(
        String name,
        float pixelX,
        float pixelY
    ) {
        MapObject object = new MapObject();

        object.setName(name);
        object.getProperties().put("x", pixelX);
        object.getProperties().put("y", pixelY);

        return object;
    }
}