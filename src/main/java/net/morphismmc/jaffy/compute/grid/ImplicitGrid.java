// JAFFY: upstream=src/compute/grid/implicit_grid.rs
//
// `pub(crate)` -> public; `ItemPlacement` alias ->
// InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>; the two n-tuple
// returns -> InBothAbsAxis<TrackCounts> (horizontal=columns, vertical=rows)
// and private records; `u16`/`i16` -> int (GAP-014); `min`/`max` on
// OriginZeroLine -> .value() compare; `Auto | Span(_)` match arms -> negated
// `instanceof Line` (the placement union is closed).
package net.morphismmc.jaffy.compute.grid;

import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.Coordinates;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.InBothAbsAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.LineGenericGridPlacementExt;

/// This module is not required for spec compliance, but is used as a performance optimisation
/// to reduce the number of allocations required when creating a grid.
public final class ImplicitGrid {
    private ImplicitGrid() {}

    /// `(OriginZeroLine, OriginZeroLine, u16)` — min line, max line, span
    /// (package-private: `child_min_line_max_line_span` is module-private upstream)
    record MinMaxSpan(OriginZeroLine min, OriginZeroLine max, int span) {}

    /// Estimate the number of rows and columns in the grid
    /// This is used as a performance optimisation to pre-size vectors and reduce allocations. It also forms a necessary
    /// step
    /// in the auto-placement
    ///   - The estimates for the explicit and negative implicit track counts are exact.
    ///   - However, the estimates for the positive explicit track count is a lower bound as auto-placement can affect
    /// this
    ///     in ways which are impossible to predict until the auto-placement algorithm is run.
    ///
    /// Note that this function internally mixes use of grid track numbers and grid line numbers
    /// `placements` is `&[ItemPlacement]` where
    /// `ItemPlacement = InBothAbsAxis<Line<OriginZeroGridPlacement>>`.
    /// Returns `(columns, rows)` as `InBothAbsAxis<TrackCounts>` (horizontal=columns, vertical=rows).
    public static InBothAbsAxis<TrackCounts> computeGridSizeEstimate(
            int explicitColCount,
            int explicitRowCount,
            List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements) {
        // Iterate over children, producing an estimate of the min and max grid lines (in origin-zero coordinates where)
        // along with the span of each item
        ChildPositions positions = getKnownChildPositions(placements);
        OriginZeroLine colMin = positions.colMin;
        OriginZeroLine colMax = positions.colMax;
        int colMaxSpan = positions.colMaxSpan;
        OriginZeroLine rowMin = positions.rowMin;
        OriginZeroLine rowMax = positions.rowMax;
        int rowMaxSpan = positions.rowMaxSpan;

        // Compute *track* count estimates for each axis from:
        //   - The explicit track counts
        //   - The origin-zero coordinate min and max grid line variables
        int negativeImplicitInlineTracks = colMin.impliedNegativeImplicitTracks();
        int explicitInlineTracks = explicitColCount;
        int positiveImplicitInlineTracks = colMax.impliedPositiveImplicitTracks(explicitColCount);
        int negativeImplicitBlockTracks = rowMin.impliedNegativeImplicitTracks();
        int explicitBlockTracks = explicitRowCount;
        int positiveImplicitBlockTracks = rowMax.impliedPositiveImplicitTracks(explicitRowCount);

        // In each axis, adjust positive track estimate if any items have a span that does not fit within
        // the total number of tracks in the estimate
        int totInlineTracks = negativeImplicitInlineTracks + explicitInlineTracks + positiveImplicitInlineTracks;
        if (totInlineTracks < colMaxSpan) {
            positiveImplicitInlineTracks = colMaxSpan - explicitInlineTracks - negativeImplicitInlineTracks;
        }

        int totBlockTracks = negativeImplicitBlockTracks + explicitBlockTracks + positiveImplicitBlockTracks;
        if (totBlockTracks < rowMaxSpan) {
            positiveImplicitBlockTracks = rowMaxSpan - explicitBlockTracks - negativeImplicitBlockTracks;
        }

        TrackCounts columnCounts =
                TrackCounts.fromRaw(negativeImplicitInlineTracks, explicitInlineTracks, positiveImplicitInlineTracks);
        TrackCounts rowCounts =
                TrackCounts.fromRaw(negativeImplicitBlockTracks, explicitBlockTracks, positiveImplicitBlockTracks);
        return new InBothAbsAxis<>(columnCounts, rowCounts);
    }

    /// `(OriginZeroLine, OriginZeroLine, u16, OriginZeroLine, OriginZeroLine, u16)`
    private record ChildPositions(
            OriginZeroLine colMin,
            OriginZeroLine colMax,
            int colMaxSpan,
            OriginZeroLine rowMin,
            OriginZeroLine rowMax,
            int rowMaxSpan) {}

