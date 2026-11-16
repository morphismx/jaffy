// JAFFY: port of upstream src/compute/grid/mod.rs —
// `DetailedGridInfo<S: CheapCloneStr>` (specialised to String, GAP-017).
//
// `impl fmt::Write` -> StringBuilder (infallible); `Option<(Point, Size)>`
// -> @Nullable ItemGridArea; `areas` field is private upstream and stays
// private here (constructed only inside this package).
// `Clone` -> copy(); `PartialEq` -> equals; `Debug` -> toString.
package net.morphismmc.jaffy.compute.grid;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import net.morphismmc.jaffy.compute.grid.types.named.AreaLines;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.grid.GridAreaAxis;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.just.math.F32;


/// Information from the computation of grid
public final class DetailedGridInfo {

    /// <https://drafts.csswg.org/css-grid-1/#grid-row>
    public final DetailedGridTracksInfo rows;
    /// <https://drafts.csswg.org/css-grid-1/#grid-column>
    public final DetailedGridTracksInfo columns;
    /// <https://drafts.csswg.org/css-grid-1/#grid-items>
    public final List<DetailedGridItemsInfo> items;
    /// Grid area edge lookup used to resolve the grid placements of absolutely positioned children
    private final Map<String, AreaLines> areas;

    /// Constructed inside the module (upstream uses a struct literal where the
    /// `areas` field is private to the module).
    DetailedGridInfo(
            DetailedGridTracksInfo rows,
            DetailedGridTracksInfo columns,
            List<DetailedGridItemsInfo> items,
            Map<String, AreaLines> areas) {
        this.rows = rows;
        this.columns = columns;
        this.items = items;
        this.areas = areas;
    }

    /// `Clone` — shallow per upstream derive (Vec/Map contents shared, matching
    /// `#[derive(Clone)]` on cheap-clone collections; the value lists are copied
    /// like Vec but the map clone is cheap-cloned upstream).
    public DetailedGridInfo copy() {
        return new DetailedGridInfo(rows.copy(), columns.copy(), List.copyOf(items), areas);
    }

    /// Write the used row track sizes and line names to the passed writer in the resolved value
    /// format of the `grid-template-rows` property
    /// (see <https://www.w3.org/TR/css-grid-1/#resolved-track-list>)
    /// Track sizes are divided by `zoom` (the container's effective CSS `zoom`, or `1.0`) to give unzoomed px.
    public void writeGridTemplateRows(StringBuilder out, float zoom) {
        rows.writeTrackList(out, zoom);
    }

    /// Write the used column track sizes and line names to the passed writer in the resolved value
    /// format of the `grid-template-columns` property
    /// (see <https://www.w3.org/TR/css-grid-1/#resolved-track-list>)
    /// Track sizes are divided by `zoom` (the container's effective CSS `zoom`, or `1.0`) to give unzoomed px.
    public void writeGridTemplateColumns(StringBuilder out, float zoom) {
        columns.writeTrackList(out, zoom);
    }

    /// Serialize the used row track sizes and line names in the resolved value format of the
    /// `grid-template-rows` property (see <https://www.w3.org/TR/css-grid-1/#resolved-track-list>)
    /// Track sizes are divided by `zoom` (the container's effective CSS `zoom`, or `1.0`) to give unzoomed px.
    public String gridTemplateRows(float zoom) {
        return rows.toTrackListString(zoom);
    }

    /// Serialize the used column track sizes and line names in the resolved value format of the
    /// `grid-template-columns` property (see <https://www.w3.org/TR/css-grid-1/#resolved-track-list>)
    /// Track sizes are divided by `zoom` (the container's effective CSS `zoom`, or `1.0`) to give unzoomed px.
    public String gridTemplateColumns(float zoom) {
        return columns.toTrackListString(zoom);
    }

    /// Resolve the physical grid area for an absolutely positioned box from its grid placement.
    /// The padding box and returned area use coordinates relative to the grid container's border box.
    public RectF32 resolveAbsoluteGridArea(
            Line<GridPlacement> gridRow,
            Line<GridPlacement> gridColumn,
            Direction direction,
            RectF32 paddingBox) {
        LineF32 cols = columns.resolveAbsoluteGridAxis(
                gridColumn, areas, GridAreaAxis.COLUMN, paddingBox.left(), paddingBox.right(),
                direction.isRtl());
        LineF32 rs = rows.resolveAbsoluteGridAxis(
                gridRow, areas, GridAreaAxis.ROW, paddingBox.top(), paddingBox.bottom(), false);
        return new RectF32(cols.start(), cols.end(), rs.start(), rs.end());
    }

    /// `(Point<f32>, Size<f32>)` return of `item_grid_area`
    public record ItemGridArea(PointF32 position, SizeF32 size) {}

    /// Compute the location and size of the grid area occupied by the item at `itemIndex` (an
    /// index into {@link DetailedGridInfo#items}), relative to the grid container's border box.
    ///
    /// The edges resolve to the edges of the tracks bounding the item's grid area (a start line
    /// resolves to the start of the track that follows it and an end line to the end of the track
    /// that precedes it), so the area excludes any gutter or content-alignment spacing around it.
    ///
    /// Returns `None` if `itemIndex` is out of bounds.
    public @Nullable ItemGridArea itemGridArea(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= items.size()) {
            return null;
        }
        DetailedGridItemsInfo item = items.get(itemIndex);
        LineF32 startCol = columns.positions.get(item.columnStart() - 1);
        LineF32 endCol = columns.positions.get(item.columnEnd() - 2);
        float left = F32.min(startCol.start(), endCol.start());
        float right = F32.max(startCol.end(), endCol.end());
        float top = rows.positions.get(item.rowStart() - 1).start();
        float bottom = rows.positions.get(item.rowEnd() - 2).end();
        return new ItemGridArea(
                new PointF32(left, top), new SizeF32(right - left, bottom - top));
    }

    /// `PartialEq`
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DetailedGridInfo other)) {
            return false;
        }
        return rows.equals(other.rows)
                && columns.equals(other.columns)
                && items.equals(other.items)
                && areas.equals(other.areas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, columns, items, areas);
    }

    /// `Debug` — approximate Rust field format
    @Override
    public String toString() {
        return "DetailedGridInfo { rows: " + rows + ", columns: " + columns
                + ", items: " + items + ", areas: " + areas + " }";
    }
}
