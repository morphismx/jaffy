// JAFFY: upstream=src/tree/taffy_tree.rs
// JAFFY-NOTE: `TaffyView` and its trait
// impls (TraversePartialTree, TraverseTree, LayoutPartialTree,
// LayoutContainingBlock, CacheTree, LayoutBlockContainer,
// LayoutFlexboxContainer, LayoutGridContainer, RoundTree).
//
// `pub(crate)` -> package-private; `&'t mut TaffyTree` -> shared reference;
// `MeasureFunction: FnMut(...)` -> {@link TaffyTree.MeasureFn} (GAP-019);
// `Option<&mut NodeContext>` -> `@Nullable NodeContext` (GAP-018);
// `block_layout`/`flexbox`/`grid` cfgs always on (GAP-007); the `&mut Layout`
// returned by `compute_oof_layout` becomes the updated return value; assoc
// style types -> `Style`/`DetailedGridInfo` (GAP-017).
package net.morphismmc.jaffy.tree;

import net.morphismmc.jaffy.Features;
import net.morphismmc.jaffy.compute.Compute;
import net.morphismmc.jaffy.compute.Oof;
import net.morphismmc.jaffy.compute.block.Block;
import net.morphismmc.jaffy.compute.block.BlockContext;
import net.morphismmc.jaffy.compute.flexbox.Flexbox;
import net.morphismmc.jaffy.compute.grid.DetailedGridInfo;
import net.morphismmc.jaffy.compute.grid.Grid;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.tree.layout.DetailedLayoutInfo;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.traits.CacheTree;
import net.morphismmc.jaffy.tree.traits.LayoutBlockContainer;
import net.morphismmc.jaffy.tree.traits.LayoutContainingBlock;
import net.morphismmc.jaffy.tree.traits.LayoutFlexboxContainer;
import net.morphismmc.jaffy.tree.traits.LayoutGridContainer;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;
import net.morphismmc.jaffy.tree.traits.RoundTree;
import net.morphismmc.jaffy.util.DebugLogger;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongIterator;

