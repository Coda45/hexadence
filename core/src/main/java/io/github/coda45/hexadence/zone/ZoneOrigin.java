package io.github.coda45.hexadence.zone;

// Where a zone comes from.
//   BASE     placed by an element's zone power (Water Puddle). Basic powers
//            react with base zones, following the interaction matrix.
//   DERIVED  produced by a reaction (Steam, Lava). Derived zones never react
//            further; they simply run their course.
public enum ZoneOrigin {
    BASE,
    DERIVED
}
