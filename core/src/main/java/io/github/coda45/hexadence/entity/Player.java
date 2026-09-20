package io.github.coda45.hexadence.entity;

import com.badlogic.gdx.math.Vector2;

import io.github.coda45.hexadence.input.PlayerCommand;

public class Player {
    
    // World units per second.
    private static final float SPEED = 5f;
    private float x;
    private float y;

    public Player(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void move(float dx, float dy) {
        x += dx;
        y += dy;
    }

    /*
    Applies one frame's movement command: normalizes diagonal input so
    holding two directions isn't faster than one, then scales by speed and delta time.
     */
    public void applyCommand(PlayerCommand command, float delta) {
        Vector2 move = new Vector2(command.moveX(), command.moveY());

        if (move.len2() > 1f) {
            move.nor();
        }

        move(move.x * SPEED * delta, move.y * SPEED * delta);
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }
}
