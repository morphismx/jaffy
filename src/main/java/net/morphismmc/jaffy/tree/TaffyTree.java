// JAFFY: port of upstream src/tree/taffy_tree.rs — `TaffyTree<NodeContext>`
// and its TraversePartialTree/TraverseTree/CacheTree/PrintTree impls.
//
// `NodeId`/`DefaultKey` -> `long` (GAP-015); `SlotMap<DefaultKey, _>` ->
// {@link SlotMap}; `SecondaryMap` -> {@link SparseSecondaryMap} (FxHash not
// observable, GAP-015); `SlotMap<Option<NodeId>>` -> {@link LongSlotMap}
// (primitive `long` values, `0L` = `None` — OptI64 sentinel, GAP-001);
// `ChildrenVec<NodeId>` ->
// fastutil `LongList`/`LongArrayList` (primitive longs, no boxing);
// `NodeContext = ()` -> any reference type (`Void` for the default);
// `std`/`block_layout`/`flexbox`/`grid`/`content_size` cfgs always on.
//
// `TaffyResult<T>`/`TaffyError` -> `T` + throws {@link TaffyException}
// on `Err` (upstream's error enum variants are the nested subclasses;
// user-sanctioned Java adaptation — docs/porting/JAVA_API.md).
//
// `Index` on the parents map (`self.parents[key]` returning
// `Option<NodeId>`) -> {@link #parentsIndex} (invalid key throws, valid
// `None` slot returns null). `clone()`/derive(Clone) intentionally absent.
package net.morphismmc.jaffy.tree;

import net.morphismmc.jaffy.compute.Compute;
import net.morphismmc.jaffy.compute.Leaf;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.infra.slotmap.LongSlotMap;
import net.morphismmc.jaffy.infra.slotmap.SlotMap;
import net.morphismmc.jaffy.infra.slotmap.SparseSecondaryMap;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.cache.ClearState;
import net.morphismmc.jaffy.tree.layout.DetailedLayoutInfo;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.traits.CacheTree;
import net.morphismmc.jaffy.tree.traits.PrintTree;
import net.morphismmc.jaffy.tree.traits.TraverseTree;
import net.morphismmc.just.math.Opt;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;

/// An entire tree of UI nodes. The entry point to Taffy's high-level API.
///
/// Allows you to build a tree of UI nodes, run Taffy's layout algorithms
/// over that tree, and then access the resultant layout.
public final class TaffyTree<NodeContext> implements TraverseTree, CacheTree, PrintTree {

    /// `MeasureFunction: FnMut(LayoutInput, NodeId, Option<&mut NodeContext>,
    /// &Style) -> LayoutOutput` (GAP-019). Captured state is the caller's own
    /// mutable container; `Option<&mut NodeContext>` -> `@Nullable
    /// NodeContext` (shared reference, GAP-018).
    @FunctionalInterface
    public interface MeasureFn<NodeContext> {
        LayoutOutput measure(LayoutInput inputs, long nodeId, @Nullable NodeContext nodeContext, Style style);
    }

    /// The {@link NodeData} for each node stored in this tree
    final SlotMap<NodeData> nodes;

    /// Functions/closures that compute the intrinsic size of leaf nodes
    final SparseSecondaryMap<NodeContext> nodeContextData;

    /// The children of each node
    ///
    /// The indexes in the outer vector correspond to the position of the
    /// parent {@link NodeData}
    final SlotMap<LongArrayList> children;

    /// The parents of each node
    ///
    /// The indexes in the outer vector correspond to the position of the
    /// child {@link NodeData}
    final LongSlotMap parents;

    /// Layout mode configuration
    final TaffyConfig config;

    /// Creates a new {@link TaffyTree}
    ///
    /// The default capacity of a {@link TaffyTree} is 16 nodes.
    public TaffyTree() {
        this(16);
    }

