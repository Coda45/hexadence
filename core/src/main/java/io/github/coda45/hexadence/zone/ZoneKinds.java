package io.github.coda45.hexadence.zone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.coda45.hexadence.power.Element;

// Every kind of zone, defined in one place. Base zones come from zone powers;
// derived zones come from reactions (see Reactions). Each definition mirrors
// its zone card.
//
// The interaction matrix is a draft, so every derived zone here is a working
// version. Values marked PLACEHOLDER are waiting on the zone cards.
public final class ZoneKinds {

    // ---- Base zones (placed by zone powers) ----

    public static final ZoneKind PUDDLE = ZoneKind.base(1, "Puddle", Element.WATER)
        .radius(0.8f)               // PLACEHOLDER
        .duration(6f)               // PLACEHOLDER
        .tint(0x338CFF99)
        .build();

    // Ember Circle's tint: a burning red, named since the zone is drawn as a
    // plain runtime circle rather than custom art.
    private static final int EMBER_RED = 0xE63300B3;

    public static final ZoneKind EMBER_CIRCLE = ZoneKind.base(10, "Ember Circle", Element.FIRE)
        .radius(0.8f)
        .innerRadius(0.4f)          // hollow center: only the outer ring burns
        .duration(5f)
        .damagePerSecond(5f)
        .tint(EMBER_RED)
        .build();

    // ---- Derived zones (produced by reactions; never react further) ----

    // Fire on Water
    public static final ZoneKind STEAM = ZoneKind.derived(2, "Steam")
        .radius(1f)                 // PLACEHOLDER
        .duration(3f)               // PLACEHOLDER
        .coversPlayers()
        .tint(0xCCCCCCD9)
        .planned("Blocks vision")
        .build();

    // Fire on Stone
    public static final ZoneKind LAVA = ZoneKind.derived(3, "Lava")
        .radius(1f)                 // PLACEHOLDER
        .duration(4f)               // PLACEHOLDER
        .damagePerSecond(15f)       // PLACEHOLDER
        .tint(0xFF4D1AB3)
        .build();

    // Water on Stone
    public static final ZoneKind QUICKSAND = ZoneKind.derived(4, "Quicksand")
        .radius(1.2f)               // PLACEHOLDER
        .duration(5f)               // PLACEHOLDER
        .speedMultiplier(0.5f)      // PLACEHOLDER
        .tint(0xC2A060B3)
        .build();

    // Water on Storm
    public static final ZoneKind HAILSTORM = ZoneKind.derived(5, "Hailstorm")
        .radius(1.2f)               // PLACEHOLDER
        .duration(4f)               // PLACEHOLDER
        .damagePerSecond(8f)        // PLACEHOLDER
        .speedMultiplier(0.7f)      // PLACEHOLDER
        .tint(0xBFE6FFB3)
        .build();

    // Stone on Fire
    public static final ZoneKind HOT_COALS = ZoneKind.derived(6, "Hot Coals")
        .radius(1f)                 // PLACEHOLDER
        .duration(5f)               // PLACEHOLDER
        .damagePerSecond(6f)        // PLACEHOLDER
        .tint(0xB33A14B3)
        .build();

    // Storm on Fire
    public static final ZoneKind INFERNO = ZoneKind.derived(7, "Inferno")
        .radius(1.4f)               // PLACEHOLDER
        .duration(3f)               // PLACEHOLDER
        .damagePerSecond(20f)       // PLACEHOLDER
        .tint(0xFF8C1AC0)
        .build();

    // Light on Stone
    public static final ZoneKind CRYSTAL_WALL = ZoneKind.derived(8, "Crystal Wall")
        .radius(1f)                 // PLACEHOLDER
        .duration(5f)               // PLACEHOLDER
        .tint(0xE6F2FFCC)
        .planned("Blocks movement and projectiles")
        .build();

    // Void on Storm
    public static final ZoneKind SINGULARITY = ZoneKind.derived(9, "Singularity")
        .radius(1.2f)               // PLACEHOLDER
        .duration(3f)               // PLACEHOLDER
        .tint(0x4D1A66CC)
        .planned("Pulls players toward its center")
        .build();

    // ---- Registry ----

    // Every zone kind must be listed here to exist in the game.
    public static final List<ZoneKind> ALL = List.of(
        PUDDLE,
        EMBER_CIRCLE,
        STEAM,
        LAVA,
        QUICKSAND,
        HAILSTORM,
        HOT_COALS,
        INFERNO,
        CRYSTAL_WALL,
        SINGULARITY
    );

    private static final Map<Integer, ZoneKind> BY_ID = indexById();

    private ZoneKinds() {
    }

    // The zone kind with this network ID, or null if there is none.
    public static ZoneKind byId(int id) {
        return BY_ID.get(id);
    }

    private static Map<Integer, ZoneKind> indexById() {
        Map<Integer, ZoneKind> byId = new HashMap<>();

        for (ZoneKind kind : ALL) {
            ZoneKind same = byId.put(kind.id(), kind);

            if (same != null) {
                throw new IllegalStateException(
                    kind.name() + " and " + same.name() + " share zone ID " + kind.id()
                );
            }
        }

        return Map.copyOf(byId);
    }
}