/// View over the Taffy tree that holds the tree itself along with a
/// reference to the context and implements LayoutTree. This allows the
/// context to be stored outside of the TaffyTree struct which makes the
/// lifetimes of the context much more flexible.
final class TaffyView<NodeContext>
        implements LayoutPartialTree,
                LayoutContainingBlock,
                LayoutBlockContainer,
                LayoutFlexboxContainer,
                LayoutGridContainer,
                CacheTree,
                RoundTree {

    /// A reference to the TaffyTree
    final TaffyTree<NodeContext> taffy;

    /// The context provided for passing to measure functions if layout is
    /// run over this struct
    final TaffyTree.MeasureFn<NodeContext> measureFunction;

    TaffyView(TaffyTree<NodeContext> taffy, TaffyTree.MeasureFn<NodeContext> measureFunction) {
        this.taffy = taffy;
        this.measureFunction = measureFunction;
    }

    /// Unified implementation that both
    /// `LayoutPartialTree::compute_child_layout` and
    /// `LayoutBlockContainer::compute_block_child_layout` delegate to.
    private LayoutOutput computeChildLayout(long nodeId, LayoutInput inputs, @Nullable BlockContext blockCtx) {
        // If RunMode is PerformHiddenLayout then this indicates that an
        // ancestor node is `Display::None` and thus that we should lay out
        // this node using hidden layout regardless of it's own display style.
        if (inputs.runMode() == RunMode.PERFORM_HIDDEN_LAYOUT) {
            if (Features.DEBUG) {
                DebugLogger.NODE_LOGGER.debugLog("HIDDEN");
            }
            // `round_layout` reaches out-of-flow boxes only through their
            // containing block's hoisted child list, and a box inside a
            // hidden subtree is in no such list. Write the final layout here
            // so that it does not keep the layout from an earlier visible
            // pass.
            taffy.nodes.indexMut(nodeId).finalLayout = Layout.withOrder(0);
            return Compute.computeHiddenLayout(this, nodeId);
        }

        // We run the following wrapped in "compute_cached_layout", which will
        // check the cache for an entry matching the node and inputs and:
        //   - Return that entry if exists
        //   - Else call the passed closure (below) to compute the result
        //
        // If there was no cache match and a new result needs to be computed
        // then that result will be added to the cache
        return Compute.computeCachedLayout(this, nodeId, inputs, (tree, node, in) -> {
            Display displayMode = tree.taffy.nodes.index(node).style.display();
            boolean hasChildren = tree.childCount(node) > 0;

            if (Features.DEBUG) {
                DebugLogger.NODE_LOGGER.debugLog(debugDisplay(displayMode));
                Compute.debugLogNode(in);
            }

            // Dispatch to a layout algorithm based on the node's display
            // style and whether the node has children or not.
            LayoutOutput output;
            if (displayMode == Display.NONE) {
                output = Compute.computeHiddenLayout(tree, node);
            } else if (!hasChildren) {
                NodeData data = tree.taffy.nodes.index(node);
                NodeContext nodeContext = data.hasContext ? tree.taffy.nodeContextData.getMut(node) : null;
                output = tree.measureFunction.measure(in, node, nodeContext, data.style);
            } else {
                output = switch (displayMode) {
                    case BLOCK -> Block.computeBlockLayout(tree, node, in, blockCtx);
                    case FLOW_ROOT -> Block.computeBlockLayout(tree, node, in, null);
                    case FLEX -> Flexbox.computeFlexboxLayout(tree, node, in);
                    case GRID -> Grid.computeGridLayout(tree, node, in);
                    case NONE -> throw new IllegalStateException(); // JAFFY(GAP-010) unreachable
                };
            }

            // Lay out any out-of-flow candidates for which this node is the
            // containing block. The rest bubble up via `output.oofCandidates`.
            // This runs inside the cache-miss closure so that cached outputs
            // already contain the processed candidate list. Only full layout
            // passes run it: measure passes must not write hoisted box
            // layouts (which would not be rewritten if the final layout pass
            // is a cache hit).
            if (in.runMode() == RunMode.PERFORM_LAYOUT) {
                output = Oof.computeOofLayout(tree, node, output);
            }

            return output;
        });
    }

    // ---- TraversePartialTree ----

    @Override
    public LongIterator childIds(long parentNodeId) {
        return taffy.childIds(parentNodeId);
    }

    @Override
    public int childCount(long parentNodeId) {
        return taffy.childCount(parentNodeId);
    }

    @Override
    public long getChildId(long parentNodeId, int childIndex) {
        return taffy.getChildId(parentNodeId, childIndex);
    }

    // ---- LayoutPartialTree ----

    @Override
    public Style getCoreContainerStyle(long nodeId) {
        return taffy.nodes.index(nodeId).style;
    }

    @Override
    public void setUnroundedLayout(long nodeId, Layout layout) {
        taffy.nodes.indexMut(nodeId).unroundedLayout = layout;
    }

    @Override
    public float resolveCalcValue(@Nullable Object val, float basis) {
        return 0.0f;
    }

    @Override
    public LayoutOutput computeChildLayout(long nodeId, LayoutInput inputs) {
        return computeChildLayout(nodeId, inputs, null);
    }

    // ---- LayoutContainingBlock ----

    @Override
    public Style getOofItemStyle(long nodeId) {
        return taffy.nodes.index(nodeId).style;
    }

    @Override
    public void clearHoistedChildren(long nodeId) {
        taffy.nodes.indexMut(nodeId).hoistedChildren.clear();
    }

    @Override
    public void addHoistedChildren(long nodeId, long[] hoisted) {
        // `extend_from_slice`
        var list = taffy.nodes.indexMut(nodeId).hoistedChildren;
        list.addElements(list.size(), hoisted);
    }

    @Override
    public DetailedLayoutInfo getDetailedLayoutInfo(long nodeId) {
        return taffy.nodes.index(nodeId).detailedLayoutInfo;
    }

    // ---- CacheTree ----

    @Override
    public @Nullable LayoutOutput cacheGet(long nodeId, LayoutInput inputs) {
        return taffy.nodes.index(nodeId).cache.get(inputs);
    }

    @Override
    public void cacheStore(long nodeId, LayoutInput inputs, LayoutOutput layoutOutput) {
        taffy.nodes.indexMut(nodeId).cache.store(inputs, layoutOutput);
    }

    @Override
    public void cacheClear(long nodeId) {
        taffy.nodes.indexMut(nodeId).cache.clear();
    }

    // ---- LayoutBlockContainer ----

    @Override
    public Style getBlockContainerStyle(long nodeId) {
        return getCoreContainerStyle(nodeId);
    }

    @Override
    public Style getBlockChildStyle(long childNodeId) {
        return getCoreContainerStyle(childNodeId);
    }

    @Override
    public LayoutOutput computeBlockChildLayout(long nodeId, LayoutInput inputs, @Nullable BlockContext blockCtx) {
        return computeChildLayout(nodeId, inputs, blockCtx);
    }

    // ---- LayoutFlexboxContainer ----

    @Override
    public Style getFlexboxContainerStyle(long nodeId) {
        return taffy.nodes.index(nodeId).style;
    }

    @Override
    public Style getFlexboxChildStyle(long childNodeId) {
        return taffy.nodes.index(childNodeId).style;
    }

    // ---- LayoutGridContainer ----

    @Override
    public Style getGridContainerStyle(long nodeId) {
        return taffy.nodes.index(nodeId).style;
    }

    @Override
    public Style getGridChildStyle(long childNodeId) {
        return taffy.nodes.index(childNodeId).style;
    }

    @Override
    public void setDetailedGridInfo(long nodeId, DetailedGridInfo detailedGridInfo) {
        taffy.nodes.indexMut(nodeId).detailedLayoutInfo = new DetailedLayoutInfo.Grid(detailedGridInfo);
    }

    // ---- RoundTree ----

    @Override
    public Layout getUnroundedLayout(long node) {
        return taffy.nodes.index(node).unroundedLayout;
    }

    @Override
    public void setFinalLayout(long nodeId, Layout layout) {
        taffy.nodes.indexMut(nodeId).finalLayout = layout;
    }

    @Override
    public boolean isOutOfFlow(long nodeId) {
        NodeData node = taffy.nodes.index(nodeId);
        return node.style.position().isOutOfFlow() && node.style.display() != Display.NONE;
    }

    @Override
    public int hoistedChildCount(long nodeId) {
        return taffy.nodes.index(nodeId).hoistedChildren.size();
    }

    @Override
    public long getHoistedChildId(long nodeId, int index) {
        return taffy.nodes.index(nodeId).hoistedChildren.getLong(index);
    }

    /// Rust `{:?}` for `Display` — `debug_log!(display_mode)` in
    /// `compute_child_layout`
    private static String debugDisplay(Display display) {
        return switch (display) {
            case BLOCK -> "Block";
            case FLOW_ROOT -> "FlowRoot";
            case FLEX -> "Flex";
            case GRID -> "Grid";
            case NONE -> "None";
        };
    }
}
