package net.morphismmc.jaffy.bench;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/// `BuildTree` adapter for the independent dev.vfyjxf:taffy port —
/// counterpart of upstream `benches/src/taffy_helpers.rs` `TaffyTreeBuilder`.
public final class VfyjxfTreeBuilder
        implements BenchTree<dev.vfyjxf.taffy.style.TaffyStyle, dev.vfyjxf.taffy.tree.NodeId> {

    private final Rng rng;
    private final GenStyle<dev.vfyjxf.taffy.style.TaffyStyle> styleGenerator;
    private final dev.vfyjxf.taffy.tree.TaffyTree tree = new dev.vfyjxf.taffy.tree.TaffyTree();
    private final dev.vfyjxf.taffy.tree.NodeId root;
    private final List<dev.vfyjxf.taffy.tree.NodeId> nodes;

    /// `BuildTree::with_rng` — `new_leaf(root_style)`, `nodes = [root]`
    public VfyjxfTreeBuilder(Rng rng, GenStyle<dev.vfyjxf.taffy.style.TaffyStyle> styleGenerator) {
        this.rng = rng;
        this.styleGenerator = styleGenerator;
        dev.vfyjxf.taffy.style.TaffyStyle rootStyle = styleGenerator.rootStyle(rng);
        this.root = tree.newLeaf(rootStyle == null ? new dev.vfyjxf.taffy.style.TaffyStyle() : rootStyle);
        this.nodes = new ArrayList<>();
        this.nodes.add(root);
    }

    public static VfyjxfTreeBuilder withSeed(long seed, GenStyle<dev.vfyjxf.taffy.style.TaffyStyle> styleGenerator) {
        return new VfyjxfTreeBuilder(new Rng(seed), styleGenerator);
    }

    @Override
    public void computeLayout(@Nullable Float width, @Nullable Float height) {
        dev.vfyjxf.taffy.style.AvailableSpace w = width == null
                ? dev.vfyjxf.taffy.style.AvailableSpace.MAX_CONTENT
                : dev.vfyjxf.taffy.style.AvailableSpace.definite(width);
        dev.vfyjxf.taffy.style.AvailableSpace h = height == null
                ? dev.vfyjxf.taffy.style.AvailableSpace.MAX_CONTENT
                : dev.vfyjxf.taffy.style.AvailableSpace.definite(height);
        tree.computeLayout(root, dev.vfyjxf.taffy.geometry.TaffySize.of(w, h));
    }

    @Override
    public void markAllDirty() {
        for (dev.vfyjxf.taffy.tree.NodeId node : nodes) {
            tree.markDirty(node);
        }
    }

    @Override
    public dev.vfyjxf.taffy.tree.NodeId createLeafNode() {
        dev.vfyjxf.taffy.style.TaffyStyle style = styleGenerator.leafStyle(rng);
        dev.vfyjxf.taffy.tree.NodeId node = tree.newLeaf(style);
        nodes.add(node);
        return node;
    }

    @Override
    public dev.vfyjxf.taffy.tree.NodeId createContainerNode(List<dev.vfyjxf.taffy.tree.NodeId> children) {
        dev.vfyjxf.taffy.style.TaffyStyle style = styleGenerator.containerStyle(rng);
        dev.vfyjxf.taffy.tree.NodeId node = tree.newWithChildren(style, children);
        nodes.add(node);
        return node;
    }

    @Override
    public dev.vfyjxf.taffy.tree.NodeId createNodeWithStyle(
            dev.vfyjxf.taffy.style.TaffyStyle style, List<dev.vfyjxf.taffy.tree.NodeId> children) {
        dev.vfyjxf.taffy.tree.NodeId node = tree.newWithChildren(style, children);
        nodes.add(node);
        return node;
    }

    @Override
    public void setRootChildren(List<dev.vfyjxf.taffy.tree.NodeId> children) {
        tree.setChildren(root, children.toArray(new dev.vfyjxf.taffy.tree.NodeId[0]));
    }

    @Override
    public int totalNodeCount() {
        return tree.totalNodeCount();
    }

    @Override
    public dev.vfyjxf.taffy.tree.NodeId root() {
        return root;
    }

    @Override
    public Rng rng() {
        return rng;
    }

    @Override
    public void disableRounding() {
        tree.disableRounding();
    }

    public dev.vfyjxf.taffy.tree.TaffyTree tree() {
        return tree;
    }
}