    /// Creates a new {@link TaffyTree} that can store `capacity` nodes before
    /// reallocation
    public TaffyTree(int capacity) {
        this.nodes = SlotMap.withCapacity(capacity);
        this.children = SlotMap.withCapacity(capacity);
        this.parents = LongSlotMap.withCapacity(capacity);
        this.nodeContextData = SparseSecondaryMap.withCapacity(capacity);
        this.config = new TaffyConfig();
    }

    /// `Default::default()` — `TaffyTree<()>`
    public static TaffyTree<Void> create() {
        return new TaffyTree<>();
    }

    /// Enable rounding of layout values. Rounding is enabled by default.
    public void enableRounding() {
        this.config.useRounding = true;
    }

    /// Disable rounding of layout values. Rounding is enabled by default.
    public void disableRounding() {
        this.config.useRounding = false;
    }

    /// Creates and adds a new unattached leaf node to the tree, and returns
    /// the node of the new node
    public long newLeaf(Style layout) {
        long id = this.nodes.insert(new NodeData(layout));
        this.children.insert(new LongArrayList(0));
        this.parents.insert(0L);

        return id;
    }

    /// Creates and adds a new unattached leaf node to the tree, and returns
    /// the `NodeId` of the new node
    ///
    /// Creates and adds a new leaf node with a supplied context
    public long newLeafWithContext(Style layout, NodeContext context) {
        NodeData data = new NodeData(layout);
        data.hasContext = true;

        long id = this.nodes.insert(data);
        this.nodeContextData.insert(id, context);

        this.children.insert(new LongArrayList(0));
        this.parents.insert(0L);

        return id;
    }

    /// Creates and adds a new node, which may have any number of `children`
    public long newWithChildren(Style layout, long[] children) {
        long id = this.nodes.insert(new NodeData(layout));

        for (long child : children) {
            this.parents.set(child, id);
        }

        this.children.insert(toLongList(children));
        this.parents.insert(0L);

        return id;
    }

    /// Drops all nodes in the tree
    public void clear() {
        this.nodes.clear();
        this.children.clear();
        this.parents.clear();
        // A context belongs to the node that owns it. Leaving it behind keeps
        // arbitrary user data -- for a measure function, a boxed closure and
        // everything it captures -- alive after the node it belonged to is
        // gone.
        this.nodeContextData.clear();
    }

