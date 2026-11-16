// JAFFY: port of upstream src/tree/layout.rs — `AxisStaticPosition` struct.
// The `debug_assert!` on unresolved self-start/self-end maps to
// `IllegalStateException` (GAP-010).
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;

/// The static position of an out-of-flow box in a single axis: the alignment container
/// (start/end coordinates) plus the alignment to apply to the box's margin box within it.
///
/// Area coordinates are initially relative to the border box of the node which produced the
/// candidate and are translated by each node's location as the candidate bubbles up the tree, so
/// they are always relative to the border box of the node currently holding the candidate.
///
/// Emitters which have no alignment area in an axis (e.g. block layout, where the static
/// position is a point) emit a degenerate area (`start == end`).
///
/// @param area the alignment container in this axis
/// @param align the alignment to apply to the box's margin box within `area`
public record AxisStaticPosition(LineF32 area, AxisStaticAlign align) {

    /// Create a `AxisStaticPosition` with a degenerate (zero-extent) area at `anchor` and no
    /// safe fallback
    public static AxisStaticPosition fromEdge(float anchor, AxisStaticEdge edge) {
        return new AxisStaticPosition(new LineF32(anchor, anchor), AxisStaticAlign.fromKeyword(edge));
    }

    /// Create a `AxisStaticPosition` which aligns the box within `area` according to its
    /// self-alignment property in this axis (`justify-self` in the inline axis, `align-self` in
    /// the block axis). `normal` and `stretch` behave as `start`. This is the static position
    /// used by block and inline layout.
    ///
    /// `alignment` must already be resolved: `auto` (`None`) replaced by the container's
    /// `justify-items`/`align-items`, and `self-start`/`self-end` resolved against the
    /// container's direction (see `AlignItems.resolveSelfRelative`).
    /// `axisIsRtl` is whether this axis is the inline axis of a right-to-left static-position
    /// containing block, in which case `start` and `end` are flipped.
    public static AxisStaticPosition fromAlignment(AlignItems alignment, LineF32 area, boolean axisIsRtl) {
        if (alignment.keyword() == AlignItemsKeyword.SELF_START
                || alignment.keyword() == AlignItemsKeyword.SELF_END) {
            throw new IllegalStateException(
                    "self-start/self-end must be resolved before computing the static position");
        }
        AlignItemsKeyword fallback = alignment.safety() == AlignmentSafety.SAFE
                ? AlignItemsKeyword.START
                : alignment.keyword();
        return new AxisStaticPosition(
                area,
                new AxisStaticAlign(
                        edgeFor(alignment.keyword(), axisIsRtl), alignment.safety(), edgeFor(fallback, axisIsRtl)));
    }

    /// The edge of the static-position rectangle that a (resolved) alignment keyword
    /// aligns the box to. Normal and Stretch (and anything else that is not an end or
    /// center alignment) fall back to start-alignment for static-position purposes.
    private static AxisStaticEdge edgeFor(AlignItemsKeyword keyword, boolean axisIsRtl) {
        boolean startPosition = !(keyword == AlignItemsKeyword.END || keyword == AlignItemsKeyword.FLEX_END)
                != axisIsRtl;
        if (keyword == AlignItemsKeyword.CENTER) {
            return AxisStaticEdge.CENTER;
        }
        return startPosition ? AxisStaticEdge.START : AxisStaticEdge.END;
    }
}
