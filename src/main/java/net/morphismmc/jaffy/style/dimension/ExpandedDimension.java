// JAFFY: port of upstream src/style/dimension.rs — `ExpandedDimension`.
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
            case Length l -> other instanceof Length o && l.val == o.val;
            case Percent p -> other instanceof Percent o && p.val == o.val;
            case FitContentPx f -> other instanceof FitContentPx o && f.val == o.val;
            case FitContentPercent f -> other instanceof FitContentPercent o && f.val == o.val;
            case FitContentCalc f -> other instanceof FitContentCalc o && f.handle == o.handle;
            case Calc c -> other instanceof Calc o && c.handle == o.handle;
            default -> getClass() == other.getClass();
        };
    }
}
