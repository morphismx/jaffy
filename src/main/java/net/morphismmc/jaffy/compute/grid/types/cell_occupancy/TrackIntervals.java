// JAFFY: upstream=src/compute/grid/types/cell_occupancy.rs
// `TrackIntervals` (module-private -> package-private).
//
// `SmallVec<[OccupiedInterval; 2]>` -> ArrayList (the inline capacity is an
// allocation optimisation; observable behaviour is identical).
// `Option<OriginZeroLine>` -> @Nullable field; `Option<i16>` -> @Opt int (OptI16.NONE).
// Default -> ctor; Clone -> copy() (deep: OccupiedInterval is mutable, GAP-005).
package net.morphismmc.jaffy.compute.grid.types.cell_occupancy;

import java.util.ArrayList;

import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI16;

import org.jspecify.annotations.Nullable;

/// The occupied cells of a single track, stored as a sorted list of disjoint intervals in
/// OriginZero line coordinates. Gaps between intervals are unoccupied. Touching intervals
/// with the same state are merged.
final class TrackIntervals {
    /// The sorted, disjoint list of occupied intervals within the track
    final ArrayList<OccupiedInterval> intervals;
    /// The furthest end line of an auto-placed item starting in this secondary-axis track
    @Nullable
    OriginZeroLine autoPlacementCursor;

    /// `Default::default()`
    TrackIntervals() {
        this.intervals = new ArrayList<>();
    }

    /// Whether the track contains any occupied cells
    boolean isEmpty() {
        return intervals.isEmpty();
    }

    /// The occupancy state of the cell whose start line is at `coordinate`
    CellOccupancyState stateAt(int coordinate) {
        for (OccupiedInterval interval : intervals) {
            if (interval.start <= coordinate && coordinate < interval.end) {
                return interval.state;
            }
        }
        return CellOccupancyState.UNOCCUPIED;
    }

    /// Set the cells covered by `start..end` to `state`, overwriting the state of any already-occupied
    /// cells within the range (matching the overwrite semantics of a dense matrix of cells)
    void paint(int start, int end, CellOccupancyState state) {
        if (start >= end) {
            return;
        }

        // Fast path: the painted range lies entirely after all existing intervals
        // (the common case, as placement queries and inserts mostly advance forwards)
        if (intervals.isEmpty()) {
            intervals.add(new OccupiedInterval(start, end, state));
            return;
        }
        OccupiedInterval last = intervals.getLast();
        if (last.end <= start) {
            if (last.state == state && last.end == start) {
                last.end = end;
            } else {
                intervals.add(new OccupiedInterval(start, end, state));
            }
            return;
        }

        ArrayList<OccupiedInterval> result = new ArrayList<>();

        // Intervals (or partial intervals) entirely before the painted range
        for (OccupiedInterval interval : intervals) {
            if (interval.end <= start) {
                result.add(interval.copy());
            } else if (interval.start < start) {
                result.add(new OccupiedInterval(interval.start, start, interval.state));
            }
        }

        // The painted range, merged with the preceding interval if it touches and has the same state
        if (!result.isEmpty() && result.getLast().state == state && result.getLast().end == start) {
            result.getLast().end = end;
        } else {
            result.add(new OccupiedInterval(start, end, state));
        }

        // Intervals (or partial intervals) entirely after the painted range
        for (OccupiedInterval interval : intervals) {
            OccupiedInterval trimmed;
            if (interval.start >= end) {
                trimmed = interval.copy();
            } else if (interval.end > end) {
                trimmed = new OccupiedInterval(end, interval.end, interval.state);
            } else {
                continue;
            }
            if (!result.isEmpty() && result.getLast().state == trimmed.state && result.getLast().end == trimmed.start) {
                result.getLast().end = trimmed.end;
            } else {
                result.add(trimmed);
            }
        }

        intervals.clear();
        intervals.addAll(result);
    }

    /// Find the extent of the occupied interval which an auto-placement search along the track
    /// would collide with last: the end of the last overlapping interval. Returns the extremal
    /// occupied cell of that interval (which may lie outside `range`: every search position
    /// before the returned extent also collides with the interval), or `null` if the range is
    /// entirely unoccupied.
    /// `Option<i16>` -> @Opt int (widened carrier, OptI16.NONE)
    @Opt
    int collisionExtent(int rangeStart, int rangeEnd) {
        for (int i = intervals.size() - 1; i >= 0; i--) {
            OccupiedInterval interval = intervals.get(i);
            if (interval.end <= rangeStart) {
                break; // `take_while(|i| i.range.end > range.start)`
            }
            if (interval.overlaps(rangeStart, rangeEnd)) {
                return interval.end - 1;
            }
        }
        return OptI16.NONE;
    }

    /// `Clone` — deep copy of the interval list (`OccupiedInterval` is mutable)
    TrackIntervals copy() {
        TrackIntervals copy = new TrackIntervals();
        for (OccupiedInterval interval : intervals) {
            copy.intervals.add(interval.copy());
        }
        copy.autoPlacementCursor = autoPlacementCursor;
        return copy;
    }

    /// `#[derive(Debug)]` — `Option<OriginZeroLine>` prints `Some(OriginZeroLine(n))`/`None`
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TrackIntervals { intervals: [");
        for (int i = 0; i < intervals.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(intervals.get(i));
        }
        sb.append("], auto_placement_cursor: ");
        if (autoPlacementCursor != null) {
            sb.append("Some(OriginZeroLine(")
                    .append(autoPlacementCursor.value())
                    .append("))");
        } else {
            sb.append("None");
        }
        return sb.append(" }").toString();
    }
}
