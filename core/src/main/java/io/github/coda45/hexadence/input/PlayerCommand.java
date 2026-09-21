package io.github.coda45.hexadence.input;

/*
 * Intent for one simulation step. Movement and aim may be unnormalized;
 * aim is a direction from the cast point, not a mouse position.
 * castPrimary requests a single shot, rather than holding the fire button down.
 */
public record PlayerCommand(
    float moveX,
    float moveY,
    float aimX,
    float aimY,
    boolean castPrimary
) {

    public static final PlayerCommand IDLE = new PlayerCommand(
        0f, 0f, 0f, 0f, false);
}
