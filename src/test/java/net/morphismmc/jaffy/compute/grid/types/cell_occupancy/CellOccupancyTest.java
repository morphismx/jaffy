// JAFFY-UPSTREAM: src/compute/grid/types/cell_occupancy.rs
// JAFFY-NOTE: `mod tests`
// `Range<i16>` -> start/end ints; `Option` -> @Nullable; `SmallVec::spilled()`
// has no Java equivalent (ArrayList has no inline capacity) — the semantic
// content of `!spilled()` is "at most 2 intervals remain after merging".
package net.morphismmc.jaffy.compute.grid.types.cell_occupancy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.just.math.OptI16;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CellOccupancyTest {

    private static OccupiedInterval interval(int start, int end, CellOccupancyState state) {
        return new OccupiedInterval(start, end, state);
    }

    private static Line<OriginZeroLine> line(int start, int end) {
        return new Line<>(new OriginZeroLine(start), new OriginZeroLine(end));
    }

    @Nested
    class TrackIntervalsTests {

        @Test
        void paintMergesTouchingSameStateIntervals() {
            TrackIntervals track = new TrackIntervals();
            track.paint(0, 2, CellOccupancyState.AUTO_PLACED);
            track.paint(4, 6, CellOccupancyState.AUTO_PLACED);
            track.paint(2, 4, CellOccupancyState.AUTO_PLACED);
            assertEquals(List.of(interval(0, 6, CellOccupancyState.AUTO_PLACED)), track.intervals);
        }

        @Test
        void paintDoesNotMergeDifferentStates() {
            TrackIntervals track = new TrackIntervals();
            track.paint(0, 2, CellOccupancyState.AUTO_PLACED);
            track.paint(2, 4, CellOccupancyState.DEFINITELY_PLACED);
            assertEquals(
                    List.of(
                            interval(0, 2, CellOccupancyState.AUTO_PLACED),
                            interval(2, 4, CellOccupancyState.DEFINITELY_PLACED)),
                    track.intervals);
        }

        @Test
        void paintOverwritesOverlappedCells() {
            TrackIntervals track = new TrackIntervals();
            track.paint(0, 6, CellOccupancyState.AUTO_PLACED);
            track.paint(2, 4, CellOccupancyState.DEFINITELY_PLACED);
            assertEquals(
                    List.of(
                            interval(0, 2, CellOccupancyState.AUTO_PLACED),
                            interval(2, 4, CellOccupancyState.DEFINITELY_PLACED),
                            interval(4, 6, CellOccupancyState.AUTO_PLACED)),
                    track.intervals);

            // Painting over everything replaces all intervals
            track.paint(-1, 7, CellOccupancyState.AUTO_PLACED);
            assertEquals(List.of(interval(-1, 7, CellOccupancyState.AUTO_PLACED)), track.intervals);
        }

        @Test
        void paintOverwritesMultipleIntervals() {
            TrackIntervals track = new TrackIntervals();
            track.paint(0, 2, CellOccupancyState.AUTO_PLACED);
            track.paint(3, 5, CellOccupancyState.DEFINITELY_PLACED);
            track.paint(6, 8, CellOccupancyState.AUTO_PLACED);
            track.paint(1, 7, CellOccupancyState.DEFINITELY_PLACED);
            assertEquals(
                    List.of(
                            interval(0, 1, CellOccupancyState.AUTO_PLACED),
                            interval(1, 7, CellOccupancyState.DEFINITELY_PLACED),
                            interval(7, 8, CellOccupancyState.AUTO_PLACED)),
                    track.intervals);
        }

        @Test
        void collisionExtentFindsExtremalOccupiedCell() {
            TrackIntervals track = new TrackIntervals();
            track.paint(2, 4, CellOccupancyState.AUTO_PLACED);
            track.paint(6, 8, CellOccupancyState.DEFINITELY_PLACED);
            // The last cell of the last overlapping interval
            assertEquals(7, track.collisionExtent(0, 10));
            assertEquals(7, track.collisionExtent(0, 7));
            assertEquals(3, track.collisionExtent(0, 6));
            assertEquals(OptI16.NONE, track.collisionExtent(4, 6));
        }

        @Test
        void stateAtDistinguishesOccupiedIntervalsAndGaps() {
            TrackIntervals track = new TrackIntervals();
            track.paint(0, 2, CellOccupancyState.DEFINITELY_PLACED);
            track.paint(2, 4, CellOccupancyState.AUTO_PLACED);
            track.paint(6, 8, CellOccupancyState.AUTO_PLACED);
            track.paint(8, 9, CellOccupancyState.DEFINITELY_PLACED);
            assertEquals(CellOccupancyState.DEFINITELY_PLACED, track.stateAt(0));
            assertEquals(CellOccupancyState.AUTO_PLACED, track.stateAt(3));
            assertEquals(CellOccupancyState.UNOCCUPIED, track.stateAt(5));
            assertEquals(CellOccupancyState.AUTO_PLACED, track.stateAt(7));
            assertEquals(CellOccupancyState.DEFINITELY_PLACED, track.stateAt(8));
        }

        @Test
        void definitelyPlacedOverwriteHidesAutoPlacedCells() {
            TrackIntervals track = new TrackIntervals();
            track.paint(0, 4, CellOccupancyState.AUTO_PLACED);
            track.paint(2, 4, CellOccupancyState.DEFINITELY_PLACED);
            assertEquals(CellOccupancyState.AUTO_PLACED, track.stateAt(1));
            assertEquals(CellOccupancyState.DEFINITELY_PLACED, track.stateAt(2));
            assertEquals(CellOccupancyState.DEFINITELY_PLACED, track.stateAt(3));
        }
    }

    @Nested
    class CellOccupancyMatrixTests {

        @Test
        void negativeExpansionPreservesOccupancy() {
            CellOccupancyMatrix matrix =
                    CellOccupancyMatrix.withTrackCounts(TrackCounts.fromRaw(0, 2, 0), TrackCounts.fromRaw(0, 2, 0));
            matrix.markAreaAs(AbsoluteAxis.HORIZONTAL, line(0, 1), line(0, 1), CellOccupancyState.AUTO_PLACED);
            // Expand by marking an area in negative tracks
            matrix.markAreaAs(AbsoluteAxis.HORIZONTAL, line(-2, -1), line(-1, 0), CellOccupancyState.AUTO_PLACED);

            assertEquals(TrackCounts.fromRaw(2, 2, 0), matrix.trackCounts(AbsoluteAxis.HORIZONTAL));
            assertEquals(TrackCounts.fromRaw(1, 2, 0), matrix.trackCounts(AbsoluteAxis.VERTICAL));

            // Original cell still occupied at the same OriginZero coordinates
            assertFalse(matrix.lineAreaIsUnoccupied(AbsoluteAxis.HORIZONTAL, line(0, 1), line(0, 1)));
            assertFalse(matrix.lineAreaIsUnoccupied(AbsoluteAxis.HORIZONTAL, line(-2, -1), line(-1, 0)));
            assertTrue(matrix.lineAreaIsUnoccupied(AbsoluteAxis.HORIZONTAL, line(-1, 0), line(0, 1)));

            // Matrix-index based queries account for the shifted origin
            assertTrue(matrix.columnIsOccupied(0)); // OriginZero column -2
            assertFalse(matrix.columnIsOccupied(1)); // OriginZero column -1
            assertTrue(matrix.columnIsOccupied(2)); // OriginZero column 0
            assertTrue(matrix.rowIsOccupied(0)); // OriginZero row -1
            assertTrue(matrix.rowIsOccupied(1)); // OriginZero row 0
            assertFalse(matrix.rowIsOccupied(2)); // OriginZero row 1
        }

        @Test
        void autoPlacedItemOnlyAdvancesCursorInFirstSecondaryTrack() {
            for (AbsoluteAxis axis : new AbsoluteAxis[] {AbsoluteAxis.HORIZONTAL, AbsoluteAxis.VERTICAL}) {
                CellOccupancyMatrix matrix =
                        CellOccupancyMatrix.withTrackCounts(TrackCounts.fromRaw(0, 4, 0), TrackCounts.fromRaw(0, 4, 0));
                matrix.markAreaAs(axis, line(1, 3), line(1, 4), CellOccupancyState.AUTO_PLACED);

                assertEquals(new OriginZeroLine(3), matrix.autoPlacementCursor(axis, new OriginZeroLine(1)));
                assertNull(matrix.autoPlacementCursor(axis, new OriginZeroLine(2)));
                assertNull(matrix.autoPlacementCursor(axis, new OriginZeroLine(3)));

                assertFalse(matrix.lineAreaIsUnoccupied(axis, line(1, 3), line(2, 4)));
            }
        }

        @Test
        void cursorsSurviveIntervalMergingAndGridExpansion() {
            for (AbsoluteAxis axis : new AbsoluteAxis[] {AbsoluteAxis.HORIZONTAL, AbsoluteAxis.VERTICAL}) {
                CellOccupancyMatrix matrix =
                        CellOccupancyMatrix.withTrackCounts(TrackCounts.fromRaw(0, 4, 0), TrackCounts.fromRaw(0, 4, 0));
                matrix.markAreaAs(axis, line(2, 4), line(1, 2), CellOccupancyState.AUTO_PLACED);
                matrix.markAreaAs(axis, line(4, 8), line(0, 2), CellOccupancyState.AUTO_PLACED);
                matrix.markAreaAs(axis, line(8, 10), line(1, 2), CellOccupancyState.DEFINITELY_PLACED);
                matrix.markAreaAs(axis, line(-3, -1), line(-2, -1), CellOccupancyState.AUTO_PLACED);
                matrix.markAreaAs(axis, line(12, 14), line(7, 8), CellOccupancyState.AUTO_PLACED);
                matrix.markAreaAs(axis, line(0, 1), line(1, 2), CellOccupancyState.AUTO_PLACED);

                assertEquals(new OriginZeroLine(4), matrix.autoPlacementCursor(axis, new OriginZeroLine(1)));
                assertEquals(new OriginZeroLine(8), matrix.autoPlacementCursor(axis, new OriginZeroLine(0)));
                assertEquals(new OriginZeroLine(-1), matrix.autoPlacementCursor(axis, new OriginZeroLine(-2)));
                assertEquals(new OriginZeroLine(14), matrix.autoPlacementCursor(axis, new OriginZeroLine(7)));
                for (int track : new int[] {-3, -1, 2, 8}) {
                    assertNull(matrix.autoPlacementCursor(axis, new OriginZeroLine(track)));
                }
            }
        }

        @Test
        void alternatingSpansMergeOccupancyAndSkipTheOccupiedPrefix() {
            for (AbsoluteAxis axis : new AbsoluteAxis[] {AbsoluteAxis.HORIZONTAL, AbsoluteAxis.VERTICAL}) {
                CellOccupancyMatrix matrix =
                        CellOccupancyMatrix.withTrackCounts(TrackCounts.fromRaw(0, 0, 0), TrackCounts.fromRaw(0, 0, 0));
                for (int i = 0; i < 128; i++) {
                    matrix.markAreaAs(axis, line(i, i + 1), line(i % 2, 2), CellOccupancyState.AUTO_PLACED);
                }

                TrackIntervals track = matrix.trackLists(axis.otherAxis()).get(1);
                assertEquals(List.of(interval(0, 128, CellOccupancyState.AUTO_PLACED)), track.intervals);
                // `!track.intervals.spilled()` — SmallVec inline capacity is 2; merging
                // means the interval count never exceeded the inline capacity
                assertTrue(track.intervals.size() <= 2);
                assertEquals(new OriginZeroLine(128), matrix.lineAreaCollisionJump(axis, line(0, 1), line(1, 2)));
            }
        }

        @Test
        void collisionJumpReturnsNextSearchPosition() {
            CellOccupancyMatrix matrix =
                    CellOccupancyMatrix.withTrackCounts(TrackCounts.fromRaw(0, 4, 0), TrackCounts.fromRaw(0, 4, 0));
            matrix.markAreaAs(AbsoluteAxis.HORIZONTAL, line(1, 3), line(0, 1), CellOccupancyState.AUTO_PLACED);

            // Jump past the end of the last colliding interval
            assertEquals(
                    new OriginZeroLine(3),
                    matrix.lineAreaCollisionJump(AbsoluteAxis.HORIZONTAL, line(0, 2), line(0, 1)));
            assertEquals(
                    new OriginZeroLine(3),
                    matrix.lineAreaCollisionJump(AbsoluteAxis.HORIZONTAL, line(0, 4), line(0, 1)));
            // No collision in a different row
            assertNull(matrix.lineAreaCollisionJump(AbsoluteAxis.HORIZONTAL, line(0, 4), line(1, 2)));
        }

        @Test
        void occupiedTrackJumpSkipsNonEmptyTracks() {
            CellOccupancyMatrix matrix =
                    CellOccupancyMatrix.withTrackCounts(TrackCounts.fromRaw(0, 4, 0), TrackCounts.fromRaw(0, 4, 0));
            matrix.markAreaAs(AbsoluteAxis.HORIZONTAL, line(0, 1), line(1, 2), CellOccupancyState.AUTO_PLACED);

            // Vertical (row) tracks: row 1 is occupied
            assertEquals(new OriginZeroLine(2), matrix.occupiedTrackJump(AbsoluteAxis.VERTICAL, line(0, 4)));
            assertNull(matrix.occupiedTrackJump(AbsoluteAxis.VERTICAL, line(2, 4)));
        }
    }
}
