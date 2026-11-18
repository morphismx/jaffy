// JAFFY-UPSTREAM: src/style/grid.rs
// `ExpandedMinTrackSizingFunction`. `*const ()` handles are `Object` (GAP-019).
package net.morphismmc.jaffy.style.grid;

/// The expanded, non-compact representation of a
/// [`MinTrackSizingFunction`].
public sealed interface ExpandedMinTrackSizingFunction {

    /// An absolute length
    record Length(float val) implements ExpandedMinTrackSizingFunction {}

    /// A percentage length
    record Percent(float val) implements ExpandedMinTrackSizingFunction {}

    /// The automatic keyword
    record Auto() implements ExpandedMinTrackSizingFunction {}

    /// The `min-content` keyword
    record MinContent() implements ExpandedMinTrackSizingFunction {}

    /// The `max-content` keyword
    record MaxContent() implements ExpandedMinTrackSizingFunction {}

    /// A `calc()` value — opaque handle (`#[cfg(feature = "calc")]`)
    record Calc(Object handle) implements ExpandedMinTrackSizingFunction {}
}
