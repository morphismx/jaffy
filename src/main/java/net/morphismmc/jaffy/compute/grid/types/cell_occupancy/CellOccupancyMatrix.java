// JAFFY: port of upstream src/compute/grid/types/cell_occupancy.rs —
// `CellOccupancyMatrix`.
//
// `pub(crate)` -> public; `Vec<TrackIntervals>` -> ArrayList;
// `splice(0..0, repeat_with(default))` -> add(0, new) per slot — each inserted
// track must be a distinct object (Rust clones per element, GAP-005);
// `TrackCounts` field `+=` -> record rebuild (GAP-005/018);
// `Range<i16>` -> int start/end; `Option<T>` -> @Nullable;
// `max`/`min` (i16) -> Integer.max/min (staticCheck bans Math.*).
package net.morphismmc.jaffy.compute.grid.types.cell_occupancy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI16;

import org.jspecify.annotations.Nullable;

/// A dynamically sized matrix (2d grid) which tracks the occupancy of each grid cell during auto-placement.
/// It also keeps tabs on how many tracks there are and which tracks are implicit and which are explicit.
///
/// Occupancy is stored sparsely as per-track interval lists (in both orientations), so memory usage
/// is proportional to the number of placed items rather than the total number of grid cells.
public final class CellOccupancyMatrix {
    /// The counts of implicit and explicit columns
    private TrackCounts columns;
    /// The counts of implicit and explicit rows
    private TrackCounts rows;
    /// For each row track: the occupied intervals within that row (in column coordinates)
    private final ArrayList<TrackIntervals> rowIntervals;
    /// For each column track: the occupied intervals within that column (in row coordinates)
    private final ArrayList<TrackIntervals> columnIntervals;

    private CellOccupancyMatrix(
            TrackCounts columns,
            TrackCounts rows,
            ArrayList<TrackIntervals> rowIntervals,
            ArrayList<TrackIntervals> columnIntervals) {
        this.columns = columns;
        this.rows = rows;
        this.rowIntervals = rowIntervals;
        this.columnIntervals = columnIntervals;
    }

    /// Create a CellOccupancyMatrix given a set of provisional track counts. The grid can expand as needed to fit more
    /// tracks,
    /// the provisional track counts represent a best effort attempt to avoid the extra allocations this requires.
    public static CellOccupancyMatrix withTrackCounts(TrackCounts columns, TrackCounts rows) {
        ArrayList<TrackIntervals> rowIntervals = new ArrayList<>(rows.len());
        for (int i = 0; i < rows.len(); i++) {
            rowIntervals.add(new TrackIntervals());
        }
        ArrayList<TrackIntervals> columnIntervals = new ArrayList<>(columns.len());
        for (int i = 0; i < columns.len(); i++) {
            columnIntervals.add(new TrackIntervals());
        }
        return new CellOccupancyMatrix(columns, rows, rowIntervals, columnIntervals);
    }

    /// The per-track interval lists for tracks in the specified axis. Each row track's intervals
    /// are in column coordinates and vice versa.
    /// (module-private upstream; test-only callers — package-private)
    List<TrackIntervals> trackLists(AbsoluteAxis trackAxis) {
        return switch (trackAxis) {
            case HORIZONTAL -> Collections.unmodifiableList(columnIntervals);
            case VERTICAL -> Collections.unmodifiableList(rowIntervals);
        };
    }

    /// Expands the grid (potentially in all 4 directions) in order to ensure that the specified
    /// spans (in OriginZero coordinates) fit within the tracked tracks
    private void expandToFitRange(Line<OriginZeroLine> rowSpan, Line<OriginZeroLine> colSpan) {
        // Calculate number of rows and columns missing to accommodate ranges (if any)
        int reqNegativeRows =
                Integer.max(-rows.negativeImplicit() - rowSpan.start().value(), 0);
        int reqPositiveRows =
                Integer.max(rowSpan.end().value() - rows.implicitEndLine().value(), 0);
        int reqNegativeCols =
                Integer.max(-columns.negativeImplicit() - colSpan.start().value(), 0);
        int reqPositiveCols =
                Integer.max(colSpan.end().value() - columns.implicitEndLine().value(), 0);

        // Add empty tracks to the front and/or back of the per-track interval lists.
        // Interval contents are stored in OriginZero coordinates, so they do not need to shift.
        // JAFFY(GAP-005): `repeat_with(TrackIntervals::default)` produces a distinct instance per
        // slot — the same number of `new TrackIntervals()` must be inserted.
        if (reqNegativeRows > 0) {
            for (int i = 0; i < reqNegativeRows; i++) {
                rowIntervals.add(0, new TrackIntervals());
            }
        }
        if (reqPositiveRows > 0) {
            int newLen = rowIntervals.size() + reqPositiveRows;
            while (rowIntervals.size() < newLen) {
                rowIntervals.add(new TrackIntervals());
            }
        }
        if (reqNegativeCols > 0) {
            for (int i = 0; i < reqNegativeCols; i++) {
                columnIntervals.add(0, new TrackIntervals());
            }
        }
        if (reqPositiveCols > 0) {
            int newLen = columnIntervals.size() + reqPositiveCols;
            while (columnIntervals.size() < newLen) {
                columnIntervals.add(new TrackIntervals());
            }
        }

        rows = new TrackCounts(
                rows.negativeImplicit() + reqNegativeRows, rows.explicit(), rows.positiveImplicit() + reqPositiveRows);
        columns = new TrackCounts(
                columns.negativeImplicit() + reqNegativeCols,
                columns.explicit(),
                columns.positiveImplicit() + reqPositiveCols);
    }

