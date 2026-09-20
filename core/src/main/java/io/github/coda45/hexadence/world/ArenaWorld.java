package io.github.coda45.hexadence.world;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;

public class ArenaWorld {

    private static final float PLAYER_SPEED = 5f;

    private final ArenaMap arenaMap;
    private final Player player;

    public ArenaWorld() {
        arenaMap = new ArenaMap();

        player = new Player(10f, 10f);
    }

    public void update(PlayerCommand command, float delta) {

        float moveX = command.moveX();
        float moveY = command.moveY();

        float lengthSquared =
            moveX * moveX +
            moveY * moveY;

        if (lengthSquared > 1f) {
            float length =
                (float) Math.sqrt(lengthSquared);

            moveX /= length;
            moveY /= length;
        }

        player.move(
            moveX * PLAYER_SPEED * delta,
            moveY * PLAYER_SPEED * delta
        );
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