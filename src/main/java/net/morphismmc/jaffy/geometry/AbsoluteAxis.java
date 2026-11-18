// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-FROM: src/tree/layout.rs
// JAFFY-NOTE: `AbsoluteAxis` enum
// Also hosts `impl TryFrom<RequestedAxis> for AbsoluteAxis` (defined in
// upstream src/tree/layout.rs) — it is the impl target type.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.just.result.Result;

public enum AbsoluteAxis {
    /// The horizontal axis
    HORIZONTAL,
    /// The vertical axis
    VERTICAL;

    /// Returns the other variant of the enum
    public AbsoluteAxis otherAxis() {
        return this == HORIZONTAL ? VERTICAL : HORIZONTAL;
    }

    /// `TryFrom<RequestedAxis> for AbsoluteAxis` — `Err(())` for `Both`
    /// (zero upstream call sites; Result keeps the shape).
    public static Result<AbsoluteAxis, Void> tryFrom(RequestedAxis value) {
        return switch (value) {
            case HORIZONTAL -> Result.ok(HORIZONTAL);
            case VERTICAL -> Result.ok(VERTICAL);
            case BOTH -> Result.err(null);
        };
    }
}
