// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Point<T>` (generic record)
//
// `set` (Rust &mut) returns a new record here (GAP-005).
package net.morphismmc.jaffy.geometry;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;

import net.morphismmc.jaffy.style.flex.FlexDirection;

public record Point<T>(T x, T y) {

    /// Applies the function `f` to both the x and y
    public <R> Point<R> map(Function<T, R> f) {
        return new Point<>(f.apply(x), f.apply(y));
    }

    /// Gets the extent of the specified layout axis (`#[cfg(feature = "grid")]`)
    public T get(AbstractAxis axis) {
        return axis == AbstractAxis.INLINE ? x : y;
    }

    /// Swap x and y components
    public Point<T> transpose() {
        return new Point<>(y, x);
    }

    /// Sets the extent of the specified layout axis (Rust `set` — returns a copy)
    public Point<T> with(AbstractAxis axis, T value) {
        return axis == AbstractAxis.INLINE ? new Point<>(value, y) : new Point<>(x, value);
    }

    /// Gets the component in the main layout axis (`#[cfg(feature = "flexbox")]`, pub(crate))
    public T main(FlexDirection direction) {
        return direction.isRow() ? x : y;
    }

    /// Gets the component in the cross layout axis (`#[cfg(feature = "flexbox")]`, pub(crate))
    public T cross(FlexDirection direction) {
        return direction.isRow() ? y : x;
    }

    /// `impl Add` — field-wise add with explicit function
    public <U, R> Point<R> zipMap(Point<U> rhs, BiFunction<T, U, R> f) {
        return new Point<>(f.apply(x, rhs.x), f.apply(y, rhs.y));
    }

    /// `From<Point<T>> for Size<T>`
    public Size<T> toSize() {
        return new Size<>(x, y);
    }

    /// `PartialEq` -> rustEq with caller-supplied element equality (GAP-004)
    public boolean rustEq(Point<T> other, BiPredicate<T, T> eq) {
        return eq.test(x, other.x) && eq.test(y, other.y);
    }
}
