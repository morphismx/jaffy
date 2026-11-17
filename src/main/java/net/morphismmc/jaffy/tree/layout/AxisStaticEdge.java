// JAFFY: upstream=src/tree/layout.rs
// JAFFY-NOTE: `AxisStaticEdge` enum
package net.morphismmc.jaffy.tree.layout;

/// The physical alignment of an out-of-flow box's margin box within its static-position
/// area (per-axis).
///
/// The static position of an out-of-flow box may depend on alignment properties
/// (`justify-content`/`align-self` in flexbox, `justify-self`/`align-self` in grid) which cannot
/// be fully resolved until the box's final size is known. To defer that resolution, the static
/// position is recorded as an *area* plus the physical alignment keyword to apply to the box's
/// margin box within that area.
public enum AxisStaticEdge {

    /// Align the start (left/top) edge of the box's margin box to the start of the area
    START,
    /// Center the box within the area
    CENTER,
    /// Align the end (right/bottom) edge of the box's margin box to the end of the area
    END
}