    /// Remove a specific node from the tree and drop it
    ///
    /// Returns the id of the node removed.
    public long remove(long node) {
        long parent = parentsIndex(node);
        if (parent != 0L) {
            LongArrayList siblings = this.children.getMut(parent);
            if (siblings != null) {
                // `retain(|f| *f != node)`
                for (int i = siblings.size() - 1; i >= 0; i--) {
                    if (siblings.getLong(i) == node) {
                        siblings.removeElements(i, i + 1);
                    }
                }
            }
            markDirty(parent);
        }

        // Remove "parent" references to a node when removing that node
        LongArrayList children = this.children.get(node);
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                this.parents.set(children.getLong(i), 0L);
            }
        }

        this.children.remove(node);
        this.parents.remove(node);
        this.nodes.remove(node);
        this.nodeContextData.remove(node);

        return node;
    }

    /// Sets the context data associated with the node
    public void setNodeContext(long node, @Nullable NodeContext measure) {
        if (measure != null) {
            this.nodes.indexMut(node).hasContext = true;
            this.nodeContextData.insert(node, measure);
        } else {
            this.nodes.indexMut(node).hasContext = false;
            this.nodeContextData.remove(node);
        }

        markDirty(node);
    }

    /// Gets a reference to the the context data associated with the node
    public @Nullable NodeContext getNodeContext(long node) {
        return this.nodeContextData.get(node);
    }

    /// Gets a mutable reference to the the context data associated with the
    /// node (shared reference — GAP-018)
    public @Nullable NodeContext getNodeContextMut(long node) {
        return this.nodeContextData.getMut(node);
    }

    /// Gets mutable references to the the context data associated with the
    /// nodes. All keys must be valid and disjoint, otherwise `null` is
    /// returned.
    public @Nullable NodeContext[] getDisjointNodeContextMut(
            java.util.function.IntFunction<NodeContext[]> generator, long... keys) {
        return this.nodeContextData.getDisjointMut(generator, keys);
    }

    /// Adds a `child` node under the supplied `parent`
    public void addChild(long parent, long child) {
        this.parents.set(child, parent);
        this.children.indexMut(parent).add(child);
        markDirty(parent);
    }

    /// Inserts a `child` node at the given `childIndex` under the supplied
    /// `parent`, shifting all children after it to the right.
    public void insertChildAtIndex(long parent, int childIndex, long child) {
        int childCount = this.children.index(parent).size();
        if (childIndex > childCount) {
            throw new TaffyException.ChildIndexOutOfBounds(parent, childIndex, childCount);
        }

        this.parents.set(child, parent);
        this.children.indexMut(parent).add(childIndex, child);
        markDirty(parent);
    }

    /// Directly sets the `children` of the supplied `parent`
    public void setChildren(long parent, long[] children) {
        // Remove node as parent from all its current children.
        LongArrayList current = this.children.index(parent);
        for (int i = 0; i < current.size(); i++) {
            this.parents.set(current.getLong(i), 0L);
        }

        // Build up relation node <-> child
        for (long child : children) {
            // Remove child from previous parent
            long previousParent = parentsIndex(child);
            if (previousParent != 0L) {
                removeChild(previousParent, child); // `.unwrap()` upstream — unreachable GAP-010
            }
            this.parents.set(child, parent);
        }

        LongArrayList parentChildren = this.children.indexMut(parent);
        parentChildren.clear();
        for (long child : children) {
            parentChildren.add(child);
        }

        markDirty(parent);
    }

    /// Removes the `child` of the parent `node`
    ///
    /// The child is not removed from the tree entirely, it is simply no
    /// longer attached to its previous parent.
    public long removeChild(long parent, long child) {
        LongArrayList children = this.children.index(parent);
        int index = children.indexOf(child);
        if (index < 0) {
            // `position().unwrap()` on a missing child
            throw new IllegalStateException(); // JAFFY(GAP-010)
        }
        return removeChildAtIndex(parent, index);
    }

    /// Removes the child at the given `index` from the `parent`
    ///
    /// The child is not removed from the tree entirely, it is simply no
    /// longer attached to its previous parent.
    public long removeChildAtIndex(long parent, int childIndex) {
        int childCount = this.children.index(parent).size();
        if (childIndex >= childCount) {
            throw new TaffyException.ChildIndexOutOfBounds(parent, childIndex, childCount);
        }

        long child = this.children.indexMut(parent).getLong(childIndex);
        this.children.indexMut(parent).removeElements(childIndex, childIndex + 1);
        this.parents.set(child, 0L);

        markDirty(parent);

        return child;
    }

    /// Removes children at the given range (`from` inclusive, `to`
    /// exclusive) from the `parent`
    ///
    /// Children are not removed from the tree entirely, they are simply no
    /// longer attached to their previous parent.
    ///
    /// Will throw if given range is invalid (see `core::slice::range`).
    public void removeChildrenRange(long parent, int from, int to) {
        LongArrayList children = this.children.index(parent);
        // `drain(range)` — collect first, then clear (RangeBounds panic on
        // invalid range is `subList`'s IndexOutOfBoundsException, GAP-010)
        LongList sub = children.subList(from, to);
        long[] drained = sub.toLongArray();
        sub.clear();
        for (long child : drained) {
            this.parents.set(child, 0L);
        }

        markDirty(parent);
    }

    /// Replaces the child at the given `childIndex` from the `parent` node
    /// with the new `child` node
    ///
    /// The child is not removed from the tree entirely, it is simply no
    /// longer attached to its previous parent.
    public long replaceChildAtIndex(long parent, int childIndex, long newChild) {
        int childCount = this.children.index(parent).size();
        if (childIndex >= childCount) {
            throw new TaffyException.ChildIndexOutOfBounds(parent, childIndex, childCount);
        }

        this.parents.set(newChild, parent);
        long oldChild = this.children.indexMut(parent).set(childIndex, newChild);
        this.parents.set(oldChild, 0L);

        markDirty(parent);

        return oldChild;
    }

    /// Returns the child node of the parent `node` at the provided
    /// `childIndex`
    public long childAtIndex(long parent, int childIndex) {
        int childCount = this.children.index(parent).size();
        if (childIndex >= childCount) {
            throw new TaffyException.ChildIndexOutOfBounds(parent, childIndex, childCount);
        }

        return this.children.index(parent).getLong(childIndex);
    }

    /// Returns the total number of nodes in the tree
    public int totalNodeCount() {
        return this.nodes.len();
    }

    /// Returns the `NodeId` of the parent node of the specified node (if it
    /// exists) — `Option<NodeId>` -> `@Opt long`
    ///
    /// - Return `0L` (`OptI64.NONE`) if the specified node has no parent
    /// - Throws if the specified node does not exist
    public @Opt long parent(long childId) {
        return parentsIndex(childId);
    }

    /// Returns a list of children that belong to the parent node
    public long[] children(long parent) {
        return this.children.index(parent).toLongArray();
    }

    /// Returns the out-of-flow (absolute/fixed) boxes whose containing block
    /// is `node`, as recorded by the last layout. These boxes are laid out by
    /// `node` rather than by their parent, and their `Layout.location` is
    /// relative to `node`.
    public long[] hoistedChildren(long node) {
        return this.nodes.index(node).hoistedChildren.toLongArray();
    }

    /// Sets the `Style` of the provided `node`
    public void setStyle(long node, Style style) {
        this.nodes.indexMut(node).style = style;
        markDirty(node);
    }

    /// Gets the `Style` of the provided `node`
    public Style style(long node) {
        return this.nodes.index(node).style;
    }

    /// Return this node layout relative to its parent
    public Layout layout(long node) {
        if (this.config.useRounding) {
            return this.nodes.index(node).finalLayout;
        } else {
            return this.nodes.index(node).unroundedLayout;
        }
    }

    /// Returns this node layout with unrounded values relative to its
    /// parent.
    public Layout unroundedLayout(long node) {
        return this.nodes.index(node).unroundedLayout;
    }

    /// Get the "detailed layout info" for a node.
    ///
    /// Currently this is only implemented for CSS Grid containers where it
    /// contains the computed size of each grid track and the computed
    /// placement of each grid item
    public DetailedLayoutInfo detailedLayoutInfo(long nodeId) {
        return this.nodes.index(nodeId).detailedLayoutInfo;
    }

    /// Marks the layout of this node and its ancestors as outdated
    public void markDirty(long node) {
        markDirtyRecursive(node);
    }

    /// The `mark_dirty_recursive` free function of upstream — operates on
    /// this tree's `nodes`/`parents` maps.
    private void markDirtyRecursive(long nodeKey) {
        ClearState clearState = this.nodes.indexMut(nodeKey).markDirty();
        if (clearState == ClearState.CLEARED && this.parents.containsKey(nodeKey)) {
            long parent = this.parents.get(nodeKey);
            if (parent != 0L) {
                markDirtyRecursive(parent);
            }
        }
    }

    /// Indicates whether the layout of this node needs to be recomputed
    public boolean dirty(long node) {
        return this.nodes.index(node).cache.isEmpty();
    }

    /// Updates the stored layout of the provided `node` and its children
    public void computeLayoutWithMeasure(
            long nodeId, Size<AvailableSpace> availableSpace, MeasureFn<NodeContext> measureFunction) {
        boolean useRounding = this.config.useRounding;
        TaffyView<NodeContext> taffyView = new TaffyView<>(this, measureFunction);
        Compute.computeRootLayout(taffyView, nodeId, availableSpace);
        if (useRounding) {
            Compute.roundLayout(taffyView, nodeId);
        }
    }

    /// Updates the stored layout of the provided `node` and its children
    public void computeLayout(long node, Size<AvailableSpace> availableSpace) {
        computeLayoutWithMeasure(
                node,
                availableSpace,
                (inputs, nodeId, context, style) -> Leaf.computeLeafLayout(
                        inputs, style, (c, b) -> 0.0f, (knownDimensions, space) -> SizeF32.ZERO));
    }

    /// Prints a debug representation of the tree's layout
    public void printTree(long root) {
        net.morphismmc.jaffy.util.PrintTree.printTree(this, root);
    }

    /// Returns an instance of LayoutTree representing the TaffyTree
    /// (`as_layout_tree` — `#[cfg(test)]` upstream; package-private here).
    TaffyView<NodeContext> asLayoutTree() {
        return new TaffyView<>(
                this,
                (inputs, nodeId, context, style) -> Leaf.computeLeafLayout(
                        inputs, style, (c, b) -> 0.0f, (knownDimensions, space) -> SizeF32.ZERO));
    }

    // ---- TraversePartialTree ----

    @Override
    public LongIterator childIds(long parentNodeId) {
        return new TaffyTreeChildIter(this.children.index(parentNodeId).listIterator());
    }

    @Override
    public int childCount(long parentNodeId) {
        return this.children.index(parentNodeId).size();
    }

    @Override
    public long getChildId(long parentNodeId, int id) {
        return this.children.index(parentNodeId).getLong(id);
    }

    // ---- CacheTree ----

    @Override
    public @Nullable LayoutOutput cacheGet(long nodeId, LayoutInput inputs) {
        return this.nodes.index(nodeId).cache.get(inputs);
    }

    @Override
    public void cacheStore(long nodeId, LayoutInput inputs, LayoutOutput layoutOutput) {
        this.nodes.indexMut(nodeId).cache.store(inputs, layoutOutput);
    }

    @Override
    public void cacheClear(long nodeId) {
        this.nodes.indexMut(nodeId).cache.clear();
    }

    // ---- PrintTree ----

    @Override
    public String getDebugLabel(long nodeId) {
        NodeData node = this.nodes.index(nodeId);
        Display display = node.style.display();
        int numChildren = childCount(nodeId);

        if (display == Display.NONE) {
            return "NONE";
        }
        if (numChildren == 0) {
            return "LEAF";
        }
        return switch (display) {
            case BLOCK -> "BLOCK";
            case FLOW_ROOT -> "FLOW-ROOT";
            case FLEX ->
                switch (node.style.flexDirection()) {
                    case ROW, ROW_REVERSE -> "FLEX ROW";
                    case COLUMN, COLUMN_REVERSE -> "FLEX COL";
                };
            case GRID -> "GRID";
            case NONE -> throw new IllegalStateException(); // JAFFY(GAP-010) unreachable
        };
    }

    @Override
    public Layout getFinalLayout(long nodeId) {
        if (this.config.useRounding) {
            return this.nodes.index(nodeId).finalLayout;
        } else {
            return this.nodes.index(nodeId).unroundedLayout;
        }
    }

    /// `self.parents[key]` — the `Index` on the `SlotMap<_, Option<NodeId>>`
    /// parents map. Invalid key throws (upstream panic); a valid `None` slot
    /// returns `0L` (`OptI64.NONE`).
    private @Opt long parentsIndex(long key) {
        if (!this.parents.containsKey(key)) {
            throw new IllegalStateException("invalid SlotMap key used"); // JAFFY(GAP-010)
        }
        return this.parents.get(key);
    }

    private static LongArrayList toLongList(long[] children) {
        return new LongArrayList(children);
    }
}
