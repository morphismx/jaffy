// JAFFY-UPSTREAM: src/style/grid.rs
// JAFFY-NOTE: `GenericRepetition` trait
// Upstream uses generic associated iterator types (`RepetitionTrackList`,
// `TemplateLineNames`); in Java these are represented as `List`s, which
// preserve the exact-size contract. The `TemplateLineNames` trait itself is
// a type-system artifact and has no Java counterpart.
// `S: CheapCloneStr` is concretised to `String` (GAP-017).
package net.morphismmc.jaffy.style.grid;

import java.util.List;

import net.morphismmc.jaffy.geometry.MinMax;

/// A trait to represent a `repeat()` clause in a `grid-template-*` definition
public interface GenericRepetition {

    /// The repetition count (integer, auto-fill, or auto-fit)
    RepetitionCount count();

    /// Get the repeated tracks
    List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> tracks();

    /// Returns the number of repeated tracks
    /// (`tracks().len().min(u16::MAX as usize) as u16`)
    default int trackCount() {
        int len = tracks().size();
        return len < 65535 ? len : 65535;
    }

    /// Returns the line names
    ///
    /// Line name sets are positional: set `i` names the `i`th line of each repetition. The list
    /// must either be empty (all lines are unnamed) or contain exactly
    /// `trackCount() + 1` sets (one set per line, including both edge lines).
    List<List<String>> lineNames();
}
