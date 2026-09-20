package io.github.coda45.hexadence.world;

import io.github.coda45.hexadence.entity.Player;
import io.github.coda45.hexadence.input.PlayerCommand;

public class ArenaWorld {

    private static final float PLAYER_SPEED = 5f;

    private final Player player;

    public ArenaWorld() {
        player = new Player(0f, 0f);
    }

    public void update(PlayerCommand command, float delta) {

        float moveX = command.moveX();
        float moveY = command.moveY();

        float lengthSquared =
            moveX * moveX +
            moveY * moveY;

        if (lengthSquared > 1f) {
            float length = (float) Math.sqrt(lengthSquared);

            moveX /= length;
            moveY /= length;
        }

        float dx = moveX * PLAYER_SPEED * delta;
        float dy = moveY * PLAYER_SPEED * delta;

        player.move(dx, dy);
    }

    public Player getPlayer() {
        return player;
    }
}