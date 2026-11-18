// JAFFY-UPSTREAM: src/tree/taffy_tree.rs
// JAFFY-NOTE: `mod tests`
//
// `TaffyTree<()>` -> `TaffyTree<Void>`; `TaffyTree<Size<f32>>` ->
// `TaffyTree<SizeF32>`; `TaffyResult` -> direct value (Err -> TaffyException); `Size::MAX_CONTENT`
// -> `Size<>(MAX_CONTENT, MAX_CONTENT)`; the two `Drop`-counter tests verify
// the context reference is removed instead (Java has no Drop).
package net.morphismmc.jaffy.tree;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.compute.Leaf;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TaffyTreeTest {

    private static final int DEFAULT_CAPACITY = 16; // capacity defined in the `new TaffyTree()` ctor

    /// Upstream `size_measure_function`
    private static LayoutOutput sizeMeasureFunction(
            LayoutInput inputs, long nodeId, @Nullable SizeF32 nodeContext, Style style) {
        return Leaf.computeLeafLayout(
                inputs,
                style,
                (val, basis) -> 0.0f,
                (knownDimensions, availableSpace) ->
                        knownDimensions.unwrapOr(nodeContext != null ? nodeContext : SizeF32.ZERO));
    }

    private static Size<AvailableSpace> maxContent() {
        return new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT);
    }

    private static Size<AvailableSpace> minContent() {
        return new Size<>(AvailableSpace.MIN_CONTENT, AvailableSpace.MIN_CONTENT);
    }

    private static Size<Dimension> dimSize(double w, double h) {
        return new Size<>(Dimension.fromLength(w), Dimension.fromLength(h));
    }

    @Test
    void newShouldAllocateDefaultCapacity() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        assertTrue(taffy.children.capacity() >= DEFAULT_CAPACITY);
        assertTrue(taffy.parents.capacity() >= DEFAULT_CAPACITY);
        assertTrue(taffy.nodes.capacity() >= DEFAULT_CAPACITY);
    }

    @Test
    void testWithCapacity() {
        int capacity = 8;
        TaffyTree<Void> taffy = new TaffyTree<>(capacity);

        assertTrue(taffy.children.capacity() >= capacity);
        assertTrue(taffy.parents.capacity() >= capacity);
        assertTrue(taffy.nodes.capacity() >= capacity);
    }

    @Test
    void testNewLeaf() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long res = taffy.newLeaf(Style.DEFAULT);
        long node = res;

        // node should be in the taffy tree and have no children
        assertEquals(0, taffy.childCount(node));
    }

    @Test
    void newLeafWithContext() {
        TaffyTree<SizeF32> taffy = new TaffyTree<>();

        long res = taffy.newLeafWithContext(Style.DEFAULT, SizeF32.ZERO);
        long node = res;

        // node should be in the taffy tree and have no children
        assertEquals(0, taffy.childCount(node));
    }

    /// Test that new_with_children works as expected
    @Test
    void testNewWithChildren() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1});

        // node should have two children
        assertEquals(2, taffy.childCount(node));
        assertEquals(child0, taffy.children(node)[0]);
        assertEquals(child1, taffy.children(node)[1]);
    }

    @Test
    void removeNodeShouldRemove() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long node = taffy.newLeaf(Style.DEFAULT);

        taffy.remove(node);
    }

    @Test
    void removeNodeShouldDetachHierarchy() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        // Build a linear tree layout: <0> <- <1> <- <2>
        long node2 = taffy.newLeaf(Style.DEFAULT);
        long node1 = taffy.newWithChildren(Style.DEFAULT, new long[] {node2});
        long node0 = taffy.newWithChildren(Style.DEFAULT, new long[] {node1});

        // Both node0 and node1 should have 1 child nodes
        assertArrayEquals(new long[] {node1}, taffy.children(node0));
        assertArrayEquals(new long[] {node2}, taffy.children(node1));

        // Disconnect the tree: <0> <2>
        taffy.remove(node1);

        // Both remaining nodes should have no child nodes
        assertEquals(0, taffy.children(node0).length);
        assertEquals(0, taffy.children(node2).length);
    }

    @Test
    void removeLastNode() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long parent = taffy.newLeaf(Style.DEFAULT);
        long child = taffy.newLeaf(Style.DEFAULT);
        taffy.addChild(parent, child);

        taffy.remove(child);
        taffy.remove(parent);
    }

    // Related to: https://github.com/DioxusLabs/taffy/issues/998
    @Test
    void removeNodeMarksParentDirty() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        // The root's height comes entirely from its child's 1px bottom border
        long child = taffy.newLeaf(Style.DEFAULT.withBorder(new Rect<>(
                LengthPercentage.length(0.0f), LengthPercentage.length(0.0f),
                LengthPercentage.length(0.0f), LengthPercentage.length(1.0f))));
        long root = taffy.newWithChildren(Style.DEFAULT, new long[] {child});

        taffy.computeLayout(root, minContent());
        assertEquals(1.0f, taffy.layout(root).size().height());

        taffy.remove(child);
        assertEquals(true, taffy.dirty(root));

        // The root no longer has any children, so its height should shrink
        // back to zero
        taffy.computeLayout(root, minContent());
        assertEquals(0.0f, taffy.layout(root).size().height());
    }

    @Test
    void setMeasure() {
        TaffyTree<SizeF32> taffy = new TaffyTree<>();
        long node = taffy.newLeafWithContext(Style.DEFAULT, new SizeF32(200.0f, 200.0f));
        taffy.computeLayoutWithMeasure(node, maxContent(), TaffyTreeTest::sizeMeasureFunction);
        assertEquals(200.0f, taffy.layout(node).size().width());

        taffy.setNodeContext(node, new SizeF32(100.0f, 100.0f));
        taffy.computeLayoutWithMeasure(node, maxContent(), TaffyTreeTest::sizeMeasureFunction);
        assertEquals(100.0f, taffy.layout(node).size().width());
    }

    @Test
    void setMeasureOfPreviouslyUnmeasuredNode() {
        TaffyTree<SizeF32> taffy = new TaffyTree<>();
        long node = taffy.newLeaf(Style.DEFAULT);
        taffy.computeLayoutWithMeasure(node, maxContent(), TaffyTreeTest::sizeMeasureFunction);
        assertEquals(0.0f, taffy.layout(node).size().width());

        taffy.setNodeContext(node, new SizeF32(100.0f, 100.0f));
        taffy.computeLayoutWithMeasure(node, maxContent(), TaffyTreeTest::sizeMeasureFunction);
        assertEquals(100.0f, taffy.layout(node).size().width());
    }

    /// Test that adding `add_child()` works
    @Test
    void addChild() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long node = taffy.newLeaf(Style.DEFAULT);
        assertEquals(0, taffy.childCount(node));

        long child0 = taffy.newLeaf(Style.DEFAULT);
        taffy.addChild(node, child0);
        assertEquals(1, taffy.childCount(node));

        long child1 = taffy.newLeaf(Style.DEFAULT);
        taffy.addChild(node, child1);
        assertEquals(2, taffy.childCount(node));
    }

    @Test
    void insertChildAtIndex() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long child2 = taffy.newLeaf(Style.DEFAULT);

        long node = taffy.newLeaf(Style.DEFAULT);
        assertEquals(0, taffy.childCount(node));

        taffy.insertChildAtIndex(node, 0, child0);
        assertEquals(1, taffy.childCount(node));
        assertEquals(child0, taffy.children(node)[0]);

        taffy.insertChildAtIndex(node, 0, child1);
        assertEquals(2, taffy.childCount(node));
        assertEquals(child1, taffy.children(node)[0]);
        assertEquals(child0, taffy.children(node)[1]);

        taffy.insertChildAtIndex(node, 1, child2);
        assertEquals(3, taffy.childCount(node));
        assertEquals(child1, taffy.children(node)[0]);
        assertEquals(child2, taffy.children(node)[1]);
        assertEquals(child0, taffy.children(node)[2]);
    }

    @Test
    void setChildren() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1});

        assertEquals(2, taffy.childCount(node));
        assertEquals(child0, taffy.children(node)[0]);
        assertEquals(child1, taffy.children(node)[1]);

        long child2 = taffy.newLeaf(Style.DEFAULT);
        long child3 = taffy.newLeaf(Style.DEFAULT);
        taffy.setChildren(node, new long[] {child2, child3});

        assertEquals(2, taffy.childCount(node));
        assertEquals(child2, taffy.children(node)[0]);
        assertEquals(child3, taffy.children(node)[1]);
    }

    /// Test that removing a child works
    @Test
    void removeChild() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1});

        assertEquals(2, taffy.childCount(node));

        taffy.removeChild(node, child0);
        assertEquals(1, taffy.childCount(node));
        assertEquals(child1, taffy.children(node)[0]);

        taffy.removeChild(node, child1);
        assertEquals(0, taffy.childCount(node));
    }

    @Test
    void removeChildAtIndex() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1});

        assertEquals(2, taffy.childCount(node));

        taffy.removeChildAtIndex(node, 0);
        assertEquals(1, taffy.childCount(node));
        assertEquals(child1, taffy.children(node)[0]);

        taffy.removeChildAtIndex(node, 0);
        assertEquals(0, taffy.childCount(node));
    }

    @Test
    void removeChildrenRange() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long child2 = taffy.newLeaf(Style.DEFAULT);
        long child3 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1, child2, child3});

        assertEquals(4, taffy.childCount(node));

        // `1..=2` inclusive -> [1, 3) exclusive
        taffy.removeChildrenRange(node, 1, 3);
        assertEquals(2, taffy.childCount(node));
        assertArrayEquals(new long[] {child0, child3}, taffy.children(node));
        for (long child : new long[] {child0, child3}) {
            assertEquals(node, taffy.parent(child));
        }
        for (long child : new long[] {child1, child2}) {
            assertEquals(0L, taffy.parent(child)); // no parent — OptI64.NONE
        }
    }

    // Related to: https://github.com/DioxusLabs/taffy/issues/510
    @Test
    void removeChildUpdatesParents() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long parent = taffy.newLeaf(Style.DEFAULT);
        long child = taffy.newLeaf(Style.DEFAULT);

        taffy.addChild(parent, child);

        taffy.remove(parent);

        // Once the parent is removed this shouldn't panic.
        taffy.setChildren(child, new long[0]); // must not throw
    }

    @Test
    void replaceChildAtIndex() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);

        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0});
        assertEquals(1, taffy.childCount(node));
        assertEquals(child0, taffy.children(node)[0]);

        taffy.replaceChildAtIndex(node, 0, child1);
        assertEquals(1, taffy.childCount(node));
        assertEquals(child1, taffy.children(node)[0]);
    }

    @Test
    void testChildAtIndex() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long child2 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1, child2});

        long r0 = taffy.childAtIndex(node, 0);
        assertEquals(child0, r0);
        long r1 = taffy.childAtIndex(node, 1);
        assertEquals(child1, r1);
        long r2 = taffy.childAtIndex(node, 2);
        assertEquals(child2, r2);
    }

    @Test
    void testChildCount() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1});

        assertEquals(2, taffy.childCount(node));
        assertEquals(0, taffy.childCount(child0));
        assertEquals(0, taffy.childCount(child1));
    }

    @Test
    void testChildren() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1});

        assertArrayEquals(new long[] {child0, child1}, taffy.children(node));
        assertEquals(0, taffy.children(child0).length);
    }

    @Test
    void testSetStyle() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long node = taffy.newLeaf(Style.DEFAULT);
        assertEquals(Display.FLEX, taffy.style(node).display());

        taffy.setStyle(node, Style.DEFAULT.withDisplay(Display.NONE));
        assertEquals(Display.NONE, taffy.style(node).display());
    }

    @Test
    void testStyle() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        Style style = Style.DEFAULT.withDisplay(Display.NONE).withFlexDirection(FlexDirection.ROW_REVERSE);

        long node = taffy.newLeaf(style);

        Style res = taffy.style(node);
        assertEquals(style, res);
    }

    @Test
    void testLayout() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long node = taffy.newLeaf(Style.DEFAULT);

        // TODO: Improve this test?
        Layout res = taffy.layout(node);
    }

    @Test
    void testMarkDirty() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child0 = taffy.newLeaf(Style.DEFAULT);
        long child1 = taffy.newLeaf(Style.DEFAULT);
        long node = taffy.newWithChildren(Style.DEFAULT, new long[] {child0, child1});

        taffy.computeLayout(node, maxContent());

        assertEquals(false, taffy.dirty(child0));
        assertEquals(false, taffy.dirty(child1));
        assertEquals(false, taffy.dirty(node));

        taffy.markDirty(node);
        assertEquals(false, taffy.dirty(child0));
        assertEquals(false, taffy.dirty(child1));
        assertEquals(true, taffy.dirty(node));

        taffy.computeLayout(node, maxContent());
        taffy.markDirty(child0);
        assertEquals(true, taffy.dirty(child0));
        assertEquals(false, taffy.dirty(child1));
        assertEquals(true, taffy.dirty(node));
    }

    @Test
    void computeLayoutShouldProduceValidResult() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long nodeResult = taffy.newLeaf(Style.DEFAULT.withSize(dimSize(10, 10)));
        long node = nodeResult;
        taffy.computeLayout(node, new Size<>(new AvailableSpace.Definite(100.0f), new AvailableSpace.Definite(100.0f)));
    }

    @Test
    void makeSureLayoutLocationIsTopLeft() {
        TaffyTree<Void> taffy = new TaffyTree<>();

        long node = taffy.newLeaf(
                Style.DEFAULT.withSize(new Size<>(Dimension.fromPercent(1.0), Dimension.fromPercent(1.0))));

        long root = taffy.newWithChildren(
                Style.DEFAULT
                        .withSize(dimSize(100, 100))
                        .withPadding(new Rect<>(
                                LengthPercentage.length(10.0f),
                                LengthPercentage.length(20.0f),
                                LengthPercentage.length(30.0f),
                                LengthPercentage.length(40.0f))),
                new long[] {node});

        taffy.computeLayout(root, maxContent());

        // If Layout::location represents top-left coord, 'node' location
        // must be (due applied 'root' padding): {x: 10, y: 30}.
        Layout layout = taffy.layout(node);
        assertEquals(10.0f, layout.location().x());
        assertEquals(30.0f, layout.location().y());
    }

    @Test
    void setChildrenReparents() {
        TaffyTree<Void> taffy = new TaffyTree<>();
        long child = taffy.newLeaf(Style.DEFAULT);
        long oldParent = taffy.newWithChildren(Style.DEFAULT, new long[] {child});

        long newParent = taffy.newLeaf(Style.DEFAULT);
        taffy.setChildren(newParent, new long[] {child});

        assertEquals(0, taffy.children(oldParent).length);
    }

    // Java has no `Drop`: the observable part of `remove_drops_the_nodes_context`
    // is that the context reference is removed from `node_context_data`.
    @Test
    void removeDropsTheNodesContext() {
        TaffyTree<SizeF32> taffy = new TaffyTree<>();
        long node = taffy.newLeafWithContext(Style.DEFAULT, new SizeF32(1.0f, 1.0f));

        taffy.remove(node);

        assertNull(taffy.getNodeContext(node), "removing a node must drop its context");
        assertTrue(taffy.nodeContextData.isEmpty());
    }

    @Test
    void clearDropsEveryNodesContext() {
        TaffyTree<SizeF32> taffy = new TaffyTree<>();
        for (int i = 0; i < 8; i++) {
            taffy.newLeafWithContext(Style.DEFAULT, new SizeF32(i, i));
        }

        taffy.clear();

        assertTrue(taffy.nodeContextData.isEmpty(), "clear() must drop all node contexts");
    }
}
