// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-NOTE: `Size<T>` (generic record)
//
// `Size<f32>` -> SizeF32, `Size<Option<f32>>` -> SizeOptF32 (SPECIALIZATIONS.md).
// `impl<T: Add>` `+`/`-` impls are not expressible generically; on SizeF32 they
// are concrete, on Size<T> call sites use `zipMap` with an explicit operator.
// `set_main`/`set_cross`/`set` (Rust &mut) return new records here (GAP-005).
package net.morphismmc.jaffy.geometry;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.ToFloatFunction;

public record Size<T>(T width, T height) {

    /// Get either the width or height depending on the AbsoluteAxis passed in
    public T getAbs(AbsoluteAxis axis) {
        return axis == AbsoluteAxis.HORIZONTAL ? width : height;
    }

    /// Swap the width and height components
    public Size<T> transpose() {
        return new Size<>(height, width);
    }

    /// Applies the function `f` to both the width and height
    public <R> Size<R> map(Function<T, R> f) {
        return new Size<>(f.apply(width), f.apply(height));
    }

    /// Applies the function `f` to the width
    public Size<T> mapWidth(UnaryOperator<T> f) {
        return new Size<>(f.apply(width), height);
    }

    /// Applies the function `f` to the height
    public Size<T> mapHeight(UnaryOperator<T> f) {
        return new Size<>(width, f.apply(height));
    }

    /// Applies the function `f` to both the width and height
    /// of this value and another passed value
    public <Other, Ret> Size<Ret> zipMap(Size<Other> other, BiFunction<T, Other, Ret> f) {
        return new Size<>(f.apply(width, other.width), f.apply(height, other.height));
    }

    /// `map` producing `Size<f32>` (GAP-006 F suffix)
    public SizeF32 mapF(ToFloatFunction<T> f) {
        return new SizeF32(f.applyAsFloat(width), f.applyAsFloat(height));
    }

    /// `map` producing `Size<Option<f32>>` (GAP-006 O suffix)
    public SizeOptF32 mapO(ToFloatFunction<T> f) {
        return new SizeOptF32(f.applyAsFloat(width), f.applyAsFloat(height));
    }

    // ---- flexbox axis helpers (`#[cfg(feature = "flexbox")]`, pub(crate)) ----

    /// Sets the extent of the main layout axis (Rust `set_main` — returns a copy)
    public Size<T> withMain(FlexDirection direction, T value) {
        return direction.isRow() ? new Size<>(value, height) : new Size<>(width, value);
    }

    /// Sets the extent of the cross layout axis (Rust `set_cross` — returns a copy)
    public Size<T> withCross(FlexDirection direction, T value) {
        return direction.isRow() ? new Size<>(width, value) : new Size<>(value, height);
    }

    /// Creates a new value of type Self with the main axis modified by the callback
    public Size<T> mapMain(FlexDirection direction, UnaryOperator<T> mapper) {
        return direction.isRow() ? new Size<>(mapper.apply(width), height) : new Size<>(width, mapper.apply(height));
    }

    /// Creates a new value of type Self with the cross axis modified by the callback
    public Size<T> mapCross(FlexDirection direction, UnaryOperator<T> mapper) {
        return direction.isRow() ? new Size<>(width, mapper.apply(height)) : new Size<>(mapper.apply(width), height);
    }

    /// Gets the extent of the main layout axis
    public T main(FlexDirection direction) {
        return direction.isRow() ? width : height;
    }

    /// Gets the extent of the cross layout axis
    public T cross(FlexDirection direction) {
        return direction.isRow() ? height : width;
    }

    // ---- grid axis helpers (`#[cfg(feature = "grid")]`, pub(crate)) ----

    /// Gets the extent of the specified layout axis
    public T get(AbstractAxis axis) {
        return axis == AbstractAxis.INLINE ? width : height;
    }

    /// Sets the extent of the specified layout axis (Rust `set` — returns a copy)
    public Size<T> with(AbstractAxis axis, T value) {
        return axis == AbstractAxis.INLINE ? new Size<>(value, height) : new Size<>(width, value);
    }

    // ---- `impl<T> Size<Option<T>>` methods (Option<T> -> nullable field) ----

    /// Performs `Option::unwrap_or` on each component separately.
    /// Only valid on `Size`s whose fields are Option-encoded (null = None).
    public Size<T> unwrapOr(Size<T> alt) {
        return new Size<>(width != null ? width : alt.width, height != null ? height : alt.height);
    }

    /// Performs `Option::or` on each component separately.
    public Size<T> or(Size<T> alt) {
        return new Size<>(width != null ? width : alt.width, height != null ? height : alt.height);
    }

    /// Return true if both components are Some, else false.
    public boolean bothAxisDefined() {
        return width != null && height != null;
    }

    /// `PartialEq` -> rustEq with caller-supplied element equality (GAP-004)
    public boolean rustEq(Size<T> other, BiPredicate<T, T> eq) {
        return eq.test(width, other.width) && eq.test(height, other.height);
    }
}
