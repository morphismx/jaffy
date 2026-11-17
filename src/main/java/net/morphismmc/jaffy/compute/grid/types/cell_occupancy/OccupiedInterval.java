// JAFFY: port of upstream src/compute/grid/types/cell_occupancy.rs —
// `OccupiedInterval` (module-private -> package-private).
//
// `range: Range<i16>` -> mutable `start`/`end` int fields (`last.range.end =
// range.end` mutates the range in place, GAP-018); `i16` -> int (GAP-014).
// Clone -> copy(); PartialEq -> equals; Debug -> toString (GAP-016).
package net.morphismmc.jaffy.compute.grid.types.cell_occupancy;

/// A run of occupied cells within a single track. The range is in OriginZero line coordinates
/// along the track (so for a row track, the range spans column lines and vice versa).
final class OccupiedInterval {
    /// The start line of the cell range covered by this interval (OriginZero coordinates)
    int start;
    /// The end line of the cell range covered by this interval (OriginZero coordinates)
    int end;
    /// The occupancy state of every cell within this interval
    final CellOccupancyState state;

    OccupiedInterval(int start, int end, CellOccupancyState state) {
        this.start = start;
        this.end = end;
        this.state = state;
    }

    /// Whether this interval overlaps the given range
    boolean overlaps(int rangeStart, int rangeEnd) {
        return this.start < rangeEnd && this.end > rangeStart;
    }

    /// `Clone`
    OccupiedInterval copy() {
        return new OccupiedInterval(start, end, state);
    }

    /// `PartialEq`
    @Override
    public boolean equals(Object o) {
        return o instanceof OccupiedInterval other && start == other.start && end == other.end && state == other.state;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * start + end) + state.hashCode();
    }

    /// `#[derive(Debug)]` — `Range<i16>` prints `start..end`
    @Override
    public String toString() {
        return "OccupiedInterval { range: " + start + ".." + end + ", state: " + state + " }";
    }
}
