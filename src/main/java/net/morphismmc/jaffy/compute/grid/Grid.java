// JAFFY: upstream=src/compute/grid/mod.rs
// JAFFY-NOTE: the module root file, so
// its top-level items live directly in the compute.grid package (module-root
// convention; only types/mod.rs-style multi-class FILES spawn a subpackage).
//
// Upstream `pub use`/`pub(crate) use` re-exports have no Java counterpart.
// `#[cfg(feature = "content_size")]` code is kept unconditionally (project
// convention: features are always on, GAP-007). `debug_log!` calls map to
// DebugLogger.NODE_LOGGER; `dbg:`-formatted values use RustFmt helpers.
// `Tree: LayoutGridContainer` -> LayoutGridContainer (extends
// LayoutPartialTree); `&mut [GridTrack]` -> List<GridTrack>;
// `Option<f32>` -> @Opt float; u16/usize/u32 -> int (GAP-014);
// `impl Fn`-style closures -> the SAM types defined in this module.
package net.morphismmc.jaffy.compute.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.morphismmc.jaffy.Features;
import net.morphismmc.jaffy.compute.common.ScrollableOverflow.ScrollableOverflowContainer;
import net.morphismmc.jaffy.compute.grid.ExplicitGrid.AutoRepeatStrategy;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.cell_occupancy.CellOccupancyMatrix;
import net.morphismmc.jaffy.compute.grid.types.coordinates.Coordinates;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.compute.grid.types.named.AreaLines;
import net.morphismmc.jaffy.compute.grid.types.named.GridLineNames;
import net.morphismmc.jaffy.compute.grid.types.named.NamedLineResolver;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.jaffy.geometry.InBothAbsAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.LineOptI32;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.BoxGenerationMode;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.ContainingBlockClaims;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.StyleHelpers.SizeAvailableSpaceExt;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridItemStyle;
import net.morphismmc.jaffy.style.grid.LineNonNamedGridPlacementExt;
import net.morphismmc.jaffy.style.grid.LineOriginZeroGridPlacementExt;
import net.morphismmc.jaffy.tree.layout.AxisStaticPosition;
import net.morphismmc.jaffy.tree.layout.Baselines;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.OofCandidate;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.layout.OofPositioningArea;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutGridContainer;
import net.morphismmc.jaffy.util.DebugLogger;
import net.morphismmc.jaffy.util.MaybeMath;
import net.morphismmc.jaffy.util.MaybeResolve;
import net.morphismmc.just.fmt.RustFmt;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

import it.unimi.dsi.fastutil.longs.LongIterator;

/// Grid layout algorithm (`compute_grid_layout`) plus the container constants
/// and static-position area helpers it uses.
public final class Grid {

    private Grid() {}

    /// `pub(crate) struct GridContainerConstants` — `Copy` -> record (immutable
    /// snapshot of per-node style/size resolution).
    private record GridContainerConstants(
            Direction direction,
            Contain contain,
            ContainingBlockClaims containingBlockClaims,
            RectF32 padding,
            RectF32 border,
            SizeF32 paddingBorderSize,
            SizeOptF32 minSize,
            SizeOptF32 maxSize,
            SizeOptF32 preferredSize,
            PointF32 scrollbarGutter,
            boolean isScrollContainer,
            RectF32 contentBoxInset,
            AlignContent alignContent,
            AlignContent justifyContent,
            AlignItems alignItems,
            AlignItems justifyItems,
            Size<AvailableSpace> availableGridSpace,
            SizeOptF32 outerNodeSize,
            SizeOptF32 innerMinSize,
            SizeOptF32 innerMaxSize,
            SizeOptF32 innerNodeSize) {}

    /// `fn compute_container_constants`
    private static GridContainerConstants computeContainerConstants(
            LayoutGridContainer tree, Style style, LayoutInput inputs) {
        SizeOptF32 knownDimensions = inputs.knownDimensions();
        SizeOptF32 parentSize = inputs.parentSize();
        Size<AvailableSpace> availableSpace = inputs.availableSpace();

        Direction direction = style.direction();
        Contain contain = style.contain();
        ContainingBlockClaims containingBlockClaims = style.isContainingBlock();

        // 1. Compute "available grid space"
        // https://www.w3.org/TR/css-grid-1/#available-grid-space
        @Opt float aspectRatio = style.aspectRatio();
        RectF32 padding = style.padding().mapF(p -> p.resolveOrZero(parentSize.width(), tree::calc));
        RectF32 border = style.border().mapF(p -> p.resolveOrZero(parentSize.width(), tree::calc));
        RectF32 paddingBorder = padding.plus(border);
        SizeF32 paddingBorderSize = paddingBorder.sumAxes();
        SizeF32 boxSizingAdjustment = style.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSize : SizeF32.ZERO;

        SizeOptF32 minSize = MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                        style.minSize(), parentSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 maxSize = MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                        style.maxSize(), parentSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 preferredSize = inputs.sizingMode() == SizingMode.INHERENT_SIZE
                ? MaybeResolve.SizeDimensionExt.maybeResolveO(style.size(), parentSize, tree::calc)
                        .maybeApplyAspectRatio(style.aspectRatio())
                        .maybeAddF(boxSizingAdjustment)
                : SizeOptF32.NONE;

        // Scrollbar gutters are reserved when the `overflow` property is set to `Overflow::Scroll`.
        // However, the axis are switched (transposed) because a node that scrolls vertically needs
        // *horizontal* space to be reserved for a scrollbar
        Point<Overflow> overflow = style.overflow();
        PointF32 scrollbarGutter = new PointF32(
                overflow.y() == Overflow.SCROLL ? style.scrollbarWidth() : 0.0f,
                overflow.x() == Overflow.SCROLL ? style.scrollbarWidth() : 0.0f);
        boolean isScrollContainer =
                overflow.x().isScrollContainer() || overflow.y().isScrollContainer();
        RectF32 contentBoxInset = new RectF32(
                paddingBorder.left() + (direction == Direction.RTL ? scrollbarGutter.x() : 0.0f),
                paddingBorder.right() + (direction == Direction.LTR ? scrollbarGutter.x() : 0.0f),
                paddingBorder.top(),
                paddingBorder.bottom() + scrollbarGutter.y());

        // `normal` behaves as `stretch` for grid containers
        AlignContent alignContent = style.gridAlignContent(AbstractAxis.BLOCK);
        AlignContent justifyContent = style.gridAlignContent(AbstractAxis.INLINE);
        AlignItems alignItems = style.alignItems();
        AlignItems justifyItems = style.justifyItems();
        SizeOptF32 knownOrPreferred = knownDimensions.or(preferredSize);
        Size<AvailableSpace> constrainedAvailableSpace = new Size<>(
                OptF32.isSome(knownOrPreferred.width())
                        ? new AvailableSpace.Definite(knownOrPreferred.width())
                        : availableSpace.width(),
                OptF32.isSome(knownOrPreferred.height())
                        ? new AvailableSpace.Definite(knownOrPreferred.height())
                        : availableSpace.height());
        constrainedAvailableSpace = MaybeMath.maybeMaxF(
                MaybeMath.maybeClampO(constrainedAvailableSpace, minSize, maxSize), paddingBorderSize);

        Size<AvailableSpace> availableGridSpace = new Size<>(
                constrainedAvailableSpace
                        .width()
                        .mapDefiniteValue(space -> space - contentBoxInset.horizontalAxisSum()),
                constrainedAvailableSpace
                        .height()
                        .mapDefiniteValue(space -> space - contentBoxInset.verticalAxisSum()));

        SizeOptF32 outerNodeSize =
                knownOrPreferred.maybeClampO(minSize, maxSize).maybeMaxF(paddingBorderSize);

        // The track sizing algorithm operates on the grid container's content box, so the min/max sizes
        // (which are border-box sizes) need converting to content-box sizes before being passed to it
        SizeOptF32 innerMinSize = minSize.maybeSubF(contentBoxInset.sumAxes());
        SizeOptF32 innerMaxSize = maxSize.maybeSubF(contentBoxInset.sumAxes());
        SizeOptF32 innerNodeSize = new SizeOptF32(
                OptF32.map(outerNodeSize.width(), space -> space - contentBoxInset.horizontalAxisSum()),
                OptF32.map(outerNodeSize.height(), space -> space - contentBoxInset.verticalAxisSum()));

        return new GridContainerConstants(
                direction,
                contain,
                containingBlockClaims,
                padding,
                border,
                paddingBorderSize,
                minSize,
                maxSize,
                preferredSize,
                scrollbarGutter,
                isScrollContainer,
                contentBoxInset,
                alignContent,
                justifyContent,
                alignItems,
                justifyItems,
                availableGridSpace,
                outerNodeSize,
                innerMinSize,
                innerMaxSize,
                innerNodeSize);
    }

