// JAFFY: upstream=src/compute/grid/placement.rs
//
// `pub(super)` -> public (called from compute.grid.mod / tests);
// `ItemPlacement` alias -> List<InBothAbsAxis<Line<GenericGridPlacement<
// OriginZeroLine>>>>; (Line,Line) tuples -> private record or out params
// pattern -> pair records; (OZ,OZ) grid_position -> two locals;
// `#[cfg(test)] println!` diagnostics are dropped (Java has no cfg(test)
// println channel; staticCheck bans System.out);
// i16 saturating_add -> Integer.min(+1, Short.MAX_VALUE);
// i32-clamped construction inlined; usize/len -> int (GAP-014);
// PartialOrd on OriginZeroLine -> .value() compare.
package net.morphismmc.jaffy.compute.grid;

import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.cell_occupancy.CellOccupancyMatrix;
import net.morphismmc.jaffy.compute.grid.types.cell_occupancy.CellOccupancyState;
import net.morphismmc.jaffy.compute.grid.types.coordinates.Coordinates;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.InBothAbsAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.LineGenericGridPlacementExt;
import net.morphismmc.jaffy.style.grid.LineOriginZeroGridPlacementExt;

/// Implements placing items in the grid and resolving the implicit grid.
/// <https://www.w3.org/TR/css-grid-1/#placement>
public final class Placement {
    private Placement() {}

    /// `(Line<OriginZeroLine>, Line<OriginZeroLine>)` — (primary_span, secondary_span)
    private record Spans(Line<OriginZeroLine> primary, Line<OriginZeroLine> secondary) {}

    /// Advances the cursor by one track.
    /// `position.0.saturating_add(1)` on i16
    /// (module-private upstream — the unit tests call it; package-private)
    static OriginZeroLine advancePosition(OriginZeroLine position) {
        return new OriginZeroLine(Integer.min(position.value() + 1, Short.MAX_VALUE));
    }

    /// Resolves an indefinite span starting at `position`.
    /// (module-private upstream — the unit tests call it; package-private)
    static Line<OriginZeroLine> resolveIndefiniteGridSpan(OriginZeroLine position, int span) {
        // `let line = |value: i32| OriginZeroLine(value.clamp(i16::MIN as i32, i16::MAX as i32) as i16)`
        return new Line<>(clampedToI16(position.value()), clampedToI16(position.value() + span));
    }

    private static OriginZeroLine clampedToI16(int value) {
        return new OriginZeroLine(Integer.max(Short.MIN_VALUE, Integer.min(value, Short.MAX_VALUE)));
    }

