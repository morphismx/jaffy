// JAFFY-UPSTREAM: src/compute/grid/types/coordinates.rs
// file-level constants. GridLine/OriginZeroLine/GridCoordinate/
// LineOriginZeroLineExt live beside it in this package.
//
// u16/i16 -> int per GAP-014 (all values bounded by MAX_GRID_TRACKS=10_000;
// i16 arithmetic never relies on wraparound inside the clamped grid).
package net.morphismmc.jaffy.compute.grid.types.coordinates;

public final class Coordinates {

    private Coordinates() {}

    /// The maximum number of tracks in each direction from the start of the
    /// explicit grid (line 0 in OriginZero coordinates), and the maximum span
    /// of a single grid item. This limits the grid to `2 * MAX_GRID_TRACKS`
    /// total tracks in each axis (including explicit tracks). Grids larger
    /// than this limit are clamped. (`pub(crate)` in Rust.)
    public static final int MAX_GRID_TRACKS = 10_000;

    /// The lowest valid grid line in OriginZero coordinates
    public static final int MIN_OZ_LINE = -MAX_GRID_TRACKS;

    /// The highest valid grid line in OriginZero coordinates
    public static final int MAX_OZ_LINE = MAX_GRID_TRACKS;
}
