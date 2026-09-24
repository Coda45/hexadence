package io.github.coda45.hexadence.spell;

public enum SpellType {

    EMBER(0.50f, 12f, 10f, 8f),
    WATER(0.85f, 10f, 5f, 8f),
    LIGHTNING(0.70f, 14f, 15f, 8f);

    private final float cooldownSeconds;
    private final float speed;
    private final float damage;
    private final float range;

    SpellType(
        float cooldownSeconds,
        float speed,
        float damage,
        float range
    ) {
        this.cooldownSeconds = cooldownSeconds;
        this.speed = speed;
        this.damage = damage;
        this.range = range;
    }

    public float getCooldownSeconds() {
        return cooldownSeconds;
    }

    public float getSpeed() {
        return speed;
    }

    public float getDamage() {
        return damage;
    }

    public float getRange() {
        return range;
    }
}