// JAFFY: port of upstream src/style/grid.rs — `impl Line<NonNamedGridPlacement>`
// (alias of `GenericGridPlacement<GridLine>`; SPECIALIZATIONS Ext pattern).
// Also hosts the `impl NonNamedGridPlacement` single-value impl.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.compute.grid.types.coordinates.Coordinates;
import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.Line;

public final class LineNonNamedGridPlacementExt {

    private LineNonNamedGridPlacementExt() {}

    /// `NonNamedGridPlacement::into_origin_zero_placement` — map a single
    /// placement to OriginZero coordinates (named lines are absent here).
    public static GenericGridPlacement<OriginZeroLine> intoOriginZeroPlacement(
            GenericGridPlacement<GridLine> self, int explicitTrackCount) {
        return switch (self) {
            case GenericGridPlacement.Auto<GridLine> a -> new GenericGridPlacement.Auto<>();
            // Spans are clamped between 1 (a zero span is an invalid value
            // which is treated as 1) and the maximum track limit
            case GenericGridPlacement.Span<GridLine> s ->
                new GenericGridPlacement.Span<>(
                        s.span() < 1
                                ? 1
                                : s.span() > Coordinates.MAX_GRID_TRACKS ? Coordinates.MAX_GRID_TRACKS : s.span());
            // Grid line zero is an invalid index, so it gets treated as Auto
            case GenericGridPlacement.Line<GridLine> l ->
                l.line().asI16() == 0
                        ? new GenericGridPlacement.Auto<>()
                        : new GenericGridPlacement.Line<>(l.line().intoOriginZeroLine(explicitTrackCount));
        };
    }

    /// `Line<NonNamedGridPlacement>::is_definite` — at least one of start/end
    /// is a NON-ZERO track index.
    public static boolean isDefinite(Line<GenericGridPlacement<GridLine>> self) {
        if (self.start() instanceof GenericGridPlacement.Line<GridLine> l
                && l.line().asI16() != 0) {
            return true;
        }
        return self.end() instanceof GenericGridPlacement.Line<GridLine> l
                && l.line().asI16() != 0;
    }

    /// `Line<NonNamedGridPlacement>::into_origin_zero` — map both positions.
    public static Line<GenericGridPlacement<OriginZeroLine>> intoOriginZero(
            Line<GenericGridPlacement<GridLine>> self, int explicitTrackCount) {
        return new Line<>(
                intoOriginZeroPlacement(self.start(), explicitTrackCount),
                intoOriginZeroPlacement(self.end(), explicitTrackCount));
    }
}
