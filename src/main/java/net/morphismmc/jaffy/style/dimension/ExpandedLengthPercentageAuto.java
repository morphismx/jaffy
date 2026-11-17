// JAFFY: upstream=src/style/dimension.rs
// JAFFY-NOTE: `ExpandedLengthPercentageAuto`
package net.morphismmc.jaffy.style.dimension;

public sealed interface ExpandedLengthPercentageAuto {

    /// An absolute length (see `LengthPercentageAuto.length`)
    record Length(float val) implements ExpandedLengthPercentageAuto {}

    /// A percentage length (see `LengthPercentageAuto.percent`)
    record Percent(float val) implements ExpandedLengthPercentageAuto {}

    /// The automatic keyword (see `LengthPercentageAuto.auto`)
    record Auto() implements ExpandedLengthPercentageAuto {}

    /// A `calc()` value — the `*const ()` pointer is an `Object` handle in Java.
    record Calc(Object handle) implements ExpandedLengthPercentageAuto {}

    /// `From<LengthPercentageAuto>` = `value.expand()`
    static ExpandedLengthPercentageAuto from(LengthPercentageAuto value) {
        return value.expand();
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`), Calc handle identity.
    default boolean rustEq(ExpandedLengthPercentageAuto other) {
        return switch (this) {
            case Length(float v) -> other instanceof Length(float ov) && v == ov;
            case Percent(float v) -> other instanceof Percent(float ov) && v == ov;
            case Auto() -> other instanceof Auto;
            case Calc(Object h) -> other instanceof Calc(Object oh) && h == oh;
        };
    }
}
