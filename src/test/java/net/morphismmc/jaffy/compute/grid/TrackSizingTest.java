// JAFFY: upstream=none
// JAFFY-NOTE: track_sizing.rs has no upstream test module. These tests cover the
// observable contracts of the ported functions: the gutter alignment
// adjustment weights, track-index resolution, cross-track flag detection, and
// the main algorithm phases (initialisation, intrinsic resolution, flex
// expansion, auto-track stretching, early exit, baseline shims, the
// other-axis gutter adjustment write-back).
package net.morphismmc.jaffy.compute.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.jaffy.compute.grid.TrackSizing.TrackSizeEstimate;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
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

class TrackSizingTest {

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

    /// `(track, basis, tree) -> track.base_size` — the estimate reads the provisional base size.
    private static final TrackSizeEstimate BASE_SIZE_ESTIMATE = (track, basis, tree) -> track.baseSize;

    private static GridTrack gutter() {
        return GridTrack.gutter(LengthPercentage.ZERO);
    }

    private static GridTrack fixedTrack(float size) {
        return GridTrack.newGridTrack(MinTrackSizingFunction.length(size), MaxTrackSizingFunction.length(size));
    }

    private static GridTrack intrinsicTrack() {
        return GridTrack.newGridTrack(MinTrackSizingFunction.minContent(), MaxTrackSizingFunction.minContent());
    }

    private static GridTrack autoTrack() {
        return GridTrack.newGridTrack(MinTrackSizingFunction.auto(), MaxTrackSizingFunction.auto());
    }

    private static GridTrack flexTrack(float factor) {
        return GridTrack.newGridTrack(MinTrackSizingFunction.auto(), MaxTrackSizingFunction.fr(factor));
    }

    private static Line<OriginZeroLine> ozLine(int start, int end) {
        return new Line<>(new OriginZeroLine(start), new OriginZeroLine(end));
    }

    /// A span-1 item placed on vec index 1 (the first non-gutter track): OriginZero
    /// lines 0..1 -> track vec indexes 0..2 -> range excluding lines = 1..2.
    private static GridItem itemOnFirstTrack() {
        GridItem item = GridItem.newWithStyleAndOrder(7L, Style.DEFAULT, AlignItems.NORMAL, AlignItems.NORMAL, 0);
        item.column = ozLine(0, 1);
        item.row = ozLine(0, 1);
        item.columnIndexes = new Line<>(0, 2);
        item.rowIndexes = new Line<>(0, 2);
        return item;
    }

    // ---------------------------------------------------------------------
    // resolve_item_track_indexes
    // ---------------------------------------------------------------------

    @Test
    void resolveItemTrackIndexesMapsLinesToVecIndexes() {
        // TrackCounts(negativeImplicit=1, explicit=2, positiveImplicit=1):
        // oz lines -1..=3 -> vec indexes 0,2,4,6,8
        TrackCounts counts = TrackCounts.fromRaw(1, 2, 1);
        GridItem item = itemOnFirstTrack();
        item.column = ozLine(-1, 1); // lines -1,1 -> indexes 0,4
        item.row = ozLine(0, 2); // lines 0,2 -> indexes 2,6

        TrackSizing.resolveItemTrackIndexes(List.of(item), counts, counts);

        assertEquals(new Line<>(0, 4), item.columnIndexes);
        assertEquals(new Line<>(2, 6), item.rowIndexes);
    }

    // ---------------------------------------------------------------------
    // determine_if_item_crosses_flexible_or_intrinsic_tracks
    // ---------------------------------------------------------------------

    @Test
    void determineCrossFlags() {
        // columns: [gutter, 1fr, gutter, fixed, gutter]; rows: [gutter, auto, gutter]
        List<GridTrack> columns = List.of(gutter(), flexTrack(1.0f), gutter(), fixedTrack(10.0f), gutter());
        List<GridTrack> rows = List.of(gutter(), autoTrack(), gutter());

        GridItem onFlex = itemOnFirstTrack(); // vec range 1..2 -> the 1fr track
        GridItem onFixed = itemOnFirstTrack();
        onFixed.columnIndexes = new Line<>(2, 4); // vec range 3..4 -> the fixed track
        GridItem unplaced = itemOnFirstTrack();
        unplaced.rowIndexes = new Line<>(0, 0); // empty range (1..0) -> crosses nothing

        TrackSizing.determineIfItemCrossesFlexibleOrIntrinsicTracks(List.of(onFlex, onFixed, unplaced), columns, rows);

        assertTrue(onFlex.crossesFlexibleColumn);
        // minmax(auto, 1fr): the auto min is intrinsic (AUTO_TAG is intrinsic)
        assertTrue(onFlex.crossesIntrinsicColumn);
        assertTrue(onFlex.crossesIntrinsicRow); // auto is intrinsic
        assertFalse(onFlex.crossesFlexibleRow);

        assertFalse(onFixed.crossesFlexibleColumn);
        assertFalse(onFixed.crossesIntrinsicColumn);

        assertFalse(unplaced.crossesIntrinsicRow);
        assertFalse(unplaced.crossesFlexibleRow);
    }

