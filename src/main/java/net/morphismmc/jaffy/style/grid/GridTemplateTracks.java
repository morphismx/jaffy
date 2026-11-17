// JAFFY: upstream=src/style/grid.rs
// `GridTemplateTracks<S, Track>` (doc-hidden upstream). `S` -> `String`
// (GAP-017). The `parse`/`FromStr` impls are deferred with the feature.
package net.morphismmc.jaffy.style.grid;

import java.util.List;

/// @param tracks the tracks
/// @param lineNames the line names for the repeated tracks
public record GridTemplateTracks<Track>(List<Track> tracks, List<List<String>> lineNames) {

    /// `Default::default` — empty tracks and line names
    public GridTemplateTracks() {
        this(List.of(), List.of());
    }
}
