// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-NOTE: `Size<Option<f32>>` specialization
// (SPECIALIZATIONS.md). Same record shape as SizeF32 but a distinct type (GAP-006).
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.FloatBinaryOperator;
import net.morphismmc.just.function.FloatUnaryOperator;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

public record SizeOptF32(@Opt float width, @Opt float height) {

    /// A [`Size`] with `None` width and height
    public static final SizeOptF32 NONE = new SizeOptF32(OptF32.NONE, OptF32.NONE);
    /// `TaffyZero::ZERO` — `Some(T::ZERO)` per component (NOT None)
    public static final SizeOptF32 ZERO = new SizeOptF32(0.0f, 0.0f);

    // ---- impl Size<Option<f32>> ----
    // `Size<Option<f32>>::new(width, height)` — the record constructor itself
    // stores Some-encodable values; fields are @Opt floats.

    /// `from_cross` — either width or height set based on direction (`#[cfg(feature = "flexbox")]`)
    public static SizeOptF32 fromCross(FlexDirection direction, @Opt float value) {
        SizeOptF32 n = NONE;
        return direction.isRow() ? new SizeOptF32(n.width, value) : new SizeOptF32(value, n.height);
    }

    /// `maybe_apply_aspect_ratio`
    public SizeOptF32 maybeApplyAspectRatio(@Opt float aspectRatio) {
        if (OptF32.isSome(aspectRatio)) {
            if (OptF32.isSome(width) && OptF32.isNone(height)) {
                return new SizeOptF32(width, width / aspectRatio);
            }
            if (OptF32.isNone(width) && OptF32.isSome(height)) {
                return new SizeOptF32(height * aspectRatio, height);
            }
        }
        return this;
    }

    // ---- impl<T> Size<Option<T>> methods ----

    /// `unwrap_or` on each component — `Size<Option<f32>> -> Size<f32>`
    public SizeF32 unwrapOr(SizeF32 alt) {
        return new SizeF32(OptF32.unwrapOr(width, alt.width()), OptF32.unwrapOr(height, alt.height()));
    }

    /// `or` on each component
    public SizeOptF32 or(SizeOptF32 alt) {
        return new SizeOptF32(OptF32.isSome(width) ? width : alt.width, OptF32.isSome(height) ? height : alt.height);
    }

    /// Return true if both components are Some, else false.
    public boolean bothAxisDefined() {
        return OptF32.isSome(width) && OptF32.isSome(height);
    }

    // ---- impl<T> Size<T> duplicated on the specialization ----

    /// Get either the width or height depending on the AbsoluteAxis passed in
    public @Opt float getAbs(AbsoluteAxis axis) {
        return axis == AbsoluteAxis.HORIZONTAL ? width : height;
    }

    /// Swap the width and height components
    public SizeOptF32 transpose() {
        return new SizeOptF32(height, width);
    }

    /// Applies the function `f` to both the width and height
    /// (f sees the raw @Opt encoding, including NONE)
    public SizeOptF32 map(FloatUnaryOperator f) {
        return new SizeOptF32(f.applyAsFloat(width), f.applyAsFloat(height));
    }

    /// `map` producing `Size<f32>` — same closure shape (GAP-006 F suffix)
    public SizeF32 mapF(FloatUnaryOperator f) {
        return new SizeF32(f.applyAsFloat(width), f.applyAsFloat(height));
    }

    /// Applies the function `f` to the width
    public SizeOptF32 mapWidth(FloatUnaryOperator f) {
        return new SizeOptF32(f.applyAsFloat(width), height);
    }

    /// Applies the function `f` to the height
    public SizeOptF32 mapHeight(FloatUnaryOperator f) {
        return new SizeOptF32(width, f.applyAsFloat(height));
    }

    /// Applies the function `f` to both components of self and other
    public SizeOptF32 zipMap(SizeOptF32 other, FloatBinaryOperator f) {
        return new SizeOptF32(f.applyAsFloat(width, other.width), f.applyAsFloat(height, other.height));
    }

    // ---- flexbox axis helpers (`#[cfg(feature = "flexbox")]`, pub(crate)) ----

    /// Sets the extent of the main layout axis (Rust `set_main`/`with_main`)
    public SizeOptF32 withMain(FlexDirection direction, @Opt float value) {
        return direction.isRow() ? new SizeOptF32(value, height) : new SizeOptF32(width, value);
    }

    /// Sets the extent of the cross layout axis (Rust `set_cross`/`with_cross`)
    public SizeOptF32 withCross(FlexDirection direction, @Opt float value) {
        return direction.isRow() ? new SizeOptF32(width, value) : new SizeOptF32(value, height);
    }

