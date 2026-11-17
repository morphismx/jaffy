// JAFFY: port of upstream src/style/grid.rs — `impl Line<GridPlacement<S>>`
// blocks (SPECIALIZATIONS Ext pattern; CustomIdent=String per GAP-017).
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.Line;

public final class LineGridPlacementExt {

    private LineGridPlacementExt() {}

    /// `Default` impl — `Line { start: Auto, end: Auto }`
    public static final Line<GridPlacement> DEFAULT = new Line<>(GridPlacement.AUTO, GridPlacement.AUTO);

    /// `TaffyGridLine for Line<GridPlacement<S>>` — `Line { start: Line(index), end: Auto }`
    public static Line<GridPlacement> fromLineIndex(int index) {
        return new Line<>(GridPlacement.fromLineIndex(index), GridPlacement.AUTO);
    }

    /// `TaffyGridSpan for Line<GridPlacement<S>>` — `Line { start: Span(span), end: Auto }`
    public static Line<GridPlacement> fromSpan(int span) {
        return new Line<>(GridPlacement.fromSpan(span), GridPlacement.AUTO);
    }

    /// `into_origin_zero_ignoring_named` — map both positions ignoring named lines
    public static Line<GenericGridPlacement<OriginZeroLine>> intoOriginZeroIgnoringNamed(
            Line<GridPlacement> self, int explicitTrackCount) {
        return new Line<>(
                self.start().intoOriginZeroPlacementIgnoringNamed(explicitTrackCount),
                self.end().intoOriginZeroPlacementIgnoringNamed(explicitTrackCount));
    }

    /// Whether the track position is definite in this axis (or the item will
    /// need auto placement). Definite iff at least one of start/end is a
    /// NON-ZERO track index or a named line.
    public static boolean isDefinite(Line<GridPlacement> self) {
        if (self.start() instanceof GridPlacement.Line l && l.line().asI16() != 0) {
            return true;
        }
        if (self.end() instanceof GridPlacement.Line l && l.line().asI16() != 0) {
            return true;
        }
        return self.start() instanceof GridPlacement.NamedLine || self.end() instanceof GridPlacement.NamedLine;
    }
}