    /// 8.5. Grid Item Placement Algorithm
    /// Place items into the grid, generating new rows/column into the implicit grid as required
    ///
    /// `items` and `placements` are parallel slices in document order: `placements[i]` holds the
    /// `grid-row`/`grid-column`
    /// styles of `items[i]`, with named lines already resolved. The placement passes run over these (small) values
    /// rather
    /// than re-reading the children's styles.
    ///
    /// [Specification](https://www.w3.org/TR/css-grid-2/#auto-placement-algo)
    /// `placements` is `&[ItemPlacement]` where `ItemPlacement =
    /// InBothAbsAxis<Line<OriginZeroGridPlacement>>`.
    public static void placeGridItems(
            CellOccupancyMatrix cellOccupancyMatrix,
            List<GridItem> items,
            List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements,
            GridAutoFlow gridAutoFlow) {
        // debug_assert_eq!(items.len(), placements.len()) — release-only check upstream; omitted
        AbsoluteAxis primaryAxis = gridAutoFlow.primaryAxis();
        AbsoluteAxis secondaryAxis = primaryAxis.otherAxis();

        // 1. Place children with definite positions
        for (int i = 0; i < items.size(); i++) {
            GridItem item = items.get(i);
            InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>> placement = placements.get(i);
            if (!(LineOriginZeroGridPlacementExt.isDefinite(placement.horizontal())
                    && LineOriginZeroGridPlacementExt.isDefinite(placement.vertical()))) {
                continue;
            }

            Spans spans = placeDefiniteGridItem(placement, primaryAxis);
            recordGridPlacement(
                    cellOccupancyMatrix,
                    item,
                    primaryAxis,
                    spans.primary,
                    spans.secondary,
                    CellOccupancyState.DEFINITELY_PLACED);
        }

        // 2. Place remaining children with definite secondary axis positions
        for (int i = 0; i < items.size(); i++) {
            GridItem item = items.get(i);
            InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>> placement = placements.get(i);
            if (!LineOriginZeroGridPlacementExt.isDefinite(placement.get(secondaryAxis))
                    || LineOriginZeroGridPlacementExt.isDefinite(placement.get(primaryAxis))) {
                continue;
            }

            Spans spans = placeDefiniteSecondaryAxisItem(cellOccupancyMatrix, placement, gridAutoFlow);

            recordGridPlacement(
                    cellOccupancyMatrix,
                    item,
                    primaryAxis,
                    spans.primary,
                    spans.secondary,
                    CellOccupancyState.AUTO_PLACED);
        }

        // 3. Determine the number of columns in the implicit grid
        // By the time we get to this point in the execution, this is actually already accounted for:
        //
        // 3.1 Start with the columns from the explicit grid
        //        => Handled by grid size estimate which is used to pre-size the GridOccupancyMatrix
        //
        // 3.2 Among all the items with a definite column position (explicitly positioned items, items positioned in
        //     the previous step, and items not yet positioned but with a definite column) add columns to the beginning
        //     and end of the implicit grid as necessary to accommodate those items.
        //        => Handled by expand_to_fit_range which expands the GridOccupancyMatrix as necessary
        //            -> Called by mark_area_as
        //            -> Called by record_grid_placement
        //
        // 3.3 If the largest column span among all the items without a definite column position is larger than the
        //     width of the implicit grid, add columns to the end of the implicit grid to accommodate that column span.
        //        => Handled by grid size estimate which is used to pre-size the GridOccupancyMatrix

        // 4. Position the remaining grid items
        // (which either have definite position only in the secondary axis or indefinite positions in both axis)
        OriginZeroLine primaryAxisGridStartLine =
                cellOccupancyMatrix.trackCounts(primaryAxis).implicitStartLine();
        OriginZeroLine secondaryAxisGridStartLine =
                cellOccupancyMatrix.trackCounts(secondaryAxis).implicitStartLine();
        // grid_position = (primary_axis_grid_start_line, secondary_axis_grid_start_line)
        OriginZeroLine primaryIdx = primaryAxisGridStartLine;
        OriginZeroLine secondaryIdx = secondaryAxisGridStartLine;
        for (int i = 0; i < items.size(); i++) {
            GridItem item = items.get(i);
            InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>> placement = placements.get(i);
            if (LineOriginZeroGridPlacementExt.isDefinite(placement.get(secondaryAxis))) {
                continue;
            }

            // Compute placement
            Spans spans = placeIndefinitelyPositionedItem(
                    cellOccupancyMatrix, placement, gridAutoFlow, primaryIdx, secondaryIdx);

            // Record item
            recordGridPlacement(
                    cellOccupancyMatrix,
                    item,
                    primaryAxis,
                    spans.primary,
                    spans.secondary,
                    CellOccupancyState.AUTO_PLACED);

            // If using the "dense" placement algorithm then reset the grid position back to grid_start_position
            // ready for the next item. Otherwise set it to the position of the current item so that the next item
            // it placed after it.
            if (gridAutoFlow.isDense()) {
                primaryIdx = primaryAxisGridStartLine;
                secondaryIdx = secondaryAxisGridStartLine;
            } else {
                primaryIdx = spans.primary.end();
                secondaryIdx = spans.secondary.start();
            }
        }
    }

    /// 8.5. Grid Item Placement Algorithm
    /// Place a single definitely placed item into the grid
    private static Spans placeDefiniteGridItem(
            InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>> placement, AbsoluteAxis primaryAxis) {
        // Resolve spans to tracks
        Line<OriginZeroLine> primarySpan =
                LineOriginZeroGridPlacementExt.resolveDefiniteGridLines(placement.get(primaryAxis));
        Line<OriginZeroLine> secondarySpan =
                LineOriginZeroGridPlacementExt.resolveDefiniteGridLines(placement.get(primaryAxis.otherAxis()));
        return new Spans(primarySpan, secondarySpan);
    }

