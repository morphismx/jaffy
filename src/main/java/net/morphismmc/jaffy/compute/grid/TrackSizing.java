// JAFFY: port of upstream src/compute/grid/track_sizing.rs — the track sizing
// algorithm (<https://www.w3.org/TR/css-grid-1/#layout-algorithm>). One Rust
// file with only private items + pub(super) functions -> single Java class.
//
// Type mapping:
//   `pub(super) fn` -> `public static` (siblings live in the types.* subpackages)
//   private fn      -> private static
//   `impl Fn(&GridTrack) -> bool` -> `java.util.function.Predicate<GridTrack>`
//   `impl Fn(&GridTrack) -> f32`  -> `TrackFloatFn` (private SAM; no boxing)
//   `fn(&GridTrack, Option<f32>, &Tree) -> Option<f32>` -> `TrackSizeEstimate` SAM
//   `impl Fn(&GridTrack, Option<f32>) -> Option<f32>`   -> `GridItem.GetTrackSizeEstimate`
//   `&mut [GridTrack]` / `&mut [GridItem]` -> `List<...>` (item state mutations
//     go through the objects; Rust sub-slices -> `List.subList` views)
//   `Vec<&mut GridItem>` -> `ArrayList<GridItem>` (same identity semantics:
//     sorting/re-slicing reorders references, field writes hit the same objects)
//   `Option<f32>` -> `@Opt float`; `f32::INFINITY` -> `Float.POSITIVE_INFINITY`
//   `usize`/`u16`/`u32` -> `int` (GAP-014)
//   `Iterator::max_by(total_cmp)` / `min_by(total_cmp)` -> loops using
//     `Float.compare`: total_cmp's ordering (NaN greatest, -0.0 < +0.0) is
//     exactly what `Float.compare` implements for every bit pattern Java can
//     represent (negative NaN cannot occur — Java canonicalises NaN payloads).
//     Ties: max_by/min_by keep the last minimal/maximal element, but equal
//     elements yield identical values, so the value is unaffected.
//   `Tree: LayoutPartialTree` generic -> concrete `LayoutPartialTree` (the only
//     bound used; matches the project's trait-port convention)
//   `resolve_item_baselines`'s `known_dimensions_cache = None` -> field set null
//   `Size::NONE` -> `SizeOptF32.NONE`; `Size::MIN_CONTENT` ->
//     `SizeAvailableSpaceExt.minContent()`
package net.morphismmc.jaffy.compute.grid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.morphismmc.jaffy.compute.common.Alignment;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.grid_item.GridItem;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.StyleHelpers.SizeAvailableSpaceExt;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;
import net.morphismmc.jaffy.util.FrontBackVecBuilder;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

public final class TrackSizing {

    private TrackSizing() {}

    /// `fn(&GridTrack, Option<f32>, &Tree) -> Option<f32>` — the track-size
    /// estimate callback threaded through the algorithm.
    @FunctionalInterface
    public interface TrackSizeEstimate {
        @Opt
        float estimate(GridTrack track, @Opt float basis, LayoutPartialTree tree);
    }

    /// `impl Fn(&GridTrack) -> f32`
    @FunctionalInterface
    private interface TrackFloatFn {
        float apply(GridTrack track);
    }

    /// `distribute_item_space_to_base_size_inner`'s `THRESHOLD`.
    private static final float DISTRIBUTE_BASE_SIZE_THRESHOLD = 0.000001f;
    /// `distribute_space_up_to_limits`'s `THRESHOLD`.
    private static final float DISTRIBUTE_LIMITS_THRESHOLD = 0.01f;

    /// Takes an axis, and a list of grid items sorted firstly by whether they cross a flex track
    /// in the specified axis (items that don't cross a flex track first) and then by the number
    /// of tracks they cross in specified axis (ascending order).
    ///
    /// The list holds references so that the `GridItem`s themselves stay in document order and
    /// only the (pointer-sized) references are sorted.
    private static final class ItemBatcher {
        /// The axis in which the ItemBatcher is operating. Used when querying properties from items.
        private final AbstractAxis axis;
        /// The starting index of the current batch
        private int indexOffset;
        /// The span of the items in the current batch
        private int currentSpan;
        /// Whether the current batch of items cross a flexible track
        private boolean currentIsFlex;

        /// Create a new ItemBatcher for the specified axis
        ItemBatcher(AbstractAxis axis) {
            this.indexOffset = 0;
            this.axis = axis;
            this.currentSpan = 1;
            this.currentIsFlex = false;
        }

        /// `Option<(&mut [&mut GridItem], bool)>` — the batch view plus whether it
        /// is the trailing flex batch.
        private record Batch(List<GridItem> items, boolean isFlex) {}

        /// This is basically a manual version of Iterator::next which passes `items`
        /// in as a parameter on each iteration to work around borrow checker rules.
        /// `&mut items[batch_range]` -> `List.subList` view.
        Batch next(List<GridItem> items) {
            if (currentIsFlex || indexOffset >= items.size()) {
                return null;
            }

            GridItem item = items.get(indexOffset);
            currentSpan = item.span(axis);
            currentIsFlex = item.crossesFlexibleTrack(axis);

            int nextIndexOffset;
            if (currentIsFlex) {
                nextIndexOffset = items.size();
            } else {
                // `.position(...)` — first flex-crossing or larger-span item.
                // (Earlier entries are sorted into smaller spans and non-flex
                // first, so scanning from 0 and scanning from indexOffset give
                // the same index; scanned from 0 to stay literal.)
                int pos = 0;
                while (pos < items.size()) {
                    GridItem it = items.get(pos);
                    if (it.crossesFlexibleTrack(axis) || it.span(axis) > currentSpan) {
                        break;
                    }
                    pos++;
                }
                nextIndexOffset = pos;
            }

            Batch batch = new Batch(items.subList(indexOffset, nextIndexOffset), currentIsFlex);
            indexOffset = nextIndexOffset;
            return batch;
        }
    }

    /// This struct captures a bunch of variables which are used to compute the intrinsic sizes of children so that
    /// those variables
    /// don't have to be passed around all over the place below. It then has methods that implement the intrinsic sizing
    /// computations
    private static final class IntrinsicSizeMeasurer {
        /// The layout tree
        private final LayoutPartialTree tree;
        /// The tracks in the opposite axis to the one we are currently sizing
        private final List<GridTrack> otherAxisTracks;
        /// A function that computes an estimate of an other-axis track's size which is passed to
        /// the child size measurement functions
        private final TrackSizeEstimate getTrackSizeEstimate;
        /// The axis we are currently sizing
        private final AbstractAxis axis;
        /// The available grid space
        private final SizeOptF32 innerNodeSize;

        IntrinsicSizeMeasurer(
                LayoutPartialTree tree,
                List<GridTrack> otherAxisTracks,
                TrackSizeEstimate getTrackSizeEstimate,
                AbstractAxis axis,
                SizeOptF32 innerNodeSize) {
            this.tree = tree;
            this.otherAxisTracks = otherAxisTracks;
            this.getTrackSizeEstimate = getTrackSizeEstimate;
            this.axis = axis;
            this.innerNodeSize = innerNodeSize;
        }

        /// Compute the available_space to be passed to the child sizing functions
        /// These are estimates based on either the max track sizing function or the provisional base size in the
        /// opposite
        /// axis to the one currently being sized.
        /// https://www.w3.org/TR/css-grid-1/#algo-overview
        SizeOptF32 gridAreaSize(GridItem item, List<GridTrack> axisTracks) {
            return item.gridAreaSizeCached(
                    axis,
                    axisTracks,
                    otherAxisTracks,
                    innerNodeSize,
                    (track, basis) -> getTrackSizeEstimate.estimate(track, basis, tree),
                    tree::calc);
        }

        /// Compute the item's resolved margins for size contributions. Horizontal percentage margins always resolve
        /// to zero if the container size is indefinite as otherwise this would introduce a cyclic dependency.
        SizeF32 marginsAxisSumsWithBaselineShims(GridItem item, @Opt float percentageBasis) {
            return item.marginsAxisSumsWithBaselineShims(percentageBasis, tree);
        }

        /// Simple pass-through function to `LayoutPartialTreeExt::calc`
        float calc(Object val, float basis) {
            return tree.calc(val, basis);
        }

        /// Retrieve the item's min content contribution from the cache or compute it using the provided parameters
        float minContentContribution(GridItem item, List<GridTrack> axisTracks) {
            SizeOptF32 gridAreaSize = gridAreaSize(item, axisTracks);
            SizeOptF32 availableSpace = gridAreaSize.with(axis, OptF32.NONE);
            SizeF32 marginAxisSums = marginsAxisSumsWithBaselineShims(item, availableSpace.width());
            float contribution = item.minContentContributionCached(axis, tree, gridAreaSize, availableSpace);
            return contribution + marginAxisSums.get(axis);
        }

