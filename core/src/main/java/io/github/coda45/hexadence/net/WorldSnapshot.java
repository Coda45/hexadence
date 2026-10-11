package io.github.coda45.hexadence.net;

import java.util.List;

import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.spell.SpellType;

// One simulation tick of world state, as the server sends it to every client.
// Plain data only: no libGDX, no Netty, no behavior. The codec turns this into
// bytes, and the client copies it back into its local ArenaWorld for drawing.
public record WorldSnapshot(
    long tick,
    List<PlayerState> players,
    List<DummyState> dummies,
    List<ProjectileState> projectiles,
    List<ZoneState> zones
) {

    public WorldSnapshot {
        // Freeze the lists so a snapshot can be shared between threads safely.
        players = List.copyOf(players);
        dummies = List.copyOf(dummies);
        projectiles = List.copyOf(projectiles);
        zones = List.copyOf(zones);
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
        float respawnRemaining,
        int kills,
        int deaths,
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
        int id,
        float x,
        float y,
        float health
    ) {
    }

    // The direction is fixed at cast time, so clients can draw it as-is.
    public record ProjectileState(
        int id,
        SpellType spellType,
        int ownerPlayerId,
        float x,
        float y,
        float directionX,
        float directionY
    ) {
    }

    // Zones never move. The stable ID, not coordinates or list order, is identity.
    public record ZoneState(
        int id,
        Zone.Type type,
        float x,
        float y,
        boolean electrified,
        int damageOwnerPlayerId
    ) {
    }
}
