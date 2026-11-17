// JAFFY-UPSTREAM: src/style/grid.rs
// JAFFY-NOTE: `GridAutoTracks(pub Vec<TrackSizingFunction>)`
// (doc-hidden upstream). The `parse`/`FromStr` impls are deferred with the feature.
package net.morphismmc.jaffy.style.grid;

import java.util.List;

import net.morphismmc.jaffy.geometry.MinMax;

/// The `grid-auto-rows`/`grid-auto-columns` track list.
public record GridAutoTracks(List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> tracks) {

    /// `Default::default` — empty track list
    public GridAutoTracks() {
        this(List.of());
    }
}
