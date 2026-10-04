package io.github.coda45.hexadence.world;

import java.util.List;

public record StepResult(
    long tick,
    List<WorldEvent> events
) {
    public StepResult {
        // Freeze this ticks event order before presentation receives it.
        events = List.copyOf(events);
    }
}