    /// Mark an area of the matrix as occupied, expanding the allocated space as necessary to accommodate the passed
    /// area.
    public void markAreaAs(
            AbsoluteAxis primaryAxis,
            Line<OriginZeroLine> primarySpan,
            Line<OriginZeroLine> secondarySpan,
            CellOccupancyState value) {
        Line<OriginZeroLine> rowSpan;
        Line<OriginZeroLine> columnSpan;
        switch (primaryAxis) {
            case HORIZONTAL -> {
                rowSpan = secondarySpan;
                columnSpan = primarySpan;
            }
            case VERTICAL -> {
                rowSpan = primarySpan;
                columnSpan = secondarySpan;
            }
            default -> throw new IllegalStateException("unreachable");
        }

        expandToFitRange(rowSpan, columnSpan);

        if (value == CellOccupancyState.AUTO_PLACED
                && primarySpan.start().value() < primarySpan.end().value()
                && secondarySpan.start().value() < secondarySpan.end().value()) {
            advanceAutoPlacementCursor(primaryAxis, secondarySpan.start(), primarySpan.end());
        }

        TrackCounts.TrackRange rowRange = rows.ozLineRangeToTrackRange(rowSpan);
        TrackCounts.TrackRange colRange = columns.ozLineRangeToTrackRange(columnSpan);
        for (int rowIndex = rowRange.start(); rowIndex < rowRange.end(); rowIndex++) {
            rowIntervals
                    .get(rowIndex)
                    .paint(columnSpan.start().value(), columnSpan.end().value(), value);
        }
        for (int columnIndex = colRange.start(); columnIndex < colRange.end(); columnIndex++) {
            columnIntervals
                    .get(columnIndex)
                    .paint(rowSpan.start().value(), rowSpan.end().value(), value);
        }
    }

    /// Determines whether a grid area specified by the bounding grid lines in OriginZero coordinates
    /// is entirely unnocupied. Returns true if all grid cells within the grid area are unnocupied, else false.
    /// `#[cfg(test)]` upstream — exists only for the test module.
    public boolean lineAreaIsUnoccupied(
            AbsoluteAxis primaryAxis, Line<OriginZeroLine> primarySpan, Line<OriginZeroLine> secondarySpan) {
        return lineAreaCollisionJump(primaryAxis, primarySpan, secondarySpan) == null;
    }

    /// Checks the specified area for occupied cells (`primary_span` and `secondary_span` are
    /// bounding grid lines in OriginZero coordinates). Returns `null` if the area is entirely
    /// unoccupied. Otherwise returns the next search position (in OriginZero coordinates, along
    /// `primary_axis`) that is not guaranteed to collide with the occupied cells found in the
    /// area. This allows the auto-placement search cursor to jump past collisions rather than
    /// advancing one track at a time.
    public @Nullable OriginZeroLine lineAreaCollisionJump(
            AbsoluteAxis primaryAxis, Line<OriginZeroLine> primarySpan, Line<OriginZeroLine> secondarySpan) {
        List<TrackIntervals> trackLists = trackLists(primaryAxis.otherAxis());
        TrackCounts secondaryCounts = trackCounts(primaryAxis.otherAxis());
        TrackCounts.TrackRange secondaryRange = secondaryCounts.ozLineRangeToTrackRange(secondarySpan);

        // Out of bounds cells are considered unoccupied, so clamp the secondary range to the
        // tracks which actually exist
        int secondaryStart = Integer.max(secondaryRange.start(), 0);
        int secondaryEnd = Integer.min(secondaryRange.end(), trackLists.size());

        int primaryStart = primarySpan.start().value();
        int primaryEnd = primarySpan.end().value();

        @Opt int extent = OptI16.NONE;
        for (int secondaryIndex = secondaryStart; secondaryIndex < secondaryEnd; secondaryIndex++) {
            @Opt int cell = trackLists.get(secondaryIndex).collisionExtent(primaryStart, primaryEnd);
            if (cell == OptI16.NONE) {
                continue;
            }
            extent = extent == OptI16.NONE ? cell : Integer.max(extent, cell);
        }

        return extent == OptI16.NONE ? null : new OriginZeroLine(extent + 1);
    }