    /// Grid layout algorithm
    /// This consists of a few phases:
    ///   - Resolving the explicit grid
    ///   - Placing items (which also resolves the implicit grid)
    ///   - Track (row/column) sizing
    ///   - Alignment & Final item placement
    /// (`pub fn compute_grid_layout`)
    public static LayoutOutput computeGridLayout(LayoutGridContainer tree, long node, LayoutInput inputs) {
        SizeOptF32 knownDimensions = inputs.knownDimensions();
        RunMode runMode = inputs.runMode();

        Style style = tree.getGridContainerStyle(node);
        GridContainerConstants constants = computeContainerConstants(tree, style, inputs);
        Direction direction = constants.direction();
        Contain contain = constants.contain();
        ContainingBlockClaims containingBlockClaims = constants.containingBlockClaims();
        RectF32 padding = constants.padding();
        RectF32 border = constants.border();
        SizeF32 paddingBorderSize = constants.paddingBorderSize();
        SizeOptF32 minSize = constants.minSize();
        SizeOptF32 maxSize = constants.maxSize();
        SizeOptF32 preferredSize = constants.preferredSize();
        PointF32 scrollbarGutter = constants.scrollbarGutter();
        boolean isScrollContainer = constants.isScrollContainer();
        RectF32 contentBoxInset = constants.contentBoxInset();
        AlignContent alignContent = constants.alignContent();
        AlignContent justifyContent = constants.justifyContent();
        AlignItems alignItems = constants.alignItems();
        AlignItems justifyItems = constants.justifyItems();
        Size<AvailableSpace> availableGridSpace = constants.availableGridSpace();
        SizeOptF32 outerNodeSize = constants.outerNodeSize();
        SizeOptF32 innerMinSize = constants.innerMinSize();
        SizeOptF32 innerMaxSize = constants.innerMaxSize();
        SizeOptF32 innerNodeSize = constants.innerNodeSize();

        // Note: we avoid accessing the grid rows/columns methods more than once as this can
        // cause an expensive-ish computation. (Upstream binds grid_template_columns/rows and
        // grid_auto_columns/rows to locals and drops them mid-function; in Java these are plain
        // record accessors, so no locals are needed.)

        if (Features.DEBUG) {
            DebugLogger.NODE_LOGGER.labelledDebugLog("parent_size", debugSizeOptF32(inputs.parentSize()));
            DebugLogger.NODE_LOGGER.labelledDebugLog("outer_node_size", debugSizeOptF32(outerNodeSize));
            DebugLogger.NODE_LOGGER.labelledDebugLog("inner_node_size", debugSizeOptF32(innerNodeSize));
        }

        // Short-circuit layout if the container's size is fully determined by the container's size and the run mode
        // is ComputeSize (and thus the container's size is all that we're interested in)
        if (runMode == RunMode.COMPUTE_SIZE) {
            if (OptF32.isSome(outerNodeSize.width()) && OptF32.isSome(outerNodeSize.height())) {
                return LayoutOutput.fromOuterSize(new SizeF32(outerNodeSize.width(), outerNodeSize.height()));
            }

            // We can also short-circuit if the width is known and only the width has been requested.
            if (inputs.axis() == RequestedAxis.HORIZONTAL && OptF32.isSome(outerNodeSize.width())) {
                return LayoutOutput.fromOuterSize(new SizeF32(outerNodeSize.width(), 0.0f));
            }
        }

        // 2. Resolve the explicit grid

        // This is very similar to the inner_node_size except if the inner_node_size is not definite but the node
        // has a min- or max- size style then that will be used in it's place.
        SizeOptF32 autoFitContainerSize = outerNodeSize
                .or(maxSize)
                .or(minSize)
                .maybeClampO(minSize, maxSize)
                .maybeMaxF(paddingBorderSize)
                .maybeSubF(contentBoxInset.sumAxes());

        // If the grid container has a definite size or max size in the relevant axis:
        //   - then the number of repetitions is the largest possible positive integer that does not cause the grid to
        // overflow the content
        //     box of its grid container.
        // Otherwise, if the grid container has a definite min size in the relevant axis:
        //   - then the number of repetitions is the smallest possible positive integer that fulfills that minimum
        // requirement
        // Otherwise, the specified track list repeats only once.
        SizeOptF32 outerOrMax = outerNodeSize.or(maxSize);
        Size<AutoRepeatStrategy> autoRepeatFitStrategy = new Size<>(
                OptF32.isSome(outerOrMax.width())
                        ? AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW
                        : AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW,
                OptF32.isSome(outerOrMax.height())
                        ? AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW
                        : AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW);

        // Compute the number of rows and columns in the explicit grid *template*
        // (explicit tracks from grid_areas are computed separately below)
        ExplicitGrid.ExplicitGridSize colExplicit = ExplicitGrid.computeExplicitGridSizeInAxis(
                style,
                autoFitContainerSize.width(),
                autoRepeatFitStrategy.width(),
                tree::calc,
                AbsoluteAxis.HORIZONTAL);
        ExplicitGrid.ExplicitGridSize rowExplicit = ExplicitGrid.computeExplicitGridSizeInAxis(
                style,
                autoFitContainerSize.height(),
                autoRepeatFitStrategy.height(),
                tree::calc,
                AbsoluteAxis.VERTICAL);
        int colAutoRepetitionCount = colExplicit.autoRepetitionCount();
        int rowAutoRepetitionCount = rowExplicit.autoRepetitionCount();

        NamedLineResolver nameResolver =
                NamedLineResolver.newNamedLineResolver(style, colAutoRepetitionCount, rowAutoRepetitionCount);

        // Clamp the explicit grid to MAX_GRID_TRACKS tracks in each axis
        // https://www.w3.org/TR/css-grid-1/#overlarge-grids
        int explicitColCount = Integer.min(
                Integer.max(colExplicit.trackCount(), nameResolver.areaColumnCount()), Coordinates.MAX_GRID_TRACKS);
        int explicitRowCount = Integer.min(
                Integer.max(rowExplicit.trackCount(), nameResolver.areaRowCount()), Coordinates.MAX_GRID_TRACKS);

        nameResolver.setExplicitColumnCount(explicitColCount);
        nameResolver.setExplicitRowCount(explicitRowCount);

        // Build the per-line names of the explicit grid from the name resolver's collected pairs
        GridLineNames detailedColumnLineNames = nameResolver.detailedLineNames(AbsoluteAxis.HORIZONTAL);
        GridLineNames detailedRowLineNames = nameResolver.detailedLineNames(AbsoluteAxis.VERTICAL);

        // 3. Create the grid items (in document order) and resolve the named lines in their `grid-row`/`grid-column`
        // styles
        // Absolutely positioned children do not take part in grid placement and do not create implicit tracks,
        // so they are excluded here. Each child's style is read and its named lines resolved exactly once; the
        // results drive both the grid size estimate and item placement below.
        int childCount = tree.childCount(node);
        List<GridItem> items = new ArrayList<>(childCount);
        List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements = new ArrayList<>(childCount);
        {
            int index = 0;
            LongIterator childIds = tree.childIds(node);
            while (childIds.hasNext()) {
                long childNode = childIds.nextLong();
                Style childStyle = tree.getGridChildStyle(childNode);
                if (childStyle.boxGenerationMode() == BoxGenerationMode.NONE
                        || childStyle.position().isOutOfFlow()) {
                    index++;
                    continue;
                }
                placements.add(new InBothAbsAxis<>(
                        LineNonNamedGridPlacementExt.intoOriginZero(
                                nameResolver.resolveColumnNames(childStyle.gridColumn()), explicitColCount),
                        LineNonNamedGridPlacementExt.intoOriginZero(
                                nameResolver.resolveRowNames(childStyle.gridRow()), explicitRowCount)));
                items.add(GridItem.newWithStyleAndOrder(childNode, childStyle, alignItems, justifyItems, index));
                index++;
            }
        }

        // 4. Implicit Grid: Estimate Track Counts
        // Estimate the number of rows and columns in the implicit grid (= the entire grid)
        // This is necessary as part of placement. Doing it early here is a perf optimisation to reduce allocations.
        InBothAbsAxis<TrackCounts> estimated =
                ImplicitGrid.computeGridSizeEstimate(explicitColCount, explicitRowCount, placements);
        TrackCounts estColCounts = estimated.horizontal();
        TrackCounts estRowCounts = estimated.vertical();

        // 5. Grid Item Placement
        // Match items (children) to a definite grid position (row start/end and column start/end position)
        CellOccupancyMatrix cellOccupancyMatrix = CellOccupancyMatrix.withTrackCounts(estColCounts, estRowCounts);
        // `items` is in document order from here on (placement only fills in each item's grid area). The track
        // sizing and baseline passes sort references to the items rather than the items themselves.
        Placement.placeGridItems(cellOccupancyMatrix, items, placements, style.gridAutoFlow());

        // Extract track counts from previous step (auto-placement can expand the number of tracks)
        TrackCounts finalColCounts = cellOccupancyMatrix.trackCounts(AbsoluteAxis.HORIZONTAL);
        TrackCounts finalRowCounts = cellOccupancyMatrix.trackCounts(AbsoluteAxis.VERTICAL);

        // 5. Initialize Tracks

        // Initialize (explicit and implicit) grid tracks (and gutters)
        // This resolves the min and max track sizing functions for all tracks and gutters
        List<GridTrack> columns = new ArrayList<>();
        List<GridTrack> rows = new ArrayList<>();
        ExplicitGrid.initializeGridTracks(
                columns,
                finalColCounts,
                style,
                AbsoluteAxis.HORIZONTAL,
                colAutoRepetitionCount,
                cellOccupancyMatrix::columnIsOccupied);
        ExplicitGrid.initializeGridTracks(
                rows,
                finalRowCounts,
                style,
                AbsoluteAxis.VERTICAL,
                rowAutoRepetitionCount,
                cellOccupancyMatrix::rowIsOccupied);

        // upstream drops the style's grid-template/grid-auto lists here (no Java counterpart)

        // 6. Track Sizing

        // Convert grid placements in origin-zero coordinates to indexes into the GridTrack (rows and columns) vectors
        // This computation is relatively trivial, but it requires the final number of negative (implicit) tracks in
        // each axis, and doing it up-front here means we don't have to keep repeating that calculation
        TrackSizing.resolveItemTrackIndexes(items, finalColCounts, finalRowCounts);
        // For each item, and in each axis, determine whether the item crosses any flexible (fr) tracks
        // Record this as a boolean (per-axis) on each item for later use in the track-sizing algorithm
        TrackSizing.determineIfItemCrossesFlexibleOrIntrinsicTracks(items, columns, rows);

        // Determine if the grid has any baseline aligned items
        boolean hasBaselineAlignedItem = false;
        for (GridItem item : items) {
            if (item.participatesInBaselineAlignment()) {
                hasBaselineAlignedItem = true;
                break;
            }
        }

        // Run track sizing algorithm for Inline axis
        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.INLINE,
                innerMinSize.get(AbstractAxis.INLINE),
                innerMaxSize.get(AbstractAxis.INLINE),
                justifyContent,
                alignContent,
                availableGridSpace,
                innerNodeSize,
                columns,
                rows,
                items,
                (track, parentSize, t) -> track.maxTrackSizingFunction.definiteValue(parentSize, t::calc),
                hasBaselineAlignedItem);
        float initialColumnSum = 0.0f;
        for (GridTrack track : columns) {
            initialColumnSum += track.baseSize;
        }
        // inner_node_size.width.or_else(|| initial_column_sum.into())
        innerNodeSize = new SizeOptF32(
                OptF32.isSome(innerNodeSize.width()) ? innerNodeSize.width() : initialColumnSum,
                innerNodeSize.height());

