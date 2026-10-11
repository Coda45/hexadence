package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.net.WorldSnapshot;
import io.github.coda45.hexadence.spell.SpellType;

// Owns gameplay state and applies commands at a fixed 60 Hz rate.
public class ArenaWorld {

    private static final float PLAYER_SPEED = 5f;
    private static final float PLAYER_RESPAWN_DELAY = 3f;
    public static final float STEP = 1f / 60f;
    // Temporary combat values for the target dummy test.
    private static final float PROJECTILE_RADIUS = 0.12f;
    private static final float MAX_PROJECTILE_SUBSTEP = PROJECTILE_RADIUS;
    private static final float DUMMY_RESPAWN_DELAY = 2f;
    private static final float DISCHARGE_DAMAGE = 20f;
    private static final float LIGHTNING_HALF_WIDTH = 0.1f;
    private long tick;

    private final ArenaCollision collision;
    private final List<SpawnPoint> playerSpawns;
    private final Map<Integer, SpawnPoint> assignedPlayerSpawns = new LinkedHashMap<>();
    //Keyed by player id. The player created by the constructor id is 0.
    private final Map<Integer, Player> players = new LinkedHashMap<>();
    private int nextPlayerId = 1;
    private final ArrayList<Projectile> projectiles = new ArrayList<>();
    private final List<TargetDummy> dummies = new ArrayList<>();

    private final List<Zone> zones = new ArrayList<>();

    public ArenaWorld(ArenaDefinition definition) {
        collision = definition.collision();
        playerSpawns = definition.playerSpawns();

        addPlayerAtAvailableSpawn(0);

        for (SpawnPoint dummySpawn : definition.dummySpawns()) {
            addDummyAtSpawn(dummySpawn);
        }
    }

    //Single-player entry point: the one commad drives player 0.
    public StepResult step(PlayerCommand command) {
        return step(Map.of(0, command));
    }

    // Advances exactly one STEP, regardless of the render frame rate.
    //A player with no entry in the map this step is treated as idle.
    public StepResult step(Map<Integer, PlayerCommand> commands) {
        tick++;

        List<WorldEvent> events = new ArrayList<>();

        Iterator<Zone> zoneIterator = zones.iterator();

        while (zoneIterator.hasNext()) {
            Zone zone = zoneIterator.next();

            zone.update(STEP);

            if (zone.hasExpired()) {
                zoneIterator.remove();
            }
        }

        // Aim, cooldowns and casts happen before anyone moves.
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            applyAimAndCasts(
                entry.getKey(),
                entry.getValue(),
                commandFor(commands, entry.getKey()),
                events
            );
        }

        // Move and remove projectiles through one iterator so list changes stay safe.
        Iterator<Projectile> projectileIterator =
            projectiles.iterator();

