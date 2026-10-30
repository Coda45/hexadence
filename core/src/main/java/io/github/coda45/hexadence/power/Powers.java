package io.github.coda45.hexadence.power;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.coda45.hexadence.zone.ZoneKinds;

// Every power in the game, defined in one place. Each definition mirrors a
// column of its spell card. To tune a power, change its numbers here; to add
// one, copy a definition with the same behavior and add it to ALL.
//
// Values marked PLACEHOLDER are working numbers waiting on the spell cards.
//
// The server and every client must run the same definitions. Merging into main
// redeploys the server automatically; players then pull and rebuild (a game
// with different definitions is turned away with "update your game").
public final class Powers {

    // ---- Fire ----

    public static final Power FIREBALL = Power.define(1, "Fireball")
        .element(Element.FIRE)
        .form(PowerForm.BASIC)
        .behavior(Behavior.PROJECTILE)
        .damage(10f)
        .cooldown(0.5f)      // PLACEHOLDER
        .speed(12f)          // PLACEHOLDER
        .range(8f)           // PLACEHOLDER
        .radius(0.12f)       // PLACEHOLDER
        .icon("ui/bg_fireball.png", "ui/rim_orange.png")
        .build();
        
    // ---- Light ----

    public static final Power RADIANT_CIRCLE =
        Power.define(7, "Radiant Circle")
        .element(Element.LIGHT)
        .form(PowerForm.ZONE)
        .behavior(Behavior.THROWN_ZONE)
        .damage(0f)
        .cooldown(1.2f)
        .speed(12f)
        .range(8f)
        .radius(0.12f)
        .zone(ZoneKinds.RADIANT_CIRCLE)
        .build();

    // ---- Water ----

    public static final Power WATER_PUDDLE = Power.define(2, "Water Puddle")
        .element(Element.WATER)
        .form(PowerForm.ZONE)
        .behavior(Behavior.THROWN_ZONE)
        .damage(5f)          // PLACEHOLDER: direct-hit damage before it splashes
        .cooldown(0.85f)     // PLACEHOLDER
        .speed(10f)          // PLACEHOLDER
        .range(8f)           // PLACEHOLDER
        .radius(0.12f)       // PLACEHOLDER
        .zone(ZoneKinds.PUDDLE)
        .icon("ui/bg_water_bolt.png", "ui/rim_blue.png")
        .build();

    public static final Power WATER_SHOTGUN = Power.define(4, "Water Shotgun")
        .element(Element.WATER)
        .form(PowerForm.BASIC)
        .behavior(Behavior.PROJECTILE)
        .damage(3f)
        .cooldown(1f)
        .speed(12f)
        .range(4f)
        .radius(0.12f)
        .pellets(8, 40f)
        .icon("ui/bg_water_bolt.png", "ui/rim_blue.png")
        .build();

    // ---- Stone ----

    public static final Power ROCK_SHARDS = Power.define(8, "Rock Shards")
        .element(Element.STONE)
        .form(PowerForm.BASIC)
        .behavior(Behavior.PROJECTILE)
        .damage(18f)
        .cooldown(1f)
        .speed(10f)
        .range(6f)
        .radius(0.16f)
        .build();

    // ---- Storm ----

    public static final Power LIGHTNING_BOLT = Power.define(3, "Lightning Bolt")
        .element(Element.STORM)
        .form(PowerForm.BASIC)
        .behavior(Behavior.BEAM)
        .damage(15f)         // PLACEHOLDER
        .cooldown(0.7f)      // PLACEHOLDER
        .range(8f)           // PLACEHOLDER
        .radius(0.1f)        // PLACEHOLDER: half the beam's width
        .icon("ui/bg_lightning.png", "ui/rim_yellow.png")
        .build();

    // ---- Void ----

    // No Void art yet; uses the default icon until one exists.
    public static final Power VOID_ROT = Power.define(5, "Void Rot")
        .element(Element.VOID)
        .form(PowerForm.BASIC)
        .behavior(Behavior.PROJECTILE)
        .damage(4f)
        .cooldown(0.5f)
        .speed(12f)
        .range(4f)
        .radius(0.12f)
        .damageOverTime(2f, 0.5f, 2f)
        .build();
    
    public static final Power NULL_ZONE = Power.define(6, "Null Zone")
        .element(Element.VOID)
        .form(PowerForm.ZONE)
        .behavior(Behavior.THROWN_ZONE)
        .damage(0f)
        .cooldown(1f)
        .speed(12f)
        .range(8f)
        .radius(0.12f)
        .zone(ZoneKinds.NULL_ZONE)
        .build();

    // ---- Registry ----

    // Every power must be listed here to exist in the game.
    public static final List<Power> ALL = List.of(
        FIREBALL,
        WATER_PUDDLE,
        WATER_SHOTGUN,
        ROCK_SHARDS,
        LIGHTNING_BOLT,
        VOID_ROT,
        NULL_ZONE,
        RADIANT_CIRCLE
    );

    private static final Map<Integer, Power> BY_ID = indexById();

    private Powers() {
    }

    // The power with this network ID, or null if there is none.
    public static Power byId(int id) {
        return BY_ID.get(id);
    }

    private static Map<Integer, Power> indexById() {
        Map<Integer, Power> byId = new HashMap<>();
        Map<String, Power> byName = new HashMap<>();

        for (Power power : ALL) {
            Power sameId = byId.put(power.id(), power);

            if (sameId != null) {
                throw new IllegalStateException(
                    power.name() + " and " + sameId.name() + " share ID " + power.id()
                );
            }

            Power sameName = byName.put(power.name(), power);

            if (sameName != null) {
                throw new IllegalStateException(
                    "Two powers are named " + power.name()
                );
            }
        }

        return Map.copyOf(byId);
    }
}
