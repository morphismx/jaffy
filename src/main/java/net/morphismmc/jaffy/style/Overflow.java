// JAFFY: upstream=src/style/mod.rs
// JAFFY-NOTE: `Overflow` enum
// The `parse` impl is deferred with the feature.
package net.morphismmc.jaffy.style;

import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

/// How children overflowing their container should affect layout
///
/// In CSS the primary effect of this property is to control whether contents of a parent container that overflow that
/// container should
/// be displayed anyway, be clipped, or trigger the container to become a scroll container. However it also has
/// secondary effects on layout,
/// the main ones being:
///
///   - The automatic minimum size Flexbox/CSS Grid items with non-`VISIBLE` overflow is `0` rather than being content
/// based
///   - `SCROLL` nodes have space in the layout reserved for a scrollbar (width controlled by the `scrollbarWidth`
/// property)
///
/// In Taffy, we only implement the layout related secondary effects as we are not concerned with drawing/painting. The
/// amount of space reserved for
/// a scrollbar is controlled by the `scrollbarWidth` property. If this is `0` then `SCROLL` behaves identically to
/// `HIDDEN`.
///
/// <https://developer.mozilla.org/en-US/docs/Web/CSS/overflow>
public enum Overflow {

    /// The automatic minimum size of this node as a flexbox/grid item should be based on the size of its content.
    /// Content that overflows this node *should* contribute to the scroll region of its parent.
    VISIBLE,
    /// The automatic minimum size of this node as a flexbox/grid item should be based on the size of its content.
    /// Content that overflows this node should *not* contribute to the scroll region of its parent.
    CLIP,
    /// The automatic minimum size of this node as a flexbox/grid item should be `0`.
    /// Content that overflows this node should *not* contribute to the scroll region of its parent.
    HIDDEN,
    /// The automatic minimum size of this node as a flexbox/grid item should be `0`. Additionally, space should be
    /// reserved
    /// for a scrollbar. The amount of space reserved is controlled by the `scrollbarWidth` property.
    /// Content that overflows this node should *not* contribute to the scroll region of its parent.
    SCROLL;

    /// Rust `#[default]` variant
    public static final Overflow DEFAULT = VISIBLE;

    /// Returns true for overflow modes that contain their contents (`HIDDEN`, `SCROLL`)
    /// or else false for overflow modes that allow their contains to spill (`VISIBLE`).
    public boolean isScrollContainer() {
        return this == HIDDEN || this == SCROLL;
    }

    /// Returns `Some(0.0)` if the overflow mode would cause the automatic minimum size of a Flexbox or CSS Grid item
    /// to be `0`. Else returns None. (`pub(crate)` in Rust.)
    public @Opt float maybeIntoAutomaticMinSize() {
        return isScrollContainer() ? 0.0f : OptF32.NONE;
    }
}
