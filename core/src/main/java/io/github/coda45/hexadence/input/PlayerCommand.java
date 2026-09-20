package io.github.coda45.hexadence.input;

public record PlayerCommand(
    float moveX,
    float moveY
) {

    public static final PlayerCommand IDLE = new PlayerCommand(0f, 0f);
}