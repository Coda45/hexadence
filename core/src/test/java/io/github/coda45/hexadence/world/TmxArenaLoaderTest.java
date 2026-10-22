package io.github.coda45.hexadence.world;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class TmxArenaLoaderTest {

    private static final float EPSILON = 0.0001f;

    // Gradle runs core's tests from the core folder.
    private static final Path ASSETS = Path.of("..", "assets");

    @Test
    void loadsTheMultiplayerMapWithoutOpenGl() {
        ArenaDefinition definition = TmxArenaLoader.load(
            ArenaMaps.MULTIPLAYER,
            path -> Files.newInputStream(ASSETS.resolve(path))
        );

        List<SpawnPoint> spawns = definition.playerSpawns();
        assertEquals(4, spawns.size());

        // player1 is at (132.333, 254.667) in Tiled's pixels, measured from
        // the top of a 20-tile-high map of 24-pixel tiles.
        assertEquals(132.333f / 24f, spawns.get(0).x(), EPSILON);
        assertEquals((480f - 254.667f) / 24f, spawns.get(0).y(), EPSILON);

        // Every player must spawn on open floor.
        for (SpawnPoint spawn : spawns) {
            assertFalse(
                definition.collision().overlaps(spawn.x(), spawn.y(), 0.1f, 0.1f),
                "spawn " + spawn + " is inside a wall"
            );
        }
    }

    @Test
    void loadsTheTrainingMap() {
        ArenaDefinition definition = TmxArenaLoader.load(
            ArenaMaps.TRAINING,
            path -> Files.newInputStream(ASSETS.resolve(path))
        );

        assertEquals(1, definition.playerSpawns().size());
        assertEquals(6, definition.dummySpawns().size());
    }

    @Test
    void readsCollisionFromAnEmbeddedTilesetAndFlipsRows() {
        // 2x2 map. Top row: wall (gid 1), half wall (gid 2).
        // Bottom row: a horizontally flipped wall, then floor.
        String tmx = """
            <map width="2" height="2" tilewidth="24" tileheight="24">
             <tileset firstgid="1">
              <tile id="0"><properties>
               <property name="blocked" type="bool" value="true"/>
              </properties></tile>
              <tile id="1"><properties>
               <property name="blocked" type="bool" value="true"/>
               <property name="halfBlocked" type="bool" value="true"/>
              </properties></tile>
             </tileset>
             <layer name="Walls" width="2" height="2">
              <data encoding="csv">1,2,
            2147483649,0</data>
             </layer>
             <objectgroup name="Spawns">
              <object name="player1" x="36" y="12"><point/></object>
             </objectgroup>
            </map>
            """;

        ArenaDefinition definition = TmxArenaLoader.load(
            "maps/test.tmx",
            files(Map.of("maps/test.tmx", tmx))
        );

        ArenaCollision collision = definition.collision();

        // World y = 1 is the TMX top row.
        assertTrue(collision.overlaps(0.5f, 1.5f, 0.1f, 0.1f));
        // The half wall only blocks the lower half of its tile.
        assertTrue(collision.overlaps(1.5f, 1.2f, 0.1f, 0.1f));
        assertFalse(collision.overlaps(1.5f, 1.8f, 0.1f, 0.1f));
        // Flip flags are ignored when matching tile IDs.
        assertTrue(collision.overlaps(0.5f, 0.5f, 0.1f, 0.1f));
        assertFalse(collision.overlaps(1.5f, 0.5f, 0.1f, 0.1f));

        assertEquals(1.5f, definition.playerSpawns().get(0).x(), EPSILON);
        assertEquals(1.5f, definition.playerSpawns().get(0).y(), EPSILON);
    }

    @Test
    void resolvesTilesetPathsRelativeToTheMap() {
        assertEquals("maps/a.tsx", TmxArenaLoader.resolve("maps/", "a.tsx"));
        assertEquals("tiles/a.tsx", TmxArenaLoader.resolve("maps/", "../tiles/a.tsx"));
        assertEquals("a.tsx", TmxArenaLoader.resolve("", "./a.tsx"));
    }

    @Test
    void reportsAMissingMap() {
        assertThrows(
            IllegalStateException.class,
            () -> TmxArenaLoader.load("maps/missing.tmx", files(Map.of()))
        );
    }

    private static TmxArenaLoader.FileOpener files(Map<String, String> contents) {
        return path -> {
            String text = contents.get(path);

            if (text == null) {
                throw new FileNotFoundException(path);
            }

            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        };
    }
}
