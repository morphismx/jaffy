// JAFFY: port of upstream src/style/dimension.rs — `ExpandedLengthPercentage`.
//
// Rust enum -> sealed interface with per-variant records (GAP-005/-013 style).
package net.morphismmc.jaffy.style.dimension;

public sealed interface ExpandedLengthPercentage {

    /// An absolute length (see `LengthPercentage.length`)
    record Length(float val) implements ExpandedLengthPercentage {}

    /// A percentage length (see `LengthPercentage.percent`)
    record Percent(float val) implements ExpandedLengthPercentage {}

    /// A `calc()` value — the `*const ()` pointer is an `Object` handle in Java.
    record Calc(Object handle) implements ExpandedLengthPercentage {}

    /// `From<LengthPercentage>` = `value.expand()`
    static ExpandedLengthPercentage from(LengthPercentage value) {
        return value.expand();
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`), Calc handle identity.
    default boolean rustEq(ExpandedLengthPercentage other) {
        return switch (this) {
            case Length l -> other instanceof Length o && l.val == o.val;
            case Percent p -> other instanceof Percent o && p.val == o.val;
            case Calc c -> other instanceof Calc o && c.handle == o.handle;
        };
    }
}
