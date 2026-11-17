// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Rect<Option<f32>>` specialization
// (SPECIALIZATIONS.md). Distinct type from RectF32 (GAP-006).
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.FloatUnaryOperator;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

public record RectOptF32(
        @Opt float left,
        @Opt float right,
        @Opt float top,
        @Opt float bottom) {

    /// A [`Rect`] with `None` as all parameters
    public static final RectOptF32 NONE = new RectOptF32(OptF32.NONE, OptF32.NONE, OptF32.NONE, OptF32.NONE);

    // ---- impl<T> Rect<T> duplicated on the specialization ----

    /// Swap the horizontal and vertical sides (`left <-> top`, `right <-> bottom`)
    public RectOptF32 transpose() {
        return new RectOptF32(top, bottom, left, right);
    }

    /// Applies the function `f` to all four sides
    /// (f sees the raw @Opt encoding, including NONE)
    public RectOptF32 map(FloatUnaryOperator f) {
        return new RectOptF32(f.applyAsFloat(left), f.applyAsFloat(right), f.applyAsFloat(top), f.applyAsFloat(bottom));
    }

    /// `map` producing `Rect<f32>` — same closure shape (GAP-006 F suffix)
    public RectF32 mapF(FloatUnaryOperator f) {
        return new RectF32(f.applyAsFloat(left), f.applyAsFloat(right), f.applyAsFloat(top), f.applyAsFloat(bottom));
    }

    /// Returns a `Line<Option<f32>>` representing the left and right properties
    public LineOptF32 horizontalComponents() {
        return new LineOptF32(left, right);
    }

    /// Returns a `Line<Option<f32>>` containing the top and bottom properties
    public LineOptF32 verticalComponents() {
        return new LineOptF32(top, bottom);
    }

    // ---- flexbox helpers (`#[cfg(feature = "flexbox")]`, pub(crate)) ----

    /// `main_start`
    public @Opt float mainStart(FlexDirection direction) {
        return direction.isRow() ? left : top;
    }

    /// `main_end`
    public @Opt float mainEnd(FlexDirection direction) {
        return direction.isRow() ? right : bottom;
    }

    /// `cross_start`
    public @Opt float crossStart(FlexDirection direction) {
        return direction.isRow() ? top : left;
    }

    /// `cross_end`
    public @Opt float crossEnd(FlexDirection direction) {
        return direction.isRow() ? bottom : right;
    }

    /// `PartialEq` -> rustEq: Option<f32> fields compare via OptF32.eq (GAP-004)
    public boolean rustEq(RectOptF32 other) {
        return OptF32.eq(left, other.left)
                && OptF32.eq(right, other.right)
                && OptF32.eq(top, other.top)
                && OptF32.eq(bottom, other.bottom);
    }
}
