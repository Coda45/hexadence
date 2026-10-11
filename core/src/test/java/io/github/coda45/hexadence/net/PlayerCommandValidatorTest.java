package io.github.coda45.hexadence.net;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.github.coda45.hexadence.input.PlayerCommand;

class PlayerCommandValidatorTest {

    @Test
    void acceptsNormalCommands() {
        assertAll(
            () -> assertTrue(
                PlayerCommandValidator.isValid(
                    PlayerCommand.IDLE
                )
            ),
            () -> assertTrue(
                PlayerCommandValidator.isValid(
                    new PlayerCommand(
                        1f,
                        -1f,
                        12.5f,
                        -7.25f,
                        true,
                        false,
                        true
                    )
                )
            )
        );
    }

    @Test
    void rejectsNonFiniteValues() {
        assertAll(
            () -> assertFalse(validatesWithMoveX(Float.NaN)),
            () -> assertFalse(validatesWithMoveX(Float.POSITIVE_INFINITY)),
            () -> assertFalse(validatesWithMoveY(Float.NEGATIVE_INFINITY)),
            () -> assertFalse(validatesWithAimX(Float.NaN)),
            () -> assertFalse(validatesWithAimY(Float.POSITIVE_INFINITY))
        );
    }

    @Test
    void rejectsMovementOutsideInputRange() {
        assertAll(
            () -> assertFalse(validatesWithMoveX(1.01f)),
            () -> assertFalse(validatesWithMoveX(-1.01f)),
            () -> assertFalse(validatesWithMoveY(1.01f)),
            () -> assertFalse(validatesWithMoveY(-1.01f))
        );
    }

    private static boolean validatesWithMoveX(float value) {
        return PlayerCommandValidator.isValid(
            new PlayerCommand(
                value, 0f,
                1f, 0f,
                false, false, false
            )
        );
    }

    private static boolean validatesWithMoveY(float value) {
        return PlayerCommandValidator.isValid(
            new PlayerCommand(
                0f, value,
                1f, 0f,
                false, false, false
            )
        );
    }

    private static boolean validatesWithAimX(float value) {
        return PlayerCommandValidator.isValid(
            new PlayerCommand(
                0f, 0f,
                value, 0f,
                false, false, false
            )
        );
    }

    private static boolean validatesWithAimY(float value) {
        return PlayerCommandValidator.isValid(
            new PlayerCommand(
                0f, 0f,
                1f, value,
                false, false, false
            )
        );
    }
}