    /// Creates a new value of type Self with the main axis modified by the callback
    public SizeOptF32 mapMain(FlexDirection direction, FloatUnaryOperator mapper) {
        return direction.isRow()
                ? new SizeOptF32(mapper.applyAsFloat(width), height)
                : new SizeOptF32(width, mapper.applyAsFloat(height));
    }

    /// Creates a new value of type Self with the cross axis modified by the callback
    public SizeOptF32 mapCross(FlexDirection direction, FloatUnaryOperator mapper) {
        return direction.isRow()
                ? new SizeOptF32(width, mapper.applyAsFloat(height))
                : new SizeOptF32(mapper.applyAsFloat(width), height);
    }

    /// Gets the extent of the main layout axis
    public @Opt float main(FlexDirection direction) {
        return direction.isRow() ? width : height;
    }

    /// Gets the extent of the cross layout axis
    public @Opt float cross(FlexDirection direction) {
        return direction.isRow() ? height : width;
    }

    // ---- grid axis helpers (`#[cfg(feature = "grid")]`, pub(crate)) ----

    /// Gets the extent of the specified layout axis
    public @Opt float get(AbstractAxis axis) {
        return axis == AbstractAxis.INLINE ? width : height;
    }

    /// Sets the extent of the specified layout axis (Rust `set` — returns a copy)
    public SizeOptF32 with(AbstractAxis axis, @Opt float value) {
        return axis == AbstractAxis.INLINE ? new SizeOptF32(value, height) : new SizeOptF32(width, value);
    }

    // ---- MaybeMath blanket impl for Self = `Size<Option<f32>>` (util/math.rs) ----
    // Suffix per GAP-006: O = Size<Option<f32>> rhs, F = Size<f32> rhs.

    /// `maybe_min` — component-wise `Option<f32>::maybe_min(Option<f32>)`
    public SizeOptF32 maybeMinO(SizeOptF32 rhs) {
        return new SizeOptF32(OptF32.maybeMinO(width, rhs.width), OptF32.maybeMinO(height, rhs.height));
    }

    /// `maybe_max` — component-wise
    public SizeOptF32 maybeMaxO(SizeOptF32 rhs) {
        return new SizeOptF32(OptF32.maybeMaxO(width, rhs.width), OptF32.maybeMaxO(height, rhs.height));
    }

    /// `maybe_clamp` — component-wise
    public SizeOptF32 maybeClampO(SizeOptF32 min, SizeOptF32 max) {
        return new SizeOptF32(
                OptF32.maybeClampO(width, min.width, max.width), OptF32.maybeClampO(height, min.height, max.height));
    }

    /// `maybe_add` — component-wise
    public SizeOptF32 maybeAddO(SizeOptF32 rhs) {
        return new SizeOptF32(OptF32.maybeAddO(width, rhs.width), OptF32.maybeAddO(height, rhs.height));
    }

    /// `maybe_sub` — component-wise
    public SizeOptF32 maybeSubO(SizeOptF32 rhs) {
        return new SizeOptF32(OptF32.maybeSubO(width, rhs.width), OptF32.maybeSubO(height, rhs.height));
    }

    /// `maybe_min` with `Size<f32>` rhs — component-wise `Option<f32>::maybe_min(f32)`
    public SizeOptF32 maybeMinF(SizeF32 rhs) {
        return new SizeOptF32(OptF32.maybeMinF(width, rhs.width()), OptF32.maybeMinF(height, rhs.height()));
    }

    /// `maybe_max` with `Size<f32>` rhs
    public SizeOptF32 maybeMaxF(SizeF32 rhs) {
        return new SizeOptF32(OptF32.maybeMaxF(width, rhs.width()), OptF32.maybeMaxF(height, rhs.height()));
    }

    /// `maybe_clamp` with `Size<f32>` bounds
    public SizeOptF32 maybeClampF(SizeF32 min, SizeF32 max) {
        return new SizeOptF32(
                OptF32.maybeClampF(width, min.width(), max.width()),
                OptF32.maybeClampF(height, min.height(), max.height()));
    }

    /// `maybe_add` with `Size<f32>` rhs
    public SizeOptF32 maybeAddF(SizeF32 rhs) {
        return new SizeOptF32(OptF32.maybeAddF(width, rhs.width()), OptF32.maybeAddF(height, rhs.height()));
    }

    /// `maybe_sub` with `Size<f32>` rhs
    public SizeOptF32 maybeSubF(SizeF32 rhs) {
        return new SizeOptF32(OptF32.maybeSubF(width, rhs.width()), OptF32.maybeSubF(height, rhs.height()));
    }

    /// `PartialEq` -> rustEq: Option<f32> fields compare via OptF32.eq (GAP-004)
    public boolean rustEq(SizeOptF32 other) {
        return OptF32.eq(width, other.width) && OptF32.eq(height, other.height);
    }
}