        // If only the container's width has been requested then we can skip sizing the rows entirely (which avoids
        // measuring the height of every item), as column sizing is not re-run when only computing the container's size.
        // Note: the short-circuit above has already handled the case where the container's width is known.
        if (runMode == RunMode.COMPUTE_SIZE && inputs.axis() == RequestedAxis.HORIZONTAL) {
            float width = F32.max(
                    F32.maybeClampO(
                            initialColumnSum + contentBoxInset.horizontalAxisSum(), minSize.width(), maxSize.width()),
                    paddingBorderSize.width());
            return LayoutOutput.fromOuterSize(new SizeF32(width, 0.0f));
        }

        for (GridItem item : items) {
            item.gridAreaSizeCache = null;
        }

        // Run track sizing algorithm for Block axis
        TrackSizing.trackSizingAlgorithm(
                tree,
                AbstractAxis.BLOCK,
                innerMinSize.get(AbstractAxis.BLOCK),
                innerMaxSize.get(AbstractAxis.BLOCK),
                alignContent,
                justifyContent,
                availableGridSpace,
                innerNodeSize,
                rows,
                columns,
                items,
                (track, parentSize, t) -> track.baseSize,
                false); // TODO: Support baseline alignment in the vertical axis
        float initialRowSum = 0.0f;
        for (GridTrack track : rows) {
            initialRowSum += track.baseSize;
        }

