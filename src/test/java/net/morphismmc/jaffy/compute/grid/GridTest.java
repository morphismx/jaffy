// JAFFY-UPSTREAM: src/compute/grid/mod.rs
// JAFFY-NOTE: behavior tests for upstream
// upstream — these cover the observable contract of DetailedGridTracksInfo /
// DetailedGridInfo / DetailedGridItemsInfo plus end-to-end computeGridLayout).
package net.morphismmc.jaffy.compute.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts.TrackRange;
import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.compute.grid.types.named.GridLineNames;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.StyleHelpers.SizeAvailableSpaceExt;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.grid.GridAreaAxis;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateComponent;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.TrackSizingFunction;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutGridContainer;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;

class GridTest {

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static GridTrack track(float offset, float baseSize) {
        GridTrack track = GridTrack.newGridTrack(
                MinTrackSizingFunction.length(baseSize), MaxTrackSizingFunction.length(baseSize));
        track.offset = offset;
        track.baseSize = baseSize;
        return track;
    }

    private static GridTrack collapsedTrack(float offset) {
        GridTrack track = track(offset, 0.0f);
        track.collapse();
        return track;
    }

    private static GridTrack gutter(float offset) {
        GridTrack gutter = GridTrack.gutter(LengthPercentage.ZERO);
        gutter.offset = offset;
        return gutter;
    }

    private static GridLineNames names(String... groups) {
        GridLineNames names = GridLineNames.withCapacity(4, groups.length + 1);
        for (String group : groups) {
            names.startLine();
            if (!group.isEmpty()) {
                for (String name : group.split(" ")) {
                    names.pushName(name);
                }
            }
        }
        return names;
    }

    private static TrackCounts counts(int negativeImplicit, int explicit, int positiveImplicit) {
        return new TrackCounts(negativeImplicit, explicit, positiveImplicit);
    }

    // -----------------------------------------------------------------
    // DetailedGridTracksInfo — positions extraction
    // -----------------------------------------------------------------

    @Test
    void positionsExtractTrackKindOnlyAndMergeCollapsedRuns() {
        // grid_tracks alternate gutter/track; only TRACKs contribute positions.
        List<GridTrack> tracks = List.of(
                gutter(0), // line 0
                track(0, 20), // track 0
                gutter(20), // line 1
                collapsedTrack(20), // collapsed track 1
                collapsedTrack(20), // collapsed track 2 (adjacent -> merges)
                gutter(20), // line 2
                track(20, 30), // track 3
                gutter(50), // line 3
                track(50, 10), // track 4
                gutter(60));
        DetailedGridTracksInfo info =
                DetailedGridTracksInfo.fromGridTracksAndTrackCount(counts(0, 5, 0), tracks, new GridLineNames());

        assertEquals(5, info.positions.size());
        assertEquals(new LineF32(0, 20), info.positions.get(0));
        assertEquals(new LineF32(20, 20), info.positions.get(1));
        assertEquals(new LineF32(20, 20), info.positions.get(2));
        assertEquals(new LineF32(20, 50), info.positions.get(3));
        assertEquals(new LineF32(50, 60), info.positions.get(4));
        // Adjacent collapsed tracks merge into a single range over positions 1..3
        assertEquals(List.of(new TrackRange(1, 3)), info.collapsedTracks);
        assertEquals(0, info.negativeImplicitTracks);
        assertEquals(5, info.explicitTracks);
        assertEquals(0, info.positiveImplicitTracks);
        assertTrue(OptF32.isNone(info.emptyAxisLine));
    }

    @Test
    void emptyAxisLineComesFromSingleGutterOffset() {
        DetailedGridTracksInfo info = DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(0, 0, 0), List.of(gutter(7.5f)), new GridLineNames());
        assertTrue(info.positions.isEmpty());
        assertEquals(7.5f, info.emptyAxisLine, 1e-6);

