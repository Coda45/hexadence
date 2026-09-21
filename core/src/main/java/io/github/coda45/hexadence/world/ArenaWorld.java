package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.input.PlayerCommand;

// Owns gameplay state and applies commands at a fixed 60 Hz rate.
public class ArenaWorld {

    private static final float PLAYER_SPEED = 5f;
    public static final float STEP = 1f / 60f;
    private static final float PROJECTILE_SPEED = 10f;
    // Temporary combat values for the target dummy test.
    private static final float PROJECTILE_RADIUS = 0.12f;
    private static final float PROJECTILE_DAMAGE = 10f;
    private static final float DUMMY_RESPAWN_DELAY = 2f;

    private final ArenaMap arenaMap;
    private final Player player;
    private final ArrayList<Projectile> projectiles = new ArrayList<>();
    private final List<TargetDummy> dummies = new ArrayList<>();
    // A negative timer means no respawn is pending. All dummies share this timer and spawn point.
    private float dummyRespawnTimer = -1f;
    private float dummySpawnX;
    private float dummySpawnY;

    public ArenaWorld() {
        arenaMap = new ArenaMap();

        player = new Player(10f, 10f);

        spawnDummy(15f, 10f);
    }

    // Advances exactly one STEP, regardless of the render frame rate.
    public void step(PlayerCommand command) {

        float moveX = command.moveX();
        float moveY = command.moveY();

        float aimX = command.aimX();
        float aimY = command.aimY();

        float aimLengthSquared =
            aimX * aimX +
            aimY * aimY;

        // Keep the last direction when the cursor is too close to the cast point.
        if (aimLengthSquared > 0.0001f) {
            float aimLength = (float) Math.sqrt(aimLengthSquared);

            player.setAim(
                aimX / aimLength,
                aimY / aimLength
            );
        }

        // Spawn before player movement, using the aim chosen for this step.
        if (command.castPrimary()) {

            projectiles.add(
                new Projectile(
                    player.getCastX(),
                    player.getCastY(),
                    player.getAimX(),
                    player.getAimY()
                )
            );
        }

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
        Iterator<Projectile> projectileIterator = projectiles.iterator();
        while (projectileIterator.hasNext()) {
            Projectile projectile = projectileIterator.next();

            projectile.update(PROJECTILE_SPEED * STEP, STEP);

            // Expiry and walls take priority over dummy hits. Tile checks use a square footprint.
            if (projectile.hasExpired()
                || arenaMap.getCollision().overlaps(
                    projectile.getX(),
                    projectile.getY(),
                    PROJECTILE_RADIUS,
                    PROJECTILE_RADIUS
                )) {

                projectileIterator.remove();
                continue;
            }

            boolean hit = false;

            for (TargetDummy dummy : dummies) {
                if (!dummy.isAlive()) {
                    continue;
                }

                float dx = projectile.getX() - dummy.getX();
                float dy = projectile.getY() - dummy.getY();
                float hitDistance = PROJECTILE_RADIUS + TargetDummy.RADIUS;

                // Circle overlap using squared distance avoids a square root for every dummy.
                if (dx * dx + dy * dy <= hitDistance * hitDistance) {
                    dummy.takeDamage(PROJECTILE_DAMAGE);
                    hit = true;
                    // A shot damages only the first live dummy it overlaps.
                    break;
                }
            }

            if (hit) {
                projectileIterator.remove();
            }
        }
        // Dead dummies stay visible until the shared respawn timer finishes.
        boolean anyDeadDummy = false;
        for (TargetDummy dummy : dummies) {
            if (!dummy.isAlive()) {
                anyDeadDummy = true;
                break;
            }
        }

        // Start once; restarting every step would prevent the timer from finishing.
        if (anyDeadDummy && dummyRespawnTimer < 0f) {
            dummyRespawnTimer = DUMMY_RESPAWN_DELAY;
        }

        if (dummyRespawnTimer >= 0f) {
            dummyRespawnTimer -= STEP;

            if (dummyRespawnTimer <= 0f) {
                // This test setup replaces all dead dummies with one at the last spawn point.
                dummies.removeIf(dummy -> !dummy.isAlive());
                spawnDummy(dummySpawnX, dummySpawnY);
                dummyRespawnTimer = -1f;
            }
        }
    
        float dx = moveX * PLAYER_SPEED * STEP;
        float dy = moveY * PLAYER_SPEED * STEP;

        float[] resolved = arenaMap.getCollision().resolveMovement(
            player.getX(), player.getY(),
            dx, dy,
            Player.HALF_WIDTH, Player.HALF_HEIGHT);

        player.move(resolved[0], resolved[1]);
    }

    // Returns the live list for rendering; callers should not modify it.
    public List<Projectile> getProjectiles() {
        return projectiles;
    }

    public Player getPlayer() {
        return player;
    }

    public ArenaMap getArenaMap() {
        return arenaMap;
    }

    public void dispose() {
        arenaMap.dispose();
    }

    // Adds a test dummy and sets the shared location used for the next respawn.
    public void spawnDummy (float x, float y) {
        dummySpawnX = x;
        dummySpawnY = y;
        dummies.add(new TargetDummy(x, y));
    }

    // Returns the live list for rendering, including dead dummies awaiting respawn.
    public List<TargetDummy> getDummies() {
        return dummies;
    }
}