        if (Features.DEBUG) {
            DebugLogger.NODE_LOGGER.labelledDebugLog("initial_column_sum", RustFmt.debug(initialColumnSum));
            DebugLogger.NODE_LOGGER.debugLog(debugTrackBaseSizes(columns));
            DebugLogger.NODE_LOGGER.labelledDebugLog("initial_row_sum", RustFmt.debug(initialRowSum));
            DebugLogger.NODE_LOGGER.debugLog(debugTrackBaseSizes(rows));
        }

        // 6. Compute container size
        SizeOptF32 resolvedStyleSize = knownDimensions.or(preferredSize);
        SizeF32 containerBorderBox = new SizeF32(
                F32.max(
                        F32.maybeClampO(
                                OptF32.isSome(resolvedStyleSize.get(AbstractAxis.INLINE))
                                        ? resolvedStyleSize.get(AbstractAxis.INLINE)
                                        : initialColumnSum + contentBoxInset.horizontalAxisSum(),
                                minSize.width(),
                                maxSize.width()),
                        paddingBorderSize.width()),
                F32.max(
                        F32.maybeClampO(
                                OptF32.isSome(resolvedStyleSize.get(AbstractAxis.BLOCK))
                                        ? resolvedStyleSize.get(AbstractAxis.BLOCK)
                                        : initialRowSum + contentBoxInset.verticalAxisSum(),
                                minSize.height(),
                                maxSize.height()),
                        paddingBorderSize.height()));
        SizeF32 containerContentBox = new SizeF32(
                F32.max(0.0f, containerBorderBox.width() - contentBoxInset.horizontalAxisSum()),
                F32.max(0.0f, containerBorderBox.height() - contentBoxInset.verticalAxisSum()));

        // If only the container's size has been requested
        if (runMode == RunMode.COMPUTE_SIZE) {
            return LayoutOutput.fromOuterSize(containerBorderBox);
        }

        // The container's size is now determined, so percentages resolve against its content box when re-running track
        // sizing.
        // This may be a no-op if the container's size was known upfront (in which case it already equals
        // `inner_node_size`).
        innerNodeSize = new SizeOptF32(containerContentBox.width(), containerContentBox.height());

        // Column sizing must be re-run (once) if:
        //   - The grid container's width was initially indefinite and there are any columns with percentage track
        // sizing functions
        //   - Any grid item crossing an intrinsically sized track's min content contribution width has changed
        // TODO: Only rerun sizing for tracks that actually require it rather than for all tracks if any need it.
        boolean rerunColumnSizing;
        boolean intrinsicColumnContributionChanged = false;

        boolean hasPercentageColumn = false;
        for (GridTrack track : columns) {
            if (track.usesPercentage()) {
                hasPercentageColumn = true;
                break;
            }
        }
        boolean hasPercentageRow = false;
        for (GridTrack track : rows) {
            if (track.usesPercentage()) {
                hasPercentageRow = true;
                break;
            }
        }
        boolean parentWidthIndefinite = OptF32.isNone(outerNodeSize.width());
        rerunColumnSizing = parentWidthIndefinite && hasPercentageColumn;