        // A completely empty track vector has no line either
        DetailedGridTracksInfo empty =
                DetailedGridTracksInfo.fromGridTracksAndTrackCount(counts(0, 0, 0), List.of(), new GridLineNames());
        assertTrue(OptF32.isNone(empty.emptyAxisLine));
    }

    @Test
    void separateCollapsedRunsStaySeparate() {
        List<GridTrack> tracks = List.of(
                gutter(0),
                collapsedTrack(0),
                gutter(0),
                track(0, 10), // non-collapsed gap
                gutter(10),
                collapsedTrack(10),
                gutter(10));
        DetailedGridTracksInfo info =
                DetailedGridTracksInfo.fromGridTracksAndTrackCount(counts(0, 3, 0), tracks, new GridLineNames());
        assertEquals(List.of(new TrackRange(0, 1), new TrackRange(2, 3)), info.collapsedTracks);
    }

    // -----------------------------------------------------------------
    // namesForLine / iterLineNames
    // -----------------------------------------------------------------

    @Test
    void namesForLineOffsetsByNegativeImplicitTracks() {
        GridLineNames names = names("a b", "c");
        DetailedGridTracksInfo info = DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(2, 1, 0),
                List.of(
                        gutter(0),
                        track(0, 5), // negative implicit track
                        gutter(5),
                        track(5, 5), // negative implicit track
                        gutter(10),
                        track(10, 20), // explicit track
                        gutter(30)),
                names);
        // line 0/1 are implicit -> no names; line 2 -> explicit line 0
        assertTrue(info.namesForLine(0).isEmpty());
        assertTrue(info.namesForLine(1).isEmpty());
        assertEquals(List.of("a", "b"), info.namesForLine(2));
        assertEquals(List.of("c"), info.namesForLine(3));
        assertTrue(info.namesForLine(4).isEmpty());
    }

    @Test
    void iterLineNamesPadsImplicitLines() {
        GridLineNames names = names("a", "b");
        DetailedGridTracksInfo info = DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(1, 1, 1),
                List.of(
                        gutter(0),
                        track(0, 10), // negative implicit
                        gutter(10),
                        track(10, 20), // explicit
                        gutter(30),
                        track(30, 5), // positive implicit
                        gutter(35)),
                names);
        List<List<String>> iterated = new java.util.ArrayList<>();
        info.iterLineNames().forEachRemaining(iterated::add);
        // 4 lines total: 1 leading implicit + 2 explicit + 1 trailing implicit
        assertEquals(4, iterated.size());
        assertEquals(List.of(), iterated.get(0));
        assertEquals(List.of("a"), iterated.get(1));
        assertEquals(List.of("b"), iterated.get(2));
        assertEquals(List.of(), iterated.get(3));
    }

    // -----------------------------------------------------------------
    // write_track_list / to_track_list_string
    // -----------------------------------------------------------------

    @Test
    void emptyAxisSerializesToNone() {
        DetailedGridTracksInfo info = DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(0, 0, 0), List.of(gutter(3)), new GridLineNames());
        assertEquals("none", info.toTrackListString(1.0f));
    }

    @Test
    void trackListSerializesSizesAndNames() {
        GridLineNames names = names("a b", "", "z");
        DetailedGridTracksInfo info = DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(0, 2, 0), List.of(gutter(0), track(0, 20), gutter(20), track(20, 30), gutter(50)), names);
        // line 0 -> [a b], track 20px; line 1 -> empty group; track 30px; line 2 -> [z]
        assertEquals("[a b] 20px 30px [z]", info.toTrackListString(1.0f));
        // zoom divides the sizes
        assertEquals("[a b] 10px 15px [z]", info.toTrackListString(2.0f));
    }

    // -----------------------------------------------------------------
    // resolveAbsoluteGridAxis / absolute_track_edge
    // -----------------------------------------------------------------

    private static DetailedGridTracksInfo twoTrackInfo() {
        return DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(0, 2, 0),
                List.of(gutter(0), track(0, 20), gutter(20), track(20, 30), gutter(50)),
                new GridLineNames());
    }

    private static DetailedGridInfo infoWith(DetailedGridTracksInfo rows, DetailedGridTracksInfo columns) {
        return new DetailedGridInfo(rows, columns, List.of(), Map.of());
    }

    @Test
    void resolvesInGridPlacementToTrackEdges() {
        DetailedGridTracksInfo info = twoTrackInfo();
        // grid-column: 1 / 3 spans both tracks (LTR, not reversed)
        LineF32 line = info.resolveAbsoluteGridAxis(
                new Line<>(GridPlacement.fromLineIndex(1), GridPlacement.fromLineIndex(3)),
                Map.of(),
                GridAreaAxis.COLUMN,
                5f,
                60f,
                false);
        assertEquals(0f, line.start(), 1e-6);
        assertEquals(50f, line.end(), 1e-6);
    }

    @Test
    void outOfGridLineResolvesToPaddingEdges() {
        DetailedGridTracksInfo info = twoTrackInfo();
        // Line 4 exceeds the grid (max line 2): treated as auto both ends
        LineF32 line = info.resolveAbsoluteGridAxis(
                new Line<>(GridPlacement.fromLineIndex(4), GridPlacement.fromLineIndex(5)),
                Map.of(),
                GridAreaAxis.COLUMN,
                5f,
                60f,
                false);
        assertEquals(5f, line.start(), 1e-6);
        assertEquals(60f, line.end(), 1e-6);
    }

    @Test
    void autoPlacementResolvesToPaddingEdges() {
        DetailedGridTracksInfo info = twoTrackInfo();
        LineF32 line = info.resolveAbsoluteGridAxis(
                new Line<>(GridPlacement.AUTO, GridPlacement.AUTO), Map.of(), GridAreaAxis.COLUMN, 5f, 60f, false);
        assertEquals(5f, line.start(), 1e-6);
        assertEquals(60f, line.end(), 1e-6);
    }

    @Test
    void reversedAxisAnchorsAtLogicalStartAndCanSwap() {
        DetailedGridTracksInfo info = twoTrackInfo();
        // is_reversed (RTL): a line used as an inline-start edge resolves to the
        // physical end of the track after it; edges are normalized start<=end.
        LineF32 line = info.resolveAbsoluteGridAxis(
                new Line<>(GridPlacement.fromLineIndex(1), GridPlacement.fromLineIndex(3)),
                Map.of(),
                GridAreaAxis.COLUMN,
                5f,
                60f,
                true);
        // start edge -> absoluteTrackEdge(0, is_start=true, is_reversed=true):
        //   isStart != isReversed -> false -> track.end = positions[0].end = 20
        // end edge -> line=1 -> absoluteTrackEdge(1,false,true): isStart != isReversed
        //   -> true -> track.start = positions[1].start = 20
        // reversed: Line { start: min(20,20), end: 20 }
        assertEquals(20f, line.start(), 1e-6);
        assertEquals(20f, line.end(), 1e-6);
    }

    @Test
    void collapsedRunAnchorsZeroSizedArea() {
        // One collapsed run covering positions 0..2 plus one real track
        List<GridTrack> tracks =
                List.of(gutter(0), collapsedTrack(10), collapsedTrack(10), gutter(10), track(10, 40), gutter(50));
        DetailedGridTracksInfo info =
                DetailedGridTracksInfo.fromGridTracksAndTrackCount(counts(0, 3, 0), tracks, new GridLineNames());
        // positions = [(10,10), (10,10), (10,50)], collapsed [0..2)
        // Line 1 (inside collapsed run) as start edge -> adjacent = range.end=2 ->
        // positions[2]; is_start && !reversed -> track.start = 10
        LineF32 line = info.resolveAbsoluteGridAxis(
                new Line<>(GridPlacement.fromLineIndex(2), GridPlacement.fromLineIndex(3)),
                Map.of(),
                GridAreaAxis.COLUMN,
                0f,
                50f,
                false);
        // start: line 1 -> idx 2 -> positions.get(2)? idx=2 < len=3 ->
        //   absoluteTrackEdge(2, true, false): range 0..2 doesn't contain 2 ->
        //   positions[2], isStart -> start = 10
        // end: line 2 -> idx 4 -> line=3 >= len -> positions.first().start = 10
        assertEquals(10f, line.start(), 1e-6);
        assertEquals(10f, line.end(), 1e-6);
    }

    // -----------------------------------------------------------------
    // DetailedGridInfo — itemGridArea / resolveAbsoluteGridArea
    // -----------------------------------------------------------------

    @Test
    void itemGridAreaComputesBoundingRect() {
        DetailedGridTracksInfo columns = twoTrackInfo();
        DetailedGridTracksInfo rows = DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(0, 1, 0), List.of(gutter(5), track(5, 20), gutter(25)), new GridLineNames());
        DetailedGridItemsInfo item = new DetailedGridItemsInfo(1, 2, 1, 3);
        DetailedGridInfo info = new DetailedGridInfo(rows, columns, List.of(item), Map.of());

        DetailedGridInfo.ItemGridArea area = info.itemGridArea(0);
        assertNotNull(area);
        // col positions (0,20) and (20,50) -> left=min(0,20)=0, right=max(20,50)=50
        assertEquals(0f, area.position().x(), 1e-6);
        assertEquals(5f, area.position().y(), 1e-6);
        assertEquals(50f, area.size().width(), 1e-6);
        assertEquals(20f, area.size().height(), 1e-6);

        assertNull(info.itemGridArea(1));
        assertNull(info.itemGridArea(-1));
    }

    @Test
    void resolveAbsoluteGridAreaDispatchesBothAxes() {
        DetailedGridTracksInfo columns = twoTrackInfo();
        DetailedGridTracksInfo rows = DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                counts(0, 1, 0), List.of(gutter(5), track(5, 20), gutter(25)), new GridLineNames());
        DetailedGridInfo info = infoWith(rows, columns);

        RectF32 area = info.resolveAbsoluteGridArea(
                new Line<>(GridPlacement.fromLineIndex(1), GridPlacement.fromLineIndex(2)),
                new Line<>(GridPlacement.fromLineIndex(1), GridPlacement.fromLineIndex(3)),
                Direction.LTR,
                new RectF32(5, 60, 5, 30));
        // columns: lines 0..2 -> positions[0].start=0 .. positions[1].end=50
        // rows: line 0..1 -> positions[0].start=5 .. positions[0].end=25
        assertEquals(new RectF32(0, 50, 5, 25), area);
    }

    // -----------------------------------------------------------------
    // DetailedGridItemsInfo::from_grid_item — 1-indexed line conversion
    // -----------------------------------------------------------------

    @Test
    void fromGridItemConvertsTrackIndexesToOneIndexedLines() {
        GridItem item = GridItem.newWithStyleAndOrder(3L, Style.DEFAULT, AlignItems.NORMAL, AlignItems.NORMAL, 0);
        item.rowIndexes = new Line<>(0, 2);
        item.columnIndexes = new Line<>(2, 6);
        DetailedGridItemsInfo info = DetailedGridItemsInfo.fromGridItem(item);
        // index / 2 + 1
        assertEquals(1, info.rowStart());
        assertEquals(2, info.rowEnd());
        assertEquals(2, info.columnStart());
        assertEquals(4, info.columnEnd());
    }

    // -----------------------------------------------------------------
    // computeGridLayout — end to end
    // -----------------------------------------------------------------

    /// Stub tree: container style + a list of identical-style children.
    private static final class StubTree implements LayoutGridContainer {
        private final Style containerStyle;
        private final Style childStyle;
        private final long[] children;
        private final LayoutOutput childOutput;
        DetailedGridInfo detailedInfo;

        StubTree(Style containerStyle, long[] children, Style childStyle, LayoutOutput childOutput) {
            this.containerStyle = containerStyle;
            this.childStyle = childStyle;
            this.children = children;
            this.childOutput = childOutput;
        }

        @Override
        public LongIterator childIds(long parentNodeId) {
            return LongIterators.wrap(children);
        }

        @Override
        public int childCount(long parentNodeId) {
            return children.length;
        }

        @Override
        public long getChildId(long parentNodeId, int childIndex) {
            return children[childIndex];
        }

        @Override
        public Style getCoreContainerStyle(long nodeId) {
            return containerStyle;
        }

        @Override
        public Style getGridContainerStyle(long nodeId) {
            return containerStyle;
        }

        @Override
        public Style getGridChildStyle(long childNodeId) {
            return childStyle;
        }

        @Override
        public void setUnroundedLayout(long nodeId, Layout layout) {}

        @Override
        public void setDetailedGridInfo(long nodeId, DetailedGridInfo info) {
            detailedInfo = info;
        }

        @Override
        public LayoutOutput computeChildLayout(long nodeId, LayoutInput inputs) {
            return childOutput;
        }
    }

    private static Style sizeStyle(double w, double h) {
        return Style.DEFAULT.withSize(new Size<>(Dimension.length((float) w), Dimension.length((float) h)));
    }

    private static LayoutInput performLayoutInput() {
        return new LayoutInput(
                RunMode.PERFORM_LAYOUT,
                SizingMode.INHERENT_SIZE,
                RequestedAxis.BOTH,
                SizeOptF32.NONE,
                SizeBool.TRUE,
                SizeOptF32.NONE,
                SizeAvailableSpaceExt.maxContent(),
                LineBool.FALSE);
    }

    @Test
    void computeGridLayoutEmptyGridReturnsContainerSize() {
        StubTree tree = new StubTree(
                sizeStyle(100, 80), new long[] {}, Style.DEFAULT, LayoutOutput.fromOuterSize(new SizeF32(10, 20)));
        LayoutOutput output = Grid.computeGridLayout(tree, 0L, performLayoutInput());
        assertEquals(new SizeF32(100, 80), output.size());
        // No items -> no baseline
        assertTrue(OptF32.isNone(output.baselines().first()));
        assertNotNull(output.oofPositioningArea());
        // Detailed info recorded even for an empty grid
        assertNotNull(tree.detailedInfo);
        assertTrue(tree.detailedInfo.items.isEmpty());
    }

    @Test
    void computeGridLayoutSizesImplicitGridFromChild() {
        // One default-styled child; implicit 1x1 grid; container indefinite.
        StubTree tree = new StubTree(
                Style.DEFAULT, new long[] {1L}, Style.DEFAULT, LayoutOutput.fromOuterSize(new SizeF32(10, 20)));
        LayoutOutput output = Grid.computeGridLayout(tree, 0L, performLayoutInput());
        assertEquals(new SizeF32(10, 20), output.size());
        assertNotNull(tree.detailedInfo);
        assertEquals(1, tree.detailedInfo.items.size());
        assertEquals(new DetailedGridItemsInfo(1, 2, 1, 2), tree.detailedInfo.items.get(0));
        assertEquals(1, tree.detailedInfo.columns.positions.size());
        assertEquals(1, tree.detailedInfo.rows.positions.size());
    }

    @Test
    void computeGridLayoutHonoursComputeSizeHorizontalShortCircuit() {
        // Explicit fixed column template: column sizing alone determines the width;
        // the horizontal short-circuit returns before row sizing with height 0.
        Style container = Style.DEFAULT.withGridTemplateColumns(
                List.of(GridTemplateComponent.single(TrackSizingFunction.fromLength(30))));
        StubTree tree = new StubTree(
                container, new long[] {1L}, Style.DEFAULT, LayoutOutput.fromOuterSize(new SizeF32(10, 20)));
        LayoutOutput output = Grid.computeGridLayout(
                tree,
                0L,
                new LayoutInput(
                        RunMode.COMPUTE_SIZE,
                        SizingMode.INHERENT_SIZE,
                        RequestedAxis.HORIZONTAL,
                        SizeOptF32.NONE,
                        SizeBool.TRUE,
                        SizeOptF32.NONE,
                        SizeAvailableSpaceExt.maxContent(),
                        LineBool.FALSE));
        assertEquals(new SizeF32(30, 0), output.size());
    }
}
