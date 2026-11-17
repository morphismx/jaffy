// JAFFY: upstream=benches/src/lib.rs

package net.morphismmc.jaffy.bench;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.TaffyTree;

/// Port of upstream `benches/src/lib.rs` — `TaffyLayoutTree`: a `TaffyTree`
/// plus the node list needed to lay it out from scratch repeatedly
/// (`mark_all_dirty` walks every node).
public final class JaffyLayoutTree {
    public final TaffyTree<Void> tree;
    public final long root;
    private final long[] nodes;

    /// `TaffyLayoutTree::new` — BFS from `root` collecting every node.
    public JaffyLayoutTree(TaffyTree<Void> tree, long root) {
        this.tree = tree;
        this.root = root;
        List<Long> nodes = new ArrayList<>();
        nodes.add(root);
        for (int next = 0; next < nodes.size(); next++) {
            long node = nodes.get(next);
            int count = tree.childCount(node);
            for (int i = 0; i < count; i++) {
                nodes.add(tree.getChildId(node, i));
            }
        }
        long[] arr = new long[nodes.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = nodes.get(i);
        }
        this.nodes = arr;
    }

    /// `mark_all_dirty` — clear the cached layout of every node in the tree
    public void markAllDirty() {
        for (long node : nodes) {
            tree.markDirty(node);
        }
    }

    /// `compute_layout(root, space)` convenience
    public void computeLayout(Size<AvailableSpace> space) {
        tree.computeLayout(root, space);
    }
}
