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
import io.github.coda45.hexadence.entity.PlayerColor;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.CastRequest;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.net.WorldSnapshot;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.zone.Reactions;
import io.github.coda45.hexadence.zone.ZoneKind;

// Owns gameplay state and applies commands at a fixed 60 Hz rate.
public class ArenaWorld {

    public static final float STEP = 1f / 60f;

    private static final float PLAYER_SPEED = 5f;
    private static final float PLAYER_RESPAWN_DELAY = 3f;
    private static final float DUMMY_RESPAWN_DELAY = 2f;

    private final ArenaCollision collision;
    private final List<SpawnPoint> playerSpawns;
    private final Map<Integer, SpawnPoint> assignedPlayerSpawns = new LinkedHashMap<>();
    // Keyed by player ID. The constructor creates local player 0.
    private final Map<Integer, Player> players = new LinkedHashMap<>();
    private final ArrayList<Projectile> projectiles = new ArrayList<>();
    private final List<TargetDummy> dummies = new ArrayList<>();
    private final List<Zone> zones = new ArrayList<>();
    // Damage-over-time started by a hit, independent of standing in a zone.
    private final Map<Integer, DotEffect> playerDotEffects = new LinkedHashMap<>();
    private final Map<TargetDummy, DotEffect> dummyDotEffects = new LinkedHashMap<>();

    private long tick;
    private int nextPlayerId = 1;
    private int nextDummyId = 1;
    private int nextProjectileId = 1;
    private int nextZoneId = 1;

    public ArenaWorld(ArenaDefinition definition) {
        collision = definition.collision();
        playerSpawns = definition.playerSpawns();

        addPlayerAtAvailableSpawn(0);

        for (SpawnPoint dummySpawn : definition.dummySpawns()) {
            addDummyAtSpawn(dummySpawn);
        }
    }

    // Single-player entry point: one command drives player 0.
    public StepResult step(PlayerCommand command) {
        return step(Map.of(0, command));
    }

    // Advances exactly one STEP, regardless of the render frame rate.
    // A player with no entry in the map this step is treated as idle.
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

            Power power = projectile.getPower();
            float radius = power.radius();

            float remainingRange = projectile.getRemainingTravel();

            float movementDistance = Math.min(
                power.speed() * STEP,
                remainingRange
            );

            // Move at most one radius per substep so it cannot skip
            // past anything thinner than itself.
            int substepCount = Math.max(
                1,
                (int) Math.ceil(
                    movementDistance
                        / radius
                )
            );

            float substepDistance =
                movementDistance / substepCount;

            boolean terminated = false;

