package net.morphismmc.jaffy.bench;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.TaffyTree;

import org.jspecify.annotations.Nullable;

/// `BuildTree` adapter for jaffy — counterpart of upstream
/// `benches/src/taffy_helpers.rs` `TaffyTreeBuilder`.
public final class JaffyTreeBuilder implements BenchTree<Style, Long> {

    private final Rng rng;
    private final GenStyle<Style> styleGenerator;
    private final TaffyTree<Void> tree = new TaffyTree<>();
    private final Long root;
    private final List<Long> nodes;

    /// `BuildTree::with_rng` — `new_leaf(root_style)`, `nodes = [root]`
    public JaffyTreeBuilder(Rng rng, GenStyle<Style> styleGenerator) {
        this.rng = rng;
        this.styleGenerator = styleGenerator;
        Style rootStyle = styleGenerator.rootStyle(rng);
        this.root = tree.newLeaf(rootStyle == null ? Style.DEFAULT : rootStyle);
        this.nodes = new ArrayList<>();
        this.nodes.add(root);
    }

    /// `BuildTreeExt::with_seed(12345)` equivalent
    public static JaffyTreeBuilder withSeed(long seed, GenStyle<Style> styleGenerator) {
        return new JaffyTreeBuilder(new Rng(seed), styleGenerator);
    }

    private static long[] toArray(List<Long> children) {
        long[] out = new long[children.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = children.get(i);
        }
        return out;
    }

    @Override
    public void computeLayout(@Nullable Float width, @Nullable Float height) {
        AvailableSpace w = width == null ? AvailableSpace.MAX_CONTENT : new AvailableSpace.Definite(width);
        AvailableSpace h = height == null ? AvailableSpace.MAX_CONTENT : new AvailableSpace.Definite(height);
        tree.computeLayout(root, new Size<>(w, h));
    }

    @Override
    public void markAllDirty() {
        for (long node : nodes) {
            tree.markDirty(node);
        }
    }

    @Override
    public Long createLeafNode() {
        Style style = styleGenerator.leafStyle(rng);
        long node = tree.newLeaf(style);
        nodes.add(node);
        return node;
    }

    @Override
    public Long createContainerNode(List<Long> children) {
        Style style = styleGenerator.containerStyle(rng);
        long node = tree.newWithChildren(style, toArray(children));
        nodes.add(node);
        return node;
    }

    @Override
    public Long createNodeWithStyle(Style style, List<Long> children) {
        long node = tree.newWithChildren(style, toArray(children));
        nodes.add(node);
        return node;
    }

    @Override
    public void setRootChildren(List<Long> children) {
        tree.setChildren(root, toArray(children));
    }

    @Override
    public int totalNodeCount() {
        return tree.totalNodeCount();
    }

    @Override
    public Long root() {
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

    @Override
    public void enableRounding() {
        tree.enableRounding();
    }

    public TaffyTree<Void> tree() {
        return tree;
    }
}
