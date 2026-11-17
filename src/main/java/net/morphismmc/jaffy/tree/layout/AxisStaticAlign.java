// JAFFY: upstream=src/tree/layout.rs
// JAFFY-NOTE: `AxisStaticAlign` struct
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.style.alignment.AlignmentSafety;

/// The alignment to apply to an out-of-flow box's margin box within its static-position area
/// in a single axis.
///
/// Keywords are physical: container-specific semantics (`justify-content`/`align-self`
/// resolution, flex-direction/wrap-reverse reversal, RTL flipping, `self-start`/`self-end`)
/// are resolved by the emitting layout algorithm.
///
/// @param keyword the physical alignment keyword
/// @param safety the `safe` overflow-position keyword from CSS Box Alignment: when `Safe`, the
///        `fallback` keyword is used instead if the box's margin box overflows the area
/// @param fallback the physical alignment keyword to fall back to when `safety` is `Safe` and
///        the box's margin box overflows the area
public record AxisStaticAlign(AxisStaticEdge keyword, AlignmentSafety safety, AxisStaticEdge fallback) {

    /// Create a `AxisStaticAlign` from a keyword with no safe fallback
    public static AxisStaticAlign fromKeyword(AxisStaticEdge keyword) {
        return new AxisStaticAlign(keyword, AlignmentSafety.DEFAULT, keyword);
    }
}