        /// Retrieve the item's max content contribution from the cache or compute it using the provided parameters
        float maxContentContribution(GridItem item, List<GridTrack> axisTracks) {
            SizeOptF32 gridAreaSize = gridAreaSize(item, axisTracks);
            SizeOptF32 availableSpace = gridAreaSize.with(axis, OptF32.NONE);
            SizeF32 marginAxisSums = marginsAxisSumsWithBaselineShims(item, availableSpace.width());
            float contribution = item.maxContentContributionCached(axis, tree, gridAreaSize, availableSpace);
            return contribution + marginAxisSums.get(axis);
        }

        /// The minimum contribution of an item is the smallest outer size it can have.
        /// Specifically:
        ///   - If the item’s computed preferred size behaves as auto or depends on the size of its containing block in
        /// the relevant axis:
        ///     Its minimum contribution is the outer size that would result from assuming the item’s used minimum size
        /// as its preferred size;
        ///   - Else the item’s minimum contribution is its min-content contribution.
        ///
        /// Because the minimum contribution often depends on the size of the item's content, it is considered a type of
        /// intrinsic size contribution.
        float minimumContribution(GridItem item, List<GridTrack> axisTracks) {
            SizeOptF32 gridAreaSize = gridAreaSize(item, axisTracks);
            SizeOptF32 availableSpace = gridAreaSize.with(axis, OptF32.NONE);
            SizeF32 marginAxisSums = marginsAxisSumsWithBaselineShims(item, availableSpace.width());
            float contribution = item.minimumContributionCached(tree, axis, axisTracks, gridAreaSize, innerNodeSize);
            return contribution + marginAxisSums.get(axis);
        }
    }

    /// When applying the track sizing algorithm and estimating the size in the other axis for content sizing items
    /// we should take into account align-content/justify-content if both the grid container and all items in the
    /// other axis have definite sizes. This function computes such a per-gutter additional size adjustment.
    public static float computeAlignmentGutterAdjustment(
            AlignContent alignment,
            @Opt float axisInnerNodeSize,
            GridItem.GetTrackSizeEstimate getTrackSizeEstimate,
            List<GridTrack> tracks) {
        if (tracks.size() <= 1) {
            return 0.0f;
        }

        // As items never cross the outermost gutters in a grid, we can simplify our calculations by
        // treating Start and End the same. The safety modifier doesn't influence gutter weight;
        // overflow fallback is handled when offsets are computed.
        int outerGutterWeight = switch (alignment.keyword()) {
            case START, FLEX_START, END, FLEX_END, CENTER -> 1;
            // Normal is resolved to Stretch in `compute_grid_layout`
            case NORMAL, STRETCH -> 0;
            case SPACE_BETWEEN -> 0;
            case SPACE_AROUND -> 1;
            case SPACE_EVENLY -> 1;
        };

        int innerGutterWeight = switch (alignment.keyword()) {
            case FLEX_START, START, FLEX_END, END, CENTER, NORMAL, STRETCH -> 0;
            case SPACE_BETWEEN -> 1;
            case SPACE_AROUND -> 2;
            case SPACE_EVENLY -> 1;
        };

        if (innerGutterWeight == 0) {
            return 0.0f;
        }

        if (OptF32.isSome(axisInnerNodeSize)) {
            // `sum::<Option<f32>>()` — a single None estimate makes the sum None
            @Opt float trackSizeSum = 0.0f;
            for (GridTrack track : tracks) {
                @Opt float estimate = getTrackSizeEstimate.estimate(track, axisInnerNodeSize);
                if (OptF32.isNone(estimate)) {
                    trackSizeSum = OptF32.NONE;
                    break;
                }
                trackSizeSum += estimate;
            }
            float freeSpace =
                    OptF32.unwrapOr(OptF32.map(trackSizeSum, sum -> F32.max(0.0f, axisInnerNodeSize - sum)), 0.0f);

            int weightedTrackCount = (((tracks.size() - 3) / 2) * innerGutterWeight) + (2 * outerGutterWeight);

            return (freeSpace / weightedTrackCount) * innerGutterWeight;
        }

        return 0.0f;
    }

    /// Convert origin-zero coordinates track placement in grid track vector indexes
    public static void resolveItemTrackIndexes(List<GridItem> items, TrackCounts columnCounts, TrackCounts rowCounts) {
        for (GridItem item : items) {
            item.columnIndexes = item.column.map(line -> line.intoTrackVecIndex(columnCounts));
            item.rowIndexes = item.row.map(line -> line.intoTrackVecIndex(rowCounts));
        }
    }

    /// Determine (in each axis) whether the item crosses any flexible tracks
    public static void determineIfItemCrossesFlexibleOrIntrinsicTracks(
            List<GridItem> items, List<GridTrack> columns, List<GridTrack> rows) {
        for (GridItem item : items) {
            item.crossesFlexibleColumn =
                    anyMatch(columns, item.trackRangeExcludingLines(AbstractAxis.INLINE), GridTrack::isFlexible);
            item.crossesIntrinsicColumn = anyMatch(
                    columns, item.trackRangeExcludingLines(AbstractAxis.INLINE), GridTrack::hasIntrinsicSizingFunction);
            item.crossesFlexibleRow =
                    anyMatch(rows, item.trackRangeExcludingLines(AbstractAxis.BLOCK), GridTrack::isFlexible);
            item.crossesIntrinsicRow = anyMatch(
                    rows, item.trackRangeExcludingLines(AbstractAxis.BLOCK), GridTrack::hasIntrinsicSizingFunction);
        }
    }

    /// `range.any(|i| tracks[i].test)` over a `TrackRange` index range.
    private static boolean anyMatch(
            List<GridTrack> tracks, TrackCounts.TrackRange range, Predicate<GridTrack> predicate) {
        for (int i = range.start(); i < range.end(); i++) {
            if (predicate.test(tracks.get(i))) {
                return true;
            }
        }
        return false;
    }

    /// Track sizing algorithm
    /// Note: Gutters are treated as empty fixed-size tracks for the purpose of the track sizing algorithm.
    public static void trackSizingAlgorithm(
            LayoutPartialTree tree,
            AbstractAxis axis,
            @Opt float axisMinSize,
            @Opt float axisMaxSize,
            AlignContent axisAlignment,
            AlignContent otherAxisAlignment,
            Size<AvailableSpace> availableGridSpace,
            SizeOptF32 innerNodeSize,
            List<GridTrack> axisTracks,
            List<GridTrack> otherAxisTracks,
            List<GridItem> items,
            TrackSizeEstimate getTrackSizeEstimate,
            boolean hasBaselineAlignedItem) {
        // 11.4 Initialise Track sizes
        // Initialize each track’s base size and growth limit.
        @Opt float percentageBasis = OptF32.or(innerNodeSize.get(axis), axisMinSize);
        initializeTrackSizes(tree, axisTracks, percentageBasis);

        // Resolve fit-content() limits against the container's inner size (these are fixed for the rest of this pass)
        @Opt float axisInnerNodeSize = innerNodeSize.get(axis);
        for (GridTrack track : axisTracks) {
            track.resolveFitContentLimit(axisInnerNodeSize, tree::calc);
        }

        // 11.5.1 Shim item baselines
        if (hasBaselineAlignedItem) {
            resolveItemBaselines(tree, axis, items, innerNodeSize);
        }

        // If all tracks have a fixed min track sizing function and base_size = growth_limit,
        // then the track sizes are already final and we can skip the rest of this function.
        // Note that tracks with an intrinsic min track sizing function can still grow beyond
        // a fixed growth limit (e.g. minmax(auto, 0px)), so they cannot be skipped.
        boolean allTracksFinal = true;
        for (GridTrack track : axisTracks) {
            if (!(track.baseSize == track.growthLimit
                    && OptF32.isSome(track.minTrackSizingFunction.definiteValue(percentageBasis, tree::calc)))) {
                allTracksFinal = false;
                break;
            }
        }
        if (allTracksFinal) {
            return;
        }

        // Pre-computations for 11.5 Resolve Intrinsic Track Sizes

        // Compute an additional amount to add to each spanned gutter when computing item's estimated size in the
        // in the opposite axis based on the alignment, container size, and estimated track sizes in that axis
        float gutterAlignmentAdjustment = computeAlignmentGutterAdjustment(
                otherAxisAlignment,
                innerNodeSize.get(axis.other()),
                (track, basis) -> getTrackSizeEstimate.estimate(track, basis, tree),
                otherAxisTracks);
        if (otherAxisTracks.size() > 3) {
            // `other_axis_tracks[2..len].iter_mut().step_by(2)`
            for (int i = 2; i < otherAxisTracks.size(); i += 2) {
                otherAxisTracks.get(i).contentAlignmentAdjustment = gutterAlignmentAdjustment;
            }
        }

        // 11.5 Resolve Intrinsic Track Sizes
        resolveIntrinsicTrackSizes(
                tree,
                axis,
                axisTracks,
                otherAxisTracks,
                items,
                availableGridSpace.get(axis),
                innerNodeSize,
                getTrackSizeEstimate);

        // 11.6. Maximise Tracks
        // Distributes free space (if any) to tracks with FINITE growth limits, up to their limits.
        maximiseTracks(axisTracks, availableGridSpace.get(axis));

        // For the purpose of the final two expansion steps ("Expand Flexible Tracks" and "Stretch auto Tracks"), we
        // only want to expand
        // into space generated by the grid container's size (as defined by either it's preferred size style or by it's
        // parent node through
        // something like stretch alignment), not just any available space. To do this we map definite available space
        // to AvailableSpace::MaxContent
        // in the case that inner_node_size is None. A min-content constraint in the block axis is also mapped to
        // AvailableSpace::MaxContent,
        // as the min-content block size of a grid container is its max-content block size.
        AvailableSpace axisAvailableSpaceForExpansion;
        if (OptF32.isSome(innerNodeSize.get(axis))) {
            axisAvailableSpaceForExpansion = new AvailableSpace.Definite(innerNodeSize.get(axis));
        } else {
            AvailableSpace axisAvailableGridSpace = availableGridSpace.get(axis);
            axisAvailableSpaceForExpansion =
                    axisAvailableGridSpace == AvailableSpace.MIN_CONTENT && axis == AbstractAxis.INLINE
                            ? AvailableSpace.MIN_CONTENT
                            : AvailableSpace.MAX_CONTENT;
        }

        // 11.7. Expand Flexible Tracks
        // This step sizes flexible tracks using the largest value it can assign to an fr without exceeding the
        // available space.
        expandFlexibleTracks(
                tree,
                axis,
                axisTracks,
                otherAxisTracks,
                items,
                axisMinSize,
                axisMaxSize,
                axisAvailableSpaceForExpansion,
                innerNodeSize,
                getTrackSizeEstimate);

        // 11.8. Stretch auto Tracks
        // This step expands tracks that have an auto max track sizing function by dividing any remaining positive,
        // definite free space equally amongst them.
        if (axisAlignment.equals(AlignContent.STRETCH)) {
            stretchAutoTracks(axisTracks, axisMinSize, axisAvailableSpaceForExpansion);
        }
    }

