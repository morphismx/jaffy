// JAFFY: port of upstream src/style/grid.rs — `GridPlacement<S>`.
// CustomIdent is fixed to String per GAP-017 (DefaultCheapStr).
// serde/parse impls (incl. saturating_i16/u16) are feature-gated: deferred.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.compute.grid.types.coordinates.Coordinates;
import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;

/// A grid line placement specification. Used for
/// grid-[row/column]-[start/end]. Defaults to `Auto`.
public sealed interface GridPlacement {

    /// `TaffyAuto::AUTO` / `Default::default()` — `Self::Auto`
    GridPlacement AUTO = new Auto();

    /// Place item according to the auto-placement algorithm, and the parent's
    /// grid_auto_flow property (Rust `#[default]`)
    record Auto() implements GridPlacement {}

    /// Place item at specified line (column or row) index
    record Line(GridLine line) implements GridPlacement {}

    /// Place item at specified named line (column or row)
    record NamedLine(String name, int index) implements GridPlacement {}

    /// Item should span specified number of tracks (columns or rows)
    record Span(int span) implements GridPlacement {}

    /// Item should span until the nth line named `<name>`. If there are less
    /// than n lines named `<name>` in the specified direction then all
    /// implicit lines will be counted.
    record NamedSpan(String name, int span) implements GridPlacement {}

    // ---- trait impls ----

    /// `TaffyGridLine::from_line_index` — `Line(GridLine::from(index))`
    static GridPlacement fromLineIndex(int index) {
        return new Line(GridLine.from(index));
    }

    /// `TaffyGridSpan::from_span` — `Span(span)`
    static GridPlacement fromSpan(int span) {
        return new Span(span);
    }

    // ---- methods ----

    /// Apply a mapping function if the `GridPlacement` is a `Line`. Otherwise
    /// return the generic equivalent of `self` unmodified.
    default GenericGridPlacement<OriginZeroLine> intoOriginZeroPlacementIgnoringNamed(int explicitTrackCount) {
        return switch (this) {
            case Auto a -> new GenericGridPlacement.Auto<>();
            // Spans are clamped between 1 (a zero span is an invalid value
            // which is treated as 1) and the maximum track limit
            case Span s ->
                new GenericGridPlacement.Span<>(
                        s.span < 1 ? 1 : s.span > Coordinates.MAX_GRID_TRACKS ? Coordinates.MAX_GRID_TRACKS : s.span);
            // Grid line zero is an invalid index, so it gets treated as Auto
            case Line l ->
                l.line().asI16() == 0
                        ? new GenericGridPlacement.Auto<>()
                        : new GenericGridPlacement.Line<>(l.line().intoOriginZeroLine(explicitTrackCount));
            case NamedLine n -> new GenericGridPlacement.Auto<>();
            case NamedSpan n -> new GenericGridPlacement.Auto<>();
        };
    }
}
