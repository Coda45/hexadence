package io.github.coda45.hexadence.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.spell.SpellType;
import io.github.coda45.hexadence.world.ArenaWorld;
import io.github.coda45.hexadence.world.StepResult;

// Server side: copies the live ArenaWorld into an immutable WorldSnapshot.
// It only reads the world, so it cannot change gameplay.
public final class SnapshotFactory {

    private SnapshotFactory() {
    }

    // Call once after each world.step(); StepResult supplies the authoritative
    // tick. Transient events travel separately from replaceable snapshots.
    public static WorldSnapshot capture(ArenaWorld world, StepResult result) {
        List<WorldSnapshot.PlayerState> players = new ArrayList<>();

        for (Map.Entry<Integer, Player> entry : world.getPlayers().entrySet()) {
            Player player = entry.getValue();

            players.add(new WorldSnapshot.PlayerState(
                entry.getKey(),
                player.getColor(),
                player.getX(),
                player.getY(),
                player.getAimX(),
                player.getAimY(),
                player.isMoving(),
                player.getHealth(),
                player.getRespawnRemaining(),
                player.getKills(),
                player.getDeaths(),
                player.getCooldownRemaining(SpellType.EMBER),
                player.getCooldownRemaining(SpellType.WATER),
                player.getCooldownRemaining(SpellType.LIGHTNING)
            ));
        }

        List<WorldSnapshot.DummyState> dummies = new ArrayList<>();

        for (TargetDummy dummy : world.getDummies()) {
            dummies.add(new WorldSnapshot.DummyState(
                dummy.getId(),
                dummy.getX(),
                dummy.getY(),
                dummy.getHealth()
            ));
        }

        List<WorldSnapshot.ProjectileState> projectiles = new ArrayList<>();

        for (Projectile projectile : world.getProjectiles()) {
            projectiles.add(new WorldSnapshot.ProjectileState(
                projectile.getId(),
                projectile.getSpellType(),
                projectile.getOwnerPlayerId(),
                projectile.getX(),
                projectile.getY(),
                projectile.getDirectionX(),
                projectile.getDirectionY()
            ));
        }

        List<WorldSnapshot.ZoneState> zones = new ArrayList<>();

        for (Zone zone : world.getZones()) {
            zones.add(new WorldSnapshot.ZoneState(
                zone.getId(),
                zone.getType(),
                zone.getX(),
                zone.getY(),
                zone.isElectrified(),
                zone.getDamageOwnerPlayerId()
            ));
        }

        return new WorldSnapshot(
            result.tick(),
            players,
            dummies,
            projectiles,
            zones
        );
    }
}
