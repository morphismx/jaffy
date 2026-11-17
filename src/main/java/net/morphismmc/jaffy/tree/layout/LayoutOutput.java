// JAFFY-UPSTREAM: src/tree/layout.rs
// JAFFY-NOTE: `LayoutOutput` struct
// `oof_positioning_area: Option<OofPositioningArea>` -> nullable field (GAP-001).
// serde impl deferred.
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.SizeF32;

import org.jspecify.annotations.Nullable;

/// A struct containing the result of laying a single node, which is returned up to the parent node
///
/// A baseline is the line on which text sits. Your node likely has a baseline if it is a text node, or contains
/// children that may be text nodes. See <https://www.w3.org/TR/css-writing-modes-3/#intro-baselines> for details.
/// If your node does not have a baseline (or you are unsure how to compute it), then simply return `Baselines.NONE`
/// for the baselines field
///
/// @param size the size of the node
/// @param scrollableOverflowRect the scrollable overflow rectangle of the node's content
///        (see `Layout.scrollableOverflowRect` for the coordinate conventions)
/// @param baselines the first and last baselines of the node in the horizontal axis, if any
/// @param topMargin top margin that can be collapsed with. This is used for CSS block layout and
///        can be set to `CollapsibleMarginSet.ZERO` for other layout modes that don't support
///        margin collapsing
/// @param bottomMargin bottom margin that can be collapsed with. Same notes as `topMargin`
/// @param marginsCanCollapseThrough whether margins can be collapsed through this node. This is
///        used for CSS block layout and can be set to `false` for other layout modes that don't
///        support margin collapsing
/// @param oofCandidates out-of-flow (absolute/fixed) descendants of this node for which this node
///        is *not* the containing block. These bubble up the tree until they reach their containing
///        block, which lays them out. Empty (allocation-free) when there are no such descendants
///        (the common case). Candidates are only produced by `RunMode.PERFORM_LAYOUT` passes.
///        Container layout algorithms place the *full* document-ordered candidate list here
///        (direct out-of-flow children plus candidates bubbled from in-flow children). The
///        out-of-flow positioning pass then lays out those for which the node is the containing
///        block and replaces this list with the unclaimed remainder.
/// @param oofPositioningArea the node's inset-resolution area for out-of-flow boxes. Set by
///        container layout algorithms for `RunMode.PERFORM_LAYOUT` runs; `null` for leaves and
///        size-only runs (in which case the out-of-flow positioning pass is a no-op).
public record LayoutOutput(
        SizeF32 size,
        ScrollableOverflow scrollableOverflowRect,
        Baselines baselines,
        CollapsibleMarginSet topMargin,
        CollapsibleMarginSet bottomMargin,
        boolean marginsCanCollapseThrough,
        OofCandidates oofCandidates,
        @Nullable OofPositioningArea oofPositioningArea) {

    /// An all-zero `LayoutOutput` for hidden nodes.
    /// `OofCandidates` is mutable, so this cannot be a shared constant — it is a
    /// static factory (upstream `const HIDDEN` is inlined fresh at each use).
    public static LayoutOutput hidden() {
        return new LayoutOutput(
                SizeF32.ZERO,
                ScrollableOverflow.ZERO,
                Baselines.NONE,
                CollapsibleMarginSet.ZERO,
                CollapsibleMarginSet.ZERO,
                false,
                OofCandidates.none(),
                null);
    }

    /// A blank layout output (`LayoutOutput::DEFAULT`)
    public static LayoutOutput defaultOutput() {
        return hidden();
    }

    /// Constructor to create a `LayoutOutput` from just the size, scrollable overflow rectangle
    /// and baselines
    public static LayoutOutput fromSizesAndBaselines(
            SizeF32 size, ScrollableOverflow scrollableOverflowRect, Baselines baselines) {
        return new LayoutOutput(
                size,
                scrollableOverflowRect,
                baselines,
                CollapsibleMarginSet.ZERO,
                CollapsibleMarginSet.ZERO,
                false,
                OofCandidates.none(),
                null);
    }

    /// Construct a `LayoutOutput` from just the container size and scrollable overflow rectangle
    public static LayoutOutput fromSizes(SizeF32 size, ScrollableOverflow scrollableOverflowRect) {
        return fromSizesAndBaselines(size, scrollableOverflowRect, Baselines.NONE);
    }

    /// Swap the horizontal and vertical axes of the output (inverse of `LayoutInput.transpose`).
    ///
    /// Baselines and collapsible margins are block-axis quantities that have no meaning on the
    /// parent's inline axis, so they are dropped. Out-of-flow candidates are unaffected.
    public LayoutOutput transpose() {
        return new LayoutOutput(
                size.transpose(),
                scrollableOverflowRect.transpose(),
                Baselines.NONE,
                CollapsibleMarginSet.ZERO,
                CollapsibleMarginSet.ZERO,
                false,
                oofCandidates,
                oofPositioningArea != null ? oofPositioningArea.transpose() : null);
    }

    /// Construct a `LayoutOutput` from just the container's size.
    public static LayoutOutput fromOuterSize(SizeF32 size) {
        return fromSizes(size, ScrollableOverflow.ZERO);
    }

    /// `Clone` — GAP-005 `.copy()`. `oofCandidates` is deep-copied (upstream
    /// `OofCandidates::clone` copies the inner Vec); the record fields are
    /// immutable and shared.
    public LayoutOutput copy() {
        return new LayoutOutput(
                size,
                scrollableOverflowRect,
                baselines,
                topMargin,
                bottomMargin,
                marginsCanCollapseThrough,
                oofCandidates.copy(),
                oofPositioningArea);
    }
}
