package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.entity.TargetDummy;
import io.github.coda45.hexadence.input.PlayerCommand;

/** Owns gameplay state and applies commands at a fixed 60 Hz rate. */
public class ArenaWorld {

    private static final float PLAYER_SPEED = 5f;
    public static final float STEP = 1f / 60f;
    private static final float PROJECTILE_SPEED = 10f;
    private static final float PROJECTILE_RADIUS = 0.12f; //radius and damage for testing purposes, 
    private static final float PROJECTILE_DAMAGE = 10f; //will be change later
    private static final float DUMMY_RESPAWN_DELAY = 2f; //2 seconds respawn

    private final ArenaMap arenaMap;
    private final Player player;
    private final ArrayList<Projectile> projectiles = new ArrayList<>();
    private final List<TargetDummy> dummies = new ArrayList<>(); // For placing test dummies.
    private float dummyRespawnTimer = -1f;
    private float dummySpawnX;
    private float dummySpawnY;

    public ArenaWorld() {
        arenaMap = new ArenaMap();

        player = new Player(10f, 10f);

        //Target dummies testing only
        spawnDummy(15f, 10f);
        //End target dummies
    }

    /** Advances exactly one STEP, regardless of the render frame rate. */
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

        // New shots also move this step. Projectiles have no collision or expiry yet.
        for (Projectile projectile : projectiles) {

            projectile.move(
                PROJECTILE_SPEED * STEP
            );
        }

        //Target dummies & projectile collision testing
        Iterator<Projectile> projectileIterator = projectiles.iterator();
        while (projectileIterator.hasNext()) {
            Projectile projectile = projectileIterator.next();
            boolean hit = false;

            for (TargetDummy dummy : dummies) {
                if (!dummy.isAlive()) {
                    continue;
                }

                float dx = projectile.getX() - dummy.getX();
                float dy = projectile.getY() - dummy.getY();
                float hitDistance = PROJECTILE_RADIUS + TargetDummy.RADIUS;

                if (dx * dx + dy * dy <= hitDistance * hitDistance) {
                    dummy.takeDamage(PROJECTILE_DAMAGE);
                    hit = true;
                    break;
                }
            }

            if (hit) {
                projectileIterator.remove();
            }
        }
        //End target dummies & projectile collision testing

        //Target dummies testing only - respawn after delay
        boolean anyDeadDummy = false;
        for (TargetDummy dummy : dummies) {
            if (!dummy.isAlive()) {
                anyDeadDummy = true;
                break;
            }
        }

        if (anyDeadDummy && dummyRespawnTimer < 0f) {
            dummyRespawnTimer = DUMMY_RESPAWN_DELAY;
        }

        if (dummyRespawnTimer >= 0f) {
            dummyRespawnTimer -= STEP;

        if (dummyRespawnTimer <= 0f) {
            dummies.removeIf(dummy -> !dummy.isAlive());
            spawnDummy(dummySpawnX, dummySpawnY);
            dummyRespawnTimer = -1f;
        }
    }
    //End target dummies
    
        float dx = moveX * PLAYER_SPEED * STEP;
        float dy = moveY * PLAYER_SPEED * STEP;

        float[] resolved = arenaMap.getCollision().resolveMovement(
            player.getX(), player.getY(),
            dx, dy,
            Player.HALF_WIDTH, Player.HALF_HEIGHT);

        player.move(resolved[0], resolved[1]);
    }

    /** Returns the live list for rendering; callers should not modify it. */
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

    //Target dummies testing only
    public void spawnDummy (float x, float y) {
        dummySpawnX = x;
        dummySpawnY = y;
        dummies.add(new TargetDummy(x, y));
    }

    public List<TargetDummy> getDummies() {
        return dummies;
    }
    //End target dummies
}
