// JAFFY: port of upstream src/compute/grid/types/coordinates.rs —
// `impl Line<OriginZeroLine>` block (SPECIALIZATIONS Ext pattern).
package net.morphismmc.jaffy.compute.grid.types.coordinates;

import net.morphismmc.jaffy.geometry.Line;

public final class LineOriginZeroLineExt {

    private LineOriginZeroLineExt() {}

    /// The number of tracks between the start and end lines
    public static int span(Line<OriginZeroLine> self) {
        int diff = self.end().value() - self.start().value();
        return diff > 0 ? diff : 0; // max(diff, 0) as u16 — JAFFY(GAP-014)
    }
}
