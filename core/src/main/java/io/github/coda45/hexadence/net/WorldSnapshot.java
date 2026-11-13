package io.github.coda45.hexadence.net;

import java.util.List;

import io.github.coda45.hexadence.entity.PlayerColor;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.zone.ZoneKind;

// One simulation tick of world state, as the server sends it to every client.
// Plain data only: no libGDX, no Netty, no behavior. The codec turns this into
// bytes, and the client copies it back into its local ArenaWorld for drawing.
public record WorldSnapshot(
    long tick,
    // Seconds left in a timed match, or NO_TIMER.
    float matchSecondsRemaining,
    // After the clock runs out, how far through the ending the match is,
    // from 0 to 1; NOT_ENDING before that. Clients fade to black with it.
    float matchEndProgress,
    List<PlayerState> players,
    List<DummyState> dummies,
    List<ProjectileState> projectiles,
    List<ZoneState> zones
) {

    public static final float NO_TIMER = -1f;
    public static final float NOT_ENDING = -1f;

    public boolean hasTimer() {
        return matchSecondsRemaining >= 0f;
    }

    public boolean isEnding() {
        return matchEndProgress >= 0f;
    }

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
        PlayerColor color,
        float x,
        float y,
        float aimX,
        float aimY,
        boolean moving,
        float health,
        float respawnRemaining,
        int kills,
        int deaths,
        // Seconds of spawn protection left; 0 when unprotected.
        float spawnShieldRemaining,
        List<SlotState> slots
    ) {
        public PlayerState {
            slots = List.copyOf(slots);
        }
    }

    // One loadout slot: which power it holds and how long until it is ready.
    // Clients need the power to draw the right HUD icon.
    public record SlotState(
        Power power,
        float cooldownRemaining
    ) {
    }

    // Dummies never move, so only health changes between ticks.
    public record DummyState(
        int id,
        float x,
        float y,
        float health
    ) {
    }

    // Direction is authoritative and may change in a Vortex or Singularity.
    public record ProjectileState(
        int id,
        Power power,
        int ownerPlayerId,
        float x,
        float y,
        float directionX,
        float directionY
    ) {
    }

    // The stable ID, not coordinates or list order, is identity: a drifting
    // zone keeps its ID while its position changes between snapshots.
    public record ZoneState(
        int id,
        ZoneKind kind,
        float x,
        float y,
        float radius,
        float health,
        int damageOwnerPlayerId
    ) {
    }
}
