// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-NOTE: `AbstractAxis` enum
package net.morphismmc.jaffy.geometry;

public enum AbstractAxis {
    /// The axis in the inline dimension, i.e. the horizontal axis in
    /// horizontal writing modes and the vertical axis in vertical modes.
    INLINE,
    /// The axis in the block dimension.
    BLOCK;

    /// Returns the other variant of the enum
    public AbstractAxis other() {
        return this == INLINE ? BLOCK : INLINE;
    }

    /// Convert an `AbstractAxis` into an `AbsoluteAxis` naively assuming
    /// that the Inline axis is Horizontal (currently always true).
    public AbsoluteAxis asAbsNaive() {
        return this == INLINE ? AbsoluteAxis.HORIZONTAL : AbsoluteAxis.VERTICAL;
    }
}
