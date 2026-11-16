package io.github.coda45.hexadence.world;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class StormEyeTest {

    private static final float EPSILON = 0.0001f;

    // Gradle runs core's tests from the core folder.
    private static final Path ASSETS = Path.of("..", "assets");

    @Test
    void pillarsAreBlockedTilesNotJoinedToTheOuterWall() {
        float[][] blockedHeights = walledGrid(12, 12);

        // Free-standing: a single tile and a 2x1 half-height block.
        blockedHeights[3][3] = 1f;
        blockedHeights[7][8] = 0.5f;
        blockedHeights[8][8] = 0.5f;

        // Joined to the wall, the second one only diagonally.
        blockedHeights[1][5] = 1f;
        blockedHeights[10][10] = 1f;

        boolean[][] pillar =
            new ArenaCollision(blockedHeights).findPillarTiles();

        assertTrue(pillar[3][3]);
        assertTrue(pillar[7][8]);
        assertTrue(pillar[8][8]);

        assertFalse(pillar[0][0]);
        assertFalse(pillar[1][5]);
        assertFalse(pillar[10][10]);
        assertFalse(pillar[5][5]);
    }

    @Test
    void eyeCoversThreeQuartersOfTheFloorBetweenThePillars() {
        float[][] blockedHeights = walledGrid(14, 12);

        // Inner faces at x = 4 and 10, y = 3 and 9: a 6x6 open square.
        blockedHeights[3][2] = 1f;
        blockedHeights[10][2] = 1f;
        blockedHeights[3][9] = 1f;
        blockedHeights[10][9] = 1f;

        StormEye eye = StormEye.locate(
            new ArenaCollision(blockedHeights)
        );

        assertEquals(7f, eye.centerX(), EPSILON);
        assertEquals(6f, eye.centerY(), EPSILON);

        assertEquals(
            StormEye.EYE_AREA_FRACTION * 36f,
            (float) (Math.PI * eye.radius() * eye.radius()),
            0.001f
        );

        assertTrue(eye.contains(7f, 6f));
        assertTrue(eye.contains(7f + eye.radius() - 0.01f, 6f));
        assertFalse(eye.contains(7f + eye.radius() + 0.01f, 6f));
    }

    @Test
    void mapWithoutPillarsHasNoEye() {
        StormEye eye = StormEye.locate(
            new ArenaCollision(walledGrid(12, 12))
        );

        assertEquals(6f, eye.centerX(), EPSILON);
        assertEquals(6f, eye.centerY(), EPSILON);
        assertEquals(0f, eye.radius(), EPSILON);
        assertFalse(eye.contains(6f, 6f));

        // Pillars on one side only do not make one either.
        float[][] blockedHeights = walledGrid(12, 12);
        blockedHeights[3][3] = 1f;
        blockedHeights[3][8] = 1f;

        assertEquals(
            0f,
            StormEye.locate(new ArenaCollision(blockedHeights)).radius(),
            EPSILON
        );
    }

    @Test
    void multiplayerMapEyeSitsBetweenItsFourPillars() {
        ArenaDefinition definition = TmxArenaLoader.load(
            ArenaMaps.MULTIPLAYER,
            path -> Files.newInputStream(ASSETS.resolve(path))
        );

        StormEye eye = StormEye.locate(definition.collision());

        assertEquals(15f, eye.centerX(), EPSILON);
        assertEquals(9f, eye.centerY(), EPSILON);

        // 75% of the 8x6 floor between the inner faces.
        assertEquals((float) Math.sqrt(36.0 / Math.PI), eye.radius(), EPSILON);
    }

    private static float[][] walledGrid(int width, int height) {
        float[][] blockedHeights = new float[width][height];

        for (int x = 0; x < width; x++) {
            blockedHeights[x][0] = 1f;
            blockedHeights[x][height - 1] = 1f;
        }

        for (int y = 0; y < height; y++) {
            blockedHeights[0][y] = 1f;
            blockedHeights[width - 1][y] = 1f;
        }

        return blockedHeights;
    }
}
