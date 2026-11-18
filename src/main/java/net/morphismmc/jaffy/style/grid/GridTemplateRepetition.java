// JAFFY-UPSTREAM: src/style/grid.rs
// JAFFY-NOTE: `GridTemplateRepetition<S>`
// struct + its `GenericRepetition` impl (upstream implements the trait on
// `&GridTemplateRepetition`; the value itself implements it in Java).
// `S: CheapCloneStr` -> `String` (GAP-017).
package net.morphismmc.jaffy.style.grid;

import java.util.List;

import net.morphismmc.jaffy.geometry.MinMax;

/// A typed representation of a `repeat(..)` in `grid-template-*` value
///
/// @param count the number of the times the repeat is repeated
/// @param tracks the tracks to repeat
/// @param lineNames the line names for the repeated tracks.
///        Line name sets are positional: set `i` names the `i`th line of each repetition. This must
///        either be empty (all lines are unnamed) or contain exactly `tracks.size() + 1` sets (one set
///        per line, including both edge lines; sets may be empty). Other lengths panic during layout.
public record GridTemplateRepetition(
        RepetitionCount count,
        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> tracks,
        List<List<String>> lineNames)
        implements GenericRepetition {}
