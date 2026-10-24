package io.github.coda45.hexadence.zone;

import java.util.List;
import java.util.Objects;

import io.github.coda45.hexadence.power.Element;
import io.github.coda45.hexadence.power.Power;
import io.github.coda45.hexadence.power.PowerForm;
import io.github.coda45.hexadence.power.Weapon;

import static io.github.coda45.hexadence.power.Element.FIRE;
import static io.github.coda45.hexadence.power.Element.LIGHT;
import static io.github.coda45.hexadence.power.Element.STONE;
import static io.github.coda45.hexadence.power.Element.STORM;
import static io.github.coda45.hexadence.power.Element.VOID;
import static io.github.coda45.hexadence.power.Element.WATER;

// The interaction matrix: what happens when a BASIC power hits a BASE zone.
// One line per filled cell, read as "cast element on field element".
//
// Rules (checked by ReactionsTest):
//   - only basic powers react, and only with base zones
//   - derived zones never react further
//   - an element does not react with its own field
//   - each basic power reacts with at most two fields (exactly two once
//     the matrix is final)
//   - where zones overlap, the newest one at that spot is the one hit
//
// The matrix is a draft. Change a line here and it changes in the game.
public final class Reactions {

    public enum Result {
        // The zone becomes a different (derived) zone, in the same spot.
        TRANSFORM,
        // The zone stays what it is but becomes charged: the next player or
        // dummy to touch it takes CHARGE_DISCHARGE_DAMAGE and the charge ends.
        CHARGE
    }

    public record Reaction(
        Weapon weapon,
        Element cast,
        Element field,
        Result result,
        ZoneKind into
    ) {
        public Reaction {
            Objects.requireNonNull(weapon);
            Objects.requireNonNull(cast);
            Objects.requireNonNull(field);
            Objects.requireNonNull(result);

            if ((result == Result.TRANSFORM) != (into != null)) {
                throw new IllegalArgumentException(
                    "Only a TRANSFORM reaction names the zone it becomes"
                );
            }
        }
    }

    // Damage a charged zone deals when it discharges.
    public static final float CHARGE_DISCHARGE_DAMAGE = 20f;   // PLACEHOLDER

    // ---- Staff matrix (cast on field) ----

    public static final List<Reaction> ALL = List.of(
        transform(FIRE,  WATER, ZoneKinds.STEAM),
        transform(FIRE,  STONE, ZoneKinds.LAVA),

        transform(WATER, STONE, ZoneKinds.QUICKSAND),
        transform(WATER, STORM, ZoneKinds.HAILSTORM),

        transform(STONE, FIRE,  ZoneKinds.HOT_COALS),
        // Stone: second reaction not decided yet

        transform(STORM, FIRE,  ZoneKinds.INFERNO),
        charge(STORM,    WATER),

        transform(LIGHT, STONE, ZoneKinds.CRYSTAL_WALL),
        // Light: second reaction not decided yet

        transform(VOID,  STORM, ZoneKinds.SINGULARITY)
        // Void on Light: marked in the matrix, result not decided yet
    );

    private Reactions() {
    }

    // What happens when this power hits this zone, or null for nothing.
    public static Reaction find(Power cast, ZoneKind zone) {
        if (cast.form() != PowerForm.BASIC || zone.isDerived()) {
            return null;
        }

        for (Reaction reaction : ALL) {
            if (
                reaction.weapon() == cast.weapon()
                && reaction.cast() == cast.element()
                && reaction.field() == zone.element()
            ) {
                return reaction;
            }
        }

        return null;
    }

    private static Reaction transform(Element cast, Element field, ZoneKind into) {
        return new Reaction(Weapon.STAFF, cast, field, Result.TRANSFORM, into);
    }

    private static Reaction charge(Element cast, Element field) {
        return new Reaction(Weapon.STAFF, cast, field, Result.CHARGE, null);
    }
}