    /// Whether it is a minimum or maximum size's space being distributed
    /// This controls behaviour of the space distribution algorithm when distributing beyond limits
    /// See "distributing space beyond limits" at https://www.w3.org/TR/css-grid-1/#extra-space
    private enum IntrinsicContributionType {
        /// It's a minimum size's space being distributed
        MINIMUM,
        /// It's a maximum size's space being distributed
        MAXIMUM
    }

    /// Add any planned base size increases to the base size after a round of distributing space to base sizes
    /// Reset the planed base size increase to zero ready for the next round.
    private static void flushPlannedBaseSizeIncreases(List<GridTrack> tracks) {
        for (GridTrack track : tracks) {
            track.baseSize += track.baseSizePlannedIncrease;
            track.baseSizePlannedIncrease = 0.0f;
        }
    }

    /// Add any planned growth limit increases to the growth limit after a round of distributing space to growth limits
    /// Reset the planed growth limit increase to zero ready for the next round.
    private static void flushPlannedGrowthLimitIncreases(List<GridTrack> tracks, boolean setInfinitelyGrowable) {
        for (GridTrack track : tracks) {
            if (track.growthLimitPlannedIncrease > 0.0f) {
                track.growthLimit = track.growthLimit == Float.POSITIVE_INFINITY
                        ? track.baseSize + track.growthLimitPlannedIncrease
                        : track.growthLimit + track.growthLimitPlannedIncrease;
                track.infinitelyGrowable = setInfinitelyGrowable;
            } else {
                track.infinitelyGrowable = false;
            }
            track.growthLimitPlannedIncrease = 0.0f;
        }
    }

    /// 11.4 Initialise Track sizes
    /// Initialize each track’s base size and growth limit.
    private static void initializeTrackSizes(
            LayoutPartialTree tree, List<GridTrack> axisTracks, @Opt float axisInnerNodeSize) {
        for (GridTrack track : axisTracks) {
            // For each track, if the track’s min track sizing function is:
            // - A fixed sizing function
            //     Resolve to an absolute length and use that size as the track’s initial base size.
            //     Note: Indefinite lengths cannot occur, as they’re treated as auto.
            // - An intrinsic sizing function
            //     Use an initial base size of zero.
            track.baseSize =
                    OptF32.unwrapOr(track.minTrackSizingFunction.definiteValue(axisInnerNodeSize, tree::calc), 0.0f);

            // For each track, if the track’s max track sizing function is:
            // - A fixed sizing function
            //     Resolve to an absolute length and use that size as the track’s initial growth limit.
            // - An intrinsic sizing function
            //     Use an initial growth limit of infinity.
            // - A flexible sizing function
            //     Use an initial growth limit of infinity.
            track.growthLimit = OptF32.unwrapOr(
                    track.maxTrackSizingFunction.definiteValue(axisInnerNodeSize, tree::calc), Float.POSITIVE_INFINITY);

            // In all cases, if the growth limit is less than the base size, increase the growth limit to match the base
            // size.
            if (track.growthLimit < track.baseSize) {
                track.growthLimit = track.baseSize;
            }
        }
    }

    /// 11.5.1 Shim baseline-aligned items so their intrinsic size contributions reflect their baseline alignment.
    private static void resolveItemBaselines(
            LayoutPartialTree tree, AbstractAxis axis, List<GridItem> items, SizeOptF32 innerNodeSize) {
        // Sort items by track in the other axis (row) start position so that we can iterate items in groups which
        // are in the same track in the other axis (row)
        AbstractAxis otherAxis = axis.other();
        List<GridItem> sortedItems = new ArrayList<>(items);
        // `sort_by_key(placement(other_axis).start)` — OriginZeroLine's Ord -> .value()
        sortedItems.sort(Comparator.comparingInt(
                item -> item.placement(otherAxis).start().value()));

        // Iterate over grid rows
        List<GridItem> remainingItems = sortedItems;
        while (!remainingItems.isEmpty()) {
            // Get the row index of the current row
            int currentRow =
                    remainingItems.getFirst().placement(otherAxis).start().value();

            // Find the item index of the first item that is in a different row (or None if we've reached the end of the
            // list)
            int nextRowFirstItem = -1;
            for (int i = 0; i < remainingItems.size(); i++) {
                if (remainingItems.get(i).placement(otherAxis).start().value() != currentRow) {
                    nextRowFirstItem = i;
                    break;
                }
            }

            // Use this index to split the `remaining_items` slice in two slices:
            //    - A `row_items` slice containing the items (that start) in the current row
            //    - A new `remaining_items` consisting of the remainder of the `remaining_items` slice
            //      that hasn't been split off into `row_items
            List<GridItem> rowItems;
            if (nextRowFirstItem >= 0) {
                rowItems = remainingItems.subList(0, nextRowFirstItem);
                remainingItems = remainingItems.subList(nextRowFirstItem, remainingItems.size());
            } else {
                rowItems = remainingItems;
                remainingItems = List.of();
            }

            // Count how many items in *this row* are baseline aligned
            // If a row has one or zero items participating in baseline alignment then baseline alignment is a no-op
            // for those items and we skip further computations for that row
            int rowBaselineItemCount = 0;
            for (GridItem item : rowItems) {
                if (item.participatesInBaselineAlignment()) {
                    rowBaselineItemCount++;
                }
            }
            if (rowBaselineItemCount <= 1) {
                continue;
            }

            // Compute the baselines of all items in the row participating in baseline alignment
            for (GridItem item : rowItems) {
                if (!item.participatesInBaselineAlignment()) {
                    continue;
                }

                LayoutOutput measuredSizeAndBaselines = tree.performChildLayout(
                        item.node,
                        SizeOptF32.NONE,
                        innerNodeSize,
                        SizeAvailableSpaceExt.minContent(),
                        SizingMode.INHERENT_SIZE,
                        LineBool.FALSE);

                float height = measuredSizeAndBaselines.size().height();
                float baseline = OptF32.unwrapOr(
                        Alignment.childFirstBaseline(
                                measuredSizeAndBaselines.baselines().first(), height, item.overflow.y()),
                        height);

                item.baseline = baseline;
            }

            // Items are aligned by the distance from the top of their margin box to their baseline

            // Compute the max baseline of all items in the row participating in baseline alignment.
            // `max_by(total_cmp).unwrap()` — the unwrap cannot fire (rowBaselineItemCount > 1).
            float rowMaxBaseline = Float.NEGATIVE_INFINITY;
            boolean rowMaxSeen = false;
            for (GridItem item : rowItems) {
                if (!item.participatesInBaselineAlignment()) {
                    continue;
                }
                float v = marginBoxBaseline(item, innerNodeSize, tree);
                if (!rowMaxSeen || Float.compare(v, rowMaxBaseline) > 0) {
                    rowMaxBaseline = v;
                    rowMaxSeen = true;
                }
            }

            // Compute the baseline shim for each item in the row participating in baseline alignment
            for (GridItem item : rowItems) {
                if (item.participatesInBaselineAlignment()) {
                    item.baselineShim = rowMaxBaseline - marginBoxBaseline(item, innerNodeSize, tree);
                    item.knownDimensionsCache = null;
                }
            }
        }
    }

