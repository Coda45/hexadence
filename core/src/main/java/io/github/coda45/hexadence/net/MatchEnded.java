package io.github.coda45.hexadence.net;

import java.util.List;
import java.util.Objects;

import io.github.coda45.hexadence.entity.PlayerColor;

// Server to every client in a room: the match timer ran out. Carries each
// player's final stats for the results screen.
//
// Right after this the room is back in its lobby (players can draft again
// and the host can start a rematch), unless the host has left, in which case
// RoomClosed follows and the room is gone.
public record MatchEnded(List<PlayerResult> results) {

    public MatchEnded {
        results = List.copyOf(results);
    }

    public record PlayerResult(
        int playerId,
        PlayerColor color,
        int kills,
        int deaths,
        float damageDealt
    ) {
        public PlayerResult {
            Objects.requireNonNull(color);
        }
    }
}
