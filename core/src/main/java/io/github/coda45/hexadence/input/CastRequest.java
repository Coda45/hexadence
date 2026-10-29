package io.github.coda45.hexadence.input;

// One request to cast the power in a loadout slot toward a cursor offset.
// The server resolves the slot through its authoritative Player loadout and
// clamps the target offset to that power's range.
public record CastRequest(
    int slot,
    float targetOffsetX,
    float targetOffsetY
) {
}
