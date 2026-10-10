package io.github.coda45.hexadence.net;

import java.util.List;

import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.spell.SpellType;
import io.github.coda45.hexadence.world.LightningStrikeEvent;

// One simulation tick of world state, as the server sends it to every client.
// Plain data only: no libGDX, no Netty, no behavior. The codec turns this into
// bytes, and the client copies it back into its local ArenaWorld for drawing.
public record WorldSnapshot(
    long tick,
    List<PlayerState> players,
    List<DummyState> dummies,
    List<ProjectileState> projectiles,
    List<ZoneState> zones,
    List<LightningStrikeEvent> events
) {

    public WorldSnapshot {
        // Freeze the lists so a snapshot can be shared between threads safely.
        players = List.copyOf(players);
        dummies = List.copyOf(dummies);
        projectiles = List.copyOf(projectiles);
        zones = List.copyOf(zones);
        events = List.copyOf(events);
    }

    // Everything the renderer and HUD read from a Player.
    public record PlayerState(
        int id,
        float x,
        float y,
        float aimX,
        float aimY,
        boolean moving,
        float health,
        float emberCooldown,
        float waterCooldown,
        float lightningCooldown
    ) {
        public float cooldownFor(SpellType spellType) {
            return switch (spellType) {
                case EMBER -> emberCooldown;
                case WATER -> waterCooldown;
                case LIGHTNING -> lightningCooldown;
            };
        }
    }

    // Dummies never move, so only health changes between ticks.
    public record DummyState(
        float x,
        float y,
        float health
    ) {
    }

    // The direction is fixed at cast time, so clients can draw it as-is.
    public record ProjectileState(
        SpellType spellType,
        float x,
        float y,
        float directionX,
        float directionY
    ) {
    }

    // Zones never move. A zone's identity on the client is (type, x, y).
    public record ZoneState(
        Zone.Type type,
        float x,
        float y,
        boolean electrified
    ) {
    }
}