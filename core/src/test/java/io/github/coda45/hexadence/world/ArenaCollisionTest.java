package io.github.coda45.hexadence.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ArenaCollisionTest {

    private static final float EPSILON = 0.0001f;

    @Test
    void fullBlockedTileBlocksItsEntireHeight() {
        float[][] blockedHeights = new float[4][4];

        // Collision grids are indexed [x][y].
        blockedHeights[1][1] = 1f;

        ArenaCollision collision = new ArenaCollision(blockedHeights);

        boolean overlaps = collision.overlaps(
            1.5f,
            1.75f,
            0.1f,
            0.1f
        );

        assertTrue(overlaps);
    }

    @Test
    void halfBlockedTileBlocksBottomButAllowsTop() {
        float[][] blockedHeights = new float[4][4];
        blockedHeights[1][1] = 0.5f;

        ArenaCollision collision = new ArenaCollision(blockedHeights);

        boolean overlapsBottom = collision.overlaps(
            1.5f,
            1.25f,
            0.1f,
            0.1f
        );

        boolean overlapsTop = collision.overlaps(
            1.5f,
            1.75f,
            0.1f,
            0.1f
        );

        assertTrue(overlapsBottom);
        assertFalse(overlapsTop);
    }

    @Test
    void blockedHorizontalMovementStillAllowsVerticalSliding() {
        float[][] blockedHeights = new float[4][4];
        blockedHeights[2][1] = 1f;

        ArenaCollision collision = new ArenaCollision(blockedHeights);

        float[] resolvedMovement = collision.resolveMovement(
            1.5f,
            1.5f,
            0.5f,
            0.25f,
            0.2f,
            0.2f
        );

        assertEquals(
            0f,
            resolvedMovement[0],
            EPSILON
        );

        assertEquals(
            0.25f,
            resolvedMovement[1],
            EPSILON
        );
    }

    @Test
    void terrainRaycastReturnsNearestWallDistance() {
        float[][] blockedHeights = new float[8][4];

        blockedHeights[3][1] = 1f;
        blockedHeights[5][1] = 1f;

        ArenaCollision collision =
            new ArenaCollision(blockedHeights);

        float distance = collision.raycastTerrain(
            1.5f, 1.5f,
            1f, 0f,
            6f,
            0.1f
        );

        // The first wall begins at x = 3, expanded left to 2.9.
        assertEquals(1.4f, distance, EPSILON);
    }

    @Test
    void terrainRaycastPassesAboveHalfBlockedTile() {
        float[][] blockedHeights = new float[6][4];
        blockedHeights[3][1] = 0.5f;

        ArenaCollision collision =
            new ArenaCollision(blockedHeights);

        float distance = collision.raycastTerrain(
            1.5f, 1.75f,
            1f, 0f,
            3f,
            0.1f
        );

        assertTrue(Float.isInfinite(distance));
    }

    @Test
    void terrainRaycastAccountsForBeamThickness() {
        float[][] blockedHeights = new float[6][4];
        blockedHeights[3][1] = 1f;

        ArenaCollision collision =
            new ArenaCollision(blockedHeights);

        float distance = collision.raycastTerrain(
            1.5f, 2.05f,
            1f, 0f,
            3f,
            0.1f
        );

        // The center ray is above the wall, but the beam edge touches it.
        assertEquals(1.4f, distance, EPSILON);
    }

    @Test
    void terrainRaycastTreatsOutsideMapAsBlocked() {
        float[][] blockedHeights = new float[4][4];

        ArenaCollision collision =
            new ArenaCollision(blockedHeights);

        float distance = collision.raycastTerrain(
            2.5f, 1.5f,
            1f, 0f,
            10f,
            0f
        );

        // The map ends at x = 4.
        assertEquals(1.5f, distance, EPSILON);
    }

    @Test
    void terrainRaycastReturnsNoHitWhenPathIsClear() {
        float[][] blockedHeights = new float[8][4];

        ArenaCollision collision =
            new ArenaCollision(blockedHeights);

        float distance = collision.raycastTerrain(
            1.5f, 1.5f,
            1f, 0f,
            3f,
            0.1f
        );

        assertTrue(Float.isInfinite(distance));
    }
}