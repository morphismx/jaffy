// JAFFY: port of upstream src/geometry.rs — `Rect<f32>` specialization
// (SPECIALIZATIONS.md). Generic Rect<T> methods duplicated per GAP-006 rules.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.FloatBinaryOperator;
import net.morphismmc.just.function.FloatUnaryOperator;
import net.morphismmc.just.math.F32;

public record RectF32(float left, float right, float top, float bottom) {

    /// A [`Rect`] with `0.0` as all parameters
    public static final RectF32 ZERO = new RectF32(0.0f, 0.0f, 0.0f, 0.0f);

    /// `Rect<f32>::new(start, end, top, bottom)` — the record ctor maps directly.

    /// `union` — smallest rectangle containing both (fields interpreted as
    /// edge coordinates: left/top min, right/bottom max)
    public RectF32 union(RectF32 other) {
        return new RectF32(
                F32.min(left, other.left),
                F32.max(right, other.right),
                F32.min(top, other.top),
                F32.max(bottom, other.bottom));
    }

    // ---- impl<T> Rect<T> duplicated on the specialization ----

    /// `impl Add` — `self + rhs` field-wise
    public RectF32 plus(RectF32 rhs) {
        return new RectF32(left + rhs.left, right + rhs.right, top + rhs.top, bottom + rhs.bottom);
    }

    /// Swap the horizontal and vertical sides (`left <-> top`, `right <-> bottom`)
    public RectF32 transpose() {
        return new RectF32(top, bottom, left, right);
    }

    /// `zip_size` — f32 components on both sides
    public RectF32 zipSize(SizeF32 size, FloatBinaryOperator f) {
        return new RectF32(
                f.applyAsFloat(left, size.width()),
                f.applyAsFloat(right, size.width()),
                f.applyAsFloat(top, size.height()),
                f.applyAsFloat(bottom, size.height()));
    }

    /// Applies the function `f` to all four sides
    public RectF32 map(FloatUnaryOperator f) {
        return new RectF32(f.applyAsFloat(left), f.applyAsFloat(right), f.applyAsFloat(top), f.applyAsFloat(bottom));
    }

    /// `map` producing `Rect<Option<f32>>` — same closure shape, Option-encoded result (GAP-006 O suffix)
    public RectOptF32 mapO(FloatUnaryOperator f) {
        return new RectOptF32(f.applyAsFloat(left), f.applyAsFloat(right), f.applyAsFloat(top), f.applyAsFloat(bottom));
    }

    /// Returns a `Line<f32>` representing the left and right properties
    public LineF32 horizontalComponents() {
        return new LineF32(left, right);
    }

    /// Returns a `Line<f32>` containing the top and bottom properties
    public LineF32 verticalComponents() {
        return new LineF32(top, bottom);
    }

    /// `horizontal_axis_sum` — left + right (NOT the width)
    public float horizontalAxisSum() {
        return left + right;
    }

    /// `vertical_axis_sum` — top + bottom (NOT the height)
    public float verticalAxisSum() {
        return top + bottom;
    }

    /// `sum_axes` — both axis sums as a `Size<f32>`
    public SizeF32 sumAxes() {
        return new SizeF32(horizontalAxisSum(), verticalAxisSum());
    }

    /// `grid_axis_sum` — the sum of the two fields representing `axis`
    public float gridAxisSum(AbsoluteAxis axis) {
        return axis == AbsoluteAxis.HORIZONTAL ? left + right : top + bottom;
    }

    // ---- flexbox helpers (`#[cfg(feature = "flexbox")]`, pub(crate)) ----

    /// `main_axis_sum`
    public float mainAxisSum(FlexDirection direction) {
        return direction.isRow() ? horizontalAxisSum() : verticalAxisSum();
    }

    /// `cross_axis_sum`
    public float crossAxisSum(FlexDirection direction) {
        return direction.isRow() ? verticalAxisSum() : horizontalAxisSum();
    }

    /// `main_start`
    public float mainStart(FlexDirection direction) {
        return direction.isRow() ? left : top;
    }

    /// `main_end`
    public float mainEnd(FlexDirection direction) {
        return direction.isRow() ? right : bottom;
    }

    /// `cross_start`
    public float crossStart(FlexDirection direction) {
        return direction.isRow() ? top : left;
    }

    /// `cross_end`
    public float crossEnd(FlexDirection direction) {
        return direction.isRow() ? bottom : right;
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`) (GAP-004)
    public boolean rustEq(RectF32 other) {
        return left == other.left && right == other.right && top == other.top && bottom == other.bottom;
    }
}