    /// `margin_box_baseline` — items are aligned by the distance from the top of
    /// their margin box to their baseline.
    private static float marginBoxBaseline(GridItem item, SizeOptF32 innerNodeSize, LayoutPartialTree tree) {
        return OptF32.unwrapOr(item.baseline, 0.0f)
                + item.margin.top().resolveOrZero(innerNodeSize.width(), tree::calc);
    }

    /// 11.5 Resolve Intrinsic Track Sizes
    private static void resolveIntrinsicTrackSizes(
            LayoutPartialTree tree,
            AbstractAxis axis,
            List<GridTrack> axisTracks,
            List<GridTrack> otherAxisTracks,
            List<GridItem> items,
            AvailableSpace axisAvailableGridSpace,
            SizeOptF32 innerNodeSize,
            TrackSizeEstimate getTrackSizeEstimate) {
        // Step 1. Shim baseline-aligned items so their intrinsic size contributions reflect their baseline alignment.

        // Already done at this point. See resolve_item_baselines function.

        // Step 2.

        // The track sizing algorithm requires us to iterate through the items in ascending order of the number of
        // tracks they span (first items that span 1 track, then items that span 2 tracks, etc).
        // To avoid having to do multiple iterations of the items, we pre-sort them into this order.
        //
        // Items that cross a flexible track are all processed together in a single batch (regardless of their span), so
        // they only need to be moved to the end of the list. And the order in which items within a batch are processed
        // does not affect the result, so the remaining items only need to be sorted if any of them span more than one
        // track.
        FrontBackVecBuilder<GridItem> partitionedItems = FrontBackVecBuilder.withCapacity(items.size());
        boolean needsSort = false;
        for (GridItem item : items) {
            if (item.crossesFlexibleTrack(axis)) {
                partitionedItems.pushBack(item);
            } else {
                needsSort |= item.span(axis) > 1;
                partitionedItems.pushFront(item);
            }
        }
        FrontBackVecBuilder.IntoVec<GridItem> partitioned = partitionedItems.intoVec();
        List<GridItem> sortedItems = partitioned.vec();
        int nonFlexItemCount = partitioned.frontLen();
        if (needsSort) {
            // `items[..non_flex_item_count].sort_by_key(|item| item.span(axis))`
            sortedItems.subList(0, nonFlexItemCount).sort(Comparator.comparingInt(item -> item.span(axis)));
        }

        // Step 2, Step 3 and Step 4
        // 2 & 3. Iterate over items that don't cross a flex track. Items should have already been sorted in ascending
        // order
        // of the number of tracks they span. Step 2 is the 1 track case and has an optimised implementation
        // 4. Next, repeat the previous step instead considering (together, rather than grouped by span size) all items
        // that do span a track with a flexible sizing function while

        // Compute item's intrinsic (content-based) sizes
        // Note: For items with a specified minimum size of auto (the initial value), the minimum contribution is
        // usually equivalent
        // to the min-content contribution—but can differ in some cases, see §6.6 Automatic Minimum Size of Grid Items.
        // Also, minimum contribution <= min-content contribution <= max-content contribution.

        @Opt float axisInnerNodeSize = innerNodeSize.get(axis);
        IntrinsicSizeMeasurer itemSizer =
                new IntrinsicSizeMeasurer(tree, otherAxisTracks, getTrackSizeEstimate, axis, innerNodeSize);

        ItemBatcher batchedItemIterator = new ItemBatcher(axis);
        ItemBatcher.Batch batch;
        while ((batch = batchedItemIterator.next(sortedItems)) != null) {
            // 2. Size tracks to fit non-spanning items: For each track with an intrinsic track sizing function and not
            // a flexible sizing function,
            // consider the items in it with a span of 1:
            int batchSpan = batch.items().getFirst().span(axis);
            if (!batch.isFlex() && batchSpan == 1) {
                for (GridItem item : batch.items()) {
                    int trackIndex = item.placementIndexes(axis).start() + 1;
                    GridTrack track = axisTracks.get(trackIndex);

                    // Handle base sizes
                    float newBaseSize;
                    if (track.minTrackSizingFunction.isMinContent()) {
                        newBaseSize = F32.max(track.baseSize, itemSizer.minContentContribution(item, axisTracks));
                    } else if (track.minTrackSizingFunction.isMaxContent()) {
                        newBaseSize = F32.max(track.baseSize, itemSizer.maxContentContribution(item, axisTracks));
                        // If the container size is indefinite and has not yet been resolved then percentage sized
                        // tracks are treated as auto
                    } else if (track.minTrackSizingFunction.behavesAsAuto(axisInnerNodeSize)) {
                        float space;
                        // QUIRK: The spec says that:
                        //
                        //   If the grid container is being sized under a min- or max-content constraint, use the items’
                        // limited
                        //   min-content contributions in place of their minimum contributions here.
                        //
                        // However, in practice browsers only seem to apply this rule if the item is not a scroll
                        // container
                        // (note that overflow:hidden counts as a scroll container), giving the automatic minimum size
                        // of scroll
                        // containers (zero) precedence over the min-content contributions.
                        if ((axisAvailableGridSpace == AvailableSpace.MIN_CONTENT
                                        || axisAvailableGridSpace == AvailableSpace.MAX_CONTENT)
                                && !item.overflow.get(axis).isScrollContainer()) {
                            float axisMinimumSize = itemSizer.minimumContribution(item, axisTracks);
                            float axisMinContentSize = itemSizer.minContentContribution(item, axisTracks);
                            @Opt
                            float limit =
                                    track.maxTrackSizingFunction.definiteLimit(axisInnerNodeSize, itemSizer::calc);
                            space = F32.max(F32.maybeMinO(axisMinContentSize, limit), axisMinimumSize);
                        } else {
                            space = itemSizer.minimumContribution(item, axisTracks);
                        }
                        newBaseSize = F32.max(track.baseSize, space);
                    } else {
                        // Do nothing as it's not an intrinsic track sizing function
                        newBaseSize = track.baseSize;
                    }
                    axisTracks.get(trackIndex).baseSize = newBaseSize;

                    // Handle growth limits
                    //
                    // Computing an item's contributions calls into the child, so they are only computed if they are
                    // used.
                    // The min-content contribution is computed for every item in an intrinsic track (even where only
                    // the
                    // max-content contribution determines the growth limit), as the check for whether track sizing
                    // needs
                    // to be re-run compares each such item's min-content contribution against the one cached here.
                    track = axisTracks.get(trackIndex);
                    MaxTrackSizingFunction maxTrackSizingFunction = track.maxTrackSizingFunction;
                    float fitContentLimit = track.fitContentLimit();
                    @Opt
                    float minContentContribution = track.hasIntrinsicSizingFunction()
                            ? itemSizer.minContentContribution(item, axisTracks)
                            : OptF32.NONE;
                    @Opt float plannedIncrease;
                    if (maxTrackSizingFunction.isFitContent()) {
                        // Always increase the growth limit to at least the size of the *fit-content limited*
                        // max-content contribution
                        float maxContentContribution =
                                F32.min(itemSizer.maxContentContribution(item, axisTracks), fitContentLimit);

                        // If item is not a scroll container, then increase the growth limit to at least the
                        // size of the min-content contribution
                        plannedIncrease = OptF32.isSome(minContentContribution)
                                        && !item.overflow.get(axis).isScrollContainer()
                                ? F32.max(minContentContribution, maxContentContribution)
                                : maxContentContribution;
                    } else if (maxTrackSizingFunction.isMaxContentAlike()
                            || (maxTrackSizingFunction.usesPercentage() && OptF32.isNone(axisInnerNodeSize))) {
                        // If the container size is indefinite and has not yet been resolved then percentage sized
                        // tracks should be treated as auto (this matches Chrome's behaviour and seems sensible)
                        plannedIncrease = itemSizer.maxContentContribution(item, axisTracks);
                    } else if (maxTrackSizingFunction.isIntrinsic()) {
                        plannedIncrease = minContentContribution;
                    } else {
                        plannedIncrease = OptF32.NONE;
                    }
                    if (OptF32.isSome(plannedIncrease)) {
                        GridTrack t = axisTracks.get(trackIndex);
                        t.growthLimitPlannedIncrease = F32.max(t.growthLimitPlannedIncrease, plannedIncrease);
                    }
                }

                for (GridTrack track : axisTracks) {
                    if (track.growthLimitPlannedIncrease > 0.0f) {
                        track.growthLimit = track.growthLimit == Float.POSITIVE_INFINITY
                                ? track.growthLimitPlannedIncrease
                                : F32.max(track.growthLimit, track.growthLimitPlannedIncrease);
                    }
                    track.infinitelyGrowable = false;
                    track.growthLimitPlannedIncrease = 0.0f;
                    if (track.growthLimit < track.baseSize) {
                        track.growthLimit = track.baseSize;
                    }
                }

                continue;
            }

            // 1. For intrinsic minimums:
            // First increase the base size of tracks with an intrinsic min track sizing function
            for (GridItem item : batch.items()) {
                if (!item.crossesIntrinsicTrack(axis)) {
                    continue;
                }
                // ...by distributing extra space as needed to accommodate these items’ minimum contributions.
                //
                // QUIRK: The spec says that:
                //
                //   If the grid container is being sized under a min- or max-content constraint, use the items’ limited
                // min-content contributions
                //   in place of their minimum contributions here.
                //
                // However, in practice browsers only seem to apply this rule if the item is not a scroll container
                // (note that overflow:hidden counts as
                // a scroll container), giving the automatic minimum size of scroll containers (zero) precedence over
                // the min-content contributions.
                float space;
                if ((axisAvailableGridSpace == AvailableSpace.MIN_CONTENT
                                || axisAvailableGridSpace == AvailableSpace.MAX_CONTENT)
                        && !item.overflow.get(axis).isScrollContainer()) {
                    float axisMinimumSize = itemSizer.minimumContribution(item, axisTracks);
                    float axisMinContentSize = itemSizer.minContentContribution(item, axisTracks);
                    @Opt float limit = item.spannedTrackLimit(axis, axisTracks, axisInnerNodeSize, itemSizer::calc);
                    float limitedMinContent = F32.max(F32.maybeMinO(axisMinContentSize, limit), axisMinimumSize);

                    // For items crossing flexible tracks, browsers hand the content-derived contribution to
                    // the flexible tracks only in proportion to the crossed flex factor sum, clamped at one
                    // (CSS Grid 2, 12.5: "if the sum is less than one, distribute that proportion of
                    // space"). The proportion applies to the space left after covering the spanned
                    // inflexible tracks, and the item's minimum contribution acts as a floor on the result:
                    // a definite size still spreads over `0fr` tracks in full. This is what lets a `0fr`
                    // track holding a `min-height: 0` item collapse to zero, which the
                    // `grid-template-rows: 0fr` to `1fr` collapse animation pattern relies on.
                    if (batch.isFlex()) {
                        TrackCounts.TrackRange spanRange = item.trackRangeExcludingLines(axis);
                        List<GridTrack> spannedTracks = axisTracks.subList(spanRange.start(), spanRange.end());
                        float inflexibleSizes = 0.0f;
                        for (GridTrack t : spannedTracks) {
                            if (!t.isFlexible()) {
                                inflexibleSizes += t.baseSize;
                            }
                        }
                        float scale = F32.min(crossedFlexFactorSum(spannedTracks), 1.0f);
                        float excess = F32.max(limitedMinContent - inflexibleSizes, 0.0f);
                        space = F32.max(axisMinimumSize, inflexibleSizes + excess * scale);
                    } else {
                        space = limitedMinContent;
                    }
                } else {
                    space = itemSizer.minimumContribution(item, axisTracks);
                }
                TrackCounts.TrackRange trackRange = item.trackRangeExcludingLines(axis);
                List<GridTrack> tracks = axisTracks.subList(trackRange.start(), trackRange.end());
                if (space > 0.0f) {
                    Predicate<GridTrack> hasIntrinsicMinTrackSizingFunction = t ->
                            OptF32.isNone(t.minTrackSizingFunction.definiteValue(axisInnerNodeSize, itemSizer::calc));
                    if (item.overflow.get(axis).isScrollContainer()) {
                        distributeItemSpaceToBaseSize(
                                batch.isFlex(),
                                space,
                                tracks,
                                hasIntrinsicMinTrackSizingFunction,
                                GridTrack::fitContentLimitedGrowthLimit,
                                IntrinsicContributionType.MINIMUM);
                    } else {
                        distributeItemSpaceToBaseSize(
                                batch.isFlex(),
                                space,
                                tracks,
                                hasIntrinsicMinTrackSizingFunction,
                                t -> t.growthLimit,
                                IntrinsicContributionType.MINIMUM);
                    }
                }
            }
            flushPlannedBaseSizeIncreases(axisTracks);

            // 2. For content-based minimums:
            // Next continue to increase the base size of tracks with a min track sizing function of min-content or
            // max-content
            // by distributing extra space as needed to account for these items' min-content contributions.
            Predicate<GridTrack> hasMinOrMaxContentMinTrackSizingFunction =
                    t -> t.minTrackSizingFunction.isMinOrMaxContent();
            for (GridItem item : batch.items()) {
                if (!item.spansTrackMatching(axis, axisTracks, hasMinOrMaxContentMinTrackSizingFunction)) {
                    continue;
                }
                float space = itemSizer.minContentContribution(item, axisTracks);
                TrackCounts.TrackRange trackRange = item.trackRangeExcludingLines(axis);
                List<GridTrack> tracks = axisTracks.subList(trackRange.start(), trackRange.end());
                if (space > 0.0f) {
                    if (item.overflow.get(axis).isScrollContainer()) {
                        distributeItemSpaceToBaseSize(
                                batch.isFlex(),
                                space,
                                tracks,
                                hasMinOrMaxContentMinTrackSizingFunction,
                                GridTrack::fitContentLimitedGrowthLimit,
                                IntrinsicContributionType.MINIMUM);
                    } else {
                        distributeItemSpaceToBaseSize(
                                batch.isFlex(),
                                space,
                                tracks,
                                hasMinOrMaxContentMinTrackSizingFunction,
                                t -> t.growthLimit,
                                IntrinsicContributionType.MINIMUM);
                    }
                }
            }
            flushPlannedBaseSizeIncreases(axisTracks);

            // 3. For max-content minimums:

            // If the grid container is being sized under a max-content constraint, continue to increase the base size
            // of tracks with
            // a min track sizing function of auto or max-content by distributing extra space as needed to account for
            // these items'
            // limited max-content contributions.

            // Wrapping the method on GridTrack is necessary in order to resolve percentage fit-content arguments.
            if (axisAvailableGridSpace == AvailableSpace.MAX_CONTENT) {
                for (GridItem item : batch.items()) {
                    if (!item.spansTrackMatching(
                            axis,
                            axisTracks,
                            t -> hasAutoMinTrackSizingFunction(t) || hasMaxContentMinTrackSizingFunction(t))) {
                        continue;
                    }
                    float axisMaxContentSize = itemSizer.maxContentContribution(item, axisTracks);
                    @Opt float limit = item.spannedTrackLimit(axis, axisTracks, axisInnerNodeSize, itemSizer::calc);
                    float space = F32.maybeMinO(axisMaxContentSize, limit);

                    // As for the intrinsic minimums above: scale the space beyond the spanned inflexible
                    // tracks by the crossed flex factor sum, clamped at one. Anchoring at the inflexible
                    // track sizes rather than the current base sizes keeps this pass from compounding with
                    // the scaling already applied to the min-content contribution.
                    if (batch.isFlex()) {
                        TrackCounts.TrackRange spanRange = item.trackRangeExcludingLines(axis);
                        List<GridTrack> spannedTracks = axisTracks.subList(spanRange.start(), spanRange.end());
                        float inflexibleSizes = 0.0f;
                        for (GridTrack t : spannedTracks) {
                            if (!t.isFlexible()) {
                                inflexibleSizes += t.baseSize;
                            }
                        }
                        float scale = F32.min(crossedFlexFactorSum(spannedTracks), 1.0f);
                        space = inflexibleSizes + F32.max(space - inflexibleSizes, 0.0f) * scale;
                    }
                    TrackCounts.TrackRange trackRange = item.trackRangeExcludingLines(axis);
                    List<GridTrack> tracks = axisTracks.subList(trackRange.start(), trackRange.end());
                    if (space > 0.0f) {
                        // If any of the tracks spanned by the item have a MaxContent min track sizing function then
                        // distribute space only to those tracks. Otherwise distribute space to tracks with an Auto min
                        // track sizing function.
                        //
                        // Note: this prioritisation of MaxContent over Auto is not mentioned in the spec (which
                        // suggests that
                        // we ought to distribute space evenly between MaxContent and Auto tracks). But it is
                        // implemented like
                        // this in both Chrome and Firefox (and it does have a certain logic to it), so we implement it
                        // too for
                        // compatibility.
                        //
                        // See: https://www.w3.org/TR/css-grid-1/#track-size-max-content-min
                        boolean anyMaxContentMin = false;
                        for (GridTrack t : tracks) {
                            if (hasMaxContentMinTrackSizingFunction(t)) {
                                anyMaxContentMin = true;
                                break;
                            }
                        }
                        if (anyMaxContentMin) {
                            distributeItemSpaceToBaseSize(
                                    batch.isFlex(),
                                    space,
                                    tracks,
                                    TrackSizing::hasMaxContentMinTrackSizingFunction,
                                    t -> Float.POSITIVE_INFINITY,
                                    IntrinsicContributionType.MAXIMUM);
                        } else {
                            distributeItemSpaceToBaseSize(
                                    batch.isFlex(),
                                    space,
                                    tracks,
                                    TrackSizing::hasAutoMinTrackSizingFunction,
                                    GridTrack::fitContentLimitedGrowthLimit,
                                    IntrinsicContributionType.MAXIMUM);
                        }
                    }
                }
                flushPlannedBaseSizeIncreases(axisTracks);
            }

            // In all cases, continue to increase the base size of tracks with a min track sizing function of
            // max-content by distributing
            // extra space as needed to account for these items' max-content contributions.
            Predicate<GridTrack> hasMaxContentMinTrackSizingFunctionPredicate =
                    TrackSizing::hasMaxContentMinTrackSizingFunction;
            for (GridItem item : batch.items()) {
                if (!item.spansTrackMatching(axis, axisTracks, hasMaxContentMinTrackSizingFunctionPredicate)) {
                    continue;
                }
                float space = itemSizer.maxContentContribution(item, axisTracks);
                TrackCounts.TrackRange trackRange = item.trackRangeExcludingLines(axis);
                List<GridTrack> tracks = axisTracks.subList(trackRange.start(), trackRange.end());
                if (space > 0.0f) {
                    distributeItemSpaceToBaseSize(
                            batch.isFlex(),
                            space,
                            tracks,
                            hasMaxContentMinTrackSizingFunctionPredicate,
                            t -> t.growthLimit,
                            IntrinsicContributionType.MAXIMUM);
                }
            }
            flushPlannedBaseSizeIncreases(axisTracks);

            // 4. If at this point any track’s growth limit is now less than its base size, increase its growth limit to
            // match its base size.
            for (GridTrack track : axisTracks) {
                if (track.growthLimit < track.baseSize) {
                    track.growthLimit = track.baseSize;
                }
            }

            // If a track is a flexible track, then it has flexible max track sizing function
            // It cannot also have an intrinsic max track sizing function, so these steps do not apply.
            if (!batch.isFlex()) {
                // 5. For intrinsic maximums: Next increase the growth limit of tracks with an intrinsic max track
                // sizing function by
                // distributing extra space as needed to account for these items' min-content contributions.
                Predicate<GridTrack> hasIntrinsicMaxTrackSizingFunction =
                        t -> !t.maxTrackSizingFunction.hasDefiniteValue(axisInnerNodeSize);
                for (GridItem item : batch.items()) {
                    if (!item.spansTrackMatching(axis, axisTracks, hasIntrinsicMaxTrackSizingFunction)) {
                        continue;
                    }
                    float space = itemSizer.minContentContribution(item, axisTracks);
                    TrackCounts.TrackRange trackRange = item.trackRangeExcludingLines(axis);
                    List<GridTrack> tracks = axisTracks.subList(trackRange.start(), trackRange.end());
                    if (space > 0.0f) {
                        distributeItemSpaceToGrowthLimit(space, tracks, hasIntrinsicMaxTrackSizingFunction);
                    }
                }
                // Mark any tracks whose growth limit changed from infinite to finite in this step as infinitely
                // growable for the next step.
                flushPlannedGrowthLimitIncreases(axisTracks, true);

                // 6. For max-content maximums: Lastly continue to increase the growth limit of tracks with a max track
                // sizing function of max-content
                // by distributing extra space as needed to account for these items' max-content contributions. However,
                // limit the growth of any
                // fit-content() tracks by their fit-content() argument.
                Predicate<GridTrack> hasMaxContentMaxTrackSizingFunction =
                        t -> t.maxTrackSizingFunction.isMaxContentAlike()
                                || (t.maxTrackSizingFunction.usesPercentage() && OptF32.isNone(axisInnerNodeSize));
                for (GridItem item : batch.items()) {
                    if (!item.spansTrackMatching(axis, axisTracks, hasMaxContentMaxTrackSizingFunction)) {
                        continue;
                    }
                    float space = itemSizer.maxContentContribution(item, axisTracks);
                    TrackCounts.TrackRange trackRange = item.trackRangeExcludingLines(axis);
                    List<GridTrack> tracks = axisTracks.subList(trackRange.start(), trackRange.end());
                    if (space > 0.0f) {
                        distributeItemSpaceToGrowthLimit(space, tracks, hasMaxContentMaxTrackSizingFunction);
                    }
                }
                // Mark any tracks whose growth limit changed from infinite to finite in this step as infinitely
                // growable for the next step.
                flushPlannedGrowthLimitIncreases(axisTracks, false);
            }
        }

        // Step 5. If any track still has an infinite growth limit (because, for example, it had no items placed
        // in it or it is a flexible track), set its growth limit to its base size.
        // NOTE: this step is super-important to ensure that the "Maximise Tracks" step doesn't affect flexible tracks
        for (GridTrack track : axisTracks) {
            if (track.growthLimit == Float.POSITIVE_INFINITY) {
                track.growthLimit = track.baseSize;
            }
        }
    }

