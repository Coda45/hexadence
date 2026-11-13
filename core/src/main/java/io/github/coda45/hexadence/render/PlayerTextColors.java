package io.github.coda45.hexadence.render;

import com.badlogic.gdx.graphics.Color;

import io.github.coda45.hexadence.entity.PlayerColor;

// Text colors for naming players on dark UI (kill feed, results screen).
// Each matches that player's robe color but stays readable.
public final class PlayerTextColors {

    private PlayerTextColors() {
    }

    public static Color of(PlayerColor color) {
        if (color == null) {
            return Color.WHITE;
        }

        return switch (color) {
            case CYAN -> Color.CYAN;
            case RED -> Color.SALMON;
            case GREEN -> Color.LIME;
            case GOLD -> Color.GOLD;
        };
    }
}
