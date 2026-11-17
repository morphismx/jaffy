// JAFFY: upstream=src/compute/grid/types/coordinates.rs
// JAFFY-NOTE: `GridLine`
// i16 newtype. `pub` kept (appears in the public GridPlacement API).
package net.morphismmc.jaffy.compute.grid.types.coordinates;

/// Represents a grid line position in "CSS Grid Line" coordinates
///
/// "CSS Grid Line" coordinates are those used in grid-row/grid-column in the
/// CSS grid spec:
///   - The line at left hand (or top) edge of the explicit grid is line 1
///     (and counts up from there)
///   - The line at the right hand (or bottom) edge of the explicit grid is -1
///     (and counts down from there)
///   - 0 is not a valid index
public record GridLine(int value) implements GridCoordinate {

    /// `From<i16>` — `Self(value)`
    public static GridLine from(int value) {
        return new GridLine(value);
    }

    /// Returns the underlying i16 (`as_i16`; widened to int per GAP-014)
    public int asI16() {
        return value;
    }

    /// Convert into OriginZero coordinates using the specified explicit track
    /// count. The result is clamped into the limited grid
    /// `[-MAX_GRID_TRACKS, MAX_GRID_TRACKS]`. (`pub(crate)` in Rust.)
    public OriginZeroLine intoOriginZeroLine(int explicitTrackCount) {
        int explicitLineCount = explicitTrackCount + 1;
        int ozLine;
        if (value > 0) {
            ozLine = value - 1;
        } else if (value < 0) {
            ozLine = value + explicitLineCount;
        } else {
            throw new IllegalStateException("Grid line of zero is invalid"); // JAFFY(GAP-010)
        }
        return OriginZeroLine.clamped(ozLine);
    }
}