    /// Whether a track:
    ///   - has an Auto MIN track sizing function
    ///   - Does not have a MinContent MAX track sizing function
    ///
    /// The latter condition was added in order to match Chrome. But I believe it is due to the provision
    /// under minmax here https://www.w3.org/TR/css-grid-1/#track-sizes which states that:
    ///
    ///    "If the max is less than the min, then the max will be floored by the min (essentially yielding minmax(min,
    /// min))"
    private static boolean hasAutoMinTrackSizingFunction(GridTrack track) {
        return track.minTrackSizingFunction.isAuto() && !track.maxTrackSizingFunction.isMinContent();
    }

    /// Whether a track has a MaxContent min track sizing function
    private static boolean hasMaxContentMinTrackSizingFunction(GridTrack track) {
        return track.minTrackSizingFunction.isMaxContent();
    }

    /// The sum of the flex factors of the flexible tracks in an item's spanned track range
    private static float crossedFlexFactorSum(List<GridTrack> tracks) {
        float sum = 0.0f;
        for (GridTrack track : tracks) {
            if (track.isFlexible()) {
                sum += track.flexFactor();
            }
        }
        return sum;
    }

    /// 11.5.1. Distributing Extra Space Across Spanned Tracks
    /// https://www.w3.org/TR/css-grid-1/#extra-space
    private static void distributeItemSpaceToBaseSize(
            boolean isFlex,
            float space,
            List<GridTrack> tracks,
            Predicate<GridTrack> trackIsAffected,
            TrackFloatFn trackLimit,
            IntrinsicContributionType intrinsicContributionType) {
        if (isFlex) {
            Predicate<GridTrack> filter = t -> t.isFlexible() && trackIsAffected.test(t);

            // If the sum of the flex factors of the affected tracks is greater than zero, distribute
            // space according to the ratios of the tracks' flex factors. Otherwise distribute space equally.
            //
            // Note: the spec says to compute the sum over all flexible tracks spanned by the item, and to
            // blend flex-factor-proportional and equal distribution when the sum is below one. But Chrome
            // computes the sum over only the affected tracks and uses pure flex-factor ratios whenever
            // that sum is non-zero, so we do the same for compatibility.
            float flexFactorSum = 0.0f;
            for (GridTrack track : tracks) {
                if (filter.test(track)) {
                    flexFactorSum += track.flexFactor();
                }
            }
            if (flexFactorSum > 0.0f) {
                distributeItemSpaceToBaseSizeInner(
                        space, tracks, filter, GridTrack::flexFactor, trackLimit, intrinsicContributionType);
            } else {
                distributeItemSpaceToBaseSizeInner(
                        space, tracks, filter, t -> 1.0f, trackLimit, intrinsicContributionType);
            }
        } else {
            distributeItemSpaceToBaseSizeInner(
                    space, tracks, trackIsAffected, t -> 1.0f, trackLimit, intrinsicContributionType);
        }
    }