        if (!rerunColumnSizing) {
            // Note: every item must be visited (no short-circuiting) as the closure updates each item's caches
            for (GridItem item : items) {
                if (!item.crossesIntrinsicColumn) {
                    continue;
                }
                SizeOptF32 gridAreaSize = item.gridAreaSize(
                        AbstractAxis.INLINE,
                        columns,
                        rows,
                        innerNodeSize,
                        (track, basis) -> track.baseSize,
                        tree::calc);
                SizeOptF32 availableSpace = gridAreaSize.with(AbstractAxis.INLINE, OptF32.NONE);
                float newMinContentContribution =
                        item.minContentContribution(AbstractAxis.INLINE, tree, gridAreaSize, availableSpace);

                boolean hasChanged = OptF32.isNone(item.minContentContributionCache.width())
                        || newMinContentContribution != item.minContentContributionCache.width();

                item.gridAreaSizeCache = gridAreaSize;
                item.minContentContributionCache =
                        item.minContentContributionCache.with(AbstractAxis.INLINE, newMinContentContribution);
                item.maxContentContributionCache =
                        item.maxContentContributionCache.with(AbstractAxis.INLINE, OptF32.NONE);
                item.minimumContributionCache = item.minimumContributionCache.with(AbstractAxis.INLINE, OptF32.NONE);

                intrinsicColumnContributionChanged |= hasChanged;
            }
            rerunColumnSizing = intrinsicColumnContributionChanged;
        } else {
            // Clear intrinsic width caches
            for (GridItem item : items) {
                item.gridAreaSizeCache = null;
                item.minContentContributionCache =
                        item.minContentContributionCache.with(AbstractAxis.INLINE, OptF32.NONE);
                item.maxContentContributionCache =
                        item.maxContentContributionCache.with(AbstractAxis.INLINE, OptF32.NONE);
                item.minimumContributionCache = item.minimumContributionCache.with(AbstractAxis.INLINE, OptF32.NONE);
            }
        }

        // Row sizing must be re-run (once) if:
        //   - The grid container's height was initially indefinite and there are any rows with percentage track sizing
        // functions
        //   - Column sizing was re-run and any grid item crossing an intrinsically sized track's min content
        // contribution height has changed
        // TODO: Only rerun sizing for tracks that actually require it rather than for all tracks if any need it.
        boolean parentHeightIndefinite = OptF32.isNone(outerNodeSize.height());
        boolean rerunRowSizing = parentHeightIndefinite && hasPercentageRow;
        boolean intrinsicRowContributionChanged = false;

        if (rerunRowSizing) {
            // Clear intrinsic height caches
            for (GridItem item : items) {
                item.gridAreaSizeCache = null;
                item.minContentContributionCache =
                        item.minContentContributionCache.with(AbstractAxis.BLOCK, OptF32.NONE);
                item.maxContentContributionCache =
                        item.maxContentContributionCache.with(AbstractAxis.BLOCK, OptF32.NONE);
                item.minimumContributionCache = item.minimumContributionCache.with(AbstractAxis.BLOCK, OptF32.NONE);
            }
        }

        if (rerunColumnSizing) {
            // Re-run track sizing algorithm for Inline axis
            TrackSizing.trackSizingAlgorithm(
                    tree,
                    AbstractAxis.INLINE,
                    innerMinSize.get(AbstractAxis.INLINE),
                    innerMaxSize.get(AbstractAxis.INLINE),
                    justifyContent,
                    alignContent,
                    availableGridSpace,
                    innerNodeSize,
                    columns,
                    rows,
                    items,
                    (track, parentSize, t) -> track.baseSize,
                    hasBaselineAlignedItem);

            // The column widths may have changed, so the items' intrinsic height contributions need to be recomputed
            // (unless row sizing is being re-run anyway, in which case the caches have already been cleared).
            if (!rerunRowSizing) {
                // Note: every item must be visited (no short-circuiting) as the closure updates each item's caches
                for (GridItem item : items) {
                    if (!item.crossesIntrinsicRow) {
                        continue;
                    }
                    SizeOptF32 gridAreaSize = item.gridAreaSize(
                            AbstractAxis.BLOCK,
                            rows,
                            columns,
                            innerNodeSize,
                            (track, basis) -> track.baseSize,
                            tree::calc);
                    SizeOptF32 availableSpace = gridAreaSize.with(AbstractAxis.BLOCK, OptF32.NONE);
                    float newMinContentContribution =
                            item.minContentContribution(AbstractAxis.BLOCK, tree, gridAreaSize, availableSpace);

                    boolean hasChanged = OptF32.isNone(item.minContentContributionCache.height())
                            || newMinContentContribution != item.minContentContributionCache.height();

                    item.gridAreaSizeCache = gridAreaSize;
                    item.minContentContributionCache =
                            item.minContentContributionCache.with(AbstractAxis.BLOCK, newMinContentContribution);
                    item.maxContentContributionCache =
                            item.maxContentContributionCache.with(AbstractAxis.BLOCK, OptF32.NONE);
                    item.minimumContributionCache = item.minimumContributionCache.with(AbstractAxis.BLOCK, OptF32.NONE);

                    intrinsicRowContributionChanged |= hasChanged;
                }
                rerunRowSizing = intrinsicRowContributionChanged;
            }
        }

        if (rerunRowSizing) {
            // Re-run track sizing algorithm for Block axis
            TrackSizing.trackSizingAlgorithm(
                    tree,
                    AbstractAxis.BLOCK,
                    innerMinSize.get(AbstractAxis.BLOCK),
                    innerMaxSize.get(AbstractAxis.BLOCK),
                    alignContent,
                    justifyContent,
                    availableGridSpace,
                    innerNodeSize,
                    rows,
                    columns,
                    items,
                    (track, parentSize, t) -> track.baseSize,
                    false); // TODO: Support baseline alignment in the vertical axis
        }

        if ((intrinsicColumnContributionChanged && !hasPercentageColumn)
                || (intrinsicRowContributionChanged && !hasPercentageRow)) {
            float finalColumnSum = 0.0f;
            for (GridTrack track : columns) {
                finalColumnSum += track.baseSize;
            }
            float finalRowSum = 0.0f;
            for (GridTrack track : rows) {
                finalRowSum += track.baseSize;
            }

            if (intrinsicColumnContributionChanged && !hasPercentageColumn) {
                float newWidth = F32.max(
                        F32.maybeClampO(
                                OptF32.isSome(resolvedStyleSize.get(AbstractAxis.INLINE))
                                        ? resolvedStyleSize.get(AbstractAxis.INLINE)
                                        : finalColumnSum + contentBoxInset.horizontalAxisSum(),
                                minSize.width(),
                                maxSize.width()),
                        paddingBorderSize.width());
                containerBorderBox = new SizeF32(newWidth, containerBorderBox.height());
                containerContentBox = new SizeF32(
                        F32.max(0.0f, containerBorderBox.width() - contentBoxInset.horizontalAxisSum()),
                        containerContentBox.height());
            }

            if (intrinsicRowContributionChanged && !hasPercentageRow) {
                float newHeight = F32.max(
                        F32.maybeClampO(
                                OptF32.isSome(resolvedStyleSize.get(AbstractAxis.BLOCK))
                                        ? resolvedStyleSize.get(AbstractAxis.BLOCK)
                                        : finalRowSum + contentBoxInset.verticalAxisSum(),
                                minSize.height(),
                                maxSize.height()),
                        paddingBorderSize.height());
                containerBorderBox = new SizeF32(containerBorderBox.width(), newHeight);
                containerContentBox = new SizeF32(
                        containerContentBox.width(),
                        F32.max(0.0f, containerBorderBox.height() - contentBoxInset.verticalAxisSum()));
            }
        }

