// JAFFY-UPSTREAM: src/style/mod.rs
// JAFFY-NOTE: `Position` enum
// The `parse` impl is deferred with the feature.
package net.morphismmc.jaffy.style;

/// The positioning strategy for this item.
///
/// This controls both how the origin is determined for the `position` field,
/// and whether or not the item will be controlled by flexbox's layout algorithm.
///
/// WARNING: this enum follows the behavior of [CSS's `position`
/// property](https://developer.mozilla.org/en-US/docs/Web/CSS/position),
/// which can be unintuitive.
///
/// `STATIC` is the default value, matching the default behavior in CSS.
public enum Position {

    /// The item is laid out in normal flow. `inset` has no effect on statically positioned items,
    /// and they do not act as a containing block for absolutely positioned descendants.
    STATIC,
    /// The item is laid out in normal flow, then the `inset` offset is applied as a correction factor.
    /// Offsets do not affect the position of any other items.
    /// Relatively positioned items act as a containing block for absolutely positioned descendants.
    RELATIVE,
    /// The item is taken out of normal flow and positioned relative to its closest positioned ancestor
    /// (its containing block), if any. Otherwise, it is placed relative to the root.
    /// No space is created for the item in the page layout, and its size will not be altered.
    ///
    /// WARNING: to opt-out of layouting entirely, you must use `Display.NONE` instead on your `Style` object.
    ABSOLUTE,
    /// The item is taken out of normal flow and positioned relative to the root (viewport),
    /// unless an ancestor overrides containing-block behavior (e.g. via transforms in a full CSS implementation).
    FIXED,
    /// The item is laid out in normal flow exactly like a statically positioned item: `inset` is *not* applied
    /// as an offset (in CSS, sticky insets are scroll thresholds, which Taffy has no knowledge of; applying the
    /// sticky offset is the responsibility of the caller once it knows the scroll position).
    /// Like `RELATIVE`, a sticky item acts as a containing block for absolutely positioned descendants.
    STICKY;

    /// Rust `#[default]` variant
    public static final Position DEFAULT = STATIC;

    /// Returns true if this position takes the item out of normal flow (`Absolute` or `Fixed`)
    public boolean isOutOfFlow() {
        return this == ABSOLUTE || this == FIXED;
    }

    /// Returns true if this position causes the item to act as a containing block
    /// for absolutely positioned descendants (any value other than `Static`)
    public boolean isPositioned() {
        return this != STATIC;
    }
}