    // ---------------------------------------------------------------------
    // compute_alignment_gutter_adjustment
    // ---------------------------------------------------------------------

    private static List<GridTrack> fiveBase10Tracks() {
        List<GridTrack> tracks = List.of(gutter(), fixedTrack(10.0f), gutter(), fixedTrack(10.0f), gutter());
        for (GridTrack t : tracks) {
            t.baseSize = 10.0f; // newGridTrack leaves baseSize 0 — set all 5 -> sum 50
        }
        return tracks;
    }

    private static float gutterAdjustment(AlignContent alignment, float innerSize, List<GridTrack> tracks) {
        return TrackSizing.computeAlignmentGutterAdjustment(
                alignment, innerSize, (track, basis) -> track.baseSize, tracks);
    }

    @Test
    void gutterAdjustmentSingleTrackIsZero() {
        assertEquals(0.0f, gutterAdjustment(AlignContent.SPACE_BETWEEN, 100.0f, List.of(gutter())));
    }

    @Test
    void gutterAdjustmentPackedAlignmentsAreZero() {
        // inner_gutter_weight == 0 -> early 0 for packed alignments
        List<GridTrack> tracks = fiveBase10Tracks();
        assertEquals(0.0f, gutterAdjustment(AlignContent.START, 100.0f, tracks));
        assertEquals(0.0f, gutterAdjustment(AlignContent.END, 100.0f, tracks));
        assertEquals(0.0f, gutterAdjustment(AlignContent.CENTER, 100.0f, tracks));
        assertEquals(0.0f, gutterAdjustment(AlignContent.STRETCH, 100.0f, tracks));
        assertEquals(0.0f, gutterAdjustment(AlignContent.NORMAL, 100.0f, tracks));
    }

    @Test
    void gutterAdjustmentDistributedAlignments() {
        // len=5 -> ((5-3)/2)=1 inner gutter + 2 outer
        List<GridTrack> tracks = fiveBase10Tracks();
        // free = 100 - 50 = 50
        // SpaceBetween: weighted = 1*1 + 2*0 = 1 -> 50/1 * 1 = 50
        assertEquals(50.0f, gutterAdjustment(AlignContent.SPACE_BETWEEN, 100.0f, tracks));
        // SpaceAround: weighted = 1*2 + 2*1 = 4 -> 50/4 * 2 = 25
        assertEquals(25.0f, gutterAdjustment(AlignContent.SPACE_AROUND, 100.0f, tracks));
        // SpaceEvenly: weighted = 1*1 + 2*1 = 3 -> 50/3 * 1
        assertEquals(50.0f / 3.0f, gutterAdjustment(AlignContent.SPACE_EVENLY, 100.0f, tracks));
    }

    @Test
    void gutterAdjustmentIndefiniteSizeIsZero() {
        assertEquals(
                0.0f,
                TrackSizing.computeAlignmentGutterAdjustment(
                        AlignContent.SPACE_BETWEEN, OptF32.NONE, (track, basis) -> track.baseSize, fiveBase10Tracks()));
    }

    @Test
    void gutterAdjustmentIndefiniteEstimateIsZero() {
        // A None estimate makes the Option-sum None -> free space unwrap_or(0) -> 0
        assertEquals(
                0.0f,
                TrackSizing.computeAlignmentGutterAdjustment(
                        AlignContent.SPACE_BETWEEN, 100.0f, (track, basis) -> OptF32.NONE, fiveBase10Tracks()));
    }

    // ---------------------------------------------------------------------
    // track_sizing_algorithm
    // ---------------------------------------------------------------------

    @Test
    void fixedTracksSkipAlgorithm() {
        List<GridTrack> columns = new ArrayList<>(List.of(gutter(), fixedTrack(10.0f), gutter()));
        StubTree tree = new StubTree();

        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.INLINE,
                OptF32.NONE,
                OptF32.NONE,
                AlignContent.NORMAL,
                AlignContent.NORMAL,
                new Size<>(new AvailableSpace.Definite(50.0f), new AvailableSpace.Definite(50.0f)),
                new SizeOptF32(50.0f, OptF32.NONE),
                columns,
                List.of(),
                List.of(),
                BASE_SIZE_ESTIMATE,
                false);

