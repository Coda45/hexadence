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
import io.github.coda45.hexadence.power.Behavior;
import io.github.coda45.hexadence.power.Loadout;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.Powers;
import io.github.coda45.hexadence.zone.Reactions;
import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneKinds;

// Owns gameplay state and applies commands at a fixed 60 Hz rate.
public class ArenaWorld {

    public static final float STEP = 1f / 60f;

    private static final float PLAYER_SPEED = 5f;
    private static final float PLAYER_RESPAWN_DELAY = 3f;
    private static final float DUMMY_RESPAWN_DELAY = 2f;
    private static final float KNOCKBACK_DAMPING_PER_STEP = 0.9f;
    private static final float VORTEX_ORBIT_SPEED = 8f;

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
    private final Map<ChannelKey, ActiveChannel> activeChannels = new LinkedHashMap<>();
    private final Map<Integer, VortexOrbit> vortexOrbits = new LinkedHashMap<>();
    // A traveling Vortex and the stationary zone it creates share one source
    // key, preventing a projectile or player from being captured/pushed twice
    // merely because the same field changed representation.
    private final Map<Integer, Integer> vortexZoneSourceKeys =
        new LinkedHashMap<>();
    private final Set<VortexPlayerContact> vortexPlayerContacts = new HashSet<>();

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
                applyExpirationExplosion(zone);
                releaseVortexOrbitsFromSource(vortexSourceKey(zone));
                vortexZoneSourceKeys.remove(zone.getId());
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

            VortexOrbit orbit = vortexOrbits.get(projectile.getId());

            if (orbit != null) {
                updateVortexOrbit(projectile, orbit);
                continue;
            }

            boolean singularityInfluenced =
                applySingularityGravity(projectile);

            Power power = projectile.getPower();
            float radius = power.radius();

            float remainingRange = projectile.getRemainingTravel();

            float movementDistance = Math.min(
                projectile.getCurrentSpeed() * STEP,
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
            boolean captured = false;

            for (int step = 0; step < substepCount; step++) {
                // Sweep the whole substep so a diagonal path cannot tunnel
                // through the tiny region around a blocked tile corner.
                BlockingHit blockingHit = findBlockingHit(
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getDirectionX(),
                    projectile.getDirectionY(),
                    substepDistance,
                    radius,
                    radius
                );

                if (Float.isFinite(blockingHit.distance())) {
                    advanceProjectile(
                        projectile,
                        blockingHit.distance(),
                        singularityInfluenced
                    );

                    if (
                        blockingHit.zone() == null
                        || !damageDestructibleZone(
                            projectile,
                            blockingHit.zone(),
                            events
                        )
                    ) {
                        leaveZoneIfAny(
                            projectile,
                            projectile.getX(),
                            projectile.getY()
                        );
                    }

                    terminated = true;
                    break;
                }

                advanceProjectile(
                    projectile,
                    substepDistance,
                    singularityInfluenced
                );

                // Zone reactions take priority over player and dummy hits.
                if (applyProjectileZoneReaction(projectile, events)) {
                    terminated = true;
                    break;
                }

                if (tryCaptureProjectile(projectile)) {
                    captured = true;
                    break;
                }

                if (
                    power != Powers.VORTEX
                    && applyProjectilePlayerHit(projectile)
                ) {
                    leaveZoneIfAny(
                        projectile,
                        projectile.getX(),
                        projectile.getY()
                    );

                    terminated = true;
                    break;
                }

                if (
                    power != Powers.VORTEX
                    && applyProjectileDummyHit(projectile)
                ) {
                    leaveZoneIfAny(
                        projectile,
                        projectile.getX(),
                        projectile.getY()
                    );

                    terminated = true;
                    break;
                }

            }

            if (captured) {
                continue;
            }

            if (terminated) {
                vortexOrbits.remove(projectile.getId());
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

                vortexOrbits.remove(projectile.getId());
                projectileIterator.remove();
            }
        }

        applyVortexImpulses();

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
            ChannelKey key = new ChannelKey(playerId, slot);
            ActiveChannel active = activeChannels.get(key);
            Power currentPower = player.getPower(slot);

