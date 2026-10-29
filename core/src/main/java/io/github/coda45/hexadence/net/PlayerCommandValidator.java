package io.github.coda45.hexadence.net;

import java.util.HashSet;
import java.util.Set;

import io.github.coda45.hexadence.input.CastRequest;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.power.Loadout;

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

        if (
            !isValidMovementComponent(command.moveX())
            || !isValidMovementComponent(command.moveY())
            || command.casts().size() > Loadout.SIZE
        ) {
            return false;
        }

        Set<Integer> slots = new HashSet<>();

        for (CastRequest cast : command.casts()) {
            if (
                cast == null
                || cast.slot() < 0
                || cast.slot() >= Loadout.SIZE
                || !slots.add(cast.slot())
                || !Float.isFinite(cast.targetOffsetX())
                || !Float.isFinite(cast.targetOffsetY())
            ) {
                return false;
            }
        }

        return true;
    }

    private static boolean isValidMovementComponent(float value) {
        return value >= MIN_MOVEMENT
            && value <= MAX_MOVEMENT;
    }
}