    /// Iterate over the children's `grid-row`/`grid-column` styles (named lines resolved), producing an estimate of the
    /// min and max grid *lines* along with the span of each item
    ///
    /// Min and max grid lines are returned in origin-zero coordinates)
    /// The span is measured in tracks spanned
    private static ChildPositions getKnownChildPositions(
            List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements) {
        OriginZeroLine colMin = new OriginZeroLine(0);
        OriginZeroLine colMax = new OriginZeroLine(0);
        int colMaxSpan = 0;
        OriginZeroLine rowMin = new OriginZeroLine(0);
        OriginZeroLine rowMax = new OriginZeroLine(0);
        int rowMaxSpan = 0;
        for (InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>> placement : placements) {
            // Note: that the children reference the lines in between (and around) the tracks not tracks themselves,
            // and thus we must subtract 1 to get an accurate estimate of the number of tracks
            MinMaxSpan childCol = childMinLineMaxLineSpan(placement.horizontal());
            MinMaxSpan childRow = childMinLineMaxLineSpan(placement.vertical());

            colMin = colMin.value() < childCol.min.value() ? colMin : childCol.min;
            colMax = colMax.value() > childCol.max.value() ? colMax : childCol.max;
            colMaxSpan = Integer.max(colMaxSpan, childCol.span);
            rowMin = rowMin.value() < childRow.min.value() ? rowMin : childRow.min;
            rowMax = rowMax.value() > childRow.max.value() ? rowMax : childRow.max;
            rowMaxSpan = Integer.max(rowMaxSpan, childRow.span);
        }
        return new ChildPositions(colMin, colMax, colMaxSpan, rowMin, rowMax, rowMaxSpan);
    }

    /// Helper function for `computeGridSizeEstimate`
    /// Produces a conservative estimate of the greatest and smallest grid lines used by a single grid item
    ///
    /// Values are returned in origin-zero coordinates
    static MinMaxSpan childMinLineMaxLineSpan(Line<GenericGridPlacement<OriginZeroLine>> ozLine) {
        // 8.3.1. Grid Placement Conflict Handling
        // A. If the placement for a grid item contains two lines, and the start line is further end-ward than the end
        //    line, swap the two lines.
        // B. If the start line is equal to the end line, remove the end line.
        // C. If the placement contains two spans, remove the one contributed by the end grid-placement property.
        // D. If the placement contains only a span for a named line, replace it with a span of 1.

        GenericGridPlacement<OriginZeroLine> start = ozLine.start();
        GenericGridPlacement<OriginZeroLine> end = ozLine.end();

        OriginZeroLine min;
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> s
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> e) {
            // Both tracks specified — see rules A and B above
            min = s.line().equals(e.line())
                    ? s.line()
                    : (s.line().value() < e.line().value() ? s.line() : e.line());
        } else if (start instanceof GenericGridPlacement.Line<OriginZeroLine> s) {
            // Start track specified (end is Auto or Span)
            min = s.line();
        } else if (end instanceof GenericGridPlacement.Line<OriginZeroLine> e) {
            // End track specified: an auto start with a definite end resolves to a span of 1
            // ending at that line
            if (start instanceof GenericGridPlacement.Auto<OriginZeroLine>) {
                min = e.line().sub(1);
            } else { // start is Span
                min = e.line().sub(((GenericGridPlacement.Span<OriginZeroLine>) start).span());
            }
        } else {
            // Only spans or autos — ignored here (accounted for separately)
            min = new OriginZeroLine(0);
        }

        OriginZeroLine max;
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> s
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> e) {
            // Both tracks specified — see rules A and B above
            max = s.line().equals(e.line())
                    ? s.line().add(1)
                    : (s.line().value() > e.line().value() ? s.line() : e.line());
        } else if (start instanceof GenericGridPlacement.Line<OriginZeroLine> s) {
            // Start track specified
            if (end instanceof GenericGridPlacement.Auto<OriginZeroLine>) {
                max = s.line().add(1);
            } else { // end is Span
                max = s.line().add(((GenericGridPlacement.Span<OriginZeroLine>) end).span());
            }
        } else if (end instanceof GenericGridPlacement.Line<OriginZeroLine> e) {
            // End track specified (start is Auto or Span)
            max = e.line();
        } else {
            // Only spans or autos
            max = new OriginZeroLine(0);
        }

        // Calculate span only for indefinitely placed items as we don't need for other items (whose required
        // space will be taken into account by min and max)
        int span = !(start instanceof GenericGridPlacement.Line<OriginZeroLine>)
                        && !(end instanceof GenericGridPlacement.Line<OriginZeroLine>)
                ? LineGenericGridPlacementExt.indefiniteSpan(ozLine)
                : 1;

        // Clamp the min and max lines into the limited grid so that the estimated implicit track counts
        // stay within the maximum track limit (https://www.w3.org/TR/css-grid-1/#overlarge-grids).
        // This matches the clamping of the actual item placements performed during placement.
        OriginZeroLine clampedMin = new OriginZeroLine(Integer.max(min.value(), Coordinates.MIN_OZ_LINE));
        OriginZeroLine clampedMax = new OriginZeroLine(Integer.min(max.value(), Coordinates.MAX_OZ_LINE));

        return new MinMaxSpan(clampedMin, clampedMax, span);
    }
}
