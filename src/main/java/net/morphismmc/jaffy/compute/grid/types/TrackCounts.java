// JAFFY: port of upstream src/compute/grid/types/grid_track_counts.rs —
// `TrackCounts` (pub(crate) in Rust) + coordinate conversion impls.
// `Range<i16>` -> nested `TrackRange` record (JDK has no int-range type).
package net.morphismmc.jaffy.compute.grid.types;

import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;

/// Stores the number of tracks in a given dimension.
/// Stores separately the number of tracks in the implicit and explicit grids
public record TrackCounts(int negativeImplicit, int explicit, int positiveImplicit) {

    /// `core::ops::Range<i16>` — exclusive-end integer range used by the
    /// CellOccupancyMatrix conversion functions.
    public record TrackRange(int start, int end) {}

    /// Create a TrackCounts instance from raw track count numbers
    public static TrackCounts fromRaw(int negativeImplicit, int explicit, int positiveImplicit) {
        return new TrackCounts(negativeImplicit, explicit, positiveImplicit);
    }

    /// Count the total number of tracks in the axis
    public int len() {
        return negativeImplicit + explicit + positiveImplicit;
    }

    /// The OriginZeroLine representing the start of the implicit grid
    public OriginZeroLine implicitStartLine() {
        return new OriginZeroLine(-negativeImplicit);
    }

    /// The OriginZeroLine representing the end of the implicit grid
    public OriginZeroLine implicitEndLine() {
        return new OriginZeroLine(explicit + positiveImplicit);
    }

    /// Converts a grid line in OriginZero coordinates into the track
    /// immediately following that grid line as an index into the
    /// CellOccupancyMatrix.
    public int ozLineToNextTrack(OriginZeroLine index) {
        return index.value() + negativeImplicit;
    }

    /// Converts start and end grid lines in OriginZero coordinates into a
    /// range of tracks as indexes into the CellOccupancyMatrix
    public TrackRange ozLineRangeToTrackRange(Line<OriginZeroLine> input) {
        int start = ozLineToNextTrack(input.start());
        int end = ozLineToNextTrack(input.end()); // Don't subtract 1 as output range is exclusive
        return new TrackRange(start, end);
    }

    /// Converts a track as an index into the CellOccupancyMatrix into the
    /// grid line immediately preceding that track in OriginZero coordinates.
    public OriginZeroLine trackToPrevOzLine(int index) {
        return new OriginZeroLine(index - negativeImplicit);
    }

    /// Converts a range of tracks as indexes into the CellOccupancyMatrix
    /// into start and end grid lines in OriginZero coordinates
    public Line<OriginZeroLine> trackRangeToOzLineRange(TrackRange input) {
        OriginZeroLine start = trackToPrevOzLine(input.start());
        OriginZeroLine end = trackToPrevOzLine(input.end()); // Don't add 1 as input range is exclusive
        return new Line<>(start, end);
    }
}
