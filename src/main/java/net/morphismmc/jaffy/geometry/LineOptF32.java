// JAFFY: port of upstream src/geometry.rs — `Line<Option<f32>>` specialization
// (SPECIALIZATIONS.md). Distinct type from LineF32 (GAP-006).
package net.morphismmc.jaffy.geometry;

import net.morphismmc.just.function.FloatUnaryOperator;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

public record LineOptF32(@Opt float start, @Opt float end) {

    /// A `Line<Option<f32>>` with both start and end `None`
    public static final LineOptF32 NONE = new LineOptF32(OptF32.NONE, OptF32.NONE);

    /// Applies the function `f` to both the start and end
    /// (f sees the raw @Opt encoding, including NONE)
    public LineOptF32 map(FloatUnaryOperator f) {
        return new LineOptF32(f.applyAsFloat(start), f.applyAsFloat(end));
    }

    /// `map` producing `Line<f32>` — same closure shape (GAP-006 F suffix)
    public LineF32 mapF(FloatUnaryOperator f) {
        return new LineF32(f.applyAsFloat(start), f.applyAsFloat(end));
    }

    /// `PartialEq` -> rustEq: Option<f32> fields compare via OptF32.eq (GAP-004)
    public boolean rustEq(LineOptF32 other) {
        return OptF32.eq(start, other.start) && OptF32.eq(end, other.end);
    }
}
