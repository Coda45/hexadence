package io.github.coda45.hexadence.zone;

import java.util.Objects;

import io.github.coda45.hexadence.power.Element;
import io.github.coda45.hexadence.power.Weapon;

// One kind of zone: what it is, its size and lifetime, and what it does to
// anyone standing in it. All kinds are defined in ZoneKinds, using the builder
// below, so each definition reads like its zone card.
//
// Units match Power: tiles, seconds, health points.
//
// Effects apply to everyone in the zone, its creator included. Where zones
// overlap, only the newest one counts at that spot.
public final class ZoneKind {

    private final int id;
    private final String name;
    private final ZoneOrigin origin;
    private final Element element;
    private final Weapon weapon;

    private final float radius;
    private final float innerRadius;
    private final float duration;

    private final float damagePerSecond;
    private final float speedMultiplier;
    private final float solidHalfLength;
    private final float solidHalfThickness;
    private final float maxHealth;
    private final boolean capturesProjectiles;
    private final float pushImpulse;
    private final float gravityRadius;
    private final int gravityLevels;
    private final float projectileGravityAccelerationStep;
    private final float playerGravitySpeedStep;
    private final float expirationExplosionRadius;
    private final float expirationExplosionDamage;
    private final float driftSpeed;

    private final boolean coversPlayers;
    private final int tint;
    private final String plannedEffect;

    private ZoneKind(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.origin = b.origin;
        this.element = b.element;
        this.weapon = b.weapon;
        this.radius = b.radius;
        this.innerRadius = b.innerRadius;
        this.duration = b.duration;
        this.damagePerSecond = b.damagePerSecond;
        this.speedMultiplier = b.speedMultiplier;
        this.solidHalfLength = b.solidHalfLength;
        this.solidHalfThickness = b.solidHalfThickness;
        this.maxHealth = b.maxHealth;
        this.capturesProjectiles = b.capturesProjectiles;
        this.pushImpulse = b.pushImpulse;
        this.gravityRadius = b.gravityRadius;
        this.gravityLevels = b.gravityLevels;
        this.projectileGravityAccelerationStep = b.projectileGravityAccelerationStep;
        this.playerGravitySpeedStep = b.playerGravitySpeedStep;
        this.expirationExplosionRadius = b.expirationExplosionRadius;
        this.expirationExplosionDamage = b.expirationExplosionDamage;
        this.driftSpeed = b.driftSpeed;
        this.coversPlayers = b.coversPlayers;
        this.tint = b.tint;
        this.plannedEffect = b.plannedEffect;
    }

    // A zone an element's zone power places. Basic powers react with these.
    public static Builder base(int id, String name, Element element) {
        return new Builder(id, name, ZoneOrigin.BASE, element);
    }

    // A zone a reaction produces. It never reacts further.
    public static Builder derived(int id, String name) {
        return new Builder(id, name, ZoneOrigin.DERIVED, null);
    }

    public int id() { return id; }
    public String name() { return name; }
    public ZoneOrigin origin() { return origin; }
    // The field's element, for base zones; null for derived zones.
    public Element element() { return element; }
    public Weapon weapon() { return weapon; }

    public float radius() { return radius; }
    // Visual hollow center radius: 0 draws a solid disk; above 0 draws a
    // ring instead. Its effects (damage, slow) still cover the whole disk.
    public float innerRadius() { return innerRadius; }
    public float duration() { return duration; }

    // Health removed per second from anyone standing in it.
    public float damagePerSecond() { return damagePerSecond; }
    // Movement speed inside it: 1 is normal, 0.5 is half, 1.25 is 25% faster.
    public float speedMultiplier() { return speedMultiplier; }
    public boolean isSolid() { return solidHalfLength > 0f; }
    public float solidHalfLength() { return solidHalfLength; }
    public float solidHalfThickness() { return solidHalfThickness; }
    // Zero means the zone cannot be damaged.
    public float maxHealth() { return maxHealth; }
    public boolean capturesProjectiles() { return capturesProjectiles; }
    // Outward velocity added once when a player touches the field.
    public float pushImpulse() { return pushImpulse; }
    public float gravityRadius() { return gravityRadius; }
    public int gravityLevels() { return gravityLevels; }
    public float projectileGravityAccelerationStep() {
        return projectileGravityAccelerationStep;
    }
    public float playerGravitySpeedStep() { return playerGravitySpeedStep; }
    public float expirationExplosionRadius() { return expirationExplosionRadius; }
    public float expirationExplosionDamage() { return expirationExplosionDamage; }
    // Tiles per second a derived zone drifts, in the direction of the hit
    // that created it, once it is placed. Zero means it stays put.
    public float driftSpeed() { return driftSpeed; }

    // Drawn over players instead of under them, like a cloud.
    public boolean coversPlayers() { return coversPlayers; }
    // Draw color as 0xRRGGBBAA.
    public int tint() { return tint; }
    // An effect the design calls for that the engine does not do yet, or "".
    public String plannedEffect() { return plannedEffect; }

    public boolean isDerived() {
        return origin == ZoneOrigin.DERIVED;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ZoneKind kind && kind.id == id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return name + " (zone #" + id + ")";
    }

    public static final class Builder {