            for (int step = 0; step < substepCount; step++) {
                // Sweep the whole substep so a diagonal path cannot tunnel
                // through the tiny region around a blocked tile corner.
                float terrainDistance = collision.raycastTerrain(
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getDirectionX(),
                    projectile.getDirectionY(),
                    substepDistance,
                    radius
                );

                if (Float.isFinite(terrainDistance)) {
                    projectile.update(terrainDistance);
                    leaveZoneIfAny(
                        projectile,
                        projectile.getX(),
                        projectile.getY()
                    );

                    terminated = true;
                    break;
                }

                projectile.update(substepDistance);

                // Zone reactions take priority over player and dummy hits.
                if (applyProjectileZoneReaction(projectile, events)) {
                    terminated = true;
                    break;
                }

                if (applyProjectilePlayerHit(projectile)) {
                    leaveZoneIfAny(
                        projectile,
                        projectile.getX(),
                        projectile.getY()
                    );

                    terminated = true;
                    break;
                }

                if (applyProjectileDummyHit(projectile)) {
                    leaveZoneIfAny(
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
                projectile.hasReachedTravelLimit()
            ) {
                leaveZoneIfAny(
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
 
        applyZoneEffects();
        applyChargedPuddleDischarges(events);
        applyDotEffects();
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

        double aimLength = Math.hypot((double) aimX, (double) aimY);

        // Keep the last direction when the cursor is too close to the cast point.
        if (player.isAlive() && aimLength > 0.0001) {
            player.setAim(
                (float) (aimX / aimLength),
                (float) (aimY / aimLength)
            );
        }

        player.updateCooldowns(STEP);

        // Spawn before player movement, using the aim chosen for this step.
        for (int slot = 0; slot < Loadout.SIZE; slot++) {
            CastRequest cast = command.castFor(slot);

            if (cast != null) {
                tryCast(playerId, player, cast, events);
            }
        }
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

        // A zone like quicksand slows whoever stands in it.
        Zone underfoot = topZoneOverlapping(player);
        float speed = PLAYER_SPEED
            * (underfoot == null ? 1f : underfoot.getKind().speedMultiplier());

        float dx = moveX * speed * STEP;
        float dy = moveY * speed * STEP;

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
        playerDotEffects.remove(id);
    }
 
    // Read-only view, in join order; the Player objects are live.
    public Map<Integer, Player> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    // Powers whose behavior creates a zone leave it where they stop.
    private void leaveZoneIfAny(
        Projectile projectile,
        float x,
        float y
    ) {
        Power power = projectile.getPower();

        if (!power.behavior().createsZone()) {
            return;
        }

        placeZone(
            power.zoneKind(),
            x,
            y,
            projectile.getOwnerPlayerId()
        );
    }

    // An instant zone follows the same terrain path rule as a projectile. Its
    // center stops where its point/probe first touches terrain; the larger zone
    // itself may overlap the blocked tile after appearing.
    private void placeZoneAtTargetOrTerrain(
        ZoneKind kind,
        float originX,
        float originY,
        CastTarget target,
        float probeRadius,
        int ownerPlayerId
    ) {
        float terrainDistance = collision.raycastTerrain(
            originX,
            originY,
            target.directionX(),
            target.directionY(),
            target.distance(),
            probeRadius
        );
        float distance = Float.isFinite(terrainDistance)
            ? terrainDistance
            : target.distance();

        placeZone(
            kind,
            originX + target.directionX() * distance,
            originY + target.directionY() * distance,
            ownerPlayerId
        );
    }

    private void placeZone(
        ZoneKind kind,
        float x,
        float y,
        int ownerPlayerId
    ) {
        zones.add(Zone.create(
            nextZoneId++,
            kind,
            x,
            y,
            ownerPlayerId
        ));
    }

    public List<Zone> getZones() {
        return zones;
    }

    // A projectile reacts with the newest zone it touches, if the reaction
    // table says that power and zone react. Returns true if the projectile
    // was used up by the reaction.
    private boolean applyProjectileZoneReaction(
        Projectile projectile,
        List<WorldEvent> events
    ) {
        Power power = projectile.getPower();

        // Zones later in the list are newer, and the newest one counts.
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone zone = zones.get(i);

            float dx = projectile.getX() - zone.getX();
            float dy = projectile.getY() - zone.getY();
            float interactionDistance =
                power.radius() + zone.getRadius();

            boolean overlaps =
                dx * dx + dy * dy
                    <= interactionDistance * interactionDistance;

            if (!overlaps) {
                continue;
            }

            Reactions.Reaction reaction = Reactions.find(power, zone.getKind());

            return reaction != null
                && applyReaction(zone, reaction, projectile.getOwnerPlayerId(), events);
        }

        return false;
    }

    // Carries out one cell of the interaction matrix on a zone. Returns
    // false if it changed nothing (charging an already charged zone).
    private boolean applyReaction(
        Zone zone,
        Reactions.Reaction reaction,
        int casterPlayerId,
        List<WorldEvent> events
    ) {
        switch (reaction.result()) {
            case TRANSFORM -> {
                // Same spot and ID, new kind. It moves to the end of the list
                // because it is now the newest zone there.
                zones.remove(zone);
                zones.add(Zone.create(
                    zone.getId(),
                    reaction.into(),
                    zone.getX(),
                    zone.getY(),
                    casterPlayerId
                ));
                return true;
            }

            case CHARGE -> {
                if (zone.isElectrified()) {
                    return false;
                }

                zone.electrify(casterPlayerId);
                events.add(new ZoneChargeChangedEvent(zone.getId(), true));
                return true;
            }
        }

        return false;
    }

    private boolean applyProjectileDummyHit(
        Projectile projectile
    ) {
        Power power = projectile.getPower();

        for (TargetDummy dummy : dummies) {
            if (!dummy.isAlive()) {
                continue;
            }

            float dx = projectile.getX() - dummy.getX();
            float dy = projectile.getY() - dummy.getY();

            float hitDistance = power.radius() + TargetDummy.RADIUS;

            boolean overlaps = dx * dx + dy * dy <= hitDistance * hitDistance;

            if (!overlaps) {
                continue;
            }

            dummy.takeDamage(
                power.damage()
            );

            applyDotIfAny(power, dummy, projectile.getOwnerPlayerId());

            // A projectile damages only the first
            // live dummy it overlaps.
            return true;
        }

        return false;
    }

    // The first thing a beam touches: terrain, a zone this beam reacts with
    // (reported as Kind.PUDDLE), another player, or a dummy.
    private LightningHit findLightningHit(
        int ownerPlayerId,
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float halfWidth,
        Power beam
    ) {
        LightningHit nearest = LightningHit.none();

        float terrainDistance =
            collision.raycastTerrain(
                originX,
                originY,
                directionX,
                directionY,
                maxDistance,
                halfWidth
            );

        if (!Float.isInfinite(terrainDistance)) {
            nearest = LightningHit.terrain(terrainDistance);
        }

        for (Zone zone : zones) {
            if (Reactions.find(beam, zone.getKind()) == null) {
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
                    zone.getRadius() + halfWidth
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
                        - halfWidth,
                    centerY
                        - Player.COMBAT_HALF_HEIGHT
                        - halfWidth,
                    centerX
                        + Player.COMBAT_HALF_WIDTH
                        + halfWidth,
                    centerY
                        + Player.COMBAT_HALF_HEIGHT
                        + halfWidth
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
                    TargetDummy.RADIUS + halfWidth
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

    // Ongoing zone effects: damage per second for anyone standing in a zone.
    // Only the newest zone at a spot counts. (Slow is applied in movePlayer.)
    private void applyZoneEffects() {
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            Player target = entry.getValue();

            if (!target.isAlive()) {
                continue;
            }

            Zone zone = topZoneOverlapping(target);

            if (zone != null && zone.getKind().damagePerSecond() > 0f) {
                damagePlayer(
                    entry.getKey(),
                    zone.getKind().damagePerSecond() * STEP,
                    zone.getDamageOwnerPlayerId()
                );
            }
        }

        for (TargetDummy dummy : dummies) {
            if (!dummy.isAlive()) {
                continue;
            }

            Zone zone = topZoneOverlapping(dummy);

            if (zone != null && zone.getKind().damagePerSecond() > 0f) {
                dummy.takeDamage(zone.getKind().damagePerSecond() * STEP);
            }
        }
    }

    // A charged zone shocks the players and dummies it is the newest zone
    // under, then loses its charge.
    private void applyChargedPuddleDischarges(
        List<WorldEvent> events
    ) {
        for (Zone zone : zones) {
            if (!zone.isElectrified()) {
                continue;
            }

            boolean discharged = false;

            for (Map.Entry<Integer, Player> entry : players.entrySet()) {
                Player target = entry.getValue();

                if (
                    target.isAlive()
                    && topZoneOverlapping(target) == zone
                ) {
                    damagePlayer(
                        entry.getKey(),
                        Reactions.CHARGE_DISCHARGE_DAMAGE,
                        zone.getDamageOwnerPlayerId()
                    );

                    discharged = true;
                }
            }

            for (TargetDummy dummy : dummies) {
                if (
                    dummy.isAlive()
                    && topZoneOverlapping(dummy) == zone
                ) {
                    dummy.takeDamage(Reactions.CHARGE_DISCHARGE_DAMAGE);
                    discharged = true;
                }
            }

            if (discharged) {
                zone.discharge();
                events.add(
                    new ZoneChargeChangedEvent(zone.getId(), false)
                );
            }
        }
    }

    // Ticks every active damage-over-time effect and removes it once expired.
    private void applyDotEffects() {
        Iterator<Map.Entry<Integer, DotEffect>> playerIterator =
            playerDotEffects.entrySet().iterator();

        while (playerIterator.hasNext()) {
            Map.Entry<Integer, DotEffect> entry = playerIterator.next();
            DotEffect effect = entry.getValue();

            if (effect.update(STEP)) {
                damagePlayer(
                    entry.getKey(),
                    effect.damagePerTick(),
                    effect.attackerPlayerId()
                );
            }

            if (effect.hasExpired()) {
                playerIterator.remove();
            }
        }

        Iterator<Map.Entry<TargetDummy, DotEffect>> dummyIterator =
            dummyDotEffects.entrySet().iterator();

        while (dummyIterator.hasNext()) {
            Map.Entry<TargetDummy, DotEffect> entry = dummyIterator.next();
            DotEffect effect = entry.getValue();

            if (effect.update(STEP)) {
                entry.getKey().takeDamage(effect.damagePerTick());
            }

            if (effect.hasExpired()) {
                dummyIterator.remove();
            }
        }
    }

    // Starts (or refreshes) the power's damage-over-time effect on a target
    // it just hit. Does nothing for a power without one.
    private void applyDotIfAny(
        Power power,
        int targetPlayerId,
        int attackerPlayerId
    ) {
        if (power.dotDamagePerTick() <= 0f) {
            return;
        }

        playerDotEffects.put(
            targetPlayerId,
            new DotEffect(
                power.dotDamagePerTick(),
                power.dotTickSeconds(),
                power.dotDurationSeconds(),
                attackerPlayerId
            )
        );
    }

    private void applyDotIfAny(
        Power power,
        TargetDummy dummy,
        int attackerPlayerId
    ) {
        if (power.dotDamagePerTick() <= 0f) {
            return;
        }

        dummyDotEffects.put(
            dummy,
            new DotEffect(
                power.dotDamagePerTick(),
                power.dotTickSeconds(),
                power.dotDurationSeconds(),
                attackerPlayerId
            )
        );
    }

    // The newest zone touching this player's feet, or null.
    private Zone topZoneOverlapping(Player player) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zoneOverlapsPlayer(zones.get(i), player)) {
                return zones.get(i);
            }
        }

        return null;
    }

    private Zone topZoneOverlapping(TargetDummy dummy) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            if (zoneOverlapsDummy(zones.get(i), dummy)) {
                return zones.get(i);
            }
        }

