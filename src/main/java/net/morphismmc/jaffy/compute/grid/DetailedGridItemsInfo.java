// JAFFY: port of upstream src/compute/grid/mod.rs — `DetailedGridItemsInfo`.
//
// `#[derive(Debug, Clone, PartialEq)]` -> record (equals/hashCode/toString)
// + copy(). u16 -> int (GAP-014).
package net.morphismmc.jaffy.compute.grid;

import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;

/// Grid area information from the placement algorithm
///
/// The values are 1-indexed grid line numbers bounding the area.
/// This matches the Chrome and Firefox's format as of 2nd Jan 2024.
public record DetailedGridItemsInfo(
        /// row-start with 1-indexed grid line numbers
        int rowStart,
        /// row-end with 1-indexed grid line numbers
        int rowEnd,
        /// column-start with 1-indexed grid line numbers
        int columnStart,
        /// column-end with 1-indexed grid line numbers
        int columnEnd) {

    /// `Clone` — record is immutable, so a shared instance is observably
    /// identical (GAP-005).
    public DetailedGridItemsInfo copy() {
        return this;
    }

    /// Conversion from the indexes of Vec<GridTrack> into 1-indexed grid line numbers.
    /// See `GridItem::row_indexes` / `GridItem::column_indexes`
    private static int toOneIndexedGridLine(int gridTrackIndex) {
        return gridTrackIndex / 2 + 1;
    }

    /// Construct from GridItems (`pub(super)`-module fn -> package-private)
    static DetailedGridItemsInfo fromGridItem(GridItem gridItem) {
        return new DetailedGridItemsInfo(
                toOneIndexedGridLine(gridItem.rowIndexes.start()),
                toOneIndexedGridLine(gridItem.rowIndexes.end()),
                toOneIndexedGridLine(gridItem.columnIndexes.start()),
                toOneIndexedGridLine(gridItem.columnIndexes.end()));
    }
}