    /// 8.5. Grid Item Placement Algorithm
    /// Step 2. Place remaining children with definite secondary axis positions
    private static Spans placeDefiniteSecondaryAxisItem(
            CellOccupancyMatrix cellOccupancyMatrix,
            InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>> placement,
            GridAutoFlow autoFlow) {
        AbsoluteAxis primaryAxis = autoFlow.primaryAxis();
        AbsoluteAxis secondaryAxis = primaryAxis.otherAxis();
        OriginZeroLine primaryAxisGridStartLine =
                cellOccupancyMatrix.trackCounts(primaryAxis).implicitStartLine();

        Line<OriginZeroLine> secondaryAxisPlacement =
                LineOriginZeroGridPlacementExt.resolveDefiniteGridLines(placement.get(secondaryAxis));
        OriginZeroLine position;
        if (autoFlow.isDense()) {
            position = primaryAxisGridStartLine;
        } else {
            OriginZeroLine cursor =
                    cellOccupancyMatrix.autoPlacementCursor(primaryAxis, secondaryAxisPlacement.start());
            position = cursor != null ? cursor : primaryAxisGridStartLine;
        }
        int primaryAxisSpan = LineGenericGridPlacementExt.indefiniteSpan(placement.get(primaryAxis));

        while (true) {
            Line<OriginZeroLine> primaryAxisPlacement = resolveIndefiniteGridSpan(position, primaryAxisSpan);

            OriginZeroLine collision = cellOccupancyMatrix.lineAreaCollisionJump(
                    primaryAxis, primaryAxisPlacement, secondaryAxisPlacement);

            if (collision == null) {
                return new Spans(primaryAxisPlacement, secondaryAxisPlacement);
            }
            position = collision;
        }
    }

    /// 8.5. Grid Item Placement Algorithm
    /// Step 4. Position the remaining grid items.
    private static Spans placeIndefinitelyPositionedItem(
            CellOccupancyMatrix cellOccupancyMatrix,
            InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>> placement,
            GridAutoFlow autoFlow,
            OriginZeroLine primaryIdx,
            OriginZeroLine secondaryIdx) {
        AbsoluteAxis primaryAxis = autoFlow.primaryAxis();
        AbsoluteAxis secondaryAxis = primaryAxis.otherAxis();

        Line<GenericGridPlacement<OriginZeroLine>> primaryPlacementStyle = placement.get(primaryAxis);
        Line<GenericGridPlacement<OriginZeroLine>> secondaryPlacementStyle = placement.get(secondaryAxis);

        int secondarySpan = LineGenericGridPlacementExt.indefiniteSpan(secondaryPlacementStyle);
        boolean hasDefinitePrimaryAxisPosition = LineOriginZeroGridPlacementExt.isDefinite(primaryPlacementStyle);
        OriginZeroLine primaryAxisGridStartLine =
                cellOccupancyMatrix.trackCounts(primaryAxis).implicitStartLine();
        OriginZeroLine primaryAxisGridEndLine =
                cellOccupancyMatrix.trackCounts(primaryAxis).implicitEndLine();
        OriginZeroLine secondaryAxisGridStartLine =
                cellOccupancyMatrix.trackCounts(secondaryAxis).implicitStartLine();

        if (hasDefinitePrimaryAxisPosition) {
            Line<OriginZeroLine> primarySpan =
                    LineOriginZeroGridPlacementExt.resolveDefiniteGridLines(primaryPlacementStyle);

            // Compute secondary axis starting position for search
            if (autoFlow.isDense()) {
                // If auto-flow is dense then we always search from the first track
                secondaryIdx = secondaryAxisGridStartLine;
            } else if (primarySpan.start().value() < primaryIdx.value()) {
                secondaryIdx = advancePosition(secondaryIdx);
            }

            // Item has fixed primary axis position: so we simply increment the secondary axis position
            // until we find a space that the item fits in
            while (true) {
                Line<OriginZeroLine> secondarySpanLine = resolveIndefiniteGridSpan(secondaryIdx, secondarySpan);

                // If area is occupied, jump the index past the collision and try again
                OriginZeroLine collision =
                        cellOccupancyMatrix.lineAreaCollisionJump(secondaryAxis, secondarySpanLine, primarySpan);
                if (collision != null) {
                    secondaryIdx = collision;
                    continue;
                }

                // Once we find a free space, return that position
                return new Spans(primarySpan, secondarySpanLine);
            }
        } else {
            int primarySpan = LineGenericGridPlacementExt.indefiniteSpan(primaryPlacementStyle);

            // Whether the item spans every track in the primary axis. Such an item can only be
            // placed at the primary axis grid start, in a stripe of entirely unoccupied tracks.
            boolean spansAllPrimaryTracks =
                    primarySpan >= cellOccupancyMatrix.trackCounts(primaryAxis).len();

            // Item does not have any fixed axis, so we search along the primary axis until we hit the end of the
            // already existent tracks, and then we reset the primary axis back to zero and increment the secondary
            // axis index. We continue in this vein until we find a space that the item fits in.
            while (true) {
                Line<OriginZeroLine> primarySpanLine = resolveIndefiniteGridSpan(primaryIdx, primarySpan);
                Line<OriginZeroLine> secondarySpanLine = resolveIndefiniteGridSpan(secondaryIdx, secondarySpan);

                // If the primary index is out of bounds, then increment the secondary index and reset the primary
                // index back to the start of the grid
                boolean primaryOutOfBounds = primarySpanLine.end().value() > primaryAxisGridEndLine.value();
                if (primaryOutOfBounds) {
                    // If the span is out of bounds even at the search start position then it can never fit,
                    // as searching only ever moves the span further away from the start of the grid. Bail out
                    // and let `record_grid_placement` clamp the placement into the limited grid
                    if (primaryIdx.equals(primaryAxisGridStartLine)) {
                        return new Spans(primarySpanLine, secondarySpanLine);
                    }
                    secondaryIdx = advancePosition(secondaryIdx);
                    primaryIdx = primaryAxisGridStartLine;
                    continue;
                }

                // If the item spans every primary axis track, it fits if and only if all of the
                // secondary axis tracks it spans are entirely unoccupied. Jump the secondary index
                // past any non-empty tracks in the spanned stripe.
                if (spansAllPrimaryTracks) {
                    OriginZeroLine nextPosition =
                            cellOccupancyMatrix.occupiedTrackJump(secondaryAxis, secondarySpanLine);
                    if (nextPosition != null) {
                        secondaryIdx = nextPosition;
                        primaryIdx = primaryAxisGridStartLine;
                        continue;
                    }
                    return new Spans(primarySpanLine, secondarySpanLine);
                }

                // If area is occupied, jump the primary index past the collision and try again
                OriginZeroLine collision =
                        cellOccupancyMatrix.lineAreaCollisionJump(primaryAxis, primarySpanLine, secondarySpanLine);
                if (collision != null) {
                    primaryIdx = collision;
                    continue;
                }

                // Once we find a free space that's in bounds, return that position
                return new Spans(primarySpanLine, secondarySpanLine);
            }
        }
    }

