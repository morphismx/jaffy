// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-NOTE: `Size<f32>` specialization
// (SPECIALIZATIONS.md). Generic Size<T> methods duplicated per GAP-006 rules.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.FloatBinaryOperator;
import net.morphismmc.just.function.FloatUnaryOperator;
import net.morphismmc.just.math.F32;

public record SizeF32(float width, float height) {

    /// A [`Size`] with zero width and height
    public static final SizeF32 ZERO = new SizeF32(0.0f, 0.0f);

    // ---- impl Size<f32> ----

    /// Applies `f32::max` (Rust NaN/±0 semantics via F32) to each component
    public SizeF32 f32Max(SizeF32 rhs) {
        return new SizeF32(F32.max(width, rhs.width), F32.max(height, rhs.height));
    }

    /// Applies `f32::min` to each component
    public SizeF32 f32Min(SizeF32 rhs) {
        return new SizeF32(F32.min(width, rhs.width), F32.min(height, rhs.height));
    }

    /// Return true if both width and height are greater than 0 else false
    public boolean hasNonZeroArea() {
        return width > 0.0f && height > 0.0f;
    }

    // ---- impl<T> Size<T> duplicated on the specialization ----

    /// Get either the width or height depending on the AbsoluteAxis passed in
    public float getAbs(AbsoluteAxis axis) {
        return axis == AbsoluteAxis.HORIZONTAL ? width : height;
    }

    /// Swap the width and height components
    public SizeF32 transpose() {
        return new SizeF32(height, width);
    }

    /// Applies the function `f` to both the width and height
    public SizeF32 map(FloatUnaryOperator f) {
        return new SizeF32(f.applyAsFloat(width), f.applyAsFloat(height));
    }

    /// `map` producing `Size<Option<f32>>` — e.g. `size.map(Some)` where the
    /// Some-encoding of a plain f32 is the raw value itself (GAP-006 O suffix)
    public SizeOptF32 mapO(FloatUnaryOperator f) {
        return new SizeOptF32(f.applyAsFloat(width), f.applyAsFloat(height));
    }

    /// Applies the function `f` to the width
    public SizeF32 mapWidth(FloatUnaryOperator f) {
        return new SizeF32(f.applyAsFloat(width), height);
    }

    /// Applies the function `f` to the height
    public SizeF32 mapHeight(FloatUnaryOperator f) {
        return new SizeF32(width, f.applyAsFloat(height));
    }

    /// Applies the function `f` to both components of self and other
    public SizeF32 zipMap(SizeF32 other, FloatBinaryOperator f) {
        return new SizeF32(f.applyAsFloat(width, other.width), f.applyAsFloat(height, other.height));
    }

    /// `impl Add` — `self + rhs` field-wise (Rust `T: Add` -> `f32 + f32`)
    public SizeF32 plus(SizeF32 rhs) {
        return new SizeF32(width + rhs.width, height + rhs.height);
    }

    /// `impl Sub` — `self - rhs` field-wise
    public SizeF32 minus(SizeF32 rhs) {
        return new SizeF32(width - rhs.width, height - rhs.height);
    }

    // ---- flexbox axis helpers (`#[cfg(feature = "flexbox")]`, pub(crate)) ----

    /// Sets the extent of the main layout axis (Rust `set_main`/`with_main`)
    public SizeF32 withMain(FlexDirection direction, float value) {
        return direction.isRow() ? new SizeF32(value, height) : new SizeF32(width, value);
    }

    /// Sets the extent of the cross layout axis (Rust `set_cross`/`with_cross`)
    public SizeF32 withCross(FlexDirection direction, float value) {
        return direction.isRow() ? new SizeF32(width, value) : new SizeF32(value, height);
    }

    /// Creates a new value of type Self with the main axis modified by the callback
    public SizeF32 mapMain(FlexDirection direction, FloatUnaryOperator mapper) {
        return direction.isRow()
                ? new SizeF32(mapper.applyAsFloat(width), height)
                : new SizeF32(width, mapper.applyAsFloat(height));
    }

    /// Creates a new value of type Self with the cross axis modified by the callback
    public SizeF32 mapCross(FlexDirection direction, FloatUnaryOperator mapper) {
        return direction.isRow()
                ? new SizeF32(width, mapper.applyAsFloat(height))
                : new SizeF32(mapper.applyAsFloat(width), height);
    }

    /// Gets the extent of the main layout axis
    public float main(FlexDirection direction) {
        return direction.isRow() ? width : height;
    }

    /// Gets the extent of the cross layout axis
    public float cross(FlexDirection direction) {
        return direction.isRow() ? height : width;
    }

    // ---- grid axis helpers (`#[cfg(feature = "grid")]`, pub(crate)) ----

    /// Gets the extent of the specified layout axis
    public float get(AbstractAxis axis) {
        return axis == AbstractAxis.INLINE ? width : height;
    }

    /// Sets the extent of the specified layout axis (Rust `set` — returns a copy)
    public SizeF32 with(AbstractAxis axis, float value) {
        return axis == AbstractAxis.INLINE ? new SizeF32(value, height) : new SizeF32(width, value);
    }

    // ---- MaybeMath blanket impl for Self = `Size<f32>` (util/math.rs) ----
    // Only the Option<f32>-rhs impl exists upstream (f32 has no MaybeMath<f32, _>).
    // Suffix per GAP-006: O = Size<Option<f32>> rhs.

    /// `maybe_min` — component-wise `f32::maybe_min(Option<f32>)`
    public SizeF32 maybeMinO(SizeOptF32 rhs) {
        return new SizeF32(F32.maybeMinO(width, rhs.width()), F32.maybeMinO(height, rhs.height()));
    }

    /// `maybe_max` — component-wise
    public SizeF32 maybeMaxO(SizeOptF32 rhs) {
        return new SizeF32(F32.maybeMaxO(width, rhs.width()), F32.maybeMaxO(height, rhs.height()));
    }

    /// `maybe_clamp` — component-wise
    public SizeF32 maybeClampO(SizeOptF32 min, SizeOptF32 max) {
        return new SizeF32(
                F32.maybeClampO(width, min.width(), max.width()), F32.maybeClampO(height, min.height(), max.height()));
    }

    /// `maybe_add` — component-wise
    public SizeF32 maybeAddO(SizeOptF32 rhs) {
        return new SizeF32(F32.maybeAddO(width, rhs.width()), F32.maybeAddO(height, rhs.height()));
    }

    /// `maybe_sub` — component-wise
    public SizeF32 maybeSubO(SizeOptF32 rhs) {
        return new SizeF32(F32.maybeSubO(width, rhs.width()), F32.maybeSubO(height, rhs.height()));
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`) (GAP-004)
    public boolean rustEq(SizeF32 other) {
        return width == other.width && height == other.height;
    }
}