    /// Inner function that doesn't account for differences due to distributing to flex items
    /// This difference is handled by the closure passed in above
    private static void distributeItemSpaceToBaseSizeInner(
            float space,
            List<GridTrack> tracks,
            Predicate<GridTrack> trackIsAffected,
            TrackFloatFn trackDistributionProportion,
            TrackFloatFn trackLimit,
            IntrinsicContributionType intrinsicContributionType) {
        // Skip this distribution if there is either
        //   - no space to distribute
        //   - no affected tracks to distribute space to
        if (space == 0.0f || tracks.stream().noneMatch(trackIsAffected)) {
            return;
        }

        // Define get_base_size function. This is passed to the distribute_space_up_to_limits helper function
        // to indicate that it is the base size that is being distributed to.
        TrackFloatFn getBaseSize = t -> t.baseSize;

        // 1. Find the space to distribute
        float trackSizes = 0.0f;
        for (GridTrack track : tracks) {
            trackSizes += track.baseSize;
        }
        float extraSpace = F32.max(0.0f, space - trackSizes);

        // 2. Distribute space up to limits:
        // Note: there are two exit conditions to this loop:
        //   - We run out of space to distribute (extra_space falls below THRESHOLD)
        //   - We run out of growable tracks to distribute to

        extraSpace = distributeSpaceUpToLimits(
                extraSpace, tracks, trackIsAffected, trackDistributionProportion, getBaseSize, trackLimit);

        // 3. Distribute remaining span beyond limits (if any)
        if (extraSpace > DISTRIBUTE_BASE_SIZE_THRESHOLD) {
            // When accommodating minimum contributions or accommodating min-content contributions:
            //   - any affected track that happens to also have an intrinsic max track sizing function;
            // When accommodating max-content contributions:
            //   - any affected track that happens to also have a max-content max track sizing function
            Predicate<GridTrack> filter = intrinsicContributionType == IntrinsicContributionType.MINIMUM
                    ? t -> t.maxTrackSizingFunction.isIntrinsic()
                    : t -> t.maxTrackSizingFunction.isMaxOrFitContent();

            // If there are no such tracks (matching filter above), then use all affected tracks.
            int numberOfTracks = 0;
            for (GridTrack track : tracks) {
                if (trackIsAffected.test(track) && filter.test(track)) {
                    numberOfTracks++;
                }
            }
            if (numberOfTracks == 0) {
                filter = t -> true;
            }

            // When distributing beyond limits, growth limits are ignored, but the argument
            // to any fit-content() max track sizing function still caps growth.
            Predicate<GridTrack> beyondLimitFilter = filter;
            distributeSpaceUpToLimits(
                    extraSpace,
                    tracks,
                    t -> trackIsAffected.test(t) && beyondLimitFilter.test(t),
                    trackDistributionProportion,
                    getBaseSize,
                    GridTrack::fitContentLimit);
        }

        // 4. For each affected track, if the track’s item-incurred increase is larger than the track’s planned increase
        // set the track’s planned increase to that value.
        for (GridTrack track : tracks) {
            if (track.itemIncurredIncrease > track.baseSizePlannedIncrease) {
                track.baseSizePlannedIncrease = track.itemIncurredIncrease;
            }

            // Reset the item_incurresed increase ready for the next space distribution
            track.itemIncurredIncrease = 0.0f;
        }
    }

