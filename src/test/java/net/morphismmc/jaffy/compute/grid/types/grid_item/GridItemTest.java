// JAFFY: grid_item.rs has no upstream test module. These tests cover the
// observable contracts of the ported methods: field population, axis dispatch,
// the Option-chain semantics of the contribution methods (incl. laziness and
// caching), and the track-range indexing convention.
package net.morphismmc.jaffy.compute.grid.types.grid_item;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;

class GridItemTest {

    /// Records how often computeChildLayout runs; returns a fixed 10x20 size.
    private static final class StubTree implements LayoutPartialTree {
        int calls = 0;

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
            calls++;
            return LayoutOutput.fromOuterSize(new SizeF32(10.0f, 20.0f));
        }
    }

    private static GridItem item(Style style) {
        return GridItem.newWithStyleAndOrder(7L, style, AlignItems.NORMAL, AlignItems.NORMAL, 3);
    }

    private static Line<OriginZeroLine> ozLine(int start, int end) {
        return new Line<>(new OriginZeroLine(start), new OriginZeroLine(end));
    }

    private static GridTrack fixedTrack(float baseSize) {
        GridTrack track = GridTrack.newGridTrack(
                MinTrackSizingFunction.length(baseSize), MaxTrackSizingFunction.length(baseSize));
        track.baseSize = baseSize;
        return track;
    }

    @Test
    void newWithStyleAndOrderPopulatesFields() {
        Style style =
                Style.DEFAULT.withItemIsReplaced(true).withSize(new Size<>(Dimension.length(30.0f), Dimension.auto()));
        GridItem item = GridItem.newWithStyleAndOrder(7L, style, AlignItems.CENTER, AlignItems.END, 3);
        assertEquals(7L, item.node);
        assertEquals(3, item.sourceOrder);
        assertEquals(ozLine(0, 0), item.row);
        assertEquals(ozLine(0, 0), item.column);
        assertTrue(item.isReplaced);
        // upstream: is_compressible_replaced() = item_is_replaced || item_is_compressible_replaced
        assertTrue(item.isCompressibleReplaced);
        assertEquals(AlignItems.CENTER, item.alignSelf); // null -> parent fallback
        assertEquals(AlignItems.END, item.justifySelf);
        assertTrue(OptF32.isNone(item.baseline));
        assertEquals(0.0f, item.baselineShim);
        assertNull(item.gridAreaSizeCache);
        assertNull(item.knownDimensionsCache);
        assertEquals(SizeOptF32.NONE, item.minContentContributionCache);
        assertEquals(SizeOptF32.NONE, item.minimumContributionCache);
        assertEquals(SizeOptF32.NONE, item.maxContentContributionCache);
        assertTrue(item.oofCandidates.isEmpty());
    }

    @Test
    void explicitAlignSelfOverridesParentFallback() {
        Style style = Style.DEFAULT.withAlignSelf(AlignItems.BASELINE).withJustifySelf(AlignItems.CENTER);
        GridItem item = item(style);
        assertEquals(AlignItems.BASELINE, item.alignSelf);
        assertEquals(AlignItems.CENTER, item.justifySelf);
    }

    @Test
    void axisDispatchSelectsRowOrColumn() {
        GridItem item = item(Style.DEFAULT);
        item.row = ozLine(1, 4);
        item.column = ozLine(-2, 5);
        item.rowIndexes = new Line<>(2, 8);
        item.columnIndexes = new Line<>(0, 10);
        assertEquals(ozLine(1, 4), item.placement(AbstractAxis.BLOCK));
        assertEquals(ozLine(-2, 5), item.placement(AbstractAxis.INLINE));
        assertEquals(new Line<>(2, 8), item.placementIndexes(AbstractAxis.BLOCK));
        assertEquals(new Line<>(0, 10), item.placementIndexes(AbstractAxis.INLINE));
        assertEquals(3, item.span(AbstractAxis.BLOCK));
        assertEquals(7, item.span(AbstractAxis.INLINE));
        // (start+1)..end — indexes between the bounding lines of the interleaved track vec
        assertEquals(new TrackCounts.TrackRange(3, 8), item.trackRangeExcludingLines(AbstractAxis.BLOCK));
        assertEquals(new TrackCounts.TrackRange(1, 10), item.trackRangeExcludingLines(AbstractAxis.INLINE));

        item.crossesFlexibleRow = true;
        item.crossesIntrinsicColumn = true;
        assertTrue(item.crossesFlexibleTrack(AbstractAxis.BLOCK));
        assertFalse(item.crossesFlexibleTrack(AbstractAxis.INLINE));
        assertTrue(item.crossesIntrinsicTrack(AbstractAxis.INLINE));
        assertFalse(item.crossesIntrinsicTrack(AbstractAxis.BLOCK));
    }

    @Test
    void spansTrackMatchingChecksSpannedTracks() {
        GridItem item = item(Style.DEFAULT);
        item.columnIndexes = new Line<>(0, 4); // covers track vec entries 1..4
        List<GridTrack> tracks = List.of(fixedTrack(0), fixedTrack(10), fixedTrack(20), fixedTrack(30), fixedTrack(0));
        assertTrue(item.spansTrackMatching(AbstractAxis.INLINE, tracks, t -> t.baseSize == 20.0f));
        assertFalse(item.spansTrackMatching(AbstractAxis.INLINE, tracks, t -> t.baseSize == 40.0f));
    }

    @Test
    void autoBlockMarginAndBaselineParticipation() {
        // No auto margins + baseline align -> participates
        Style baseline = Style.DEFAULT.withAlignSelf(AlignItems.BASELINE);
        assertTrue(item(baseline).participatesInBaselineAlignment());

        // Auto top margin disqualifies
        LengthPercentageAuto auto = LengthPercentageAuto.auto();
        Style autoMargin = Style.DEFAULT
                .withAlignSelf(AlignItems.BASELINE)
                .withMargin(new Rect<>(auto, auto, auto, LengthPercentageAuto.length(0.0f)));
        GridItem withAutoTop = item(autoMargin);
        assertTrue(withAutoTop.hasAutoBlockMargin());
        assertFalse(withAutoTop.participatesInBaselineAlignment());

        // Non-baseline align does not participate
        assertFalse(item(Style.DEFAULT).participatesInBaselineAlignment());

        // Cyclic dependency disqualifies: percentage height over intrinsic row
        Style pctHeight = Style.DEFAULT
                .withAlignSelf(AlignItems.BASELINE)
                .withSize(new Size<>(Dimension.auto(), Dimension.percent(0.5f)));
        GridItem cyclic = item(pctHeight);
        cyclic.crossesIntrinsicRow = true;
        assertTrue(cyclic.hasCyclicBlockSizeDependency());
        assertFalse(cyclic.participatesInBaselineAlignment());
    }

    @Test
    void marginsAxisSumsResolveAgainstAreaWidthAndAddShim() {
        // Upstream resolves left/right margins against Some(0.0): percentages -> 0.
        // Top/bottom resolve against inner_node_width.
        Style style = Style.DEFAULT.withMargin(new Rect<>(
                LengthPercentageAuto.length(4.0f),
                LengthPercentageAuto.length(6.0f),
                LengthPercentageAuto.percent(0.2f), // 0.2 * 100 = 20
                LengthPercentageAuto.auto())); // auto -> 0
        GridItem item = item(style);
        item.baselineShim = 3.0f;
        SizeF32 sums = item.marginsAxisSumsWithBaselineShims(100.0f, new StubTree());
        assertEquals(10.0f, sums.width()); // 4 + 6
        assertEquals(23.0f, sums.height()); // 20 + shim(3) + 0
    }

    @Test
    void marginsAxisSumsInlinePercentagesResolveAgainstZero() {
        Style style = Style.DEFAULT.withMargin(new Rect<>(
                LengthPercentageAuto.percent(0.5f), // resolves against Some(0.0) -> 0
                LengthPercentageAuto.percent(0.5f),
                LengthPercentageAuto.length(0.0f),
                LengthPercentageAuto.length(0.0f)));
        GridItem item = item(style);
        SizeF32 sums = item.marginsAxisSumsWithBaselineShims(100.0f, new StubTree());
        assertEquals(0.0f, sums.width()); // upstream: inline margins always resolve vs Some(0.0)
    }

    @Test
    void minContentContributionUsesKnownWidthWithoutMeasuring() {
        Style style = Style.DEFAULT.withSize(new Size<>(Dimension.length(30.0f), Dimension.auto()));
        GridItem item = item(style);
        StubTree tree = new StubTree();
        float contribution = item.minContentContribution(
                AbstractAxis.INLINE, tree, new SizeOptF32(100.0f, 50.0f), new SizeOptF32(200.0f, 200.0f));
        assertEquals(30.0f, contribution);
        assertEquals(0, tree.calls); // known dimension short-circuits the child measure
    }

    @Test
    void knownDimensionsStretchFillsGridAreaMinusMargins() {
        // Right margin must be non-auto: an auto inline margin disables stretch
        Style style = Style.DEFAULT.withMargin(new Rect<>(
                LengthPercentageAuto.length(5.0f),
                LengthPercentageAuto.length(0.0f),
                LengthPercentageAuto.length(7.0f),
                LengthPercentageAuto.length(0.0f)));
        GridItem item = item(style); // auto size + normal alignment -> stretch
        StubTree tree = new StubTree();
        float contribution = item.minContentContribution(
                AbstractAxis.INLINE, tree, new SizeOptF32(100.0f, 50.0f), new SizeOptF32(200.0f, 200.0f));
        assertEquals(95.0f, contribution); // 100 - 5 - 0
        assertEquals(0, tree.calls);
    }

    @Test
    void knownDimensionsClampsByMinAndMaxSize() {
        Style style = Style.DEFAULT
                .withSize(new Size<>(Dimension.length(30.0f), Dimension.auto()))
                .withMinSize(new Size<>(LengthPercentageAuto.length(50.0f), LengthPercentageAuto.auto()));
        GridItem item = item(style);
        StubTree tree = new StubTree();
        assertEquals(
                50.0f,
                item.minContentContribution(AbstractAxis.INLINE, tree, new SizeOptF32(100.0f, 50.0f), SizeOptF32.NONE));
    }

    @Test
    void knownDimensionsAppliesAspectRatio() {
        Style style = Style.DEFAULT
                .withSize(new Size<>(Dimension.length(60.0f), Dimension.auto()))
                .withAspectRatio(2.0f);
        GridItem item = item(style);
        StubTree tree = new StubTree();
        // height = width / aspect_ratio -> 30
        float contribution =
                item.minContentContribution(AbstractAxis.BLOCK, tree, new SizeOptF32(100.0f, 50.0f), SizeOptF32.NONE);
        assertEquals(30.0f, contribution);
        assertEquals(0, tree.calls);
    }

    @Test
    void gridAreaSizeSumsTracksWhenAllDefinite() {
        GridItem item = item(Style.DEFAULT);
        // item covers lines 0..2 in both axes: tracks vec entries 1..4 horizontal, 1..4 vertical
        item.columnIndexes = new Line<>(0, 4);
        item.rowIndexes = new Line<>(0, 4);
        List<GridTrack> columns = List.of(fixedTrack(0), fixedTrack(10), fixedTrack(20), fixedTrack(30), fixedTrack(0));
        List<GridTrack> rows = List.of(fixedTrack(0), fixedTrack(5), fixedTrack(5), fixedTrack(5), fixedTrack(0));
        SizeOptF32 size = item.gridAreaSize(
                AbstractAxis.INLINE,
                columns,
                rows,
                new SizeOptF32(100.0f, 50.0f),
                (track, space) -> track.baseSize, // estimate = base size
                (calc, basis) -> 0.0f);
        assertEquals(60.0f, size.width()); // 10+20+30
        assertEquals(15.0f, size.height()); // 5+5+5
    }

    @Test
    void gridAreaSizeIsNoneWhenATrackIsIndefinite() {
        GridItem item = item(Style.DEFAULT);
        item.columnIndexes = new Line<>(0, 4);
        item.rowIndexes = new Line<>(0, 4);
        List<GridTrack> columns = List.of(
                fixedTrack(0),
                GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.AUTO),
                fixedTrack(20),
                fixedTrack(30),
                fixedTrack(0));
        SizeOptF32 size = item.gridAreaSize(
                AbstractAxis.INLINE,
                columns,
                List.of(fixedTrack(0), fixedTrack(5), fixedTrack(5), fixedTrack(5), fixedTrack(0)),
                new SizeOptF32(100.0f, 50.0f),
                (track, space) -> track.baseSize,
                (calc, basis) -> 0.0f);
        assertTrue(OptF32.isNone(size.width()));
        assertEquals(15.0f, size.height());
    }

    @Test
    void gridAreaSizeAddsContentAlignmentAdjustmentToEstimates() {
        GridItem item = item(Style.DEFAULT);
        item.columnIndexes = new Line<>(0, 4);
        item.rowIndexes = new Line<>(0, 4);
        GridTrack adjusted = fixedTrack(10);
        adjusted.contentAlignmentAdjustment = 2.5f;
        List<GridTrack> columns = List.of(fixedTrack(0), fixedTrack(10), adjusted, fixedTrack(30), fixedTrack(0));
        SizeOptF32 size = item.gridAreaSize(
                AbstractAxis.BLOCK, // axis roles swap: inline size comes from `other` tracks via the estimate fn
                List.of(fixedTrack(0), fixedTrack(5), fixedTrack(5), fixedTrack(5), fixedTrack(0)),
                columns,
                new SizeOptF32(100.0f, 50.0f),
                (track, space) -> 100.0f,
                (calc, basis) -> 0.0f);
        assertEquals(15.0f, size.height());
        assertEquals(302.5f, size.width()); // 100 + (100+2.5) + 100
    }

    @Test
    void minContentContributionCachedMeasuresOnce() {
        // normal + not replaced -> stretch fills the width, so force non-stretch alignment
        GridItem item = item(Style.DEFAULT);
        item.justifySelf = AlignItems.START; // START is not stretch -> width unknown
        item.alignSelf = AlignItems.START;
        item.alignSelf = AlignItems.START;
        StubTree tree = new StubTree();
        SizeOptF32 area = new SizeOptF32(100.0f, 50.0f);
        float first = item.minContentContributionCached(AbstractAxis.INLINE, tree, area, SizeOptF32.NONE);
        float second = item.minContentContributionCached(AbstractAxis.INLINE, tree, area, SizeOptF32.NONE);
        assertEquals(10.0f, first); // stub returns width 10
        assertEquals(first, second);
        assertEquals(1, tree.calls); // second call served from cache
    }

    @Test
    void knownDimensionsCacheKeysOnGridAreaSize() {
        GridItem item = item(Style.DEFAULT);
        item.justifySelf = AlignItems.START; // not stretch -> width unknown -> measures
        StubTree tree = new StubTree();
        SizeOptF32 areaA = new SizeOptF32(100.0f, 50.0f);
        SizeOptF32 areaB = new SizeOptF32(80.0f, 50.0f);
        item.minContentContribution(AbstractAxis.INLINE, tree, areaA, SizeOptF32.NONE);
        assertNotNull(item.knownDimensionsCache);
        assertEquals(areaA, item.knownDimensionsCache.gridAreaSize());
        item.minContentContribution(AbstractAxis.INLINE, tree, areaB, SizeOptF32.NONE);
        assertEquals(areaB, item.knownDimensionsCache.gridAreaSize()); // recomputed for the new key
        item.minContentContribution(AbstractAxis.INLINE, tree, areaB, SizeOptF32.NONE);
        assertEquals(areaB, item.knownDimensionsCache.gridAreaSize()); // same key -> entry reused
        assertEquals(3, tree.calls); // the uncached wrapper always measures when width is unknown
    }

    @Test
    void minimumContributionPrefersDefiniteSizeClampedByMin() {
        Style style = Style.DEFAULT
                .withSize(new Size<>(Dimension.length(20.0f), Dimension.auto()))
                .withMinSize(new Size<>(LengthPercentageAuto.length(25.0f), LengthPercentageAuto.auto()));
        GridItem item = item(style);
        item.columnIndexes = new Line<>(0, 4);
        StubTree tree = new StubTree();
        float result = item.minimumContribution(
                tree,
                AbstractAxis.INLINE,
                List.of(fixedTrack(0), fixedTrack(10), fixedTrack(10), fixedTrack(10), fixedTrack(0)),
                new SizeOptF32(100.0f, 50.0f),
                new SizeOptF32(100.0f, 50.0f));
        assertEquals(25.0f, result); // definite 20 clamped up to min 25
        assertEquals(0, tree.calls);
    }

    @Test
    void minimumContributionOfSizingKeywordIsMinContent() {
        Style style = Style.DEFAULT.withSize(new Size<>(Dimension.minContent(), Dimension.auto()));
        GridItem item = item(style);
        item.columnIndexes = new Line<>(0, 4);
        StubTree tree = new StubTree();
        float result = item.minimumContribution(
                tree,
                AbstractAxis.INLINE,
                List.of(fixedTrack(0), fixedTrack(10), fixedTrack(10), fixedTrack(10), fixedTrack(0)),
                new SizeOptF32(100.0f, 50.0f),
                new SizeOptF32(100.0f, 50.0f));
        assertEquals(10.0f, result); // min-content contribution from the stub
        assertEquals(1, tree.calls);
    }

    @Test
    void minimumContributionOfScrollContainerIsZero() {
        Style style = Style.DEFAULT.withOverflow(new Point<>(Overflow.HIDDEN, Overflow.HIDDEN));
        GridItem item = item(style);
        item.columnIndexes = new Line<>(0, 4);
        StubTree tree = new StubTree();
        float result = item.minimumContribution(
                tree,
                AbstractAxis.INLINE,
                List.of(fixedTrack(0), fixedTrack(10), fixedTrack(10), fixedTrack(10), fixedTrack(0)),
                new SizeOptF32(100.0f, 50.0f),
                new SizeOptF32(100.0f, 50.0f));
        assertEquals(0.0f, result); // hidden overflow -> automatic minimum size 0
        assertEquals(0, tree.calls);
    }

    @Test
    void minimumContributionAutoMinUsesContentForAutoMinTrack() {
        GridItem item = item(Style.DEFAULT);
        item.justifySelf = AlignItems.START;
        item.alignSelf = AlignItems.START;
        item.columnIndexes = new Line<>(0, 2); // spans a single track (entries 1..2)
        StubTree tree = new StubTree();
        GridTrack autoMinTrack = GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.AUTO);
        float result = item.minimumContribution(
                tree,
                AbstractAxis.INLINE,
                List.of(fixedTrack(0), autoMinTrack, fixedTrack(0)),
                new SizeOptF32(100.0f, 50.0f),
                new SizeOptF32(100.0f, 50.0f));
        assertEquals(10.0f, result); // content-based minimum from the stub
        assertEquals(1, tree.calls);
    }

    @Test
    void minimumContributionAutoMinIsZeroWhenSpanningFrTrack() {
        GridItem item = item(Style.DEFAULT);
        item.justifySelf = AlignItems.START;
        item.columnIndexes = new Line<>(0, 4); // spans three entries
        StubTree tree = new StubTree();
        GridTrack frTrack = GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.fr(1.0f));
        float result = item.minimumContribution(
                tree,
                AbstractAxis.INLINE,
                List.of(
                        fixedTrack(0),
                        GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.AUTO),
                        frTrack,
                        fixedTrack(0),
                        fixedTrack(0)),
                new SizeOptF32(100.0f, 50.0f),
                new SizeOptF32(100.0f, 50.0f));
        assertEquals(0.0f, result); // spans >1 track incl. fr -> automatic minimum 0
        assertEquals(0, tree.calls);
    }

    @Test
    void spannedTrackLimitsSumFixedMaxSizingFunctions() {
        GridItem item = item(Style.DEFAULT);
        item.columnIndexes = new Line<>(0, 4); // entries 1..4
        List<GridTrack> tracks = List.of(
                fixedTrack(0),
                GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.length(10)),
                GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.length(20)),
                GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.length(30)),
                fixedTrack(0));
        assertEquals(60.0f, item.spannedTrackLimit(AbstractAxis.INLINE, tracks, OptF32.NONE, (calc, basis) -> 0.0f));
        assertEquals(
                60.0f, item.spannedFixedTrackLimit(AbstractAxis.INLINE, tracks, OptF32.NONE, (calc, basis) -> 0.0f));
    }

    @Test
    void spannedTrackLimitsAreNoneOverNonFixedTracks() {
        GridItem item = item(Style.DEFAULT);
        item.columnIndexes = new Line<>(0, 4);
        List<GridTrack> tracks = List.of(
                fixedTrack(0),
                GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.length(10)),
                GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.AUTO),
                fixedTrack(0),
                fixedTrack(0));
        assertTrue(
                OptF32.isNone(item.spannedTrackLimit(AbstractAxis.INLINE, tracks, OptF32.NONE, (calc, basis) -> 0.0f)));
        assertTrue(OptF32.isNone(
                item.spannedFixedTrackLimit(AbstractAxis.INLINE, tracks, OptF32.NONE, (calc, basis) -> 0.0f)));
    }

    @Test
    void fitContentLimitExcludedFromFixedLimitButIncludedInDefiniteLimit() {
        GridItem item = item(Style.DEFAULT);
        item.columnIndexes = new Line<>(0, 2);
        List<GridTrack> tracks = List.of(
                fixedTrack(0),
                GridTrack.newGridTrack(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.fitContentPx(40.0f)),
                fixedTrack(0));
        // fit-content(40px): definite_limit = 40 but definite_value = None
        assertEquals(40.0f, item.spannedTrackLimit(AbstractAxis.INLINE, tracks, OptF32.NONE, (calc, basis) -> 0.0f));
        assertTrue(OptF32.isNone(
                item.spannedFixedTrackLimit(AbstractAxis.INLINE, tracks, OptF32.NONE, (calc, basis) -> 0.0f)));
    }
}
