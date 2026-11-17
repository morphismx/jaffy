// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Rect<T>` (generic record)
//
// `Rect<f32>` -> RectF32, `Rect<Option<f32>>` -> RectOptF32.
// `impl<T: Add>` Add impl -> `zipMap` with an explicit operator.
package net.morphismmc.jaffy.geometry;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.function.ObjFloatFunction;
import net.morphismmc.just.function.ToFloatFunction;

public record Rect<T>(T left, T right, T top, T bottom) {

    /// `impl Add<Rect<U>> for Rect<T>` — field-wise `self + rhs` with an
    /// explicit add function (Rust resolves `T::add` statically).
    public <U, R> Rect<R> zipMap(Rect<U> rhs, BiFunction<T, U, R> f) {
        return new Rect<>(
                f.apply(left, rhs.left), f.apply(right, rhs.right), f.apply(top, rhs.top), f.apply(bottom, rhs.bottom));
    }

    /// Swap the horizontal and vertical sides (`left <-> top`, `right <-> bottom`)
    public Rect<T> transpose() {
        return new Rect<>(top, bottom, left, right);
    }

    /// `zip_size` — applies `f` to all four sides; left/right use
    /// `size.width`, top/bottom use `size.height` (`#[cfg(flexbox|block)]`).
    public <U, R> Rect<R> zipSize(Size<U> size, BiFunction<T, U, R> f) {
        return new Rect<>(
                f.apply(left, size.width()),
                f.apply(right, size.width()),
                f.apply(top, size.height()),
                f.apply(bottom, size.height()));
    }

    /// `zip_size` over a `Size<f32>` producing `Rect<Option<f32>>`
    /// (e.g. `inset.zip_size(parent_size, |p, s| p.maybe_resolve(s, calc))`).
    public RectOptF32 zipSizeO(SizeF32 size, ObjFloatFunction<T> f) {
        return new RectOptF32(
                f.applyAsFloat(left, size.width()),
                f.applyAsFloat(right, size.width()),
                f.applyAsFloat(top, size.height()),
                f.applyAsFloat(bottom, size.height()));
    }

    /// Applies the function `f` to the left, right, top, and bottom properties
    public <R> Rect<R> map(Function<T, R> f) {
        return new Rect<>(f.apply(left), f.apply(right), f.apply(top), f.apply(bottom));
    }

    /// `map` producing `Rect<f32>` (GAP-006 F suffix)
    public RectF32 mapF(ToFloatFunction<T> f) {
        return new RectF32(f.applyAsFloat(left), f.applyAsFloat(right), f.applyAsFloat(top), f.applyAsFloat(bottom));
    }

    /// `map` producing `Rect<Option<f32>>` (GAP-006 O suffix)
    public RectOptF32 mapO(ToFloatFunction<T> f) {
        return new RectOptF32(f.applyAsFloat(left), f.applyAsFloat(right), f.applyAsFloat(top), f.applyAsFloat(bottom));
    }

    /// Returns a `Line<T>` representing the left and right properties of the Rect
    public Line<T> horizontalComponents() {
        return new Line<>(left, right);
    }

    /// Returns a `Line<T>` containing the top and bottom properties of the Rect
    public Line<T> verticalComponents() {
        return new Line<>(top, bottom);
    }

    // ---- `impl<T: Add>` methods — take the add function explicitly ----

    /// `grid_axis_sum` — the sum of the two fields representing `axis`
    public <U> U gridAxisSum(AbsoluteAxis axis, BiFunction<T, T, U> add) {
        return axis == AbsoluteAxis.HORIZONTAL ? add.apply(left, right) : add.apply(top, bottom);
    }

    /// `horizontal_axis_sum` — left + right (NOT the width)
    public <U> U horizontalAxisSum(BiFunction<T, T, U> add) {
        return add.apply(left, right);
    }

    /// `vertical_axis_sum` — top + bottom (NOT the height)
    public <U> U verticalAxisSum(BiFunction<T, T, U> add) {
        return add.apply(top, bottom);
    }

    /// `sum_axes` — both axis sums as a `Size<U>`
    public <U> Size<U> sumAxes(BiFunction<T, T, U> add) {
        return new Size<>(horizontalAxisSum(add), verticalAxisSum(add));
    }

    /// `main_axis_sum` (`#[cfg(feature = "flexbox")]`, pub(crate))
    public <U> U mainAxisSum(FlexDirection direction, BiFunction<T, T, U> add) {
        return direction.isRow() ? horizontalAxisSum(add) : verticalAxisSum(add);
    }

    /// `cross_axis_sum` (`#[cfg(feature = "flexbox")]`, pub(crate))
    public <U> U crossAxisSum(FlexDirection direction, BiFunction<T, T, U> add) {
        return direction.isRow() ? verticalAxisSum(add) : horizontalAxisSum(add);
    }

    // ---- `impl<T> Rect<T>` flexbox side pickers (`#[cfg(feature = "flexbox")]`, pub(crate)) ----

    /// `main_start` — start/top value from the perspective of the main axis
    public T mainStart(FlexDirection direction) {
        return direction.isRow() ? left : top;
    }

    /// `main_end` — end/bottom value from the perspective of the main axis
    public T mainEnd(FlexDirection direction) {
        return direction.isRow() ? right : bottom;
    }

    /// `cross_start` — start/top value from the perspective of the cross axis
    public T crossStart(FlexDirection direction) {
        return direction.isRow() ? top : left;
    }

    /// `cross_end` — end/bottom value from the perspective of the cross axis
    public T crossEnd(FlexDirection direction) {
        return direction.isRow() ? bottom : right;
    }

    /// `PartialEq` -> rustEq with caller-supplied element equality (GAP-004)
    public boolean rustEq(Rect<T> other, BiPredicate<T, T> eq) {
        return eq.test(left, other.left)
                && eq.test(right, other.right)
                && eq.test(top, other.top)
                && eq.test(bottom, other.bottom);
    }
}
