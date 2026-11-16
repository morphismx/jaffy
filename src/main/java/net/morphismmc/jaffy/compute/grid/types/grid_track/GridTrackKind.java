// JAFFY: port of upstream src/compute/grid/types/grid_track.rs — `GridTrackKind`.
//
// `pub(in super::super)` -> public (callers cross Java package boundaries).
package net.morphismmc.jaffy.compute.grid.types.grid_track;


/// Whether a GridTrack represents an actual track or a gutter.
public enum GridTrackKind {
    /// Track is an actual track
    TRACK,
    /// Track is a gutter (aka grid line) (aka gap)
    GUTTER,
}
