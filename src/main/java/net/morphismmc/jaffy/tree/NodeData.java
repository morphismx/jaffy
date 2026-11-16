// JAFFY: port of upstream src/tree/taffy_tree.rs — `NodeData`.
//
// private struct -> package-private mutable class; `Copy`/`Clone`-style
// field rewrites reassign the immutable records (Layout/DetailedLayoutInfo);
// `ChildrenVec<NodeId>` -> `LongArrayList` (primitive longs, no boxing).
package net.morphismmc.jaffy.tree;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.tree.cache.Cache;
import net.morphismmc.jaffy.tree.cache.ClearState;
import net.morphismmc.jaffy.tree.layout.DetailedLayoutInfo;
import net.morphismmc.jaffy.tree.layout.Layout;


/// Layout information for a given node. Stored in a {@link TaffyTree}.
final class NodeData {

    /// The layout strategy used by this node
    Style style;

    /// The always unrounded results of the layout computation. We must store
    /// this separately from the rounded layout to avoid errors from rounding
    /// already-rounded values. See
    /// <https://github.com/DioxusLabs/taffy/issues/501>.
    Layout unroundedLayout;

    /// The final results of the layout computation.
    /// These may be rounded or unrounded depending on what the `useRounding`
    /// config setting is set to.
    Layout finalLayout;

    /// Whether the node has context data associated with it or not
    boolean hasContext;

    /// Out-of-flow (absolute/fixed) boxes whose containing block is this
    /// node, as recorded by the out-of-flow positioning pass
    final LongArrayList hoistedChildren;

    /// The cached results of the layout computation
    final Cache cache;

    /// The computation result from layout algorithm
    DetailedLayoutInfo detailedLayoutInfo;

    /// Create the data for a new node
    NodeData(Style style) {
        this.style = style;
        this.cache = new Cache();
        this.unroundedLayout = Layout.of();
        this.finalLayout = Layout.of();
        this.hasContext = false;
        this.hoistedChildren = new LongArrayList();
        this.detailedLayoutInfo = DetailedLayoutInfo.NONE;
    }

    /// Marks a node and all of its ancestors as requiring relayout
    ///
    /// This clears any cached data and signals that the data must be
    /// recomputed. If the node was already marked as dirty, returns true
    ClearState markDirty() {
        return cache.clear();
    }
}