    /// 11.5.1. Distributing Extra Space Across Spanned Tracks
    /// This is simplified (and faster) version of the algorithm for growth limits
    /// https://www.w3.org/TR/css-grid-1/#extra-space
    private static void distributeItemSpaceToGrowthLimit(
            float space, List<GridTrack> tracks, Predicate<GridTrack> trackIsAffected) {
        // Skip this distribution if there is either
        //   - no space to distribute
        //   - no affected tracks to distribute space to
        if (space == 0.0f || tracks.stream().noneMatch(trackIsAffected)) {
            return;
        }

        // 1. Find the space to distribute
        float trackSizes = 0.0f;
        for (GridTrack track : tracks) {
            trackSizes += track.growthLimit == Float.POSITIVE_INFINITY ? track.baseSize : track.growthLimit;
        }
        float extraSpace = F32.max(0.0f, space - trackSizes);

        // 2. Distribute space up to limits:
        // For growth limits the limit is the growth limit itself unless the track is marked infinitely growable (or its
        // growth limit is still infinite), in which case it is the track's fit-content() argument if it has one, or
        // infinity if it does not. As the affected size of a track that isn't growable is its growth limit, those
        // tracks won't receive any space in this step.
        TrackFloatFn growthLimitOrBaseSize = t -> t.growthLimit == Float.POSITIVE_INFINITY ? t.baseSize : t.growthLimit;
        float remaining = distributeSpaceUpToLimits(
                extraSpace,
                tracks,
                t -> trackIsAffected.test(t) && (t.infinitelyGrowable || t.growthLimit == Float.POSITIVE_INFINITY),
                t -> 1.0f,
                growthLimitOrBaseSize,
                GridTrack::fitContentLimit);

        // 3. Distribute space beyond limits
        // If space remains after all tracks are frozen, unfreeze and continue to distribute space to the item-incurred
        // increase
        // ...when handling any intrinsic growth limit: all affected tracks.
        if (remaining > 0.0f) {
            distributeSpaceUpToLimits(
                    remaining, tracks, trackIsAffected, t -> 1.0f, growthLimitOrBaseSize, GridTrack::fitContentLimit);
        }

        // 4. For each affected track, if the track’s item-incurred increase is larger than the track’s planned increase
        // set the track’s planned increase to that value.
        for (GridTrack track : tracks) {
            if (track.itemIncurredIncrease > track.growthLimitPlannedIncrease) {
                track.growthLimitPlannedIncrease = track.itemIncurredIncrease;
            }

            // Reset the item_incurresed increase ready for the next space distribution
            track.itemIncurredIncrease = 0.0f;
        }
    }

    /// 11.6 Maximise Tracks
    /// Distributes free space (if any) to tracks with FINITE growth limits, up to their limits.
    private static void maximiseTracks(List<GridTrack> axisTracks, AvailableSpace axisAvailableGridSpace) {
        float usedSpace = 0.0f;
        for (GridTrack track : axisTracks) {
            usedSpace += track.baseSize;
        }
        float freeSpace = axisAvailableGridSpace.computeFreeSpace(usedSpace);
        if (freeSpace == Float.POSITIVE_INFINITY) {
            for (GridTrack track : axisTracks) {
                track.baseSize = track.growthLimit;
            }
        } else if (freeSpace > 0.0f) {
            distributeSpaceUpToLimits(
                    freeSpace,
                    axisTracks,
                    t -> true,
                    t -> 1.0f,
                    t -> t.baseSize,
                    GridTrack::fitContentLimitedGrowthLimit);
            for (GridTrack track : axisTracks) {
                track.baseSize += track.itemIncurredIncrease;
                track.itemIncurredIncrease = 0.0f;
            }
        }
    }

    /// 11.7. Expand Flexible Tracks
    /// This step sizes flexible tracks using the largest value it can assign to an fr without exceeding the available
    /// space.
    private static void expandFlexibleTracks(
            LayoutPartialTree tree,
            AbstractAxis axis,
            List<GridTrack> axisTracks,
            List<GridTrack> otherAxisTracks,
            List<GridItem> items,
            @Opt float axisMinSize,
            @Opt float axisMaxSize,
            AvailableSpace axisAvailableSpaceForExpansion,
            SizeOptF32 innerNodeSize,
            TrackSizeEstimate getTrackSizeEstimate) {
        IntrinsicSizeMeasurer itemSizer =
                new IntrinsicSizeMeasurer(tree, otherAxisTracks, getTrackSizeEstimate, axis, innerNodeSize);

        // First, find the grid’s used flex fraction:
        float flexFraction;
        if (axisAvailableSpaceForExpansion instanceof AvailableSpace.Definite definite) {
            // If the free space is zero:
            //    The used flex fraction is zero.
            // Otherwise, if the free space is a definite length:
            //   The used flex fraction is the result of finding the size of an fr using all of the grid tracks and
            //   a space to fill of the available grid space.
            float usedSpace = 0.0f;
            for (GridTrack track : axisTracks) {
                usedSpace += track.baseSize;
            }
            float freeSpace = definite.value() - usedSpace;
            flexFraction = freeSpace <= 0.0f ? 0.0f : findSizeOfFr(axisTracks, definite.value());
        } else if (axisAvailableSpaceForExpansion == AvailableSpace.MIN_CONTENT) {
            // If ... sizing the grid container under a min-content constraint the used flex fraction is zero.
            flexFraction = 0.0f;
        } else {
            // Otherwise, if the free space is an indefinite length:
            // The used flex fraction is the maximum of:
            float flexFractionFromTracks = 0.0f;
            boolean seenTrack = false;
            for (GridTrack track : axisTracks) {
                // For each flexible track, if the flexible track’s flex factor is greater than one,
                // the result of dividing the track’s base size by its flex factor; otherwise, the track’s base size.
                if (!track.maxTrackSizingFunction.isFr()) {
                    continue;
                }
                float flexFactor = track.flexFactor();
                float v = flexFactor > 1.0f ? track.baseSize / flexFactor : track.baseSize;
                if (!seenTrack || Float.compare(v, flexFractionFromTracks) > 0) {
                    flexFractionFromTracks = v;
                    seenTrack = true;
                }
            }
            float flexFractionFromItems = 0.0f;
            boolean seenItem = false;
            for (GridItem item : items) {
                // For each grid item that crosses a flexible track, the result of finding the size of an fr using all
                // the grid tracks
                // that the item crosses and a space to fill of the item’s max-content contribution.
                if (!item.crossesFlexibleTrack(axis)) {
                    continue;
                }
                float maxContentContribution = itemSizer.maxContentContribution(item, axisTracks);
                TrackCounts.TrackRange range = item.trackRangeExcludingLines(axis);
                float v = findSizeOfFr(axisTracks.subList(range.start(), range.end()), maxContentContribution);
                if (!seenItem || Float.compare(v, flexFractionFromItems) > 0) {
                    flexFractionFromItems = v;
                    seenItem = true;
                }
            }
            float flexFraction0 = F32.max(flexFractionFromTracks, flexFractionFromItems);

            // If using this flex fraction would cause the grid to be smaller than the grid container’s min-width/height
            // (or larger than the
            // grid container’s max-width/height), then redo this step, treating the free space as definite and the
            // available grid space as equal
            // to the grid container’s inner size when it’s sized to its min-width/height (max-width/height).
            // (Note: min_size takes precedence over max_size)
            float hypotheticalGridSize = 0.0f;
            for (GridTrack track : axisTracks) {
                if (track.maxTrackSizingFunction.isFr()) {
                    float trackFlexFactor = track.maxTrackSizingFunction.value();
                    hypotheticalGridSize += F32.max(track.baseSize, trackFlexFactor * flexFraction0);
                } else {
                    hypotheticalGridSize += track.baseSize;
                }
            }
            float minSize = OptF32.unwrapOr(axisMinSize, 0.0f);
            float maxSize = OptF32.unwrapOr(axisMaxSize, Float.POSITIVE_INFINITY);
            if (hypotheticalGridSize < minSize) {
                flexFraction = findSizeOfFr(axisTracks, minSize);
            } else if (hypotheticalGridSize > maxSize) {
                flexFraction = findSizeOfFr(axisTracks, maxSize);
            } else {
                flexFraction = flexFraction0;
            }
        }

        // For each flexible track, if the product of the used flex fraction and the track’s flex factor is greater
        // than the track’s base size, set its base size to that product.
        for (GridTrack track : axisTracks) {
            if (track.maxTrackSizingFunction.isFr()) {
                float trackFlexFactor = track.maxTrackSizingFunction.value();
                track.baseSize = F32.max(track.baseSize, trackFlexFactor * flexFraction);
            }
        }
    }

