// JAFFY: upstream=src/compute/grid/util/test_helpers.rs
// JAFFY-NOTE: test-only
// helpers (`#[cfg(test)] pub(super)` upstream -> src/test/java here).
//
// The three traits (`CreateParentTestNode`, `CreateChildTestNode`,
// `CreateExpectedPlacement`) each have exactly one impl — on tuples, which
// Java cannot express — so they flatten to static methods:
//   `(f32,f32,i32,i32)::into_grid`            -> intoGrid(w, h, cols, rows)
//   `(GridPlacement x4)::into_grid_child`     -> intoGridChild(cs, ce, rs, re)
//   `(i16,i16,i16,i16)::into_oz`              -> intoOz(cs, ce, rs, re)
// `grid_items`' `(usize, &Style)` pairs carry a caller-chosen node id which
// upstream reuses for BOTH NodeId::from(index) and source_order — flattened
// to the IdStyle record (callers supply arbitrary ids, e.g. 1..4).
package net.morphismmc.jaffy.compute.grid.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;
import net.morphismmc.jaffy.compute.grid.types.named.NamedLineResolver;
import net.morphismmc.jaffy.geometry.InBothAbsAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateComponent;
import net.morphismmc.jaffy.style.grid.LineNonNamedGridPlacementExt;

/// Helpers for use in unit tests within the grid module
public final class TestHelpers {
    private TestHelpers() {}

    /// `CreateExpectedPlacement::into_oz` result tuple
    /// (col_start, col_end, row_start, row_end) in OriginZero coordinates —
    /// matches the `actual_placement` tuple in upstream placement tests.
    public record OzPlacement(
            OriginZeroLine columnStart, OriginZeroLine columnEnd, OriginZeroLine rowStart, OriginZeroLine rowEnd) {}

    /// `(usize, &Style)` pair — upstream reuses the usize for both
    /// `NodeId::from(index)` and `source_order: index as u16`.
    public record IdStyle(int nodeId, Style style) {}

    /// Create a `GridItem` (with start alignment) for each `(nodeId, style)`
    /// pair, as `compute_grid_layout` does for the in-flow children of a
    /// grid container
    public static List<GridItem> gridItems(List<IdStyle> children) {
        ArrayList<GridItem> items = new ArrayList<>(children.size());
        for (IdStyle child : children) {
            items.add(GridItem.newWithStyleAndOrder(
                    child.nodeId(), child.style(), AlignItems.START, AlignItems.START, child.nodeId()));
        }
        return items;
    }

    /// Resolve the named lines in the `grid-row`/`grid-column` styles of the given child styles (and convert them to
    /// origin-zero coordinates), as `compute_grid_layout` does before estimating the grid size and placing items
    public static List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> resolveNamedLines(
            List<Style> childStyles, NamedLineResolver nameResolver, int explicitColCount, int explicitRowCount) {
        ArrayList<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements =
                new ArrayList<>(childStyles.size());
        for (Style style : childStyles) {
            placements.add(new InBothAbsAxis<>(
                    LineNonNamedGridPlacementExt.intoOriginZero(
                            nameResolver.resolveColumnNames(style.gridColumn()), explicitColCount),
                    LineNonNamedGridPlacementExt.intoOriginZero(
                            nameResolver.resolveRowNames(style.gridRow()), explicitRowCount)));
        }
        return placements;
    }

    /// `CreateParentTestNode for (f32, f32, i32, i32)::into_grid`
    /// A grid container style of the given size with `cols`/`rows` `fr(1)` tracks.
    public static Style intoGrid(float width, float height, int cols, int rows) {
        return Style.DEFAULT
                .withDisplay(Display.GRID)
                .withSize(new Size<>(Dimension.length(width), Dimension.length(height)))
                .withGridTemplateColumns(Collections.nCopies(cols, GridTemplateComponent.fromFr(1.0)))
                .withGridTemplateRows(Collections.nCopies(rows, GridTemplateComponent.fromFr(1.0)));
    }

    /// `CreateChildTestNode for (GridPlacement x4)::into_grid_child`
    /// A grid child style with the given `grid-column`/`grid-row` placements.
    public static Style intoGridChild(
            GridPlacement columnStart, GridPlacement columnEnd, GridPlacement rowStart, GridPlacement rowEnd) {
        return Style.DEFAULT
                .withDisplay(Display.GRID)
                .withGridColumn(new Line<>(columnStart, columnEnd))
                .withGridRow(new Line<>(rowStart, rowEnd));
    }

    /// `CreateExpectedPlacement for (i16, i16, i16, i16)::into_oz`
    public static OzPlacement intoOz(int columnStart, int columnEnd, int rowStart, int rowEnd) {
        return new OzPlacement(
                new OriginZeroLine(columnStart), new OriginZeroLine(columnEnd),
                new OriginZeroLine(rowStart), new OriginZeroLine(rowEnd));
    }
}
