// JAFFY: upstream=src/compute/grid/types/named.rs
// JAFFY-NOTE: `AreaLines`
//
// `Copy + Clone` -> record (GAP-005); `line_mut() -> &mut u32` cannot exist on
// a record, so the two use sites are expressed as `lineRaw` + `withLine`
// (GAP-018-style field-level update: callers write the updated value back into
// the containing map). `pub(crate)` -> public because `GridAreasMap` values
// travel to other packages; the accessor methods stay package-private
// (module-private upstream).
package net.morphismmc.jaffy.compute.grid.types.named;

import net.morphismmc.jaffy.style.grid.GridAreaAxis;
import net.morphismmc.jaffy.style.grid.GridAreaEnd;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

/// The one-indexed positions of the first line that starts and the first line that ends a named grid area in
/// each axis, i.e. the first line named `<area-name>-start`/`<area-name>-end` (explicitly or by
/// `grid-template-areas`). A value of 0 means that there is no such line.
public record AreaLines(int rowStart, int rowEnd, int columnStart, int columnEnd) {

    /// `Default::default()`
    public static final AreaLines DEFAULT = new AreaLines(0, 0, 0, 0);

    /// The raw field for the passed edge of the area (0 if there is none).
    /// (`line_mut`'s read half — module-private upstream.)
    int lineRaw(GridAreaAxis axis, GridAreaEnd end) {
        return switch (axis) {
            case ROW -> end == GridAreaEnd.START ? rowStart : rowEnd;
            case COLUMN -> end == GridAreaEnd.START ? columnStart : columnEnd;
        };
    }

    /// `line_mut`'s write half — returns a copy with the passed edge's field set
    /// to `value` (module-private upstream).
    AreaLines withLine(GridAreaAxis axis, GridAreaEnd end, int value) {
        return switch (axis) {
            case ROW ->
                end == GridAreaEnd.START
                        ? new AreaLines(value, rowEnd, columnStart, columnEnd)
                        : new AreaLines(rowStart, value, columnStart, columnEnd);
            case COLUMN ->
                end == GridAreaEnd.START
                        ? new AreaLines(rowStart, rowEnd, value, columnEnd)
                        : new AreaLines(rowStart, rowEnd, columnStart, value);
        };
    }

    /// The line for the passed edge of the area (`None` if there is none)
    /// `Option<&u32>` -> @Opt @Unsigned int, OptI32.NONE (module-private upstream).
    @Opt
    @Unsigned
    int line(GridAreaAxis axis, GridAreaEnd end) {
        int line = lineRaw(axis, end);
        return line != 0 ? line : OptI32.NONE;
    }

    /// `#[derive(Debug)]`
    @Override
    public String toString() {
        return "AreaLines { row_start: " + rowStart + ", row_end: " + rowEnd + ", column_start: " + columnStart
                + ", column_end: " + columnEnd + " }";
    }
}