    /// Clamp a placement into the limited grid, preserving a span of at least 1 track.
    /// Items placed outside of the limited grid are clamped into it.
    ///
    /// See: <https://www.w3.org/TR/css-grid-1/#overlarge-grids>
    private static Line<OriginZeroLine> clampSpanToLimitedGrid(Line<OriginZeroLine> span) {
        int start =
                Integer.max(Coordinates.MIN_OZ_LINE, Integer.min(span.start().value(), Coordinates.MAX_OZ_LINE - 1));
        int end = Integer.max(start + 1, Integer.min(span.end().value(), Coordinates.MAX_OZ_LINE));
        return new Line<>(new OriginZeroLine(start), new OriginZeroLine(end));
    }

    /// Record the grid item's placement in both the CellOccupancyMatrix and the item itself
    /// once a definite placement has been determined
    private static void recordGridPlacement(
            CellOccupancyMatrix cellOccupancyMatrix,
            GridItem item,
            AbsoluteAxis primaryAxis,
            Line<OriginZeroLine> primarySpan,
            Line<OriginZeroLine> secondarySpan,
            CellOccupancyState placementType) {
        // Clamp placements into the limited grid to prevent arithmetic overflow when growing the
        // implicit grid (https://www.w3.org/TR/css-grid-1/#overlarge-grids)
        primarySpan = clampSpanToLimitedGrid(primarySpan);
        secondarySpan = clampSpanToLimitedGrid(secondarySpan);

        // Mark area of grid as occupied
        cellOccupancyMatrix.markAreaAs(primaryAxis, primarySpan, secondarySpan, placementType);

        // Record placement on the item
        switch (primaryAxis) {
            case HORIZONTAL -> {
                item.column = primarySpan;
                item.row = secondarySpan;
            }
            case VERTICAL -> {
                item.column = secondarySpan;
                item.row = primarySpan;
            }
            default -> throw new IllegalStateException("unreachable");
        }
    }
}
