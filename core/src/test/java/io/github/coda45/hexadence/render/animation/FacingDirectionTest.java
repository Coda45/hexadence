package io.github.coda45.hexadence.render.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class FacingDirectionTest {

    // Aim vector for an angle in degrees (0 = east, 90 = north, y up).
    private static FacingDirection at(double degrees) {
        double radians = Math.toRadians(degrees);
        return FacingDirection.fromAim(
            (float) Math.cos(radians),
            (float) Math.sin(radians)
        );
    }

    @Test
    void pickCardinalDirectionsFromAxisAlignedAim() {
        assertEquals(FacingDirection.EAST, FacingDirection.fromAim(1f, 0f));
        assertEquals(FacingDirection.NORTH, FacingDirection.fromAim(0f, 1f));
        assertEquals(FacingDirection.WEST, FacingDirection.fromAim(-1f, 0f));
        assertEquals(FacingDirection.SOUTH, FacingDirection.fromAim(0f, -1f));
    }

    @Test
    void pickDiagonalDirectionsFromAngledAim() {
        assertEquals(FacingDirection.NORTHEAST, at(45));
        assertEquals(FacingDirection.NORTHWEST, at(135));
        assertEquals(FacingDirection.SOUTHWEST, at(225));
        assertEquals(FacingDirection.SOUTHEAST, at(315));
    }

    @Test
    void eachSectorCoversPlusOrMinusTwentyDegreesOfItsCentre() {
        FacingDirection[] centres = {
            FacingDirection.EAST, FacingDirection.NORTHEAST,
            FacingDirection.NORTH, FacingDirection.NORTHWEST,
            FacingDirection.WEST, FacingDirection.SOUTHWEST,
            FacingDirection.SOUTH, FacingDirection.SOUTHEAST
        };

        for (int i = 0; i < centres.length; i++) {
            double centre = i * 45.0;

            assertEquals(centres[i], at(centre - 20), "centre " + centre + " -20");
            assertEquals(centres[i], at(centre), "centre " + centre);
            assertEquals(centres[i], at(centre + 20), "centre " + centre + " +20");
        }
    }

    @Test
    void switchesSectorAtTwentyTwoPointFiveDegrees() {
        assertEquals(FacingDirection.EAST, at(22));
        assertEquals(FacingDirection.NORTHEAST, at(23));
        assertEquals(FacingDirection.NORTHEAST, at(67));
        assertEquals(FacingDirection.NORTH, at(68));
        assertEquals(FacingDirection.EAST, at(338));
        assertEquals(FacingDirection.SOUTHEAST, at(337));
    }

    @Test
    void defaultsToNorthWhenAimIsZero() {
        assertEquals(FacingDirection.NORTH, FacingDirection.fromAim(0f, 0f));
    }
}