    /// Given a span of tracks in `axis` (in OriginZero coordinates), returns the next search
    /// position past all non-empty tracks within the span, or `null` if all tracks within the
    /// span are entirely unoccupied. Used to place items which span every track in the other
    /// axis (such items can only fit in a stripe of entirely unoccupied tracks).
    public @Nullable OriginZeroLine occupiedTrackJump(AbsoluteAxis axis, Line<OriginZeroLine> span) {
        TrackCounts counts = trackCounts(axis);
        List<TrackIntervals> trackLists = trackLists(axis);
        TrackCounts.TrackRange range = counts.ozLineRangeToTrackRange(span);
        int start = Integer.max(range.start(), 0);
        int end = Integer.min(range.end(), trackLists.size());
        for (int trackIndex = end - 1; trackIndex >= start; trackIndex--) {
            if (!trackLists.get(trackIndex).isEmpty()) {
                return counts.trackToPrevOzLine(trackIndex).add(1);
            }
        }
        return null;
    }

    /// Determines whether the specified row contains any items
    public boolean rowIsOccupied(int rowIndex) {
        List<TrackIntervals> trackLists = trackLists(AbsoluteAxis.VERTICAL);
        return rowIndex >= 0
                && rowIndex < trackLists.size()
                && !trackLists.get(rowIndex).isEmpty();
    }

    /// Determines whether the specified column contains any items
    public boolean columnIsOccupied(int columnIndex) {
        List<TrackIntervals> trackLists = trackLists(AbsoluteAxis.HORIZONTAL);
        return columnIndex >= 0
                && columnIndex < trackLists.size()
                && !trackLists.get(columnIndex).isEmpty();
    }

    /// Returns the track counts of this CellOccunpancyMatrix in the relevant axis
    /// `&TrackCounts` -> the immutable record (safe to share)
    public TrackCounts trackCounts(AbsoluteAxis trackType) {
        return switch (trackType) {
            case HORIZONTAL -> columns;
            case VERTICAL -> rows;
        };
    }

    /// The sparse placement cursor for items starting in the given secondary-axis track.
    public @Nullable OriginZeroLine autoPlacementCursor(AbsoluteAxis trackType, OriginZeroLine startAt) {
        TrackCounts trackCounts = trackCounts(trackType.otherAxis());
        int trackComputedIndex = trackCounts.ozLineToNextTrack(startAt);
        List<TrackIntervals> trackLists = trackLists(trackType.otherAxis());
        if (trackComputedIndex < 0 || trackComputedIndex >= trackLists.size()) {
            // Index out of bounds: no tracks to search
            return null;
        }
        return trackLists.get(trackComputedIndex).autoPlacementCursor;
    }

    /// Advances the sparse placement cursor to at least `primary_end`.
    /// The secondary-axis track must already exist in the matrix.
    private void advanceAutoPlacementCursor(
            AbsoluteAxis primaryAxis, OriginZeroLine secondaryStart, OriginZeroLine primaryEnd) {
        int trackIndex = trackCounts(primaryAxis.otherAxis()).ozLineToNextTrack(secondaryStart);
        ArrayList<TrackIntervals> tracks = primaryAxis == AbsoluteAxis.HORIZONTAL ? rowIntervals : columnIntervals;
        TrackIntervals track = tracks.get(trackIndex);
        // `*cursor = max(*cursor, Some(primary_end))` — Option's Ord: None < Some(_)
        track.autoPlacementCursor =
                track.autoPlacementCursor == null || track.autoPlacementCursor.value() < primaryEnd.value()
                        ? primaryEnd
                        : track.autoPlacementCursor;
    }

    /// `Debug` impl that represents the matrix in a compact 2d text format
    @Override
    public String toString() {
        StringBuilder f = new StringBuilder();
        f.append("Rows: neg_implicit=")
                .append(rows.negativeImplicit())
                .append(" explicit=")
                .append(rows.explicit())
                .append(" pos_implicit=")
                .append(rows.positiveImplicit())
                .append('\n');
        f.append("Cols: neg_implicit=")
                .append(columns.negativeImplicit())
                .append(" explicit=")
                .append(columns.explicit())
                .append(" pos_implicit=")
                .append(columns.positiveImplicit())
                .append('\n');
        if (rows.len() > 100 || columns.len() > 100) {
            f.append("State: (not printed: more than 100 tracks)\n");
            return f.toString();
        }
        f.append("State:\n");

        for (TrackIntervals row : rowIntervals) {
            for (int columnIndex = 0; columnIndex < columns.len(); columnIndex++) {
                OriginZeroLine coordinate = columns.trackToPrevOzLine(columnIndex);
                char letter = switch (row.stateAt(coordinate.value())) {
                    case UNOCCUPIED -> '_';
                    case DEFINITELY_PLACED -> 'D';
                    case AUTO_PLACED -> 'A';
                };
                f.append(letter);
            }
            f.append('\n');
        }
        return f.toString();
    }
}
