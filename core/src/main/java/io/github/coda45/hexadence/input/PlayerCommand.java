package io.github.coda45.hexadence.input;

/*
 * Intent for one simulation step. Movement and aim may be unnormalized;
 * aim is a direction from the cast point, not a mouse position.
 * Cast booleans request spells; they do not guarantee that a projectile spawns.
 */
public record PlayerCommand(
    float moveX,
    float moveY,
    float aimX,
    float aimY,
    boolean castEmber,
    boolean castWater,
    boolean castLightning
) {

    public static final PlayerCommand IDLE = new PlayerCommand(
        0f, 0f,
        0f, 0f,
        false, false, false
    );
}
