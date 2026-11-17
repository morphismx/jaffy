// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Point<Option<f32>>` specialization
// (SPECIALIZATIONS.md). Distinct type from PointF32 (GAP-006).
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.FloatUnaryOperator;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

public record PointOptF32(@Opt float x, @Opt float y) {

    /// A [`Point`] with values (None, None)
    public static final PointOptF32 NONE = new PointOptF32(OptF32.NONE, OptF32.NONE);

    /// Applies the function `f` to both the x and y
    /// (f sees the raw @Opt encoding, including NONE)
    public PointOptF32 map(FloatUnaryOperator f) {
        return new PointOptF32(f.applyAsFloat(x), f.applyAsFloat(y));
    }

    /// Gets the extent of the specified layout axis (`#[cfg(feature = "grid")]`)
    public @Opt float get(AbstractAxis axis) {
        return axis == AbstractAxis.INLINE ? x : y;
    }

    /// Swap x and y components
    public PointOptF32 transpose() {
        return new PointOptF32(y, x);
    }

    /// Sets the extent of the specified layout axis (Rust `set` — returns a copy)
    public PointOptF32 with(AbstractAxis axis, @Opt float value) {
        return axis == AbstractAxis.INLINE ? new PointOptF32(value, y) : new PointOptF32(x, value);
    }

    /// Gets the component in the main layout axis (`#[cfg(feature = "flexbox")]`, pub(crate))
    public @Opt float main(FlexDirection direction) {
        return direction.isRow() ? x : y;
    }

    /// Gets the component in the cross layout axis (`#[cfg(feature = "flexbox")]`, pub(crate))
    public @Opt float cross(FlexDirection direction) {
        return direction.isRow() ? y : x;
    }

    /// `From<Point<T>> for Size<T>` — produces `Size<Option<f32>>`
    public SizeOptF32 toSize() {
        return new SizeOptF32(x, y);
    }

    /// `PartialEq` -> rustEq: Option<f32> fields compare via OptF32.eq (GAP-004)
    public boolean rustEq(PointOptF32 other) {
        return OptF32.eq(x, other.x) && OptF32.eq(y, other.y);
    }
}
