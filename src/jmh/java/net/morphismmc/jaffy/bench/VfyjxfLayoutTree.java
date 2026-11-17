package net.morphismmc.jaffy.bench;

import java.util.ArrayList;
import java.util.List;

/// dev.vfyjxf:taffy counterpart of {@link JaffyLayoutTree} —
/// `TaffyLayoutTree` for the independent port.
public final class VfyjxfLayoutTree {
    public final dev.vfyjxf.taffy.tree.TaffyTree tree;
    public final dev.vfyjxf.taffy.tree.NodeId root;
    private final List<dev.vfyjxf.taffy.tree.NodeId> nodes;

    /// `TaffyLayoutTree::new` — BFS from `root` collecting every node.
    public VfyjxfLayoutTree(dev.vfyjxf.taffy.tree.TaffyTree tree, dev.vfyjxf.taffy.tree.NodeId root) {
        this.tree = tree;
        this.root = root;
        List<dev.vfyjxf.taffy.tree.NodeId> nodes = new ArrayList<>();
        nodes.add(root);
        for (int next = 0; next < nodes.size(); next++) {
            nodes.addAll(tree.getChildren(nodes.get(next)));
        }
        this.nodes = nodes;
    }

    /// `mark_all_dirty`
    public void markAllDirty() {
        for (dev.vfyjxf.taffy.tree.NodeId node : nodes) {
            tree.markDirty(node);
        }
    }

    /// `compute_layout(root, space)` convenience
    public void computeLayout(dev.vfyjxf.taffy.geometry.TaffySize<dev.vfyjxf.taffy.style.AvailableSpace> space) {
        tree.computeLayout(root, space);
    }
}
