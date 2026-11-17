// JAFFY: upstream=benches/src/lib.rs

package net.morphismmc.jaffy.bench;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// Port of upstream `benches/src/lib.rs` — `trait BuildTree` +
/// `trait BuildTreeExt` (default methods merged into this interface).
/// Implementations adapt a concrete library (`JaffyTreeBuilder`,
/// `VfyjxfTreeBuilder`); `S` is the library style type, `N` its node id.
public interface BenchTree<S, N> {

    /// `compute_layout_inner` — `Option<f32>` per axis: `null` maps to
    /// `AvailableSpace::MaxContent`, a value maps to `Definite` (Rust `From`).
    void computeLayout(@Nullable Float width, @Nullable Float height);

    /// `mark_all_dirty`
    void markAllDirty();

    /// `random_usize`
    default int randomUsize(int lo, int hi) {
        return rng().nextUsize(lo, hi);
    }

    /// `create_leaf_node` — style comes from the builder's style generator
    N createLeafNode();

    /// `create_container_node` — style comes from the builder's style generator
    N createContainerNode(List<N> children);

    /// `create_node_with_style`
    N createNodeWithStyle(S style, List<N> children);

    /// `set_root_children`
    void setRootChildren(List<N> children);

    /// `total_node_count`
    int totalNodeCount();

    N root();

    Rng rng();

    /// `enable_rounding` / `disable_rounding` (relayout.rs disables rounding)
    default void disableRounding() {}

    default void enableRounding() {}

    /// `build_n_leaf_nodes`
    default List<N> buildNLeafNodes(int n) {
        List<N> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(createLeafNode());
        }
        return out;
    }

    /// `build_deep_tree` — helper to recursively construct a deep tree
    default List<N> buildDeepTree(int maxNodes, int branchingFactor) {
        if (maxNodes <= branchingFactor) {
            return buildNLeafNodes(maxNodes);
        }
        List<N> out = new ArrayList<>(branchingFactor);
        for (int i = 0; i < branchingFactor; i++) {
            int share = (maxNodes - branchingFactor) / branchingFactor;
            List<N> children = buildDeepTree(share, branchingFactor);
            out.add(createContainerNode(children));
        }
        return out;
    }

    /// `build_super_deep_hierarchy` — a tree with a higher depth
    default void buildSuperDeepHierarchy(int depth, int nodesPerLevel) {
        List<N> children = new ArrayList<>(nodesPerLevel);
        for (int i = 0; i < depth; i++) {
            N nodeWithChildren = createContainerNode(children);
            children.clear();
            children.add(nodeWithChildren);
            for (int j = 0; j < nodesPerLevel - 1; j++) {
                children.add(createLeafNode());
            }
        }
        setRootChildren(children);
    }

    /// `build_deep_hierarchy`
    default void buildDeepHierarchy(int nodeCount, int branchingFactor) {
        List<N> children = buildDeepTree(nodeCount, branchingFactor);
        setRootChildren(children);
    }

    /// `build_flat_hierarchy` — a tree with many children that have shallow depth
    default void buildFlatHierarchy(int targetNodeCount) {
        List<N> children = new ArrayList<>();
        while (totalNodeCount() < targetNodeCount) {
            int count = randomUsize(1, 4);
            List<N> subChildren = buildNLeafNodes(count);
            N node = createContainerNode(subChildren);
            children.add(node);
        }
        setRootChildren(children);
    }
}
