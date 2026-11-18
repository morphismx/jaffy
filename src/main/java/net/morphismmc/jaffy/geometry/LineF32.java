// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-NOTE: `Line<f32>` specialization
// (SPECIALIZATIONS.md). Generic Line<T> methods duplicated per GAP-006 rules.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.just.function.FloatUnaryOperator;

public record LineF32(float start, float end) {

    /// Applies the function `f` to both the start and end
    public LineF32 map(FloatUnaryOperator f) {
        return new LineF32(f.applyAsFloat(start), f.applyAsFloat(end));
    }

    /// `impl<T: Add + Copy> sum` — adds the start and end values together
    public float sum() {
        return start + end;
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`) (GAP-004)
    public boolean rustEq(LineF32 other) {
        return start == other.start && end == other.end;
    }
}