            if (active != null) {
                boolean stillHeld =
                    player.isAlive()
                    && cast != null
                    && currentPower.equals(active.power());

                if (!stillHeld) {
                    activeChannels.remove(key);

                    if (currentPower.equals(active.power())) {
                        player.startCooldown(slot);
                    }

                    events.add(new BeamChannelEndEvent(playerId, active.power()));
                    continue;
                }

                if (active.advance(STEP)) {
                    castChannelTick(playerId, player, active.power(), cast, events);
                }

                continue;
            }

            if (cast != null) {
                if (currentPower.isChanneled()) {
                    if (!player.isAlive() || !player.isSlotReady(slot)) {
                        continue;
                    }

                    activeChannels.put(
                        key,
                        new ActiveChannel(
                            currentPower,
                            currentPower.channelTickSeconds()
                        )
                    );
                    castChannelTick(playerId, player, currentPower, cast, events);
                } else {
                    tryCast(playerId, player, cast, events);
                }
            }
        }
    }

    private void castChannelTick(
        int playerId,
        Player player,
        Power power,
        CastRequest request,
        List<WorldEvent> events
    ) {
        CastTarget target = Targeting.resolveOffset(
            player.getCastX(),
            player.getCastY(),
            request.targetOffsetX(),
            request.targetOffsetY(),
            power.range(),
            player.getAimX(),
            player.getAimY()
        );

        castBeam(
            playerId,
            player,
            power,
            target.directionX(),
            target.directionY(),
            events
        );
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
        float[] gravityPull = singularityPlayerPull(player);

        float dx = (
            moveX * speed
                + player.getKnockbackVelocityX()
                + gravityPull[0]
        ) * STEP;
        float dy = (
            moveY * speed
                + player.getKnockbackVelocityY()
                + gravityPull[1]
        ) * STEP;

        float[] resolved = resolveWorldMovement(
            player.getCollisionX(),
            player.getCollisionY(),
            dx,
            dy,
            Player.HALF_WIDTH,
            Player.HALF_HEIGHT
        );

        player.move(resolved[0], resolved[1]);
        player.dampenKnockback(KNOCKBACK_DAMPING_PER_STEP);
    }

    private void advanceProjectile(
        Projectile projectile,
        float distance,
        boolean externallyInfluenced
    ) {
        if (externallyInfluenced) {
            projectile.updateWithoutConsumingRange(distance);
        } else {
            projectile.update(distance);
        }
    }

    private boolean applySingularityGravity(Projectile projectile) {
        boolean influenced = false;

        for (Zone zone : zones) {
            ZoneKind kind = zone.getKind();
            int level = gravityLevel(
                projectile.getX(),
                projectile.getY(),
                zone
            );

            if (level == 0) {
                continue;
            }

            influenced = true;

            projectile.accelerateToward(
                zone.getX(),
                zone.getY(),
                kind.projectileGravityAccelerationStep() * level * level,
                STEP
            );
        }

        return influenced;
    }

    private float[] singularityPlayerPull(Player player) {
        float pullX = 0f;
        float pullY = 0f;

        for (Zone zone : zones) {
            int level = gravityLevel(
                player.getCollisionX(),
                player.getCollisionY(),
                zone
            );

            if (level == 0) {
                continue;
            }

            float dx = zone.getX() - player.getCollisionX();
            float dy = zone.getY() - player.getCollisionY();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= 0.0001f) {
                continue;
            }

            float pullSpeed = zone.getKind().playerGravitySpeedStep() * level;
            pullX += dx / distance * pullSpeed;
            pullY += dy / distance * pullSpeed;
        }

        return new float[] { pullX, pullY };
    }

    private int gravityLevel(float x, float y, Zone zone) {
        ZoneKind kind = zone.getKind();

        if (kind.gravityRadius() <= 0f) {
            return 0;
        }

        float dx = x - zone.getX();
        float dy = y - zone.getY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance > kind.gravityRadius()) {
            return 0;
        }

        float bandWidth = kind.gravityRadius() / kind.gravityLevels();
        int bandFromCenter = Math.min(
            kind.gravityLevels() - 1,
            (int) (distance / bandWidth)
        );
        return kind.gravityLevels() - bandFromCenter;
    }

    private void applyExpirationExplosion(Zone zone) {
        ZoneKind kind = zone.getKind();
        float radius = kind.expirationExplosionRadius();

        if (radius <= 0f) {
            return;
        }

        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            Player player = entry.getValue();

            if (
                player.isAlive()
                    && circleOverlapsPlayer(
                        zone.getX(),
                        zone.getY(),
                        radius,
                        player
                    )
            ) {
                damagePlayer(
                    entry.getKey(),
                    kind.expirationExplosionDamage(),
                    zone.getDamageOwnerPlayerId()
                );
            }
        }

        for (TargetDummy dummy : dummies) {
            if (
                dummy.isAlive()
                    && circleOverlapsDummy(
                        zone.getX(),
                        zone.getY(),
                        radius,
                        dummy
                    )
            ) {
                dummy.takeDamage(kind.expirationExplosionDamage());
            }
        }
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
        activeChannels.keySet().removeIf(key -> key.playerId() == id);
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

        Zone placed = placeZone(
            power.zoneKind(),
            x,
            y,
            projectile.getOwnerPlayerId()
        );

        if (power == Powers.VORTEX) {
            vortexZoneSourceKeys.put(
                placed.getId(),
                projectileVortexSourceKey(projectile)
            );
        }
    }

    // An instant zone follows the same terrain path rule as a projectile. Its
    // center stops where its point/probe first touches terrain; the larger zone
    // itself may overlap the blocked tile after appearing.
    private boolean placeZoneAtTargetOrTerrain(
        ZoneKind kind,
        float originX,
        float originY,
        CastTarget target,
        float probeRadius,
        int ownerPlayerId
    ) {
        if (kind.isSolid()) {
            return placeSolidZone(kind, originX, originY, target, ownerPlayerId);
        }

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
        return true;
    }

    private Zone placeZone(
        ZoneKind kind,
        float x,
        float y,
        int ownerPlayerId
    ) {
        Zone zone = Zone.create(
            nextZoneId++,
            kind,
            x,
            y,
            ownerPlayerId
        );
        zones.add(zone);
        return zone;
    }

    private boolean placeSolidZone(
        ZoneKind kind,
        float originX,
        float originY,
        CastTarget target,
        int ownerPlayerId
    ) {
        // Solid zones retain sub-tile cursor precision. Their full footprint is
        // swept so a blocked cursor places the zone just before the obstacle.
        float halfWidth = kind.solidHalfLength();
        float halfHeight = kind.solidHalfThickness();

        BlockingHit hit = findBlockingHit(
            originX,
            originY,
            target.directionX(),
            target.directionY(),
            target.distance(),
            halfWidth,
            halfHeight
        );
        float distance = Float.isFinite(hit.distance())
            ? Math.max(0f, hit.distance() - 0.001f)
            : target.distance();
        float x = originX + target.directionX() * distance;
        float y = originY + target.directionY() * distance;
        RectangleCollider wall = new RectangleCollider(x, y, halfWidth, halfHeight);

        if (overlapsWorldObstacle(wall) || overlapsLivingPlayer(wall)) {
            return false;
        }

        placeZone(kind, x, y, ownerPlayerId);
        return true;
    }

    private boolean overlapsLivingPlayer(WorldCollider collider) {
        for (Player player : players.values()) {
            if (
                player.isAlive()
                && collider.overlaps(
                    player.getCollisionX(),
                    player.getCollisionY(),
                    Player.HALF_WIDTH,
                    Player.HALF_HEIGHT
                )
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean overlapsWorldObstacle(RectangleCollider candidate) {
        if (
            collision.overlaps(
                candidate.centerX(),
                candidate.centerY(),
                candidate.halfWidth(),
                candidate.halfHeight()
            )
        ) {
            return true;
        }

        for (Zone zone : zones) {
            if (
                zone.getKind().isSolid()
                && zoneCollider(zone).overlaps(
                    candidate.centerX(),
                    candidate.centerY(),
                    candidate.halfWidth(),
                    candidate.halfHeight()
                )
            ) {
                return true;
            }
        }

        return false;
    }

    private BlockingHit findBlockingHit(
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float footprintHalfWidth,
        float footprintHalfHeight
    ) {
        return findBlockingHit(
            originX,
            originY,
            directionX,
            directionY,
            maxDistance,
            footprintHalfWidth,
            footprintHalfHeight,
            null
        );
    }

    private BlockingHit findBlockingHit(
        float originX,
        float originY,
        float directionX,
        float directionY,
        float maxDistance,
        float footprintHalfWidth,
        float footprintHalfHeight,
        Zone ignoredZone
    ) {
        float nearest = collision.raycast(
            originX,
            originY,
            directionX,
            directionY,
            maxDistance,
            footprintHalfWidth,
            footprintHalfHeight
        );
        Zone nearestZone = null;

        for (Zone zone : zones) {
            if (zone == ignoredZone || !zone.getKind().isSolid()) {
                continue;
            }

            float distance = zoneCollider(zone).raycast(
                originX,
                originY,
                directionX,
                directionY,
                Math.min(maxDistance, nearest),
                footprintHalfWidth,
                footprintHalfHeight
            );

            if (distance < nearest) {
                nearest = distance;
                nearestZone = zone;
            }
        }

        return new BlockingHit(nearest, nearestZone);
    }

    private RectangleCollider zoneCollider(Zone zone) {
        ZoneKind kind = zone.getKind();
        return new RectangleCollider(
            zone.getX(),
            zone.getY(),
            kind.solidHalfLength(),
            kind.solidHalfThickness()
        );
    }

    private boolean overlapsWorldObstacle(
        float x,
        float y,
        float halfWidth,
        float halfHeight
    ) {
        if (collision.overlaps(x, y, halfWidth, halfHeight)) {
            return true;
        }

        for (Zone zone : zones) {
            if (
                zone.getKind().isSolid()
                && zoneCollider(zone).overlaps(x, y, halfWidth, halfHeight)
            ) {
                return true;
            }
        }

        return false;
    }

    private float[] resolveWorldMovement(
        float x,
        float y,
        float dx,
        float dy,
        float halfWidth,
        float halfHeight
    ) {
        float allowedDx = overlapsWorldObstacle(x + dx, y, halfWidth, halfHeight)
            ? 0f
            : dx;
        float newX = x + allowedDx;
        float allowedDy = overlapsWorldObstacle(newX, y + dy, halfWidth, halfHeight)
            ? 0f
            : dy;

        return new float[] { allowedDx, allowedDy };
    }

    public List<Zone> getZones() {
        return zones;
    }

    // Read-only query for client presentation such as Steam view obscuring.
    public boolean isPlayerOverlappingZoneKind(
        Player player,
        ZoneKind kind
    ) {
        if (player == null || !player.isAlive()) {
            return false;
        }

        for (Zone zone : zones) {
            if (zone.getKind() == kind && zoneOverlapsPlayer(zone, player)) {
                return true;
            }
        }

        return false;
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

            boolean overlaps;

            if (zone.getKind().isSolid()) {
                overlaps = zoneCollider(zone).overlaps(
                    projectile.getX(),
                    projectile.getY(),
                    power.radius(),
                    power.radius()
                );
            } else {
                float dx = projectile.getX() - zone.getX();
                float dy = projectile.getY() - zone.getY();
                float interactionDistance = power.radius() + zone.getRadius();
                overlaps = dx * dx + dy * dy
                    <= interactionDistance * interactionDistance;
            }

            if (!overlaps) {
                continue;
            }

            return applyProjectileReactionWithZone(projectile, zone, events);
        }

        return false;
    }

    private boolean applyProjectileReactionWithZone(
        Projectile projectile,
        Zone zone,
        List<WorldEvent> events
    ) {
        Reactions.Reaction reaction = Reactions.find(
            projectile.getPower(),
            zone.getKind()
        );

        return reaction != null
            && applyReaction(zone, reaction, projectile.getOwnerPlayerId(), events);
    }

    // Solid destructible zones absorb the hit. Their elemental reaction is
    // deferred until the hit that depletes their health, so a Stone Wall is
    // useful as cover instead of transforming on first contact.
    private boolean damageDestructibleZone(
        Projectile projectile,
        Zone zone,
        List<WorldEvent> events
    ) {
        return damageDestructibleZone(
            zone,
            projectile.getPower(),
            projectile.getOwnerPlayerId(),
            events
        );
    }

    private boolean damageDestructibleZone(
        Zone zone,
        Power power,
        int casterPlayerId,
        List<WorldEvent> events
    ) {
        return damageDestructibleZone(
            zone,
            power,
            power.damage(),
            casterPlayerId,
            events
        );
    }

    private boolean damageDestructibleZone(
        Zone zone,
        Power power,
        float damage,
        int casterPlayerId,
        List<WorldEvent> events
    ) {
        if (!zone.isDestructible()) {
            return false;
        }

        Reactions.Reaction reaction = Reactions.find(power, zone.getKind());

        // Stone Wall is an elemental interaction target rather than a generic
        // damage sponge. A spell still collides with it, but only a spell that
        // can transform Stone is allowed to reduce its health.
        if (zone.getKind() == ZoneKinds.STONE_WALL && reaction == null) {
            return true;
        }

        zone.takeDamage(damage);

        if (!zone.hasBeenDestroyed()) {
            return true;
        }

        if (reaction != null) {
            applyReaction(zone, reaction, casterPlayerId, events);
        } else {
            zones.remove(zone);
        }

        return true;
    }

    private boolean tryCaptureProjectile(Projectile projectile) {
        if (projectile.getPower() == Powers.VORTEX) {
            return false;
        }

        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone zone = zones.get(i);

            if (!zone.getKind().capturesProjectiles()) {
                continue;
            }

            if (
                captureProjectile(
                    projectile,
                    new ZoneVortexSource(zone, vortexSourceKey(zone))
                )
            ) {
                return true;
            }
        }

        for (Projectile source : projectiles) {
            if (source == projectile || source.getPower() != Powers.VORTEX) {
                continue;
            }

            if (
                captureProjectile(
                    projectile,
                    new ProjectileVortexSource(source)
                )
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean captureProjectile(
        Projectile projectile,
        VortexSource source
    ) {
        if (projectile.hasCompletedVortexOrbit(source.key())) {
            return false;
        }

        float dx = projectile.getX() - source.x();
        float dy = projectile.getY() - source.y();
        float overlap = projectile.getPower().radius() + source.radius();

        if (dx * dx + dy * dy > overlap * overlap) {
            return false;
        }

        float angle = (float) Math.atan2(dy, dx);
        int mixed = projectile.getId() * 1_103_515_245 + source.key() * 12_345;
        float fraction = (mixed & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        float rotations = 0.5f + fraction;
        VortexOrbit orbit = new VortexOrbit(
            source,
            angle,
            rotations * (float) (Math.PI * 2.0)
        );
        vortexOrbits.put(projectile.getId(), orbit);
        positionProjectileOnOrbit(projectile, orbit, 0f);
        return true;
    }

    private void updateVortexOrbit(
        Projectile projectile,
        VortexOrbit orbit
    ) {
        float angularStep = VORTEX_ORBIT_SPEED / orbit.source().radius() * STEP;
        float actualStep = Math.min(angularStep, orbit.radiansRemaining());
        orbit.advance(actualStep);
        positionProjectileOnOrbit(
            projectile,
            orbit,
            orbit.source().radius() * actualStep
        );

        if (orbit.radiansRemaining() > 0.0001f) {
            return;
        }

        vortexOrbits.remove(projectile.getId());
        releaseProjectileFromVortex(projectile, orbit);
    }

    private void releaseVortexOrbitsFromSource(int sourceKey) {
        Iterator<Map.Entry<Integer, VortexOrbit>> iterator =
            vortexOrbits.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Integer, VortexOrbit> entry = iterator.next();
            VortexOrbit orbit = entry.getValue();

            if (orbit.source().key() != sourceKey) {
                continue;
            }

            Projectile projectile = projectileById(entry.getKey());
            if (projectile != null) {
                releaseProjectileFromVortex(projectile, orbit);
            }
            iterator.remove();
        }
    }

    private Projectile projectileById(int projectileId) {
        for (Projectile projectile : projectiles) {
            if (projectile.getId() == projectileId) {
                return projectile;
            }
        }

        return null;
    }

    private void releaseProjectileFromVortex(
        Projectile projectile,
        VortexOrbit orbit
    ) {
        float tangentX = -(float) Math.sin(orbit.angle());
        float tangentY = (float) Math.cos(orbit.angle());
        int owner = orbit.source().ownerPlayerId() == Zone.NO_PLAYER
            ? projectile.getOwnerPlayerId()
            : orbit.source().ownerPlayerId();
        projectile.releaseFromVortex(
            orbit.source().key(),
            tangentX,
            tangentY,
            owner
        );
    }

    private int vortexSourceKey(Zone zone) {
        return vortexZoneSourceKeys.getOrDefault(zone.getId(), zone.getId());
    }

    private static int projectileVortexSourceKey(Projectile projectile) {
        return -projectile.getId() - 1;
    }

    private void positionProjectileOnOrbit(
        Projectile projectile,
        VortexOrbit orbit,
        float orbitDistance
    ) {
        float cosine = (float) Math.cos(orbit.angle());
        float sine = (float) Math.sin(orbit.angle());
        float tangentX = -sine;
        float tangentY = cosine;
        projectile.orbitTo(
            orbit.source().x() + cosine * orbit.source().radius(),
            orbit.source().y() + sine * orbit.source().radius(),
            tangentX,
            tangentY,
            orbitDistance
        );
    }

    private void applyVortexImpulses() {
        Set<VortexPlayerContact> currentContacts = new HashSet<>();

        for (Zone zone : zones) {
            if (zone.getKind().pushImpulse() <= 0f) {
                continue;
            }

            applyVortexImpulse(
                new ZoneVortexSource(zone, vortexSourceKey(zone)),
                zone.getKind().pushImpulse(),
                currentContacts
            );
        }

        for (Projectile projectile : projectiles) {
            if (projectile.getPower() != Powers.VORTEX) {
                continue;
            }

            applyVortexImpulse(
                new ProjectileVortexSource(projectile),
                ZoneKinds.VORTEX.pushImpulse(),
                currentContacts
            );
        }

        vortexPlayerContacts.retainAll(currentContacts);
    }

    private void applyVortexImpulse(
        VortexSource source,
        float impulseSpeed,
        Set<VortexPlayerContact> currentContacts
    ) {
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            Player player = entry.getValue();

            if (
                !player.isAlive()
                || !circleOverlapsPlayer(source.x(), source.y(), source.radius(), player)
            ) {
                continue;
            }

            VortexPlayerContact contact = new VortexPlayerContact(
                source.key(),
                entry.getKey()
            );
            currentContacts.add(contact);

            if (vortexPlayerContacts.contains(contact)) {
                continue;
            }

            float dx = player.getCollisionX() - source.x();
            float dy = player.getCollisionY() - source.y();
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            float directionX = length > 0.0001f ? dx / length : player.getAimX();
            float directionY = length > 0.0001f ? dy / length : player.getAimY();
            player.addKnockbackVelocity(
                directionX * impulseSpeed,
                directionY * impulseSpeed
            );
        }

        vortexPlayerContacts.addAll(currentContacts);
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
                releaseVortexOrbitsFromSource(vortexSourceKey(zone));
                vortexZoneSourceKeys.remove(zone.getId());
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
        Power beam,
        Zone ignoredZone
    ) {
        LightningHit nearest = LightningHit.none();

        BlockingHit blockingHit =
            findBlockingHit(
                originX,
                originY,
                directionX,
                directionY,
                maxDistance,
                halfWidth,
                halfWidth,
                ignoredZone
            );

        if (!Float.isInfinite(blockingHit.distance())) {
            if (
                beam == Powers.BEAM_OF_LIGHT
                    && blockingHit.zone() != null
                    && blockingHit.zone().getKind() == ZoneKinds.CRYSTAL_WALL
            ) {
                nearest = LightningHit.crystal(
                    blockingHit.distance(),
                    blockingHit.zone()
                );
            } else if (
                blockingHit.zone() != null
                    && blockingHit.zone().isDestructible()
            ) {
                nearest = LightningHit.wall(
                    blockingHit.distance(),
                    blockingHit.zone()
                );
            } else {
                nearest = LightningHit.terrain(blockingHit.distance());
            }
        }

        for (Zone zone : zones) {
            if (
                zone == ignoredZone
                    || zone.isDestructible()
                    || zone.getKind().isSolid()
                    || Reactions.find(beam, zone.getKind()) == null
            ) {
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
        return circleOverlapsDummy(
            zone.getX(),
            zone.getY(),
            zone.getRadius(),
            dummy
        );
    }

    private boolean circleOverlapsDummy(
        float x,
        float y,
        float radius,
        TargetDummy dummy
    ) {
        float dx = x - dummy.getX();
        float dy = y - dummy.getY();
        float overlapDistance = radius + TargetDummy.RADIUS;

        return dx * dx + dy * dy
            <= overlapDistance * overlapDistance;
    }

    private boolean zoneOverlapsPlayer(Zone zone, Player player) {
        return circleOverlapsPlayer(
            zone.getX(),
            zone.getY(),
            zone.getRadius(),
            player
        );
    }

    private boolean circleOverlapsPlayer(
        float x,
        float y,
        float radius,
        Player player
    ) {
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
            Math.min(x, playerRight)
        );

        float closestY = Math.max(
            playerBottom,
            Math.min(y, playerTop)
        );

        float dx = x - closestX;
        float dy = y - closestY;

        return dx * dx + dy * dy
            <= radius * radius;
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
                projectile.restoreState(
                    state.x(),
                    state.y(),
                    state.directionX(),
                    state.directionY(),
                    state.ownerPlayerId()
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
        vortexOrbits.clear();
        vortexZoneSourceKeys.clear();

        for (WorldSnapshot.ZoneState state : snapshot.zones()) {
            Zone zone = previousZones.remove(state.id());

            if (zone == null || !zone.getKind().equals(state.kind())) {
                zone = createZoneFromSnapshot(state);
            }

            zone.setDamageOwnerPlayerId(
                state.damageOwnerPlayerId()
            );
            zone.restoreHealth(state.health());

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
        Zone zone = Zone.create(
            state.id(),
            state.kind(),
            state.x(),
            state.y(),
            state.radius(),
            Float.MAX_VALUE,
            state.damageOwnerPlayerId()
        );
        zone.restoreHealth(state.health());
        return zone;
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
                power,
                null
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

            case WALL ->
                damageDestructibleZone(
                    hit.zone(),
                    power,
                    playerId,
                    events
                );

            case CRYSTAL -> {
                castCrystalSplitBeams(
                    playerId,
                    power,
                    hit.zone(),
                    directionX,
                    directionY,
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

        BeamStrikeEvent.EndReason endReason =
            switch (hit.kind()) {
                case NONE ->
                    BeamStrikeEvent.EndReason.RANGE_LIMIT;

                case TERRAIN ->
                    BeamStrikeEvent.EndReason.TERRAIN;

                case WALL ->
                    BeamStrikeEvent.EndReason.WALL;

                case CRYSTAL ->
                    BeamStrikeEvent.EndReason.WALL;

                case PUDDLE ->
                    BeamStrikeEvent.EndReason.ZONE_REACTION;

                case PLAYER ->
                    BeamStrikeEvent.EndReason.PLAYER;

                case DUMMY ->
                    BeamStrikeEvent.EndReason.DUMMY;
            };

        events.add(
            new BeamStrikeEvent(
                playerId,
                power,
                originX,
                originY,
                originX + directionX * strikeDistance,
                originY + directionY * strikeDistance,
                endReason
            )
        );
    }

    private void castCrystalSplitBeams(
        int playerId,
        Power power,
        Zone crystal,
        float incomingDirectionX,
        float incomingDirectionY,
        List<WorldEvent> events
    ) {
        float halfSplitRadians = (float) Math.toRadians(22.5f);

        for (int branch = 1; branch <= 2; branch++) {
            float angle = branch == 1 ? -halfSplitRadians : halfSplitRadians;
            float[] direction = rotateDirection(
                incomingDirectionX,
                incomingDirectionY,
                angle
            );

            LightningHit hit = findLightningHit(
                playerId,
                crystal.getX(),
                crystal.getY(),
                direction[0],
                direction[1],
                Powers.BEAM_OF_LIGHT_CRYSTAL_RANGE,
                power.radius(),
                power,
                crystal
            );

            applyCrystalSplitHit(playerId, power, hit, events);

            float strikeDistance = hit.kind() == LightningHit.Kind.NONE
                ? Powers.BEAM_OF_LIGHT_CRYSTAL_RANGE
                : hit.distance();

            events.add(new BeamStrikeEvent(
                playerId,
                power,
                crystal.getX(),
                crystal.getY(),
                crystal.getX() + direction[0] * strikeDistance,
                crystal.getY() + direction[1] * strikeDistance,
                beamEndReason(hit.kind()),
                branch
            ));
        }
    }

    private void applyCrystalSplitHit(
        int playerId,
        Power power,
        LightningHit hit,
        List<WorldEvent> events
    ) {
        float splitDamage = power.damage()
            * Powers.BEAM_OF_LIGHT_CRYSTAL_DAMAGE_MULTIPLIER;

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

            case WALL ->
                damageDestructibleZone(
                    hit.zone(),
                    power,
                    splitDamage,
                    playerId,
                    events
                );

            case PLAYER ->
                damagePlayer(hit.playerId(), splitDamage, playerId);

            case DUMMY ->
                hit.dummy().takeDamage(splitDamage);

            case NONE, TERRAIN, CRYSTAL -> {
                // A split ray stops at terrain or another crystal. Crystals do
                // not recursively multiply beams or take light-beam damage.
            }
        }
    }

    private BeamStrikeEvent.EndReason beamEndReason(LightningHit.Kind kind) {
        return switch (kind) {
            case NONE -> BeamStrikeEvent.EndReason.RANGE_LIMIT;
            case TERRAIN -> BeamStrikeEvent.EndReason.TERRAIN;
            case WALL, CRYSTAL -> BeamStrikeEvent.EndReason.WALL;
            case PUDDLE -> BeamStrikeEvent.EndReason.ZONE_REACTION;
            case PLAYER -> BeamStrikeEvent.EndReason.PLAYER;
            case DUMMY -> BeamStrikeEvent.EndReason.DUMMY;
        };
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

            float damage = projectile.getPower().damage();

            Player attacker =
                players.get(projectile.getOwnerPlayerId());

            Zone attackerZone =
                attacker == null
                    ? null
                    : topZoneOverlapping(attacker);

            if (
                attackerZone != null
                && attackerZone.getKind() == ZoneKinds.RADIANT_CIRCLE
            ) {
                damage *= 1.25f;
            }

            damagePlayer(
                targetPlayerId,
                damage,
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
                accepted = placeZoneAtTargetOrTerrain(
                    power.zoneKind(),
                    player.getCastX(),
                    player.getCastY(),
                    target,
                    power.radius(),
                    playerId
                );
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

    private record BlockingHit(float distance, Zone zone) {
    }

    private record ChannelKey(int playerId, int slot) {
    }

    private record VortexPlayerContact(int sourceKey, int playerId) {
    }

    private interface VortexSource {
        int key();
        float x();
        float y();
        float radius();
        int ownerPlayerId();
    }

    private record ZoneVortexSource(
        Zone zone,
        int sourceKey
    ) implements VortexSource {
        @Override
        public int key() { return sourceKey; }
        @Override
        public float x() { return zone.getX(); }
        @Override
        public float y() { return zone.getY(); }
        @Override
        public float radius() { return zone.getRadius(); }
        @Override
        public int ownerPlayerId() { return zone.getDamageOwnerPlayerId(); }
    }

    private record ProjectileVortexSource(Projectile projectile)
        implements VortexSource {

        @Override
        public int key() { return projectileVortexSourceKey(projectile); }
        @Override
        public float x() { return projectile.getX(); }
        @Override
        public float y() { return projectile.getY(); }
        @Override
        public float radius() { return ZoneKinds.VORTEX.radius(); }
        @Override
        public int ownerPlayerId() { return projectile.getOwnerPlayerId(); }
    }

    private static final class VortexOrbit {

        private final VortexSource source;
        private float angle;
        private float radiansRemaining;

        private VortexOrbit(
            VortexSource source,
            float angle,
            float radiansRemaining
        ) {
            this.source = source;
            this.angle = angle;
            this.radiansRemaining = radiansRemaining;
        }

        private VortexSource source() { return source; }
        private float angle() { return angle; }
        private float radiansRemaining() { return radiansRemaining; }

        private void advance(float radians) {
            angle += radians;
            radiansRemaining = Math.max(0f, radiansRemaining - radians);
        }
    }

    private static final class ActiveChannel {

        private final Power power;
        private float secondsUntilTick;

        private ActiveChannel(Power power, float secondsUntilTick) {
            this.power = power;
            this.secondsUntilTick = secondsUntilTick;
        }

        private Power power() {
            return power;
        }

        private boolean advance(float deltaSeconds) {
            secondsUntilTick -= deltaSeconds;

            if (secondsUntilTick > 0f) {
                return false;
            }

            do {
                secondsUntilTick += power.channelTickSeconds();
            } while (secondsUntilTick <= 0f);

            return true;
        }
    }
}
