// JAFFY: port of upstream src/style/grid.rs —
// `impl<T: GridCoordinate> Line<GenericGridPlacement<T>>` (generic impl →
// generic static; SPECIALIZATIONS Ext pattern).
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.compute.grid.types.coordinates.GridCoordinate;
import net.morphismmc.jaffy.geometry.Line;

public final class LineGenericGridPlacementExt {

    private LineGenericGridPlacementExt() {}

    /// `indefinite_span` — resolves the span for an indefinite placement (a
    /// placement that does not consist of two `Line`s). Panics if called on a
    /// definite placement.
    public static <T extends GridCoordinate> int indefiniteSpan(Line<GenericGridPlacement<T>> self) {
        var start = self.start();
        var end = self.end();
        // Upstream match arms, in order:
        if (start instanceof GenericGridPlacement.Line<T> && end instanceof GenericGridPlacement.Auto<T>) {
            return 1;
        }
        if (start instanceof GenericGridPlacement.Auto<T> && end instanceof GenericGridPlacement.Line<T>) {
            return 1;
        }
        if (start instanceof GenericGridPlacement.Auto<T> && end instanceof GenericGridPlacement.Auto<T>) {
            return 1;
        }
        if (start instanceof GenericGridPlacement.Line<T> && end instanceof GenericGridPlacement.Span<T> s) {
            return s.span();
        }
        if (start instanceof GenericGridPlacement.Span<T> s && end instanceof GenericGridPlacement.Line<T>) {
            return s.span();
        }
        if (start instanceof GenericGridPlacement.Span<T> s && end instanceof GenericGridPlacement.Auto<T>) {
            return s.span();
        }
        if (start instanceof GenericGridPlacement.Auto<T> && end instanceof GenericGridPlacement.Span<T> s) {
            return s.span();
        }
        if (start instanceof GenericGridPlacement.Span<T> s && end instanceof GenericGridPlacement.Span<T>) {
            return s.span();
        }
        throw new IllegalStateException(
                "indefinite_span should only be called on indefinite grid tracks"); // JAFFY(GAP-010)
    }
}
