// JAFFY: traits.rs has no upstream test module. This exercises the
// `LayoutPartialTreeExt` -> default-method ports: the LayoutInput each
// convenience method assembles is the observable contract (run_mode, axis,
// known_dimensions_are_definite).
package net.morphismmc.jaffy.tree.traits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongIterator;
import org.junit.jupiter.api.Test;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.jaffy.tree.layout.DetailedLayoutInfo;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;

class TreeTraitsTest {

    /** Minimal tree that records the last inputs it was called with. */
    private static final class StubTree implements LayoutPartialTree {
        LayoutInput lastInputs;
        long lastNodeId = -1;

        @Override
        public LongIterator childIds(long parentNodeId) {
            return LongIterators.EMPTY_ITERATOR;
        }

        @Override
        public int childCount(long parentNodeId) {
            return 0;
        }

        @Override
        public long getChildId(long parentNodeId, int childIndex) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public Style getCoreContainerStyle(long nodeId) {
            return Style.DEFAULT;
        }

        @Override
        public void setUnroundedLayout(long nodeId, Layout layout) {}

        @Override
        public LayoutOutput computeChildLayout(long nodeId, LayoutInput inputs) {
            lastNodeId = nodeId;
            lastInputs = inputs;
            return LayoutOutput.fromOuterSize(new SizeF32(10.0f, 20.0f));
        }
    }

    private static final SizeOptF32 KNOWN = new SizeOptF32(1.0f, OptF32.NONE);
    private static final SizeOptF32 PARENT = new SizeOptF32(2.0f, 3.0f);
    private static final Size<AvailableSpace> SPACE =
            new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT);
    private static final LineBool COLLAPSIBLE = LineBool.FALSE;

    @Test
    void measureChildSizeAssemblesComputeSizeInput() {
        var tree = new StubTree();
        float size = tree.measureChildSize(
                7L, KNOWN, PARENT, SPACE, SizingMode.CONTENT_SIZE, AbsoluteAxis.HORIZONTAL, COLLAPSIBLE);
        assertEquals(10.0f, size);
        assertEquals(7L, tree.lastNodeId);
        assertEquals(RunMode.COMPUTE_SIZE, tree.lastInputs.runMode());
        assertEquals(SizingMode.CONTENT_SIZE, tree.lastInputs.sizingMode());
        assertEquals(RequestedAxis.HORIZONTAL, tree.lastInputs.axis()); // axis.into()
        assertEquals(KNOWN, tree.lastInputs.knownDimensions());
        assertTrue(tree.lastInputs.knownDimensionsAreDefinite().width());
        assertTrue(tree.lastInputs.knownDimensionsAreDefinite().height());
        assertEquals(PARENT, tree.lastInputs.parentSize());
        assertEquals(SPACE, tree.lastInputs.availableSpace());
        assertEquals(COLLAPSIBLE, tree.lastInputs.verticalMarginsAreCollapsible());
    }

    @Test
    void measureChildSizeVerticalUsesVerticalAxis() {
        var tree = new StubTree();
        float size = tree.measureChildSize(
                0L, KNOWN, PARENT, SPACE, SizingMode.INHERENT_SIZE, AbsoluteAxis.VERTICAL, COLLAPSIBLE);
        assertEquals(20.0f, size); // getAbs(vertical) -> height
        assertEquals(RequestedAxis.VERTICAL, tree.lastInputs.axis());
    }

    @Test
    void measureChildSizeBothAssemblesBothAxisInput() {
        var tree = new StubTree();
        SizeF32 size = tree.measureChildSizeBoth(
                1L, KNOWN, PARENT, SPACE, SizingMode.INHERENT_SIZE, COLLAPSIBLE);
        assertEquals(new SizeF32(10.0f, 20.0f), size);
        assertEquals(RunMode.COMPUTE_SIZE, tree.lastInputs.runMode());
        assertEquals(RequestedAxis.BOTH, tree.lastInputs.axis());
    }

    @Test
    void performChildLayoutAssemblesPerformLayoutInput() {
        var tree = new StubTree();
        LayoutOutput out = tree.performChildLayout(
                2L, KNOWN, PARENT, SPACE, SizingMode.CONTENT_SIZE, COLLAPSIBLE);
        assertEquals(new SizeF32(10.0f, 20.0f), out.size());
        assertEquals(RunMode.PERFORM_LAYOUT, tree.lastInputs.runMode());
        assertEquals(RequestedAxis.BOTH, tree.lastInputs.axis());
    }

    @Test
    void resolveCalcValueAndCalcDefaultToZero() {
        var tree = new StubTree();
        assertEquals(0.0f, tree.resolveCalcValue(new Object(), 100.0f));
        assertEquals(0.0f, tree.calc(new Object(), 100.0f));
        assertEquals(0.0f, tree.calc(null, 0.0f));
    }

    @Test
    void containingBlockDetailedLayoutInfoDefaultsToNone() {
        var block = new LayoutContainingBlock() {
            @Override
            public Style getOofItemStyle(long nodeId) {
                return Style.DEFAULT;
            }

            @Override
            public void clearHoistedChildren(long nodeId) {}

            @Override
            public void addHoistedChildren(long nodeId, long[] hoisted) {}

            @Override
            public LongIterator childIds(long parentNodeId) {
                return LongIterators.EMPTY_ITERATOR;
            }

            @Override
            public int childCount(long parentNodeId) {
                return 0;
            }

            @Override
            public long getChildId(long parentNodeId, int childIndex) {
                return 0;
            }

            @Override
            public Style getCoreContainerStyle(long nodeId) {
                return Style.DEFAULT;
            }

            @Override
            public void setUnroundedLayout(long nodeId, Layout layout) {}

            @Override
            public LayoutOutput computeChildLayout(long nodeId, LayoutInput inputs) {
                return LayoutOutput.fromOuterSize(SizeF32.ZERO);
            }
        };
        assertEquals(DetailedLayoutInfo.NONE, block.getDetailedLayoutInfo(0L));
    }
}
