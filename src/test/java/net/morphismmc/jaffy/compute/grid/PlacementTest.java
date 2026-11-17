// JAFFY: port of upstream src/compute/grid/placement.rs `mod tests`
// (mod test_placement_algorithm + 2 free tests).
// `line(i)` -> GridPlacement.fromLineIndex; `span(n)` -> GridPlacement.fromSpan;
// `auto()` -> GridPlacement.AUTO.
// `Vec<(usize, Style, ExpectedPlacement)>` children -> List<TestChild> record;
// `NodeId::from(*id)` -> item.node (long index);
// `(*expected).into_oz()` -> TestHelpers.intoOz / OzPlacement record equality.
package net.morphismmc.jaffy.compute.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.cell_occupancy.CellOccupancyMatrix;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;
import net.morphismmc.jaffy.compute.grid.types.named.NamedLineResolver;
import net.morphismmc.jaffy.compute.grid.util.TestHelpers;
import net.morphismmc.jaffy.compute.grid.util.TestHelpers.OzPlacement;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.InBothAbsAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.GridPlacement;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PlacementTest {

    /// `(usize, Style, ExpectedPlacement)` — index, style, expected oz placement
    private record TestChild(int index, Style style, OzPlacement expected) {}

    private static GridPlacement line(int i) {
        return GridPlacement.fromLineIndex(i);
    }

    private static GridPlacement span(int n) {
        return GridPlacement.fromSpan(n);
    }

    private static Style autoChild() {
        return TestHelpers.intoGridChild(
                GridPlacement.AUTO, GridPlacement.AUTO, GridPlacement.AUTO, GridPlacement.AUTO);
    }

    @Nested
    class TestPlacementAlgorithm {

        void placementTestRunner(
                int explicitColCount,
                int explicitRowCount,
                List<TestChild> children,
                TrackCounts expectedColCounts,
                TrackCounts expectedRowCounts,
                GridAutoFlow flow) {
            // Setup test
            NamedLineResolver nameResolver = NamedLineResolver.newNamedLineResolver(Style.DEFAULT, 0, 0);
            nameResolver.setExplicitColumnCount(explicitColCount);
            nameResolver.setExplicitRowCount(explicitRowCount);
            List<Style> childStyles = new ArrayList<>(children.size());
            List<TestHelpers.IdStyle> idStyles = new ArrayList<>(children.size());
            for (TestChild child : children) {
                childStyles.add(child.style());
                idStyles.add(new TestHelpers.IdStyle(child.index(), child.style()));
            }
            List<GridItem> items = TestHelpers.gridItems(idStyles);
            List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements =
                    TestHelpers.resolveNamedLines(childStyles, nameResolver, explicitColCount, explicitRowCount);
            InBothAbsAxis<TrackCounts> estimatedSizes =
                    ImplicitGrid.computeGridSizeEstimate(explicitColCount, explicitRowCount, placements);
            CellOccupancyMatrix cellOccupancyMatrix =
                    CellOccupancyMatrix.withTrackCounts(estimatedSizes.horizontal(), estimatedSizes.vertical());

            // Run placement algorithm
            Placement.placeGridItems(cellOccupancyMatrix, items, placements, flow);

            // Assert that each item has been placed in the right location (items are in the same order as children)
            assertEquals(children.size(), items.size());
            for (int idx = 0; idx < children.size(); idx++) {
                TestChild child = children.get(idx);
                GridItem item = items.get(idx);
                assertEquals(child.index(), item.node);
                OzPlacement actualPlacement =
                        new OzPlacement(item.column.start(), item.column.end(), item.row.start(), item.row.end());
                assertEquals(child.expected(), actualPlacement, "Item " + idx + " (0-indexed)");
            }

            // Assert that the correct number of implicit rows have been generated
            assertEquals(expectedRowCounts, cellOccupancyMatrix.trackCounts(AbsoluteAxis.VERTICAL), "row track counts");
            assertEquals(
                    expectedColCounts, cellOccupancyMatrix.trackCounts(AbsoluteAxis.HORIZONTAL), "column track counts");
        }

        @Test
        void onlyFixedPlacement() {
            GridAutoFlow flow = GridAutoFlow.ROW;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            // node, style (grid coords), expected_placement (oz coords)
            List<TestChild> children = List.of(
                    new TestChild(
                            1,
                            TestHelpers.intoGridChild(line(1), GridPlacement.AUTO, line(1), GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 1, 0, 1)),
                    new TestChild(
                            2,
                            TestHelpers.intoGridChild(line(-4), GridPlacement.AUTO, line(-3), GridPlacement.AUTO),
                            TestHelpers.intoOz(-1, 0, 0, 1)),
                    new TestChild(
                            3,
                            TestHelpers.intoGridChild(line(-3), GridPlacement.AUTO, line(-4), GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 1, -1, 0)),
                    new TestChild(
                            4,
                            TestHelpers.intoGridChild(line(3), span(2), line(5), GridPlacement.AUTO),
                            TestHelpers.intoOz(2, 4, 4, 5)));
            TrackCounts expectedCols = new TrackCounts(1, 2, 2);
            TrackCounts expectedRows = new TrackCounts(1, 2, 3);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void placementSpanningOrigin() {
            GridAutoFlow flow = GridAutoFlow.ROW;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            List<TestChild> children = List.of(
                    new TestChild(
                            1,
                            TestHelpers.intoGridChild(line(-1), line(-1), line(-1), line(-1)),
                            TestHelpers.intoOz(2, 3, 2, 3)),
                    new TestChild(
                            2,
                            TestHelpers.intoGridChild(line(-1), span(2), line(-1), span(2)),
                            TestHelpers.intoOz(2, 4, 2, 4)),
                    new TestChild(
                            3,
                            TestHelpers.intoGridChild(line(-4), line(-4), line(-4), line(-4)),
                            TestHelpers.intoOz(-1, 0, -1, 0)),
                    new TestChild(
                            4,
                            TestHelpers.intoGridChild(line(-4), span(2), line(-4), span(2)),
                            TestHelpers.intoOz(-1, 1, -1, 1)));
            TrackCounts expectedCols = new TrackCounts(1, 2, 2);
            TrackCounts expectedRows = new TrackCounts(1, 2, 2);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void onlyAutoPlacementRowFlow() {
            GridAutoFlow flow = GridAutoFlow.ROW;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            Style autoChild = autoChild();
            List<TestChild> children = List.of(
                    new TestChild(1, autoChild, TestHelpers.intoOz(0, 1, 0, 1)),
                    new TestChild(2, autoChild, TestHelpers.intoOz(1, 2, 0, 1)),
                    new TestChild(3, autoChild, TestHelpers.intoOz(0, 1, 1, 2)),
                    new TestChild(4, autoChild, TestHelpers.intoOz(1, 2, 1, 2)),
                    new TestChild(5, autoChild, TestHelpers.intoOz(0, 1, 2, 3)),
                    new TestChild(6, autoChild, TestHelpers.intoOz(1, 2, 2, 3)),
                    new TestChild(7, autoChild, TestHelpers.intoOz(0, 1, 3, 4)),
                    new TestChild(8, autoChild, TestHelpers.intoOz(1, 2, 3, 4)));
            TrackCounts expectedCols = new TrackCounts(0, 2, 0);
            TrackCounts expectedRows = new TrackCounts(0, 2, 2);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void onlyAutoPlacementColumnFlow() {
            GridAutoFlow flow = GridAutoFlow.COLUMN;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            Style autoChild = autoChild();
            List<TestChild> children = List.of(
                    new TestChild(1, autoChild, TestHelpers.intoOz(0, 1, 0, 1)),
                    new TestChild(2, autoChild, TestHelpers.intoOz(0, 1, 1, 2)),
                    new TestChild(3, autoChild, TestHelpers.intoOz(1, 2, 0, 1)),
                    new TestChild(4, autoChild, TestHelpers.intoOz(1, 2, 1, 2)),
                    new TestChild(5, autoChild, TestHelpers.intoOz(2, 3, 0, 1)),
                    new TestChild(6, autoChild, TestHelpers.intoOz(2, 3, 1, 2)),
                    new TestChild(7, autoChild, TestHelpers.intoOz(3, 4, 0, 1)),
                    new TestChild(8, autoChild, TestHelpers.intoOz(3, 4, 1, 2)));
            TrackCounts expectedCols = new TrackCounts(0, 2, 2);
            TrackCounts expectedRows = new TrackCounts(0, 2, 0);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void oversizedItem() {
            GridAutoFlow flow = GridAutoFlow.ROW;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            List<TestChild> children = List.of(new TestChild(
                    1,
                    TestHelpers.intoGridChild(span(5), GridPlacement.AUTO, GridPlacement.AUTO, GridPlacement.AUTO),
                    TestHelpers.intoOz(0, 5, 0, 1)));
            TrackCounts expectedCols = new TrackCounts(0, 2, 3);
            TrackCounts expectedRows = new TrackCounts(0, 2, 0);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void fixedInSecondaryAxis() {
            GridAutoFlow flow = GridAutoFlow.ROW;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            List<TestChild> children = List.of(
                    new TestChild(
                            1,
                            TestHelpers.intoGridChild(span(2), GridPlacement.AUTO, line(1), GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 2, 0, 1)),
                    new TestChild(
                            2,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, GridPlacement.AUTO, line(2), GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 1, 1, 2)),
                    new TestChild(
                            3,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, GridPlacement.AUTO, line(1), GridPlacement.AUTO),
                            TestHelpers.intoOz(2, 3, 0, 1)),
                    new TestChild(
                            4,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, GridPlacement.AUTO, line(4), GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 1, 3, 4)));
            TrackCounts expectedCols = new TrackCounts(0, 2, 1);
            TrackCounts expectedRows = new TrackCounts(0, 2, 2);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void definiteInSecondaryAxisWithFullyDefiniteNegative() {
            GridAutoFlow flow = GridAutoFlow.ROW;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            List<TestChild> children = List.of(
                    new TestChild(
                            2,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, GridPlacement.AUTO, line(2), GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 1, 1, 2)),
                    new TestChild(
                            1,
                            TestHelpers.intoGridChild(line(-4), GridPlacement.AUTO, line(2), GridPlacement.AUTO),
                            TestHelpers.intoOz(-1, 0, 1, 2)),
                    new TestChild(
                            3,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, GridPlacement.AUTO, line(1), GridPlacement.AUTO),
                            TestHelpers.intoOz(-1, 0, 0, 1)));
            TrackCounts expectedCols = new TrackCounts(1, 2, 0);
            TrackCounts expectedRows = new TrackCounts(0, 2, 0);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void densePackingAlgorithm() {
            GridAutoFlow flow = GridAutoFlow.ROW_DENSE;
            int explicitColCount = 4;
            int explicitRowCount = 4;
            List<TestChild> children = List.of(
                    // Definitely positioned in column 2
                    new TestChild(
                            1,
                            TestHelpers.intoGridChild(line(2), GridPlacement.AUTO, line(1), GridPlacement.AUTO),
                            TestHelpers.intoOz(1, 2, 0, 1)),
                    // Spans 2 columns, so positioned after item 1
                    new TestChild(
                            2,
                            TestHelpers.intoGridChild(
                                    span(2), GridPlacement.AUTO, GridPlacement.AUTO, GridPlacement.AUTO),
                            TestHelpers.intoOz(2, 4, 0, 1)),
                    // Spans 1 column, so should be positioned before item 1
                    new TestChild(3, autoChild(), TestHelpers.intoOz(0, 1, 0, 1)));
            TrackCounts expectedCols = new TrackCounts(0, 4, 0);
            TrackCounts expectedRows = new TrackCounts(0, 4, 0);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void sparsePackingAlgorithm() {
            GridAutoFlow flow = GridAutoFlow.ROW;
            int explicitColCount = 4;
            int explicitRowCount = 4;
            List<TestChild> children = List.of(
                    // Width 3
                    new TestChild(
                            1,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, span(3), GridPlacement.AUTO, GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 3, 0, 1)),
                    // Width 3 (wraps to next row)
                    new TestChild(
                            2,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, span(3), GridPlacement.AUTO, GridPlacement.AUTO),
                            TestHelpers.intoOz(0, 3, 1, 2)),
                    // Width 1 (uses second row as we're already on it)
                    new TestChild(
                            3,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, span(1), GridPlacement.AUTO, GridPlacement.AUTO),
                            TestHelpers.intoOz(3, 4, 1, 2)));
            TrackCounts expectedCols = new TrackCounts(0, 4, 0);
            TrackCounts expectedRows = new TrackCounts(0, 4, 0);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void autoPlacementInNegativeTracks() {
            GridAutoFlow flow = GridAutoFlow.ROW_DENSE;
            int explicitColCount = 2;
            int explicitRowCount = 2;
            List<TestChild> children = List.of(
                    // Row 1. Definitely positioned in column -2
                    new TestChild(
                            1,
                            TestHelpers.intoGridChild(line(-5), GridPlacement.AUTO, line(1), GridPlacement.AUTO),
                            TestHelpers.intoOz(-2, -1, 0, 1)),
                    // Row 2. Auto positioned in column -2
                    new TestChild(
                            2,
                            TestHelpers.intoGridChild(
                                    GridPlacement.AUTO, GridPlacement.AUTO, line(2), GridPlacement.AUTO),
                            TestHelpers.intoOz(-2, -1, 1, 2)),
                    // Row 1. Auto positioned in column -1
                    new TestChild(3, autoChild(), TestHelpers.intoOz(-1, 0, 0, 1)));
            TrackCounts expectedCols = new TrackCounts(2, 2, 0);
            TrackCounts expectedRows = new TrackCounts(0, 2, 0);
            placementTestRunner(explicitColCount, explicitRowCount, children, expectedCols, expectedRows, flow);
        }

        @Test
        void overlargePlacementIsClamped() {
            int explicitColCount = 9000;
            int explicitRowCount = 0;
            Style style =
                    TestHelpers.intoGridChild(line(-19005), GridPlacement.AUTO, GridPlacement.AUTO, GridPlacement.AUTO);
            List<Style> childStyles = List.of(style);
            NamedLineResolver nameResolver = NamedLineResolver.newNamedLineResolver(Style.DEFAULT, 0, 0);
            nameResolver.setExplicitColumnCount(explicitColCount);
            nameResolver.setExplicitRowCount(explicitRowCount);
            // upstream: [(0usize, style)] — node id 0
            List<GridItem> items = TestHelpers.gridItems(List.of(new TestHelpers.IdStyle(0, style)));
            List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements =
                    TestHelpers.resolveNamedLines(childStyles, nameResolver, explicitColCount, explicitRowCount);
            InBothAbsAxis<TrackCounts> estimatedSizes =
                    ImplicitGrid.computeGridSizeEstimate(explicitColCount, explicitRowCount, placements);
            CellOccupancyMatrix cellOccupancyMatrix =
                    CellOccupancyMatrix.withTrackCounts(estimatedSizes.horizontal(), estimatedSizes.vertical());
            Placement.placeGridItems(cellOccupancyMatrix, items, placements, GridAutoFlow.ROW);
            assertEquals(new Line<>(new OriginZeroLine(-10000), new OriginZeroLine(-9999)), items.get(0).column);
        }
    }

    @Test
    void autoPlacementCursorSaturatesAtIntegerBounds() {
        assertEquals(
                new OriginZeroLine(Short.MAX_VALUE), Placement.advancePosition(new OriginZeroLine(Short.MAX_VALUE)));
    }

    @Test
    void indefiniteSpansSaturateAtIntegerBounds() {
        assertEquals(
                new Line<>(new OriginZeroLine(Short.MAX_VALUE), new OriginZeroLine(Short.MAX_VALUE)),
                Placement.resolveIndefiniteGridSpan(new OriginZeroLine(Short.MAX_VALUE), 1));
    }
}
