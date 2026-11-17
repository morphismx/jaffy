// JAFFY-UPSTREAM: src/style/dimension.rs
// JAFFY-NOTE: `ExpandedDimension`
package net.morphismmc.jaffy.style.dimension;

public sealed interface ExpandedDimension {

    /// An absolute length (see `Dimension.length`)
    record Length(float val) implements ExpandedDimension {}

    /// A percentage length (see `Dimension.percent`)
    record Percent(float val) implements ExpandedDimension {}

    /// The automatic keyword (see `Dimension.auto`)
    record Auto() implements ExpandedDimension {}

    /// The `min-content` keyword (see `Dimension.minContent`)
    record MinContent() implements ExpandedDimension {}

    /// The `max-content` keyword (see `Dimension.maxContent`)
    record MaxContent() implements ExpandedDimension {}

    /// A `fit-content(...)` value with a length limit (see `Dimension.fitContentPx`)
    record FitContentPx(float val) implements ExpandedDimension {}

    /// A `fit-content(...)` value with a percentage limit (see `Dimension.fitContentPercent`)
    record FitContentPercent(float val) implements ExpandedDimension {}

    /// `fit-content()` with a `calc()` limit — the `*const ()` is an `Object` handle.
    record FitContentCalc(Object handle) implements ExpandedDimension {}

    /// The `fit-content` keyword with no limit (see `Dimension.fitContent`)
    record FitContent() implements ExpandedDimension {}

    /// The `stretch` keyword (see `Dimension.stretch`)
    record Stretch() implements ExpandedDimension {}

    /// The `content` keyword (see `Dimension.content`)
    record Content() implements ExpandedDimension {}

    /// A `calc()` value — opaque handle to the calc representation.
    record Calc(Object handle) implements ExpandedDimension {}

    /// `From<Dimension>` = `value.expand()`
    static ExpandedDimension from(Dimension value) {
        return value.expand();
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`), handles identity.
    default boolean rustEq(ExpandedDimension other) {
        return switch (this) {
            case Length(float v) -> other instanceof Length(float ov) && v == ov;
            case Percent(float v) -> other instanceof Percent(float ov) && v == ov;
            case FitContentPx(float v) -> other instanceof FitContentPx(float ov) && v == ov;
            case FitContentPercent(float v) -> other instanceof FitContentPercent(float ov) && v == ov;
            case FitContentCalc(Object h) -> other instanceof FitContentCalc(Object oh) && h == oh;
            case Calc(Object h) -> other instanceof Calc(Object oh) && h == oh;
            default -> getClass() == other.getClass();
        };
    }
}
