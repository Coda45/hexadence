package io.github.coda45.hexadence.world;

import java.util.ArrayList;
import java.util.List;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.entity.Projectile;
import io.github.coda45.hexadence.input.PlayerCommand;

public class ArenaWorld {

    private static final float PLAYER_SPEED = 5f;
    public static final float STEP = 1f / 60f;
    private static final float PROJECTILE_SPEED = 10f;

    private final ArenaMap arenaMap;
    private final Player player;
    private final ArrayList<Projectile> projectiles = new ArrayList<>();

    public ArenaWorld() {
        arenaMap = new ArenaMap();

        player = new Player(10f, 10f);
    }

    public void step(PlayerCommand command) {

        float moveX = command.moveX();
        float moveY = command.moveY();

        float aimX = command.aimX();
        float aimY = command.aimY();

        float aimLengthSquared =
            aimX * aimX +
            aimY * aimY;

        if (aimLengthSquared > 0.0001f) {
            float aimLength = (float) Math.sqrt(aimLengthSquared);

            player.setAim(
                aimX / aimLength,
                aimY / aimLength
            );
        }

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

        if (lengthSquared > 1f) {
            float length = (float) Math.sqrt(lengthSquared);

            moveX /= length;
            moveY /= length;
        }

        for (Projectile projectile : projectiles) {

            projectile.move(
                PROJECTILE_SPEED * STEP
            );
        }

        float dx = moveX * PLAYER_SPEED * STEP;
        float dy = moveY * PLAYER_SPEED * STEP;

        float[] resolved = arenaMap.getCollision().resolveMovement(
            player.getX(), player.getY(),
            dx, dy,
            Player.HALF_WIDTH, Player.HALF_HEIGHT);

        player.move(resolved[0], resolved[1]);
    }

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
}
