// JAFFY: port of upstream src/geometry.rs — `InBothAbsAxis<T>` (Rust `pub(crate)`).
//
// Container that holds an item in each absolute axis without specifying
// what kind of item it is. (Rust pub(crate) — public here since jaffy is one module.)
package net.morphismmc.jaffy.geometry;

public record InBothAbsAxis<T>(T horizontal, T vertical) {

    /// Get the contained item based on the AbsoluteAxis passed (`#[cfg(feature = "grid")]`)
    public T get(AbsoluteAxis axis) {
        return axis == AbsoluteAxis.HORIZONTAL ? horizontal : vertical;
    }

    /// `PartialEq` -> rustEq with caller-supplied element equality (GAP-004)
    public boolean rustEq(InBothAbsAxis<T> other, java.util.function.BiPredicate<T, T> eq) {
        return eq.test(horizontal, other.horizontal) && eq.test(vertical, other.vertical);
    }
}