    /// 11.7.1. Find the Size of an fr
    /// This algorithm finds the largest size that an fr unit can be without exceeding the target size.
    /// It must be called with a set of grid tracks and some quantity of space to fill.
    private static float findSizeOfFr(List<GridTrack> tracks, float spaceToFill) {
        // Handle the trivial case where there is no space to fill
        // Do not remove as otherwise the loop below will loop infinitely
        if (spaceToFill == 0.0f) {
            return 0.0f;
        }

        // If the product of the hypothetical fr size (computed below) and any flexible track’s flex factor
        // is less than the track’s base size, then we must restart this algorithm treating all such tracks as
        // inflexible.
        // We therefore wrap the entire algorithm in a loop, with an hypothetical_fr_size of INFINITY such that the
        // above
        // condition can never be true for the first iteration.
        float hypotheticalFrSize = Float.POSITIVE_INFINITY;
        float previousIterHypotheticalFrSize;
        // Every restart of the algorithm treats at least one more flexible track as inflexible, so a valid
        // hypothetical fr size is always found within `tracks.len() + 1` iterations. Non-finite values (which
        // can arise from non-finite style inputs) break that invariant as comparisons against `NaN` are always
        // false, so we bound the iteration count to guarantee termination.
        int maxIterations = tracks.size() + 1;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            // Let leftover space be the space to fill minus the base sizes of the non-flexible grid tracks.
            // Let flex factor sum be the sum of the flex factors of the flexible tracks. If this value is less than 1,
            // set it to 1 instead.
            // We compute both of these in a single loop to avoid iterating over the data twice
            float usedSpace = 0.0f;
            float naiveFlexFactorSum = 0.0f;
            for (GridTrack track : tracks) {
                // Tracks for which flex_factor * hypothetical_fr_size < track.base_size are treated as inflexible
                if (track.maxTrackSizingFunction.isFr()
                        && track.maxTrackSizingFunction.value() * hypotheticalFrSize >= track.baseSize) {
                    naiveFlexFactorSum += track.maxTrackSizingFunction.value();
                } else {
                    usedSpace += track.baseSize;
                }
            }
            float leftoverSpace = spaceToFill - usedSpace;
            float flexFactor = F32.max(naiveFlexFactorSum, 1.0f);

            // Let the hypothetical fr size be the leftover space divided by the flex factor sum.
            previousIterHypotheticalFrSize = hypotheticalFrSize;
            hypotheticalFrSize = leftoverSpace / flexFactor;

            // If the product of the hypothetical fr size and a flexible track’s flex factor is less than the track’s
            // base size,
            // restart this algorithm treating all such tracks as inflexible.
            // We keep track of the hypothetical_fr_size
            boolean hypotheticalFrSizeIsValid = true;
            for (GridTrack track : tracks) {
                if (track.maxTrackSizingFunction.isFr()) {
                    float flexFactor2 = track.maxTrackSizingFunction.value();
                    if (!(flexFactor2 * hypotheticalFrSize >= track.baseSize
                            || flexFactor2 * previousIterHypotheticalFrSize < track.baseSize)) {
                        hypotheticalFrSizeIsValid = false;
                        break;
                    }
                }
            }
            if (hypotheticalFrSizeIsValid) {
                break;
            }
        }

        // Return the hypothetical fr size.
        return hypotheticalFrSize;
    }

    /// 11.8. Stretch auto Tracks
    /// This step expands tracks that have an auto max track sizing function by dividing any remaining positive,
    /// definite free space equally amongst them.
    private static void stretchAutoTracks(
            List<GridTrack> axisTracks, @Opt float axisMinSize, AvailableSpace axisAvailableSpaceForExpansion) {
        int numAutoTracks = 0;
        for (GridTrack track : axisTracks) {
            if (track.maxTrackSizingFunction.isAuto()) {
                numAutoTracks++;
            }
        }
        if (numAutoTracks > 0) {
            float usedSpace = 0.0f;
            for (GridTrack track : axisTracks) {
                usedSpace += track.baseSize;
            }

            // If the free space is indefinite, but the grid container has a definite min-width/height
            // use that size to calculate the free space for this step instead.
            float freeSpace;
            if (axisAvailableSpaceForExpansion.isDefinite()) {
                freeSpace = axisAvailableSpaceForExpansion.computeFreeSpace(usedSpace);
            } else {
                freeSpace = OptF32.isSome(axisMinSize) ? axisMinSize - usedSpace : 0.0f;
            }
            if (freeSpace > 0.0f) {
                float extraSpacePerAutoTrack = freeSpace / numAutoTracks;
                for (GridTrack track : axisTracks) {
                    if (track.maxTrackSizingFunction.isAuto()) {
                        track.baseSize += extraSpacePerAutoTrack;
                    }
                }
            }
        }
    }

    /// Helper function for distributing space to tracks evenly
    /// Used by both distribute_item_space_to_base_size and maximise_tracks steps
    private static float distributeSpaceUpToLimits(
            float spaceToDistribute,
            List<GridTrack> tracks,
            Predicate<GridTrack> trackIsAffected,
            TrackFloatFn trackDistributionProportion,
            TrackFloatFn trackAffectedProperty,
            TrackFloatFn trackLimit) {
        // Each iteration freezes at least one track at its limit, so the loop always completes within
        // `tracks.len() + 1` iterations. Non-finite values (which can arise from non-finite style inputs) can
        // prevent any space from being distributed, so we bound the iteration count to guarantee termination.
        int maxIterations = tracks.size() + 1;

        float remaining = spaceToDistribute;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            if (remaining <= DISTRIBUTE_LIMITS_THRESHOLD) {
                break;
            }
            float trackDistributionProportionSum = 0.0f;
            for (GridTrack track : tracks) {
                if (trackAffectedProperty.apply(track) + track.itemIncurredIncrease < trackLimit.apply(track)
                        && trackIsAffected.test(track)) {
                    trackDistributionProportionSum += trackDistributionProportion.apply(track);
                }
            }

            if (trackDistributionProportionSum == 0.0f) {
                break;
            }

            // Compute item-incurred increase for this iteration
            float minIncreaseLimit = Float.NaN;
            boolean foundIncreaseLimit = false;
            for (GridTrack track : tracks) {
                if (trackAffectedProperty.apply(track) + track.itemIncurredIncrease < trackLimit.apply(track)
                        && trackIsAffected.test(track)) {
                    float limit =
                            (trackLimit.apply(track) - trackAffectedProperty.apply(track) - track.itemIncurredIncrease)
                                    / trackDistributionProportion.apply(track);
                    if (!foundIncreaseLimit || Float.compare(limit, minIncreaseLimit) < 0) {
                        minIncreaseLimit = limit;
                        foundIncreaseLimit = true;
                    }
                }
            }
            if (!foundIncreaseLimit) {
                // `min_by(...).unwrap()` — cannot fire: proportion_sum > 0 implies
                // at least one matching track existed.
                throw new IllegalStateException("distribute_space_up_to_limits: no growable track found");
            }
            float iterationItemIncurredIncrease = F32.min(minIncreaseLimit, remaining / trackDistributionProportionSum);

            for (GridTrack track : tracks) {
                if (!trackIsAffected.test(track)) {
                    continue;
                }
                float increase = iterationItemIncurredIncrease * trackDistributionProportion.apply(track);
                if (increase > 0.0f
                        && trackAffectedProperty.apply(track) + track.itemIncurredIncrease + increase
                                <= trackLimit.apply(track) + DISTRIBUTE_LIMITS_THRESHOLD) {
                    track.itemIncurredIncrease += increase;
                    remaining -= increase;
                }
            }
        }

        return remaining;
    }
}
