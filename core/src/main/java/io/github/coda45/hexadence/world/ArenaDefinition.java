package io.github.coda45.hexadence.world;

import java.util.List;
import java.util.Objects;

// Plain simulation data needed to construct an arena.
// This class knows nothing about TMX files, textures, cameras, or rendering.
public record ArenaDefinition(
    ArenaCollision collision,
    List<SpawnPoint> playerSpawns,
    List<SpawnPoint> dummySpawns
) {
    public ArenaDefinition {
        collision = Objects.requireNonNull(collision);
        playerSpawns = List.copyOf(playerSpawns);
        dummySpawns = List.copyOf(dummySpawns);

        if (playerSpawns.isEmpty()) {
            throw new IllegalArgumentException(
                "An arena needs at least one player spawn"
            );
        }
    }

    // Convenience constructor for existing single-player tests.
    public ArenaDefinition(
        ArenaCollision collision,
        SpawnPoint playerSpawn,
        List<SpawnPoint> dummySpawns
    ) {
        this(
            collision,
            List.of(playerSpawn),
            dummySpawns
        );
    }
}