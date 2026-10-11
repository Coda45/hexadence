package io.github.coda45.hexadence.net;

import io.github.coda45.hexadence.input.PlayerCommand;

final class PlayerCommandValidator {

    private static final float MIN_MOVEMENT = -1f;
    private static final float MAX_MOVEMENT = 1f;

    private PlayerCommandValidator() {
    }

    static boolean isValid(PlayerCommand command) {
        if (command == null) {
            return false;
        }

        if (
            !Float.isFinite(command.moveX())
            || !Float.isFinite(command.moveY())
            || !Float.isFinite(command.aimX())
            || !Float.isFinite(command.aimY())
        ) {
            return false;
        }

        return isValidMovementComponent(command.moveX())
            && isValidMovementComponent(command.moveY());
    }

    private static boolean isValidMovementComponent(float value) {
        return value >= MIN_MOVEMENT
            && value <= MAX_MOVEMENT;
    }
}