        while (projectileIterator.hasNext()) {
            Projectile projectile =
                projectileIterator.next();

            SpellType spellType =
                projectile.getSpellType();

            float remainingRange = Math.max(
                0f,
                spellType.getRange()
                    - projectile.getDistanceTraveled()
            );

            float movementDistance = Math.min(
                spellType.getSpeed() * STEP,
                remainingRange
            );

            int substepCount = Math.max(
                1,
                (int) Math.ceil(
                    movementDistance
                        / MAX_PROJECTILE_SUBSTEP
                )
            );

            float substepDistance =
                movementDistance / substepCount;

            boolean terminated = false;

            for (int step = 0; step < substepCount; step++) {
                float previousX = projectile.getX();
                float previousY = projectile.getY();

                projectile.update(substepDistance);

                // Terrain takes priority at every substep.
                if (collision.overlaps(
                        projectile.getX(),
                        projectile.getY(),
                        PROJECTILE_RADIUS,
                        PROJECTILE_RADIUS
                    )) {

                    // Terrain effects use the last clear position,
                    // not a position already penetrating the wall.
                    createPuddleIfWater(
                        projectile,
                        previousX,
                        previousY
                    );

                    terminated = true;
                    break;
                }

                // Zone reactions take priority over dummy hits.
                if (applyProjectileZoneReaction(projectile)) {
                    terminated = true;
                    break;
                }

                if (applyProjectilePlayerHit(projectile)) {
                    createPuddleIfWater(
                        projectile,
                        projectile.getX(),
                        projectile.getY()
                    );

                    terminated = true;
                    break;
                }

                if (applyProjectileDummyHit(projectile)) {
                    createPuddleIfWater(
                        projectile,
                        projectile.getX(),
                        projectile.getY()
                    );

                    terminated = true;
                    break;
                }
            }

            if (terminated) {
                projectileIterator.remove();
                continue;
            }

            if (
                projectile.hasReachedMaxRange(
                    spellType.getRange()
                )
            ) {
                createPuddleIfWater(
                    projectile,
                    projectile.getX(),
                    projectile.getY()
                );

                projectileIterator.remove();
            }
        }

    
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            movePlayer(
                entry.getValue(),
                commandFor(commands, entry.getKey())
            );
        }
 
        applyChargedPuddleDischarges();
        updatePlayerRespawns();
        updateDummyRespawns();
        return new StepResult(tick, events);
    }
        
    private static PlayerCommand commandFor(
        Map<Integer, PlayerCommand> commands,
        int playerId
    ) {
        PlayerCommand command = commands.get(playerId);
 
        return command != null ? command : PlayerCommand.IDLE;
    }

    private void applyAimAndCasts(
        int playerId,
        Player player,
        PlayerCommand command,
        List<WorldEvent> events
    ) {
        float aimX = command.aimX();
        float aimY = command.aimY();

        float aimLengthSquared = (aimX * aimX) + (aimY * aimY);

        // Keep the last direction when the cursor is too close to the cast point.
        if (player.isAlive() && aimLengthSquared > 0.0001f) {
            float aimLength = (float) Math.sqrt(aimLengthSquared);

            player.setAim(
                aimX / aimLength,
                aimY / aimLength
            );
        }

        player.updateCooldowns(STEP);

        // Spawn before player movement, using the aim chosen for this step.
        tryCast(playerId, player, command.castEmber(), SpellType.EMBER);
        tryCast(playerId, player, command.castWater(), SpellType.WATER);
        castLightning(playerId, player, command.castLightning(), events);
    }

    private void movePlayer(Player player, PlayerCommand command) {
        if (!player.isAlive()) {
            return;
        }

        float moveX = command.moveX();
        float moveY = command.moveY();

        float lengthSquared =
            moveX * moveX +
            moveY * moveY;

        // Cap diagonal input so it cannot move faster than a single direction.
        if (lengthSquared > 1f) {
            float length = (float) Math.sqrt(lengthSquared);

            moveX /= length;
            moveY /= length;
        }

        float dx = moveX * PLAYER_SPEED * STEP;
        float dy = moveY * PLAYER_SPEED * STEP;

        float[] resolved =
                collision.resolveMovement(
                    player.getCollisionX(),
                    player.getCollisionY(),
                    dx,
                    dy,
                    Player.HALF_WIDTH,
                    Player.HALF_HEIGHT
                );

            player.move(resolved[0], resolved[1]);
    }

    // Adds a player at the arena spawn and returns its id.
    public int addPlayer() {
        int id = nextPlayerId++;

        addPlayerAtAvailableSpawn(id);

        return id;
    }
 
    public void removePlayer(int id) {
        players.remove(id);
        assignedPlayerSpawns.remove(id);
    }
 
    // Read-only view, in join order; the Player objects are live.
    public Map<Integer, Player> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    private void createPuddleIfWater(
        Projectile projectile,
        float x,
        float y
    ) {
        if (
            projectile.getSpellType()
                != SpellType.WATER
        ) {
            return;
        }

        zones.add(
            Zone.createPuddle(x, y, projectile.getOwnerPlayerId()));
    }

    public List<Zone> getZones() {
        return zones;
    }

    private boolean applyProjectileZoneReaction(
        Projectile projectile
    ) {
        SpellType spellType = projectile.getSpellType();

        if (
            spellType != SpellType.EMBER
            && spellType != SpellType.LIGHTNING
        ) {
            return false;
        }

        for (int i = 0; i < zones.size(); i++) {
            Zone zone = zones.get(i);

            if (zone.getType() != Zone.Type.PUDDLE) {
                continue;
            }

            float dx = projectile.getX() - zone.getX();
            float dy = projectile.getY() - zone.getY();
            float interactionDistance =
                PROJECTILE_RADIUS + zone.getRadius();

            boolean overlaps =
                dx * dx + dy * dy
                    <= interactionDistance * interactionDistance;

            if (!overlaps) {
                continue;
            }

            if (spellType == SpellType.EMBER) {
                zones.set(
                    i,
                    Zone.createSteam(
                        zone.getX(),
                        zone.getY(),
                        projectile.getOwnerPlayerId()
                    )
                );

                return true;
            }

            if (
                spellType == SpellType.LIGHTNING
                && !zone.isElectrified()
            ) {
                zone.electrify();
                return true;
            }
        }

        return false;
    }

    private boolean applyProjectileDummyHit(
        Projectile projectile
    ) {
        SpellType spellType = projectile.getSpellType();

        for (TargetDummy dummy : dummies) {
            if (!dummy.isAlive()) {
                continue;
            }

            float dx = projectile.getX() - dummy.getX();
            float dy = projectile.getY() - dummy.getY();

            float hitDistance = PROJECTILE_RADIUS + TargetDummy.RADIUS;

            boolean overlaps = dx * dx + dy * dy <= hitDistance * hitDistance;

            if (!overlaps) {
                continue;
            }

            dummy.takeDamage(
                spellType.getDamage()
            );

            // A projectile damages only the first
            // live dummy it overlaps.
            return true;
        }

        return false;
    }

    private LightningHit findLightningHit(
        int ownerPlayerId,
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance
    ) {
        LightningHit nearest = LightningHit.none();

        float terrainDistance =
            collision.raycastTerrain(
                originX,
                originY,
                directionX,
                directionY,
                maxDistance,
                LIGHTNING_HALF_WIDTH
            );

        if (!Float.isInfinite(terrainDistance)) {
            nearest = LightningHit.terrain(terrainDistance);
        }

        for (Zone zone : zones) {
            if (zone.getType() != Zone.Type.PUDDLE) {
                continue;
            }

            float distance =
                CombatGeometry.rayCircleEntryDistance(
                    originX,
                    originY,
                    directionX,
                    directionY,
                    maxDistance,
                    zone.getX(),
                    zone.getY(),
                    zone.getRadius() + LIGHTNING_HALF_WIDTH
                );

            if (Float.isInfinite(distance)) {
                continue;
            }

            LightningHit candidate =
                LightningHit.puddle(distance, zone);

            if (candidate.isCloserThan(nearest)) {
                nearest = candidate;
            }
        }

        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            if (entry.getKey() == ownerPlayerId) {
                continue;
            }

            Player target = entry.getValue();

            if (!target.isAlive()) {
                continue;
            }

            float centerX = target.getCombatCenterX();
            float centerY = target.getCombatCenterY();

            float distance =
                CombatGeometry.rayAabbEntryDistance(
                    originX,
                    originY,
                    directionX,
                    directionY,
                    maxDistance,
                    centerX
                        - Player.COMBAT_HALF_WIDTH
                        - LIGHTNING_HALF_WIDTH,
                    centerY
                        - Player.COMBAT_HALF_HEIGHT
                        - LIGHTNING_HALF_WIDTH,
                    centerX
                        + Player.COMBAT_HALF_WIDTH
                        + LIGHTNING_HALF_WIDTH,
                    centerY
                        + Player.COMBAT_HALF_HEIGHT
                        + LIGHTNING_HALF_WIDTH
                );

            if (Float.isInfinite(distance)) {
                continue;
            }

            LightningHit candidate = LightningHit.player(distance, entry.getKey(), target);

            if (candidate.isCloserThan(nearest)) {
                nearest = candidate;
            }
        }

        for (TargetDummy dummy : dummies) {
            if (!dummy.isAlive()) {
                continue;
            }

            float distance =
                CombatGeometry.rayCircleEntryDistance(
                    originX,
                    originY,
                    directionX,
                    directionY,
                    maxDistance,
                    dummy.getX(),
                    dummy.getY(),
                    TargetDummy.RADIUS + LIGHTNING_HALF_WIDTH
                );

            if (Float.isInfinite(distance)) {
                continue;
            }

            LightningHit candidate =
                LightningHit.dummy(distance, dummy);

            if (candidate.isCloserThan(nearest)) {
                nearest = candidate;
            }
        }

        return nearest;
    }

    private void applyChargedPuddleDischarges() {
        for (Zone zone : zones) {
            if (
                zone.getType() != Zone.Type.PUDDLE
                || !zone.isElectrified()
            ) {
                continue;
            }

            boolean discharged = false;

            for (Map.Entry<Integer, Player> entry : players.entrySet()) {
                Player target = entry.getValue();

                if (
                    target.isAlive()
                    && zoneOverlapsPlayer(zone, target)
                ) {
                    damagePlayer(
                        entry.getKey(),
                        DISCHARGE_DAMAGE,
                        zone.getDamageOwnerPlayerId()
                    );

                    discharged = true;
                }
            }
            
            for (TargetDummy dummy : dummies) {
                if (
                    dummy.isAlive()
                    && zoneOverlapsDummy(zone, dummy)
                ) {
                    dummy.takeDamage(DISCHARGE_DAMAGE);
                    discharged = true;
                }
            }

            if (discharged) {
                zone.discharge();
            }
        }
    }

    private boolean zoneOverlapsDummy(
        Zone zone,
        TargetDummy dummy
    ) {
        float dx = zone.getX() - dummy.getX();
        float dy = zone.getY() - dummy.getY();
        float overlapDistance =
            zone.getRadius() + TargetDummy.RADIUS;

        return dx * dx + dy * dy
            <= overlapDistance * overlapDistance;
    }

    private boolean zoneOverlapsPlayer(Zone zone, Player player) {
        float collisionX = player.getCollisionX();
        float collisionY = player.getCollisionY();

        float playerLeft =
            collisionX - Player.HALF_WIDTH;

        float playerRight =
            collisionX + Player.HALF_WIDTH;

        float playerBottom =
            collisionY - Player.HALF_HEIGHT;

        float playerTop =
            collisionY + Player.HALF_HEIGHT;

        float closestX = Math.max(
            playerLeft,
            Math.min(zone.getX(), playerRight)
        );

        float closestY = Math.max(
            playerBottom,
            Math.min(zone.getY(), playerTop)
        );

        float dx = zone.getX() - closestX;
        float dy = zone.getY() - closestY;

        return dx * dx + dy * dy
            <= zone.getRadius() * zone.getRadius();
    }

    private void addPlayerAtAvailableSpawn(int playerId) {
        SpawnPoint spawn = findAvailablePlayerSpawn();

        players.put(
            playerId,
            new Player(spawn.x(), spawn.y())
        );

        assignedPlayerSpawns.put(playerId, spawn);
    }

    private SpawnPoint findAvailablePlayerSpawn() {
        for (SpawnPoint spawn : playerSpawns) {
            if (!assignedPlayerSpawns.containsValue(spawn)) {
                return spawn;
            }
        }

        throw new IllegalStateException(
            "No player spawn is available"
        );
    }

    private void addDummyAtSpawn(SpawnPoint spawn) {
        dummies.add(
            new TargetDummy(
                spawn.x(),
                spawn.y()
            )
        );
    }

    private void updatePlayerRespawns() {
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            int playerId = entry.getKey();
            Player player = entry.getValue();

            if (player.isAlive()) {
                continue;
            }

            if (!player.hasRespawnPending()) {
                player.beginRespawn(PLAYER_RESPAWN_DELAY);
            }

            SpawnPoint spawn = assignedPlayerSpawns.get(playerId);

            if (spawn != null) {
                player.updateRespawn(
                    STEP,
                    spawn.x(),
                    spawn.y()
                );
            }
        }
    }

    private void updateDummyRespawns() {
        for (TargetDummy dummy : dummies) {
            if (
                !dummy.isAlive()
                && !dummy.hasRespawnPending()
            ) {
                dummy.beginRespawn(DUMMY_RESPAWN_DELAY);
                continue;
            }

            dummy.updateRespawn(STEP);
        }
    }

    // Returns the live list for rendering; callers should not modify it.
    public List<Projectile> getProjectiles() {
        return projectiles;
    }

    // Client side: make this world mirror what the host sent.
    // Never call step() on a world that is being fed snapshots.
    public void applySnapshot(WorldSnapshot snapshot) {
        tick = snapshot.tick();

        // Players: update or create by id, drop anyone who left.
        Set<Integer> present = new HashSet<>();

        for (WorldSnapshot.PlayerState state : snapshot.players()) {
            present.add(state.id());

            Player player = players.computeIfAbsent(
                state.id(),
                id -> new Player(state.x(), state.y())
            );

            player.restoreState(
                state.x(),
                state.y(),
                state.aimX(),
                state.aimY(),
                state.moving(),
                state.health(),
                state.respawnRemaining(),
                state.kills(),
                state.deaths()
            );

            for (SpellType spellType : SpellType.values()) {
                player.setCooldownRemaining(
                    spellType,
                    state.cooldownFor(spellType)
                );
            }
        }

        players.keySet().retainAll(present);

        // Dummies never move, so the same index is the same dummy.
        for (int i = 0; i < dummies.size() && i < snapshot.dummies().size(); i++) {
            dummies.get(i).setHealth(snapshot.dummies().get(i).health());
        }

        // Projectiles are rebuilt every snapshot.
        projectiles.clear();

        for (WorldSnapshot.ProjectileState state : snapshot.projectiles()) {
            projectiles.add(
                new Projectile(
                    state.x(),
                    state.y(),
                    state.directionX(),
                    state.directionY(),
                    state.spellType(),
                    state.ownerPlayerId()
                )
            );
        }

        // Zones keep their Java object between snapshots so the
        // charge flash can play; match by type and position.
        List<Zone> previous = new ArrayList<>(zones);
        zones.clear();

        for (WorldSnapshot.ZoneState state : snapshot.zones()) {
            Zone zone = takeMatchingZone(previous, state);

            zone.setDamageOwnerPlayerId(state.damageOwnerPlayerId());

            if (state.electrified() && !zone.isElectrified()) {
                zone.electrify(state.damageOwnerPlayerId());
            } else if (
                !state.electrified()
                && zone.isElectrified()
            ) {
                zone.discharge();
            }

            // Do this after electrify/discharge because those methods reset the timer.
            zone.setChargeFlashRemaining( state.chargeFlashRemaining());

            zones.add(zone);
        }
    }

    private static Zone takeMatchingZone(
        List<Zone> candidates,
        WorldSnapshot.ZoneState state
    ) {
        for (Iterator<Zone> it = candidates.iterator(); it.hasNext();) {
            Zone zone = it.next();

            if (
                zone.getType() == state.type()
                && zone.getX() == state.x()
                && zone.getY() == state.y()
            ) {
                it.remove();
                return zone;
            }
        }

        return state.type() == Zone.Type.PUDDLE
            ? Zone.createPuddle(state.x(), state.y(), state.damageOwnerPlayerId())
            : Zone.createSteam(state.x(), state.y(), state.damageOwnerPlayerId());
    }

    // Client side: lets the charge flash fade between snapshots.
    public void updateZoneVisuals(float deltaSeconds) {
        for (Zone zone : zones) {
            zone.update(deltaSeconds);
        }
    }

    //Player 0, the local player in single-player.
    public Player getPlayer() {
        return players.get(0);
    }

    private void castLightning(
        int playerId,
        Player player,
        boolean requested,
        List<WorldEvent> events
    ) {
        SpellType lightning = SpellType.LIGHTNING;

        if (
            !player.isAlive()
            || !requested
            || !player.isSpellReady(lightning)
        ) {
            return;
        }

        float originX = player.getCastX();
        float originY = player.getCastY();

        float directionX = player.getAimX();
        float directionY = player.getAimY();

        LightningHit hit =
            findLightningHit(
                playerId,
                originX,
                originY,
                directionX,
                directionY,
                lightning.getRange()
            );

        switch (hit.kind()) {
            case PUDDLE -> {
                Zone puddle = hit.zone();

                if (!puddle.isElectrified()) {
                    puddle.electrify(playerId);
                }
            }

            case PLAYER ->
                damagePlayer(
                    hit.playerId(),
                    lightning.getDamage(),
                    playerId
                );

            case DUMMY ->
                hit.dummy().takeDamage(
                    lightning.getDamage()
                );

            case NONE, TERRAIN -> {
                // The cast still succeeds, but there is no gameplay effect.
            }
        }

        player.startCooldown(lightning);
        float strikeDistance =
            hit.kind() == LightningHit.Kind.NONE
                ? lightning.getRange()
                : hit.distance();

        LightningStrikeEvent.EndReason endReason =
            switch (hit.kind()) {
                case NONE ->
                    LightningStrikeEvent.EndReason.RANGE_LIMIT;

                case TERRAIN ->
                    LightningStrikeEvent.EndReason.TERRAIN;

                case PUDDLE ->
                    LightningStrikeEvent.EndReason.PUDDLE;

                case PLAYER ->
                    LightningStrikeEvent.EndReason.PLAYER;

                case DUMMY ->
                    LightningStrikeEvent.EndReason.DUMMY;
            };

        events.add(
            new LightningStrikeEvent(
                originX,
                originY,
                originX + directionX * strikeDistance,
                originY + directionY * strikeDistance,
                endReason
            )
        );
    }

    private boolean applyProjectilePlayerHit(
        Projectile projectile
    ) {
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            int targetPlayerId = entry.getKey();
            Player target = entry.getValue();

            if (
                targetPlayerId == projectile.getOwnerPlayerId()
                || !target.isAlive()
            ) {
                continue;
            }

            if (!projectileOverlapsPlayer(projectile, target)) {
                continue;
            }

            damagePlayer(
                targetPlayerId,
                projectile.getSpellType().getDamage(),
                projectile.getOwnerPlayerId()
            );

            return true;
        }

        return false;
    }

    private boolean projectileOverlapsPlayer(
        Projectile projectile,
        Player player
    ) {
        float centerX = player.getCombatCenterX();
        float centerY = player.getCombatCenterY();

        float closestX = Math.max(
            centerX - Player.COMBAT_HALF_WIDTH,
            Math.min(
                projectile.getX(),
                centerX + Player.COMBAT_HALF_WIDTH
            )
        );

        float closestY = Math.max(
            centerY - Player.COMBAT_HALF_HEIGHT,
            Math.min(
                projectile.getY(),
                centerY + Player.COMBAT_HALF_HEIGHT
            )
        );

        float dx = projectile.getX() - closestX;
        float dy = projectile.getY() - closestY;

        return dx * dx + dy * dy
            <= PROJECTILE_RADIUS * PROJECTILE_RADIUS;
    }

    private void tryCast(
        int playerId,
        Player player,
        boolean requested,
        SpellType spellType
    ) {
        if (
            !player.isAlive()
            || !requested
            || !player.isSpellReady(spellType)
        ) {
            return;
        }

        projectiles.add(
            new Projectile(
                player.getCastX(),
                player.getCastY(),
                player.getAimX(),
                player.getAimY(),
                spellType,
                playerId
            )
        );

        player.startCooldown(spellType);
    }

    public int getPlayerCapacity() {
        return playerSpawns.size();
    }

    private void damagePlayer(
        int victimPlayerId,
        float amount,
        int attackerPlayerId
    ) {
        Player victim = players.get(victimPlayerId);

        if (victim == null || !victim.isAlive()) {
            return;
        }

        victim.takeDamage(amount);

        if (victim.isAlive()) {
            return;
        }

        victim.recordDeath();
        victim.beginRespawn(PLAYER_RESPAWN_DELAY);

        if (attackerPlayerId == victimPlayerId) {
            return;
        }

        Player attacker = players.get(attackerPlayerId);

        if (attacker != null) {
            attacker.recordKill();
        }
    }

    // Returns the live list for rendering, including dead dummies awaiting respawn.
    public List<TargetDummy> getDummies() {
        return dummies;
    }
}