        private final int id;
        private final String name;
        private final ZoneOrigin origin;
        private final Element element;
        private Weapon weapon = Weapon.STAFF;
        private float radius;
        private float innerRadius;
        private float duration;
        private float damagePerSecond;
        private float speedMultiplier = 1f;
        private float solidHalfLength;
        private float solidHalfThickness;
        private float maxHealth;
        private boolean capturesProjectiles;
        private float pushImpulse;
        private float gravityRadius;
        private int gravityLevels;
        private float projectileGravityAccelerationStep;
        private float playerGravitySpeedStep;
        private float expirationExplosionRadius;
        private float expirationExplosionDamage;
        private float driftSpeed;
        private boolean coversPlayers;
        private int tint = 0xFFFFFF99;
        private String plannedEffect = "";

        private Builder(int id, String name, ZoneOrigin origin, Element element) {
            this.id = id;
            this.name = Objects.requireNonNull(name);
            this.origin = origin;
            this.element = element;
        }

        public Builder weapon(Weapon weapon) { this.weapon = weapon; return this; }
        public Builder radius(float tiles) { this.radius = tiles; return this; }
        public Builder innerRadius(float tiles) { this.innerRadius = tiles; return this; }
        public Builder duration(float seconds) { this.duration = seconds; return this; }
        public Builder damagePerSecond(float hp) { this.damagePerSecond = hp; return this; }
        public Builder speedMultiplier(float multiplier) { this.speedMultiplier = multiplier; return this; }
        public Builder solidWall(float halfLength, float halfThickness) {
            this.solidHalfLength = halfLength;
            this.solidHalfThickness = halfThickness;
            return this;
        }
        public Builder health(float hp) { this.maxHealth = hp; return this; }
        public Builder capturesProjectiles() { this.capturesProjectiles = true; return this; }
        public Builder pushesPlayers(float impulseTilesPerSecond) {
            this.pushImpulse = impulseTilesPerSecond;
            return this;
        }
        public Builder gravity(
            float radius,
            int levels,
            float projectileAccelerationStep,
            float playerSpeedStep
        ) {
            this.gravityRadius = radius;
            this.gravityLevels = levels;
            this.projectileGravityAccelerationStep = projectileAccelerationStep;
            this.playerGravitySpeedStep = playerSpeedStep;
            return this;
        }
        public Builder expirationExplosion(float radius, float damage) {
            this.expirationExplosionRadius = radius;
            this.expirationExplosionDamage = damage;
            return this;
        }
        public Builder drift(float tilesPerSecond) { this.driftSpeed = tilesPerSecond; return this; }
        public Builder coversPlayers() { this.coversPlayers = true; return this; }
        public Builder tint(int rgba) { this.tint = rgba; return this; }
        public Builder planned(String effect) { this.plannedEffect = effect; return this; }

        // Checks the definition, so a mistake stops the game at startup with a
        // clear message instead of causing odd behavior in a match.
        public ZoneKind build() {
            require(id >= 1 && id <= 255, "id must be 1 to 255");
            require(!name.isBlank(), "name is required");
            require(weapon != null, "weapon is required");
            require(radius > 0f, "radius must be above 0");
            require(innerRadius >= 0f, "inner radius cannot be negative");
            require(innerRadius < radius, "inner radius must be less than radius");
            require(duration > 0f, "duration must be above 0");
            require(damagePerSecond >= 0f, "damage per second cannot be negative");
            require(speedMultiplier > 0f, "speed multiplier must be above 0");
            require(solidHalfLength >= 0f, "solid half length cannot be negative");
            require(solidHalfThickness >= 0f, "solid half thickness cannot be negative");
            require(maxHealth >= 0f, "health cannot be negative");
            require(
                (solidHalfLength == 0f) == (solidHalfThickness == 0f),
                "solid wall needs both half length and half thickness"
            );
            require(maxHealth == 0f || solidHalfLength > 0f, "only a solid zone can have health");
            require(pushImpulse >= 0f, "push impulse cannot be negative");
            require(gravityRadius >= 0f, "gravity radius cannot be negative");
            require(gravityLevels >= 0, "gravity levels cannot be negative");
            require(
                (gravityRadius == 0f) == (gravityLevels == 0),
                "gravity needs both a radius and level count"
            );
            require(
                gravityRadius == 0f
                    || projectileGravityAccelerationStep > 0f,
                "gravity needs projectile acceleration"
            );
            require(
                gravityRadius == 0f || playerGravitySpeedStep > 0f,
                "gravity needs player pull speed"
            );
            require(
                expirationExplosionRadius >= 0f,
                "explosion radius cannot be negative"
            );
            require(
                expirationExplosionDamage >= 0f,
                "explosion damage cannot be negative"
            );
            require(
                (expirationExplosionRadius == 0f)
                    == (expirationExplosionDamage == 0f),
                "expiration explosion needs both radius and damage"
            );
            require(driftSpeed >= 0f, "drift speed cannot be negative");

            if (origin == ZoneOrigin.BASE) {
                require(element != null, "a base zone needs an element");
            }

            return new ZoneKind(this);
        }

        private void require(boolean condition, String problem) {
            if (!condition) {
                throw new IllegalStateException(
                    "Zone '" + name + "' (#" + id + "): " + problem
                );
            }
        }
    }
}
