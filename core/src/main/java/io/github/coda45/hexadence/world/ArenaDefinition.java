package io.github.coda45.hexadence.world;

import java.util.List;
import java.util.Objects;

// Plain simulation data needed to construct an arena.
// This class knows nothing about TMX files, textures, cameras, or rendering.
public record ArenaDefinition(
    ArenaCollision collision,
    SpawnPoint playerSpawn,
    List<SpawnPoint> dummySpawns
) {
    public ArenaDefinition {
        collision = Objects.requireNonNull(collision);
        playerSpawn = Objects.requireNonNull(playerSpawn);
        dummySpawns = List.copyOf(dummySpawns);
    }
}