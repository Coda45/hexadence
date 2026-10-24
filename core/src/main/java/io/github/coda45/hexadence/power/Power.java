package io.github.coda45.hexadence.power;

import java.util.Objects;

import io.github.coda45.hexadence.zone.ZoneKind;
import io.github.coda45.hexadence.zone.ZoneOrigin;

// One power's definition: what it is and its numbers. All powers are defined
// in Powers, using the builder below, so each definition reads like its
// spell card.
//
// Units: distances and sizes in tiles (the map is 30 x 20 tiles; players move
// 5 tiles per second), times in seconds, damage in health points (players
// have 100).
//
// A Power is fixed data shared by everyone, so it is safe to use as a map
// key. Two powers are equal when their IDs are equal.
public final class Power {

    private final int id;
    private final String name;
    private final Element element;
    private final Weapon weapon;
    private final PowerForm form;
    private final Behavior behavior;

    private final float damage;
    private final float cooldownSeconds;
    private final float speed;
    private final float range;
    private final float radius;

    private final ZoneKind zoneKind;

    private final String iconPath;
    private final String iconRimPath;

    private Power(Builder b) {
        this.id = b.id;
        this.name = b.name;
        this.element = b.element;
        this.weapon = b.weapon;
        this.form = b.form;
        this.behavior = b.behavior;
        this.damage = b.damage;
        this.cooldownSeconds = b.cooldownSeconds;
        this.speed = b.speed;
        this.range = b.range;
        this.radius = b.radius;
        this.zoneKind = b.zoneKind;
        this.iconPath = b.iconPath;
        this.iconRimPath = b.iconRimPath;
    }

    // Starts a definition. The ID is how the power travels over the network:
    // give every power a different one from 1 to 255, and never change or
    // reuse one once it exists.
    public static Builder define(int id, String name) {
        return new Builder(id, name);
    }

    public int id() { return id; }
    public String name() { return name; }
    public Element element() { return element; }
    public Weapon weapon() { return weapon; }
    public PowerForm form() { return form; }
    public Behavior behavior() { return behavior; }

    // Health removed from whatever it hits directly.
    public float damage() { return damage; }
    public float cooldownSeconds() { return cooldownSeconds; }
    // Tiles per second, for powers that travel.
    public float speed() { return speed; }
    // Tiles it can travel or reach.
    public float range() { return range; }
    // Projectile radius, or half the width of a beam, in tiles.
    public float radius() { return radius; }

    // Zone left behind, for powers that create one; otherwise null. Its size
    // and lifetime are part of the zone's own definition in ZoneKinds.
    public ZoneKind zoneKind() { return zoneKind; }

    // HUD icon images, relative to the assets folder.
    public String iconPath() { return iconPath; }
    public String iconRimPath() { return iconRimPath; }

    @Override
    public boolean equals(Object other) {
        return other instanceof Power power && power.id == id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return name + " (#" + id + ")";
    }

    public static final class Builder {

        private final int id;
        private final String name;
        private Element element;
        private Weapon weapon = Weapon.STAFF;
        private PowerForm form;
        private Behavior behavior;
        private float damage;
        private float cooldownSeconds;
        private float speed;
        private float range;
        private float radius;
        private ZoneKind zoneKind;
        private String iconPath = "ui/bg_fireball.png";
        private String iconRimPath = "ui/rim_orange.png";

        private Builder(int id, String name) {
            this.id = id;
            this.name = Objects.requireNonNull(name);
        }

        public Builder element(Element element) { this.element = element; return this; }
        public Builder weapon(Weapon weapon) { this.weapon = weapon; return this; }
        public Builder form(PowerForm form) { this.form = form; return this; }
        public Builder behavior(Behavior behavior) { this.behavior = behavior; return this; }
        public Builder damage(float damage) { this.damage = damage; return this; }
        public Builder cooldown(float seconds) { this.cooldownSeconds = seconds; return this; }
        public Builder speed(float tilesPerSecond) { this.speed = tilesPerSecond; return this; }
        public Builder range(float tiles) { this.range = tiles; return this; }
        public Builder radius(float tiles) { this.radius = tiles; return this; }

        public Builder zone(ZoneKind kind) {
            this.zoneKind = kind;
            return this;
        }

        public Builder icon(String backgroundPath, String rimPath) {
            this.iconPath = backgroundPath;
            this.iconRimPath = rimPath;
            return this;
        }

        // Checks that the definition has everything its behavior needs, so a
        // mistake stops the game at startup with a clear message instead of
        // causing odd behavior in a match.
        public Power build() {
            require(id >= 1 && id <= 255, "id must be 1 to 255");
            require(!name.isBlank(), "name is required");
            require(element != null, "element is required");
            require(weapon != null, "weapon is required");
            require(form != null, "form is required");
            require(behavior != null, "behavior is required");
            require(cooldownSeconds > 0f, "cooldown must be above 0");
            require(damage >= 0f, "damage cannot be negative");
            require(range > 0f, "range must be above 0");
            require(radius > 0f, "radius must be above 0");

            if (behavior.travels()) {
                require(speed > 0f, "speed must be above 0 for a power that travels");
            }

            if (behavior.createsZone()) {
                require(zoneKind != null, "zone(...) is required for " + behavior);
                require(
                    zoneKind.origin() == ZoneOrigin.BASE,
                    "a power places a base zone, not a derived one"
                );
                require(
                    zoneKind.element() == element,
                    "its zone must be the same element as the power"
                );
            } else {
                require(zoneKind == null, "only zone-creating behaviors may set zone(...)");
            }

            return new Power(this);
        }

        private void require(boolean condition, String problem) {
            if (!condition) {
                throw new IllegalStateException(
                    "Power '" + name + "' (#" + id + "): " + problem
                );
            }
        }
    }
}
