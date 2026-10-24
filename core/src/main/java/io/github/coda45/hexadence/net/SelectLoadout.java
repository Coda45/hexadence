package io.github.coda45.hexadence.net;

import java.util.Objects;

import io.github.coda45.hexadence.power.Loadout;

// Client to server, in the lobby: "these are my three powers", and whether
// the player has locked them in. Sending it again with locked = false unlocks,
// so the player can change their mind until the match starts. Ignored once
// the match has started.
public record SelectLoadout(Loadout loadout, boolean locked) {

    public SelectLoadout {
        Objects.requireNonNull(loadout);
    }
}