        // initialize_track_sizes resolved both tracks; the early-return path left them final
        assertEquals(10.0f, columns.get(1).baseSize);
        assertEquals(10.0f, columns.get(1).growthLimit);
        assertEquals(0, tree.calls); // no child measurement happened
    }

    @Test
    void intrinsicMinContentTrackGrowsToContentSize() {
        List<GridTrack> columns = new ArrayList<>(List.of(gutter(), intrinsicTrack(), gutter()));
        GridItem item = itemOnFirstTrack();
        StubTree tree = new StubTree();

        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.INLINE,
                OptF32.NONE,
                OptF32.NONE,
                AlignContent.NORMAL,
                AlignContent.NORMAL,
                new Size<>(new AvailableSpace.Definite(50.0f), new AvailableSpace.Definite(50.0f)),
                new SizeOptF32(50.0f, OptF32.NONE),
                columns,
                List.of(gutter(), fixedTrack(20.0f), gutter()),
                List.of(item),
                BASE_SIZE_ESTIMATE,
                false);

        // stub child is 10 wide -> base size and growth limit grow to the contribution
        assertEquals(10.0f, columns.get(1).baseSize);
        assertEquals(10.0f, columns.get(1).growthLimit);
        assertTrue(tree.calls > 0);
    }

    @Test
    void frTrackExpandsIntoDefiniteSpace() {
        List<GridTrack> columns = new ArrayList<>(List.of(gutter(), flexTrack(1.0f), gutter()));
        StubTree tree = new StubTree();

        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.INLINE,
                OptF32.NONE,
                OptF32.NONE,
                AlignContent.NORMAL,
                AlignContent.NORMAL,
                new Size<>(new AvailableSpace.Definite(100.0f), new AvailableSpace.Definite(100.0f)),
                new SizeOptF32(100.0f, OptF32.NONE),
                columns,
                List.of(),
                List.of(),
                BASE_SIZE_ESTIMATE,
                false);

        // 1fr * 100px available = 100px
        assertEquals(100.0f, columns.get(1).baseSize);
    }

    @Test
    void stretchAlignExpandsAutoTracks() {
        List<GridTrack> columns = new ArrayList<>(List.of(gutter(), autoTrack(), gutter(), autoTrack(), gutter()));
        StubTree tree = new StubTree();

        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.INLINE,
                OptF32.NONE,
                OptF32.NONE,
                AlignContent.STRETCH,
                AlignContent.NORMAL,
                new Size<>(new AvailableSpace.Definite(100.0f), new AvailableSpace.Definite(100.0f)),
                new SizeOptF32(100.0f, OptF32.NONE),
                columns,
                List.of(),
                List.of(),
                BASE_SIZE_ESTIMATE,
                false);

        // 100px free split equally over the two auto tracks
        assertEquals(50.0f, columns.get(1).baseSize);
        assertEquals(50.0f, columns.get(3).baseSize);
    }

    @Test
    void baselineShimAppliedToAlignedItems() {
        List<GridTrack> columns = new ArrayList<>(List.of(gutter(), intrinsicTrack(), gutter()));
        GridItem first = itemOnFirstTrack();
        first.alignSelf = AlignItems.BASELINE;
        GridItem second = itemOnFirstTrack();
        second.node = 8L;
        second.column = ozLine(0, 1);
        second.columnIndexes = new Line<>(0, 2);
        second.alignSelf = AlignItems.BASELINE;
        StubTree tree = new StubTree();

        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.INLINE,
                OptF32.NONE,
                OptF32.NONE,
                AlignContent.NORMAL,
                AlignContent.NORMAL,
                new Size<>(new AvailableSpace.Definite(50.0f), new AvailableSpace.Definite(50.0f)),
                new SizeOptF32(50.0f, OptF32.NONE),
                columns,
                List.of(gutter(), fixedTrack(20.0f), gutter()),
                List.of(first, second),
                BASE_SIZE_ESTIMATE,
                true);

        // both items were laid out for baselines and carry the computed baseline
        // (contribution measurement below adds further calls, so assert >= 2)
        assertTrue(tree.calls >= 2);
        assertTrue(OptF32.isSome(first.baseline));
        assertTrue(OptF32.isSome(second.baseline));
        // equal baselines -> zero shim
        assertEquals(0.0f, first.baselineShim);
        assertEquals(0.0f, second.baselineShim);
    }

    @Test
    void gutterAlignmentAdjustmentWrittenToOtherAxisGutters() {
        List<GridTrack> columns = new ArrayList<>(List.of(gutter(), intrinsicTrack(), gutter()));
        // other axis (rows): [gutter, track, gutter, track, gutter] — indices 2 and 4
        // are touched by `step_by(2)` starting at 2 (upstream applies the adjustment
        // to the trailing gutter too — faithful port).
        List<GridTrack> rows = fiveBase10Tracks();
        GridItem item = itemOnFirstTrack();
        StubTree tree = new StubTree();

        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.INLINE,
                OptF32.NONE,
                OptF32.NONE,
                AlignContent.NORMAL,
                AlignContent.SPACE_BETWEEN,
                new Size<>(new AvailableSpace.Definite(50.0f), new AvailableSpace.Definite(100.0f)),
                new SizeOptF32(50.0f, 100.0f),
                columns,
                rows,
                List.of(item),
                BASE_SIZE_ESTIMATE,
                false);

        // free = 100 - 50 = 50; SpaceBetween weighted count = 1 -> adjustment 50
        assertEquals(50.0f, rows.get(2).contentAlignmentAdjustment);
        assertEquals(50.0f, rows.get(4).contentAlignmentAdjustment);
        assertEquals(0.0f, rows.get(0).contentAlignmentAdjustment);
    }
}
