package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.entity.Zone;
import io.github.coda45.hexadence.input.PlayerCommand;
import io.github.coda45.hexadence.spell.SpellType;

// Owns gameplay state and applies commands at a fixed 60 Hz rate.
public class ArenaWorld {

    private static final float PLAYER_SPEED = 5f;
    public static final float STEP = 1f / 60f;
    // Temporary combat values for the target dummy test.
    private static final float PROJECTILE_RADIUS = 0.12f;
    private static final float MAX_PROJECTILE_SUBSTEP = PROJECTILE_RADIUS;
    private static final float DUMMY_RESPAWN_DELAY = 2f;
    private static final float DISCHARGE_DAMAGE = 20f;

    private final ArenaCollision collision;
    private final Player player;
    private final ArrayList<Projectile> projectiles = new ArrayList<>();
    private final List<TargetDummy> dummies = new ArrayList<>();

    private final List<Zone> zones = new ArrayList<>();

    public ArenaWorld(ArenaDefinition definition) {
        collision = definition.collision();

        SpawnPoint playerSpawn = definition.playerSpawn();

        player = new Player(
            playerSpawn.x(),
            playerSpawn.y()
        );

        for (SpawnPoint dummySpawn : definition.dummySpawns()) {
            addDummyAtSpawn(dummySpawn);
        }
    }

    // Advances exactly one STEP, regardless of the render frame rate.
    public void step(PlayerCommand command) {

        Iterator<Zone> zoneIterator = zones.iterator();

        while (zoneIterator.hasNext()) {
            Zone zone = zoneIterator.next();

            zone.update(STEP);

            if (zone.hasExpired()) {
                zoneIterator.remove();
            }
        }

        float moveX = command.moveX();
        float moveY = command.moveY();

        float aimX = command.aimX();
        float aimY = command.aimY();

        float aimLengthSquared = (aimX * aimX) + (aimY * aimY);

        // Keep the last direction when the cursor is too close to the cast point.
        if (aimLengthSquared > 0.0001f) {
            float aimLength = (float) Math.sqrt(aimLengthSquared);

            player.setAim(
                aimX / aimLength,
                aimY / aimLength
            );
        }

        player.updateCooldowns(STEP);

        // Spawn before player movement, using the aim chosen for this step.
        tryCast(command.castEmber(), SpellType.EMBER);
        tryCast(command.castWater(), SpellType.WATER);
        tryCast(command.castLightning(), SpellType.LIGHTNING);

        float lengthSquared =
            moveX * moveX +
            moveY * moveY;

        // Cap diagonal input so it cannot move faster than a single direction.
        if (lengthSquared > 1f) {
            float length = (float) Math.sqrt(lengthSquared);

            moveX /= length;
            moveY /= length;
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

    
        float dx = moveX * PLAYER_SPEED * STEP;
        float dy = moveY * PLAYER_SPEED * STEP;

        float[] resolved = collision.resolveMovement(
            player.getCollisionX(),
            player.getCollisionY(),
            dx, dy,
            Player.HALF_WIDTH, Player.HALF_HEIGHT);

        player.move(resolved[0], resolved[1]);
        applyChargedPuddleDischarges();
        updateDummyRespawns();
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
            Zone.createPuddle(x, y)
        );
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
                        zone.getY()
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

    private void applyChargedPuddleDischarges() {
        for (Zone zone : zones) {
            if (
                zone.getType() != Zone.Type.PUDDLE
                || !zone.isElectrified()
            ) {
                continue;
            }

            boolean discharged = false;

            if (
                player.isAlive()
                && zoneOverlapsPlayer(zone)
            ) {
                player.takeDamage(DISCHARGE_DAMAGE);
                discharged = true;
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

    private boolean zoneOverlapsPlayer(Zone zone) {
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

    private void addDummyAtSpawn(SpawnPoint spawn) {
        dummies.add(
            new TargetDummy(
                spawn.x(),
                spawn.y()
            )
        );
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

    public Player getPlayer() {
        return player;
    }

    private void tryCast(
        boolean requested,
        SpellType spellType
    ) {
        if (!requested || !player.isSpellReady(spellType)) {
            return;
        }

        projectiles.add(
            new Projectile(
                player.getCastX(),
                player.getCastY(),
                player.getAimX(),
                player.getAimY(),
                spellType
            )
        );

        player.startCooldown(spellType);
    }

    // Returns the live list for rendering, including dead dummies awaiting respawn.
    public List<TargetDummy> getDummies() {
        return dummies;
    }
}