        // If only the container's size has been requested
        if (runMode == RunMode.COMPUTE_SIZE) {
            return LayoutOutput.fromOuterSize(containerBorderBox);
        }

        // 8. Track Alignment

        // Align columns
        float inlineSizeWithoutScrollbar = F32.max(containerBorderBox.width() - paddingBorderSize.width(), 0.0f);
        float inlineScrollbarGutterForAlignment = F32.min(scrollbarGutter.x(), inlineSizeWithoutScrollbar);
        Alignment.alignTracks(
                containerContentBox.get(AbstractAxis.INLINE),
                new LineF32(
                        padding.left() + (direction.isRtl() ? inlineScrollbarGutterForAlignment : 0.0f),
                        padding.right() + (direction.isRtl() ? 0.0f : inlineScrollbarGutterForAlignment)),
                new LineF32(border.left(), border.right()),
                columns,
                justifyContent,
                direction.isRtl());
        // Align rows
        Alignment.alignTracks(
                containerContentBox.get(AbstractAxis.BLOCK),
                new LineF32(padding.top(), padding.bottom()),
                new LineF32(border.top(), border.bottom()),
                rows,
                alignContent,
                false);

        // 9. Size, Align, and Position Grid Items

        RectF32 itemOverflowRect = RectF32.ZERO;

        // Out-of-flow candidates in document order: direct out-of-flow children of the grid
        // interleaved with candidates bubbled from in-flow children's subtrees. These are laid out
        // by the out-of-flow positioning pass (`compute_oof_layout`), which runs after this algorithm.
        OofCandidates oofCandidates = OofCandidates.none();

        InBothAbsAxis<AlignItems> containerAlignmentStyles = new InBothAbsAxis<>(justifyItems, alignItems);

        RectF32 paddingBoxInset = border.plus(new RectF32(
                direction.isRtl() ? scrollbarGutter.x() : 0.0f,
                direction.isRtl() ? 0.0f : scrollbarGutter.x(),
                0.0f,
                scrollbarGutter.y()));
        ScrollableOverflowContainer overflowContainer = new ScrollableOverflowContainer(
                isScrollContainer,
                new SizeF32(containerBorderBox.width() - paddingBoxInset.horizontalAxisSum(), 0.0f),
                new PointBool(direction.isRtl(), false),
                padding);

        // Position in-flow children (stored in items vector)
        for (int index = 0; index < items.size(); index++) {
            GridItem item = items.get(index);
            // Tracks are stored in logical order. In RTL the physical offsets are assigned
            // right-to-left, so an item's physical left edge is derived from its logical end
            // line and its physical right edge from its logical start line.
            RectF32 gridArea = new RectF32(
                    direction.isRtl()
                            ? columns.get(item.columnIndexes.end() - 1).offset
                            : columns.get(item.columnIndexes.start() + 1).offset,
                    direction.isRtl()
                            ? columns.get(item.columnIndexes.start()).offset
                            : columns.get(item.columnIndexes.end()).offset,
                    rows.get(item.rowIndexes.start() + 1).offset,
                    rows.get(item.rowIndexes.end()).offset);
            Alignment.AlignAndPositionResult positioned = Alignment.alignAndPositionItem(
                    tree,
                    item.node,
                    index,
                    gridArea,
                    new SizeF32(gridArea.right() - gridArea.left(), gridArea.bottom() - gridArea.top()),
                    containerAlignmentStyles,
                    item.baselineShim,
                    direction,
                    paddingBoxInset,
                    overflowContainer,
                    item.oofCandidates);
            item.yPosition = positioned.yWithoutRelativeOffset();
            item.height = positioned.height();
            item.baseline = positioned.baseline();

            itemOverflowRect = itemOverflowRect.union(positioned.contribution());
        }

        // Position hidden and absolutely positioned children, merging in the candidates bubbled out
        // of in-flow children's subtrees (`items` is sorted by `source_order`, i.e. child index)
        int order = items.size();
        int itemCursor = 0;
        for (int index = 0; index < tree.childCount(node); index++) {
            if (itemCursor < items.size() && items.get(itemCursor).sourceOrder == index) {
                oofCandidates.append(items.get(itemCursor).oofCandidates);
                itemCursor++;
                continue;
            }

            long child = tree.getChildId(node, index);
            Style childStyle = tree.getGridChildStyle(child);

            // Position hidden child
            if (childStyle.boxGenerationMode() == BoxGenerationMode.NONE) {
                tree.setUnroundedLayout(child, Layout.withOrder(order));
                tree.performChildLayout(
                        child,
                        SizeOptF32.NONE,
                        SizeOptF32.NONE,
                        SizeAvailableSpaceExt.maxContent(),
                        SizingMode.INHERENT_SIZE,
                        LineBool.FALSE);
                order++;
                continue;
            }

            // Emit out-of-flow children as candidates for the out-of-flow positioning pass
            // (`compute_oof_layout`), which lays out those for which this grid is the containing
            // block after this algorithm has run (resolving their grid areas from the detailed grid
            // info recorded below).
            if (childStyle.position().isOutOfFlow()) {
                Position position = childStyle.position();
                Direction itemDirection = childStyle.direction();
                AlignItems justifySelf = (childStyle.justifySelf() != null ? childStyle.justifySelf() : justifyItems)
                        .resolveSelfRelative(itemDirection, direction, true);
                AlignItems alignSelf = (childStyle.alignSelf() != null ? childStyle.alignSelf() : alignItems)
                        .resolveSelfRelative(itemDirection, direction, false);

                // The static-position rectangle: the grid area determined by the grid-placement
                // properties when this grid is the child's containing block, and otherwise the
                // grid's content box, as if the child were the sole grid item in a grid area
                // coinciding with the grid's content edges (grid placement does not apply when the
                // grid is not the containing block).
                // https://www.w3.org/TR/css-grid-1/#static-position
                RectF32 area = containingBlockClaims.forPosition(position)
                        ? resolveStaticPositionGridArea(
                                childStyle,
                                nameResolver,
                                finalRowCounts,
                                finalColCounts,
                                rows,
                                columns,
                                direction,
                                border,
                                scrollbarGutter,
                                containerBorderBox)
                        : new RectF32(
                                border.left() + padding.left() + (direction.isRtl() ? scrollbarGutter.x() : 0.0f),
                                containerBorderBox.width()
                                        - border.right()
                                        - padding.right()
                                        - (direction.isRtl() ? 0.0f : scrollbarGutter.x()),
                                border.top() + padding.top(),
                                containerBorderBox.height() - border.bottom() - padding.bottom() - scrollbarGutter.y());

                oofCandidates.push(new OofCandidate(
                        child,
                        order,
                        position,
                        new Point<>(
                                AxisStaticPosition.fromAlignment(
                                        justifySelf, new LineF32(area.left(), area.right()), direction.isRtl()),
                                AxisStaticPosition.fromAlignment(
                                        alignSelf, new LineF32(area.top(), area.bottom()), false))));

                order++;
            }
        }

