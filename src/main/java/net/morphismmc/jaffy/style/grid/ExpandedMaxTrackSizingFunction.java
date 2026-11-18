// JAFFY-UPSTREAM: src/style/grid.rs
// `ExpandedMaxTrackSizingFunction`. `*const ()` handles are `Object` (GAP-019).
package net.morphismmc.jaffy.style.grid;

/// The expanded, non-compact representation of a
/// [`MaxTrackSizingFunction`].
public sealed interface ExpandedMaxTrackSizingFunction {

    /// An absolute length
    record Length(float val) implements ExpandedMaxTrackSizingFunction {}

    /// A percentage length
    record Percent(float val) implements ExpandedMaxTrackSizingFunction {}

    /// The automatic keyword
    record Auto() implements ExpandedMaxTrackSizingFunction {}

    /// The `min-content` keyword
    record MinContent() implements ExpandedMaxTrackSizingFunction {}

    /// The `max-content` keyword
    record MaxContent() implements ExpandedMaxTrackSizingFunction {}

    /// A `fit-content(...)` value with a length limit
    record FitContentPx(float val) implements ExpandedMaxTrackSizingFunction {}

    /// A `fit-content(...)` value with a percentage limit
    record FitContentPercent(float val) implements ExpandedMaxTrackSizingFunction {}

    /// Track maximum size should be content sized under a max-content
    /// constraint, but not larger than the `calc()` limit (`#[cfg(feature = "calc")]`)
    record FitContentCalc(Object handle) implements ExpandedMaxTrackSizingFunction {}

    /// A fraction of the leftover space
    record Fr(float val) implements ExpandedMaxTrackSizingFunction {}

    /// A `calc()` value — opaque handle (`#[cfg(feature = "calc")]`)
    record Calc(Object handle) implements ExpandedMaxTrackSizingFunction {}
}
