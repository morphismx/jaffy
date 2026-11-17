// JAFFY: port of upstream src/compute/mod.rs — `mod tests`.
package net.morphismmc.jaffy.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.traits.CacheTree;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongList;

class ComputeTest {

    /// A tree stub that mirrors the upstream test's TaffyTree behaviour for
    /// hidden layout: `compute_child_layout(child, LayoutInput::HIDDEN)`
    /// recurses into `computeHiddenLayout` (the real TaffyTree dispatch maps
    /// `PERFORM_HIDDEN_LAYOUT` runs to `compute_hidden_layout`).
    private static final class StubTree implements LayoutPartialTree, CacheTree {
        private final Long2ObjectOpenHashMap<LongList> children = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<Layout> unrounded = new Long2ObjectOpenHashMap<>();
        private int cacheClears = 0;

        long addChild(long parent, long child) {
            children.computeIfAbsent(parent, k -> new LongArrayList()).add(child);
            return child;
        }

        void addRoot(long node) {
            children.computeIfAbsent(node, k -> new LongArrayList());
        }

        @Override
        public LongIterator childIds(long parentNodeId) {
            LongList ids = children.get(parentNodeId);
            return ids == null ? LongIterators.EMPTY_ITERATOR : ids.iterator();
        }

        @Override
        public int childCount(long parentNodeId) {
            LongList ids = children.get(parentNodeId);
            return ids == null ? 0 : ids.size();
        }

        @Override
        public long getChildId(long parentNodeId, int childIndex) {
            LongList ids = children.get(parentNodeId);
            if (ids == null) {
                throw new IndexOutOfBoundsException();
            }
            return ids.getLong(childIndex);
        }

        @Override
        public Style getCoreContainerStyle(long nodeId) {
            return Style.DEFAULT;
        }

        @Override
        public void setUnroundedLayout(long nodeId, Layout layout) {
            unrounded.put(nodeId, layout);
        }

        @Override
        public LayoutOutput computeChildLayout(long nodeId, LayoutInput inputs) {
            if (inputs.runMode() == RunMode.PERFORM_HIDDEN_LAYOUT) {
                return Compute.computeHiddenLayout(this, nodeId);
            }
            return LayoutOutput.hidden();
        }

        @Override
        public @Nullable LayoutOutput cacheGet(long nodeId, LayoutInput inputs) {
            return null;
        }

        @Override
        public void cacheStore(long nodeId, LayoutInput inputs, LayoutOutput layoutOutput) {}

        @Override
        public void cacheClear(long nodeId) {
            cacheClears++;
        }
    }

    /// Upstream `hidden_layout_should_hide_recursively`: whatever size and
    /// display-mode the nodes had previously, all layouts resolve to ZERO due
    /// to the root's Display::None.
    @Test
    void hiddenLayoutShouldHideRecursively() {
        StubTree tree = new StubTree();
        long grandchild00 = 4, grandchild01 = 5, child00 = 1, grandchild02 = 6, child01 = 2, root = 0;
        tree.addRoot(root);
        tree.addChild(root, child00);
        tree.addChild(root, child01);
        tree.addChild(child00, grandchild00);
        tree.addChild(child00, grandchild01);
        tree.addChild(child01, grandchild02);

        Compute.computeHiddenLayout(tree, root);

        for (long node : new long[] {root, child00, child01, grandchild00, grandchild01, grandchild02}) {
            Layout layout = tree.unrounded.get(node);
            assertEquals(SizeF32.ZERO, layout.size(), "node " + node + " size");
            assertEquals(0.0f, layout.location().x());
            assertEquals(0.0f, layout.location().y());
        }
        assertEquals(6, tree.cacheClears); // every visited node has its cache cleared
    }
}