        SizeF32 absolutePositionArea = containerBorderBox.minus(paddingBoxInset.sumAxes());
        PointF32 absolutePositionOffset = new PointF32(paddingBoxInset.left(), paddingBoxInset.top());
        // Store the detailed grid info before the out-of-flow positioning pass so that the pass can
        // resolve the grid areas of out-of-flow boxes whose containing block is this grid
        Map<String, AreaLines> areas =
                nameResolver.populateDetailedLineResolvers(detailedRowLineNames, detailedColumnLineNames);
        List<DetailedGridItemsInfo> detailedItems = new ArrayList<>(items.size());
        for (GridItem item : items) {
            detailedItems.add(DetailedGridItemsInfo.fromGridItem(item));
        }
        tree.setDetailedGridInfo(
                node,
                new DetailedGridInfo(
                        DetailedGridTracksInfo.fromGridTracksAndTrackCount(finalRowCounts, rows, detailedRowLineNames),
                        DetailedGridTracksInfo.fromGridTracksAndTrackCount(
                                finalColCounts, columns, detailedColumnLineNames),
                        detailedItems,
                        areas));

        OofPositioningArea oofPositioningArea = new OofPositioningArea(absolutePositionArea, absolutePositionOffset);

        // If there are no in-flow items then return the container size and the overflow (no baseline)
        if (items.isEmpty()) {
            // (upstream cfg(feature = "content_size") block — kept unconditionally)
            float clientWidth =
                    F32.max(containerBorderBox.width() - border.horizontalAxisSum() - scrollbarGutter.x(), 0.0f);
            RectF32 overflowRect = isScrollContainer
                    ? itemOverflowRect.union(new RectF32(
                            direction.isRtl() ? F32.min(clientWidth - padding.left(), 0.0f) : 0.0f,
                            direction.isRtl() ? clientWidth : padding.right(),
                            0.0f,
                            padding.bottom()))
                    : itemOverflowRect.union(new RectF32(0.0f, direction.isRtl() ? clientWidth : 0.0f, 0.0f, 0.0f));
            return new LayoutOutput(
                    containerBorderBox,
                    net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                            overflowRect,
                            containerBorderBox
                                    .minus(border.sumAxes())
                                    .minus(new SizeF32(scrollbarGutter.x(), scrollbarGutter.y())),
                            new PointBool(direction.isRtl(), false),
                            isScrollContainer),
                    Baselines.NONE,
                    net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet.ZERO,
                    net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet.ZERO,
                    false,
                    oofCandidates,
                    oofPositioningArea);
        }

        // Determine the grid container baseline(s) (currently we only compute the first baseline)

        // Layout containment suppresses the box's baseline for baseline-alignment purposes
        @Opt float gridContainerBaseline;
        if (contain.suppressesBaseline()) {
            gridContainerBaseline = OptF32.NONE;
        } else {
            // Get the row index of the first row containing items
            int firstRow = Integer.MAX_VALUE;
            for (GridItem item : items) {
                firstRow = Integer.min(firstRow, item.rowIndexes.start());
            }

            // Check if any items in *this row* participate in baseline alignment
            // (items with an auto block-axis margin do not participate:
            // https://www.w3.org/TR/css-align-3/#baseline-align-self)
            GridItem firstItem = null;
            for (GridItem item : items) {
                if (item.rowIndexes.start() == firstRow) {
                    firstItem = item;
                    break;
                }
            }
            GridItem baselineItem = firstItem;
            if (!firstItem.participatesInBaselineAlignment()) {
                for (GridItem item : items) {
                    if (item.rowIndexes.start() == firstRow && item.participatesInBaselineAlignment()) {
                        baselineItem = item;
                        break;
                    }
                }
            }

            gridContainerBaseline =
                    baselineItem.yPosition + OptF32.unwrapOr(baselineItem.baseline, baselineItem.height);
        }

        // A scroll container's own padding at the end of the content is part of its scrollable
        // overflow region, so it is included in the in-flow overflow rect. Boxes that are not
        // scroll containers do not extend their overflow region by their own padding.
        float clientWidth =
                F32.max(containerBorderBox.width() - border.horizontalAxisSum() - scrollbarGutter.x(), 0.0f);
        RectF32 overflowRect = isScrollContainer
                ? itemOverflowRect.union(new RectF32(
                        direction.isRtl() ? F32.min(clientWidth - padding.left(), 0.0f) : 0.0f,
                        direction.isRtl() ? clientWidth : padding.right(),
                        0.0f,
                        padding.bottom()))
                : itemOverflowRect.union(new RectF32(0.0f, direction.isRtl() ? clientWidth : 0.0f, 0.0f, 0.0f));
        net.morphismmc.jaffy.tree.layout.ScrollableOverflow scrollableOverflowRect =
                net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                        overflowRect,
                        containerBorderBox
                                .minus(border.sumAxes())
                                .minus(new SizeF32(scrollbarGutter.x(), scrollbarGutter.y())),
                        new PointBool(direction.isRtl(), false),
                        isScrollContainer);

        return new LayoutOutput(
                containerBorderBox,
                scrollableOverflowRect,
                Baselines.fromFirst(gridContainerBaseline),
                net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet.ZERO,
                net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet.ZERO,
                false,
                oofCandidates,
                oofPositioningArea);
    }

    /// `debug_log!(dbg: tracks.iter().map(base_size))` — the track base sizes as a list.
    private static String debugTrackBaseSizes(List<GridTrack> tracks) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < tracks.size(); i++) {
            if (i != 0) {
                out.append(", ");
            }
            out.append(RustFmt.debug(tracks.get(i).baseSize));
        }
        return out.append(']').toString();
    }

    private static String debugSizeOptF32(SizeOptF32 size) {
        return "Size { width: " + debugOptF32(size.width()) + ", height: " + debugOptF32(size.height()) + " }";
    }

    /// Rust `{:?}` for `Option<f32>` — `Some(v)` / `None`
    private static String debugOptF32(@Opt float v) {
        return OptF32.isSome(v) ? "Some(" + RustFmt.debug(v) + ")" : "None";
    }

    /// Resolve the static-position rectangle (the grid area determined by the grid-placement
    /// properties) for an out-of-flow child of a positioned grid container.
    ///
    /// Content alignment (align-content/justify-content) may distribute free space before, between,
    /// or after tracks. Grid lines used by absolutely positioned items resolve to the edges of the
    /// tracks adjacent to the line rather than to the raw gutter offset:
    ///   - As a start edge, a line resolves to the start of the track that follows it
    ///   - As an end edge, a line resolves to the end of the track that precedes it
    private static RectF32 resolveStaticPositionGridArea(
            GridItemStyle childStyle,
            NamedLineResolver nameResolver,
            TrackCounts finalRowCounts,
            TrackCounts finalColCounts,
            List<GridTrack> rows,
            List<GridTrack> columns,
            Direction direction,
            RectF32 border,
            PointF32 scrollbarGutter,
            SizeF32 containerBorderBox) {
        // Convert grid-col-{start/end} into Option's of indexes into the columns vector
        // The Option is None if the style property is Auto and an unresolvable Span
        // (`Line<Option<usize>>` -> `LineOptI32`; `and_then` short-circuit on None)
        LineOptI32 maybeColIndexes = LineOriginZeroGridPlacementExt.resolveAbsolutelyPositionedGridTracks(
                        LineNonNamedGridPlacementExt.intoOriginZero(
                                nameResolver.resolveColumnNames(childStyle.gridColumn()), finalColCounts.explicit()))
                .mapOptI32(line -> line == null ? OptI32.NONE : toIndex(line, finalColCounts));
        // Convert grid-row-{start/end} into Option's of indexes into the row vector
        // The Option is None if the style property is Auto and an unresolvable Span
        LineOptI32 maybeRowIndexes = LineOriginZeroGridPlacementExt.resolveAbsolutelyPositionedGridTracks(
                        LineNonNamedGridPlacementExt.intoOriginZero(
                                nameResolver.resolveRowNames(childStyle.gridRow()), finalRowCounts.explicit()))
                .mapOptI32(line -> line == null ? OptI32.NONE : toIndex(line, finalRowCounts));

        // In RTL the item's physical left edge derives from its logical end line and its
        // physical right edge from its logical start line.
        float gridAreaLeft;
        float gridAreaRight;
        if (direction.isRtl()) {
            gridAreaLeft = maybeColIndexes.end() != OptI32.NONE
                    ? rtlLineAsEndEdge(columns, maybeColIndexes.end())
                    : border.left() + scrollbarGutter.x();
            gridAreaRight = maybeColIndexes.start() != OptI32.NONE
                    ? rtlLineAsStartEdge(columns, maybeColIndexes.start())
                    : containerBorderBox.width() - border.right();
        } else {
            gridAreaLeft = maybeColIndexes.start() != OptI32.NONE
                    ? lineAsStartEdge(columns, maybeColIndexes.start())
                    : border.left();
            gridAreaRight = maybeColIndexes.end() != OptI32.NONE
                    ? lineAsEndEdge(columns, maybeColIndexes.end())
                    : containerBorderBox.width() - border.right() - scrollbarGutter.x();
        }

        return new RectF32(
                gridAreaLeft,
                gridAreaRight,
                maybeRowIndexes.start() != OptI32.NONE ? lineAsStartEdge(rows, maybeRowIndexes.start()) : border.top(),
                maybeRowIndexes.end() != OptI32.NONE
                        ? lineAsEndEdge(rows, maybeRowIndexes.end())
                        : containerBorderBox.height() - border.bottom() - scrollbarGutter.y());
    }

    /// `maybe_grid_line.and_then(try_into_track_vec_index)` — `Option<usize>` ->
    /// `@Opt @Unsigned int` (`OptI32.NONE` encodes failure).
    private static @Opt @Unsigned int toIndex(OriginZeroLine line, TrackCounts counts) {
        int idx = line.tryIntoTrackVecIndex(counts);
        return idx >= 0 ? idx : OptI32.NONE;
    }

    /// Resolve a grid line (by track vector index) used as a start edge to a position
    private static float lineAsStartEdge(List<GridTrack> tracks, int index) {
        return index + 1 < tracks.size() ? tracks.get(index + 1).offset : tracks.get(index).offset;
    }

    /// Resolve a grid line (by track vector index) used as an end edge to a position
    private static float lineAsEndEdge(List<GridTrack> tracks, int index) {
        if (index == 0) {
            return 1 < tracks.size() ? tracks.get(1).offset : tracks.getFirst().offset;
        }
        return tracks.get(index).offset;
    }

    /// Resolve a grid line used as an inline-start edge to a physical right x-position (RTL)
    private static float rtlLineAsStartEdge(List<GridTrack> tracks, int index) {
        if (tracks.size() > index + 1) {
            // The gutter's offset is the physical right edge of the track that follows the line
            return tracks.get(index).offset;
        } else if (index == 0) {
            return tracks.getFirst().offset;
        } else {
            // No track follows the line: resolve to the line itself, which is the physical
            // left edge of the track that precedes it (the trailing gutter is assigned its
            // offset before any alignment offset is applied, so it cannot be used here)
            return tracks.get(index - 1).offset;
        }
    }

    /// Resolve a grid line used as an inline-end edge to a physical left x-position (RTL)
    private static float rtlLineAsEndEdge(List<GridTrack> tracks, int index) {
        if (index == 0) {
            return tracks.getFirst().offset;
        }
        return tracks.get(index - 1).offset;
    }
}
