// JAFFY: port of upstream src/geometry.rs — `Point<f32>` specialization
// (SPECIALIZATIONS.md). Generic Point<T> methods duplicated per GAP-006 rules.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.FloatUnaryOperator;

public record PointF32(float x, float y) {

    /// A [`Point`] with values (0,0), representing the origin
    public static final PointF32 ZERO = new PointF32(0.0f, 0.0f);

    /// Applies the function `f` to both the x and y
    public PointF32 map(FloatUnaryOperator f) {
        return new PointF32(f.applyAsFloat(x), f.applyAsFloat(y));
    }

    /// `impl Add` — `self + rhs` component-wise
    public PointF32 plus(PointF32 rhs) {
        return new PointF32(x + rhs.x, y + rhs.y);
    }

    /// Gets the extent of the specified layout axis (`#[cfg(feature = "grid")]`)
    public float get(AbstractAxis axis) {
        return axis == AbstractAxis.INLINE ? x : y;
    }

    /// Swap x and y components
    public PointF32 transpose() {
        return new PointF32(y, x);
    }

    /// Sets the extent of the specified layout axis (Rust `set` — returns a copy)
    public PointF32 with(AbstractAxis axis, float value) {
        return axis == AbstractAxis.INLINE ? new PointF32(value, y) : new PointF32(x, value);
    }

    /// Gets the component in the main layout axis (`#[cfg(feature = "flexbox")]`, pub(crate))
    public float main(FlexDirection direction) {
        return direction.isRow() ? x : y;
    }

    /// Gets the component in the cross layout axis (`#[cfg(feature = "flexbox")]`, pub(crate))
    public float cross(FlexDirection direction) {
        return direction.isRow() ? y : x;
    }

    /// `From<Point<T>> for Size<T>`
    public SizeF32 toSize() {
        return new SizeF32(x, y);
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`) (GAP-004)
    public boolean rustEq(PointF32 other) {
        return x == other.x && y == other.y;
    }
}
