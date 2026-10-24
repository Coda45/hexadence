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
    private final float duration;

    private final float damagePerSecond;
    private final float speedMultiplier;

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
        this.duration = b.duration;
        this.damagePerSecond = b.damagePerSecond;
        this.speedMultiplier = b.speedMultiplier;
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
    public float duration() { return duration; }

    // Health removed per second from anyone standing in it.
    public float damagePerSecond() { return damagePerSecond; }
    // Movement speed inside it: 1 is normal, 0.5 is half speed.
    public float speedMultiplier() { return speedMultiplier; }

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
        private float duration;
        private float damagePerSecond;
        private float speedMultiplier = 1f;
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
        public Builder duration(float seconds) { this.duration = seconds; return this; }
        public Builder damagePerSecond(float hp) { this.damagePerSecond = hp; return this; }
        public Builder speedMultiplier(float multiplier) { this.speedMultiplier = multiplier; return this; }
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
            require(duration > 0f, "duration must be above 0");
            require(damagePerSecond >= 0f, "damage per second cannot be negative");
            require(
                speedMultiplier > 0f && speedMultiplier <= 1f,
                "speed multiplier must be above 0 and at most 1"
            );

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