        return null;
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
            new Player(spawn.x(), spawn.y(), findAvailablePlayerColor())
        );

        assignedPlayerSpawns.put(playerId, spawn);
    }

    private PlayerColor findAvailablePlayerColor() {
        for (PlayerColor color : PlayerColor.values()) {
            boolean alreadyUsed =
                players.values().stream()
                    .anyMatch(
                        player ->
                            player.getColor() == color
                    );

            if (!alreadyUsed) {
                return color;
            }
        }

        throw new IllegalStateException(
            "No player color is available"
        );
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
                nextDummyId++,
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
                id -> new Player(state.x(), state.y(), state.color())
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
                state.deaths(),
                state.color()
            );

            for (int slot = 0; slot < state.slots().size(); slot++) {
                WorldSnapshot.SlotState slotState = state.slots().get(slot);

                player.restoreSlot(
                    slot,
                    slotState.power(),
                    slotState.cooldownRemaining()
                );
            }
        }

        players.keySet().retainAll(present);

        Map<Integer, TargetDummy> previousDummies = new LinkedHashMap<>();

        for (TargetDummy dummy : dummies) {
            previousDummies.put(dummy.getId(), dummy);
        }

        dummies.clear();

        for (WorldSnapshot.DummyState state : snapshot.dummies()) {
            TargetDummy dummy = previousDummies.remove(state.id());

            if (dummy == null) {
                dummy = new TargetDummy(
                    state.id(),
                    state.x(),
                    state.y()
                );
            }

            dummy.setHealth(state.health());
            dummies.add(dummy);
        }

        // Match projectiles by ID so existing client objects survive snapshots.
        Map<Integer, Projectile> previousProjectiles = new LinkedHashMap<>();

        for (Projectile projectile : projectiles) {
            previousProjectiles.put(
                projectile.getId(),
                projectile
            );
        }

        projectiles.clear();

        for (
            WorldSnapshot.ProjectileState state
                : snapshot.projectiles()
        ) {
            Projectile projectile =
                previousProjectiles.remove(state.id());

            if (projectile == null) {
                projectile = new Projectile(
                    state.id(),
                    state.x(),
                    state.y(),
                    state.directionX(),
                    state.directionY(),
                    state.power(),
                    state.ownerPlayerId()
                );
            } else {
                projectile.restorePosition(
                    state.x(),
                    state.y()
                );
            }

            projectiles.add(projectile);
        }

        // Preserve replica objects by stable ID so client-side presentation
        // can retain per-entity state across snapshots.
        Map<Integer, Zone> previousZones = new LinkedHashMap<>();

        for (Zone zone : zones) {
            previousZones.put(zone.getId(), zone);
        }

        zones.clear();

        for (WorldSnapshot.ZoneState state : snapshot.zones()) {
            Zone zone = previousZones.remove(state.id());

            if (zone == null || !zone.getKind().equals(state.kind())) {
                zone = createZoneFromSnapshot(state);
            }

            zone.setDamageOwnerPlayerId(
                state.damageOwnerPlayerId()
            );

            if (state.electrified() && !zone.isElectrified()) {
                zone.electrify(state.damageOwnerPlayerId());
            } else if (
                !state.electrified()
                    && zone.isElectrified()
            ) {
                zone.discharge();
            }

            zones.add(zone);
        }
    }

    // Clients never expire zones themselves; the server removes them from
    // later snapshots. So the duration here only needs to be positive.
    private static Zone createZoneFromSnapshot(
        WorldSnapshot.ZoneState state
    ) {
        return Zone.create(
            state.id(),
            state.kind(),
            state.x(),
            state.y(),
            state.radius(),
            Float.MAX_VALUE,
            state.damageOwnerPlayerId()
        );
    }

    // Player 0 is the local player in single-player.
    public Player getPlayer() {
        return players.get(0);
    }

    // A beam hits the first thing in its path instantly.
    private void castBeam(
        int playerId,
        Player player,
        Power power,
        float directionX,
        float directionY,
        List<WorldEvent> events
    ) {
        float originX = player.getCastX();
        float originY = player.getCastY();

        LightningHit hit =
            findLightningHit(
                playerId,
                originX,
                originY,
                directionX,
                directionY,
                power.range(),
                power.radius(),
                power
            );

        switch (hit.kind()) {
            case PUDDLE -> {
                Zone zone = hit.zone();

                applyReaction(
                    zone,
                    Reactions.find(power, zone.getKind()),
                    playerId,
                    events
                );
            }

            case PLAYER ->
                damagePlayer(
                    hit.playerId(),
                    power.damage(),
                    playerId
                );

            case DUMMY ->
                hit.dummy().takeDamage(
                    power.damage()
                );

            case NONE, TERRAIN -> {
                // The cast still succeeds, but there is no gameplay effect.
            }
        }

        float strikeDistance =
            hit.kind() == LightningHit.Kind.NONE
                ? power.range()
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
                projectile.getPower().damage(),
                projectile.getOwnerPlayerId()
            );

            applyDotIfAny(
                projectile.getPower(),
                targetPlayerId,
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

        float radius = projectile.getPower().radius();

        return dx * dx + dy * dy
            <= radius * radius;
    }

    // Uses the power in one loadout slot, if the player can right now.
    // This is the one place each Behavior is turned into gameplay.
    private void tryCast(
        int playerId,
        Player player,
        CastRequest request,
        List<WorldEvent> events
    ) {
        int slot = request.slot();

        if (!player.isAlive() || !player.isSlotReady(slot)) {
            return;
        }

        Power power = player.getPower(slot);
        CastTarget target = Targeting.resolveOffset(
            player.getCastX(),
            player.getCastY(),
            request.targetOffsetX(),
            request.targetOffsetY(),
            power.range(),
            player.getAimX(),
            player.getAimY()
        );

        boolean accepted = false;

        switch (power.behavior()) {
            case PROJECTILE -> {
                int pelletCount = power.pelletCount();
                float spreadRadians =
                    (float) Math.toRadians(power.spreadDegrees());

                for (int pellet = 0; pellet < pelletCount; pellet++) {
                    float angle = pelletCount <= 1
                        ? 0f
                        : -spreadRadians / 2f
                            + spreadRadians * pellet / (pelletCount - 1);

                    float[] direction = rotateDirection(
                        target.directionX(),
                        target.directionY(),
                        angle
                    );

                    projectiles.add(
                        new Projectile(
                            nextProjectileId++,
                            player.getCastX(),
                            player.getCastY(),
                            direction[0],
                            direction[1],
                            power.range(),
                            power,
                            playerId
                        )
                    );
                }
                accepted = true;
            }

            case THROWN_ZONE -> {
                if (target.distance() <= 0.0001f) {
                    accepted = false;
                    break;
                }

                projectiles.add(
                    new Projectile(
                        nextProjectileId++,
                        player.getCastX(),
                        player.getCastY(),
                        target.directionX(),
                        target.directionY(),
                        target.distance(),
                        power,
                        playerId
                    )
                );
                accepted = true;
            }

            case PLACED_ZONE -> {
                placeZoneAtTargetOrTerrain(
                    power.zoneKind(),
                    player.getCastX(),
                    player.getCastY(),
                    target,
                    power.radius(),
                    playerId
                );
                accepted = true;
            }

            case BEAM -> {
                castBeam(
                    playerId,
                    player,
                    power,
                    target.directionX(),
                    target.directionY(),
                    events
                );
                accepted = true;
            }
        }

        if (accepted) {
            player.startCooldown(slot);
        }
    }

    // Rotates a normalized direction by an angle in radians, for spreading a
    // cast's pellets across a cone around its aimed direction.
    private static float[] rotateDirection(
        float directionX,
        float directionY,
        float radians
    ) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        return new float[] {
            directionX * cos - directionY * sin,
            directionX * sin + directionY * cos
        };
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
