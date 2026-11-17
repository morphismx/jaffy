// JAFFY: port of upstream src/compute/grid/types/coordinates.rs —
// `OriginZeroLine` i16 newtype + its Add/Sub/AddAssign impls.
// `pub(crate)` in Rust (kept public for cross-package compute access).
package net.morphismmc.jaffy.compute.grid.types.coordinates;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;

/// Represents a grid line position in "OriginZero" coordinates
///
/// "OriginZero" coordinates are a normalized form:
///   - The line at left hand (or top) edge of the explicit grid is line 0
///   - The next line to the right (or down) is 1, and so on
///   - The next line to the left (or up) is -1, and so on
public record OriginZeroLine(int value) implements GridCoordinate {

    // ---- Add/Sub with Self ----

    /// `Add<OriginZeroLine>`
    public OriginZeroLine add(OriginZeroLine rhs) {
        return new OriginZeroLine(value + rhs.value);
    }

    /// `Sub<OriginZeroLine>`
    public OriginZeroLine sub(OriginZeroLine rhs) {
        return new OriginZeroLine(value - rhs.value);
    }

    // ---- Add/Sub with u16 ----

    /// `Add<u16>`
    public OriginZeroLine add(int rhs) {
        return new OriginZeroLine(value + rhs);
    }

    /// `AddAssign<u16>` — `&mut self` in Rust; returns the new value here
    /// (GAP-005): callers write `line = line.addAssign(n)`.
    public OriginZeroLine addAssign(int rhs) {
        return new OriginZeroLine(value + rhs);
    }

    /// `Sub<u16>`
    public OriginZeroLine sub(int rhs) {
        return new OriginZeroLine(value - rhs);
    }

    // ---- inherent ----

    /// Create an `OriginZeroLine`, clamping the line into the limited grid
    /// `[-MAX_GRID_TRACKS, MAX_GRID_TRACKS]`. (`pub(crate)` in Rust.)
    public static OriginZeroLine clamped(int line) {
        int clamped = line < Coordinates.MIN_OZ_LINE
                ? Coordinates.MIN_OZ_LINE
                : line > Coordinates.MAX_OZ_LINE ? Coordinates.MAX_OZ_LINE : line;
        return new OriginZeroLine(clamped);
    }

    /// Convert into "CSS Grid Line" coordinates using the specified explicit
    /// track count. The inverse of `GridLine::into_origin_zero_line`.
    public GridLine intoGridLine(int explicitTrackCount) {
        int explicitLineCount = explicitTrackCount + 1;
        if (value >= 0) {
            return new GridLine(value + 1);
        } else {
            return new GridLine(value - explicitLineCount);
        }
    }

    /// Converts a grid line in OriginZero coordinates into the index of that
    /// same grid line in the GridTrackVec.
    public int intoTrackVecIndex(TrackCounts trackCounts) {
        int idx = tryIntoTrackVecIndex(trackCounts);
        if (idx < 0) {
            if (value > 0) {
                throw new IllegalStateException(
                        "OriginZero grid line cannot be more than the number of positive grid lines"); // JAFFY(GAP-010)
            } else {
                throw new IllegalStateException(
                        "OriginZero grid line cannot be less than the number of negative grid lines"); // JAFFY(GAP-010)
            }
        }
        return idx;
    }

    /// Fallible version of {@link #intoTrackVecIndex} used for the placement
    /// of absolutely positioned grid items: returns -1 when the line is
    /// outside the implicit grid instead of panicking.
    ///
    /// Java has no Option<usize> spec type in use here; upstream callers only
    /// branch on Some/None, so -1 encodes None (indices are always >= 0).
    public int tryIntoTrackVecIndex(TrackCounts trackCounts) {
        // OriginZero grid line cannot be less than the number of negative grid lines
        if (value < -trackCounts.negativeImplicit()) {
            return -1;
        }
        // OriginZero grid line cannot be more than the number of positive grid lines
        if (value > trackCounts.explicit() + trackCounts.positiveImplicit()) {
            return -1;
        }
        return 2 * (value + trackCounts.negativeImplicit());
    }

    /// The minimum number of negative implicit tracks there must be if a grid
    /// item starts at this line.
    public int impliedNegativeImplicitTracks() {
        if (value < 0) {
            return -value; // self.0.unsigned_abs()
        } else {
            return 0;
        }
    }

    /// The minimum number of positive implicit tracks there must be if a
    /// grid item ends at this line.
    public int impliedPositiveImplicitTracks(int explicitTrackCount) {
        if (value > explicitTrackCount) {
            return value - explicitTrackCount;
        } else {
            return 0;
        }
    }
}
