// JAFFY: port of upstream src/tree/traits.rs — `RoundTree` trait.
//
// `usize` -> `int` (GAP-014); `Layout` is an immutable record — Rust's
// by-value return is a plain return here.
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.tree.layout.Layout;

/// Trait used by the `round_layout` method which takes a tree of unrounded
/// float-valued layouts and performs rounding to snap the values to the
/// pixel grid.
///
/// As indicated by its dependence on {@link TraverseTree}, it requires full
/// recursive access to the tree.
public interface RoundTree extends TraverseTree {

    /// Get the node's unrounded layout
    Layout getUnroundedLayout(long nodeId);

    /// Set the node's final layout (`set_final_layout`)
    void setFinalLayout(long nodeId, Layout layout);

    /// Whether the node is an out-of-flow (absolute/fixed) box. Out-of-flow
    /// boxes are hoisted to their containing block, so `round_layout` skips
    /// them when visiting a node's children and instead visits them via their
    /// containing block's hoisted child list.
    ///
    /// This should return `true` for box-generating nodes whose position style
    /// is `absolute` or `fixed`, and `false` otherwise (including for
    /// `display: none` nodes).
    boolean isOutOfFlow(long nodeId);

    /// The number of out-of-flow boxes whose containing block is `nodeId`
    /// (as recorded by `LayoutContainingBlock#addHoistedChildren`)
    int hoistedChildCount(long nodeId);

    /// Get the nth out-of-flow box whose containing block is `nodeId`
    /// (as recorded by `LayoutContainingBlock#addHoistedChildren`)
    long getHoistedChildId(long nodeId, int index);
}
