// JAFFY: port of upstream src/tree/traits.rs — `LayoutContainingBlock` trait.
//
// GAT `OofItemStyle<'a>` -> `Style` (GAP-017); `&[NodeId]` -> `long[]`;
// `&DetailedLayoutInfo` -> `DetailedLayoutInfo` (immutable, shared reference).
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.tree.layout.DetailedLayoutInfo;


/// Extends {@link LayoutPartialTree} with the operations needed by the
/// out-of-flow positioning pass (`compute_oof_layout`), which lays out the
/// absolute/fixed boxes for which a node acts as the containing block.
///
/// This trait is required by `compute_oof_layout` and `compute_root_layout`.
public interface LayoutContainingBlock extends LayoutPartialTree {

    /// Get the style of an out-of-flow box being positioned by its containing
    /// block
    ///
    /// Interpreted in the containing block's axes (see
    /// {@link LayoutPartialTree#getCoreContainerStyle}).
    Style getOofItemStyle(long nodeId);

    /// Clear the list of out-of-flow (absolute/fixed) boxes whose containing
    /// block is `nodeId`.
    ///
    /// This is called (exactly once) for each node laid out with
    /// `RunMode::PerformLayout`, before the boxes it lays out are recorded
    /// with {@link #addHoistedChildren}, so that lists recorded by previous
    /// layout runs do not persist.
    void clearHoistedChildren(long nodeId);

    /// Append to the list of out-of-flow boxes whose containing block is
    /// `nodeId`.
    ///
    /// This is called by each containing block for the boxes it lays out, and
    /// by `compute_root_layout` to record boxes (e.g. `position: fixed`
    /// boxes) which are positioned by the final root positioning pass, which
    /// runs after the root node's own layout algorithm has already recorded
    /// its list. The recorded lists are consumed by `round_layout` (out-of-
    /// flow boxes are rounded via their containing block rather than via their
    /// parent), and are also useful for consumers implementing
    /// paint/hit-testing traversals.
    void addHoistedChildren(long nodeId, long[] hoisted);

    /// Read back the detailed layout information most recently recorded for
    /// `nodeId` (as stored by `LayoutGridContainer#setDetailedGridInfo`).
    ///
    /// This is used by the out-of-flow positioning pass to resolve the grid
    /// area of absolutely positioned boxes whose containing block is a grid
    /// container. Implementing this method is optional: the default returns
    /// {@link DetailedLayoutInfo#NONE}, in which case such boxes are
    /// positioned relative to the grid container's padding box instead of
    /// their grid area.
    default DetailedLayoutInfo getDetailedLayoutInfo(long nodeId) {
        return DetailedLayoutInfo.NONE;
    }
}
