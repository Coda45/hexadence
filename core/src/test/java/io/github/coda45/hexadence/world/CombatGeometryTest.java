package io.github.coda45.hexadence.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CombatGeometryTest {

    private static final float EPSILON = 0.0001f;

    @Test
    void rayReturnsDistanceToNearEdgeOfCircle() {
        float distance =
            CombatGeometry.rayCircleEntryDistance(
                0f, 0f,
                1f, 0f,
                10f,
                5f, 0f,
                1f
            );

        assertEquals(4f, distance, EPSILON);
    }

    @Test
    void rayMissesCircleOutsideItsPath() {
        float distance =
            CombatGeometry.rayCircleEntryDistance(
                0f, 0f,
                1f, 0f,
                10f,
                5f, 2f,
                1f
            );

        assertTrue(Float.isInfinite(distance));
    }

    @Test
    void rayStartingInsideCircleHitsImmediately() {
        float distance =
            CombatGeometry.rayCircleEntryDistance(
                0f, 0f,
                1f, 0f,
                10f,
                0.5f, 0f,
                1f
            );

        assertEquals(0f, distance, EPSILON);
    }

    @Test
    void rayIgnoresCircleBeyondMaximumRange() {
        float distance =
            CombatGeometry.rayCircleEntryDistance(
                0f, 0f,
                1f, 0f,
                4f,
                6f, 0f,
                1f
            );

        assertTrue(Float.isInfinite(distance));
    }

    @Test
    void rayIgnoresCircleBehindOrigin() {
        float distance =
            CombatGeometry.rayCircleEntryDistance(
                0f, 0f,
                1f, 0f,
                10f,
                -3f, 0f,
                1f
            );

        assertTrue(Float.isInfinite(distance));
    }
    @Test
    void rayReturnsDistanceToNearEdgeOfRectangle() {
        float distance =
            CombatGeometry.rayAabbEntryDistance(
                0f, 1.5f,
                1f, 0f,
                10f,
                3f, 1f,
                4f, 2f
            );

        assertEquals(3f, distance, EPSILON);
    }

    @Test
    void rayPassesAboveHalfHeightRectangle() {
        float distance =
            CombatGeometry.rayAabbEntryDistance(
                0f, 1.75f,
                1f, 0f,
                10f,
                3f, 1f,
                4f, 1.5f
            );

        assertTrue(Float.isInfinite(distance));
    }

    @Test
    void rayHitsBottomHalfOfHalfHeightRectangle() {
        float distance =
            CombatGeometry.rayAabbEntryDistance(
                0f, 1.25f,
                1f, 0f,
                10f,
                3f, 1f,
                4f, 1.5f
            );

        assertEquals(3f, distance, EPSILON);
    }

    @Test
    void diagonalRayReturnsNormalizedTravelDistance() {
        float diagonalComponent =
            (float) Math.sqrt(0.5f);

        float distance =
            CombatGeometry.rayAabbEntryDistance(
                0f, 0f,
                diagonalComponent,
                diagonalComponent,
                10f,
                2f, 2f,
                3f, 3f
            );

        assertEquals(
            (float) Math.sqrt(8f),
            distance,
            EPSILON
        );
    }

    @Test
    void rayMissesRectangleOutsideItsPath() {
        float distance =
            CombatGeometry.rayAabbEntryDistance(
                0f, 0f,
                1f, 0f,
                10f,
                3f, 1f,
                4f, 2f
            );

        assertTrue(Float.isInfinite(distance));
    }

    @Test
    void rayIgnoresRectangleBeyondMaximumRange() {
        float distance =
            CombatGeometry.rayAabbEntryDistance(
                0f, 1.5f,
                1f, 0f,
                2.9f,
                3f, 1f,
                4f, 2f
            );

        assertTrue(Float.isInfinite(distance));
    }
}