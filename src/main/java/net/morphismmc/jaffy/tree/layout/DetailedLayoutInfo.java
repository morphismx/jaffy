// JAFFY: port of upstream src/tree/layout.rs — `DetailedLayoutInfo` enum.
// `Box<DetailedGridInfo<S>>` -> `DetailedGridInfo` directly (reference type);
// `S: CheapCloneStr` = `String` (GAP-017). serde impl deferred.
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.compute.grid.DetailedGridInfo;

/// The additional information from layout algorithm
public sealed interface DetailedLayoutInfo {

    /// For node that hasn't had any detailed information yet
    record None() implements DetailedLayoutInfo {}

    /// For a node that is a grid container: the detailed grid information
    /// (`DetailedLayoutInfo::Grid`)
    record Grid(DetailedGridInfo gridInfo) implements DetailedLayoutInfo {}

    /// The `DetailedLayoutInfo::None` variant value
    None NONE = new None();
}
