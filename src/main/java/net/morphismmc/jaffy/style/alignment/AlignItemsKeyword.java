// JAFFY-UPSTREAM: src/style/alignment.rs
// JAFFY-NOTE: `AlignItemsKeyword`
package net.morphismmc.jaffy.style.alignment;

/// The position-keyword half of [`AlignItems`] (and its aliases `AlignSelf`,
/// `JustifyItems`, `JustifySelf`).
///
/// Compute paths match on this enum directly so every match is exhaustive and
/// requires no `Safe*` siblings.
public enum AlignItemsKeyword {
    /// The layout mode's default alignment: usually `Stretch`, but `Start`
    /// in some cases. (Rust `#[default]`)
    NORMAL,
    /// Items are packed toward the start of the axis.
    START,
    /// Items are packed toward the end of the axis.
    END,
    /// Items are packed towards the flex-relative start of the axis.
    /// For flex containers with `RowReverse`/`ColumnReverse` this is
    /// equivalent to `End`; in all other cases equivalent to `Start`.
    FLEX_START,
    /// Items are packed towards the flex-relative end of the axis.
    FLEX_END,
    /// Packed toward the start of the axis as determined by the item's own
    /// writing mode/direction (rather than the container's).
    SELF_START,
    /// Packed toward the end of the axis as determined by the item's own
    /// writing mode/direction (rather than the container's).
    SELF_END,
    /// Items are packed along the center of the cross axis.
    CENTER,
    /// Items are aligned such as their baselines align.
    BASELINE,
    /// Stretch to fill the container.
    STRETCH;

    /// `Default` impl — `#[default] Normal`
    public static final AlignItemsKeyword DEFAULT = NORMAL;
}
