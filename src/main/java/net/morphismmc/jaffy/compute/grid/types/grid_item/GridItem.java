// JAFFY: port of upstream src/compute/grid/types/grid_item.rs — `GridItem`
// (`KnownDimensionsCacheEntry` is in its sibling file).
//
// `pub(in super::super)` -> public (callers in compute.grid.* siblings);
// `NodeId` -> long (GAP-015); `u16`/`usize` -> int, `Line<u16>` -> Line<Integer>
// (GAP-014, boxing forced by generics); `Range<usize>` -> TrackCounts.TrackRange
// (JDK has no int-range type — same "track index range" record reused);
// `Size<Option<f32>>` -> SizeOptF32; `Option<f32>` -> @Opt float;
// `impl Fn(&GridTrack, Option<f32>) -> Option<f32>` -> GetTrackSizeEstimate
// (GAP-019-style adapter); `&dyn/&impl Fn(*const (), f32) -> f32` -> CalcResolver;
// `sum::<Option<f32>>` -> explicit loops with None propagation;
// `unwrap_or_else`/`or_else` chains -> if-chains (same laziness);
// `&mut self` vs `&self` -> ordinary instance methods (GAP-018);
// `Size::set(axis, v)` -> `with(axis, v)` reassignment (record, GAP-005).
// `#[derive(Debug)]` -> toString (best-effort Rust field format, GAP-016).
package net.morphismmc.jaffy.compute.grid.types.grid_item;

import java.util.List;
import java.util.function.Predicate;
import net.morphismmc.jaffy.compute.common.SizingKeyword;
import net.morphismmc.jaffy.compute.common.SizingKeyword.SizingKeywordResolution;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.LineOriginZeroLineExt;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.just.fmt.RustFmt;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.CompactBits;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.grid.GridItemStyle;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.F32;
import net.morphismmc.jaffy.util.MaybeResolve.RectLengthPercentageAutoExt;
import net.morphismmc.jaffy.util.MaybeResolve.RectLengthPercentageExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeDimensionExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeLengthPercentageAutoExt;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import org.jspecify.annotations.Nullable;


/// Represents a single grid item
public final class GridItem {
    /// The id of the node that this item represents
    public long node;

    /// The index of the item in the children array
    public int sourceOrder;

    /// The item's definite row-start and row-end, as resolved by the placement algorithm
    /// (in origin-zero coordinates). Set by the placement algorithm.
    public Line<OriginZeroLine> row;
    /// The items definite column-start and column-end, as resolved by the placement algorithm
    /// (in origin-zero coordinates). Set by the placement algorithm.
    public Line<OriginZeroLine> column;

    /// Is it a compressible replaced element?
    /// https://drafts.csswg.org/css-sizing-3/#min-content-zero
    public boolean isCompressibleReplaced;
    /// Whether the item is a replaced element. Such items are not stretched by `normal` alignment.
    public boolean isReplaced;
    /// The item's overflow style
    public Point<Overflow> overflow;
    /// The item's box_sizing style
    public BoxSizing boxSizing;
    /// The item's size style
    public Size<Dimension> size;
    /// The item's min_size style
    public Size<LengthPercentageAuto> minSize;
    /// The item's max_size style
    public Size<LengthPercentageAuto> maxSize;
    /// The item's aspect_ratio style
    public @Opt float aspectRatio;
    /// The item's padding style
    public Rect<LengthPercentage> padding;
    /// The item's border style
    public Rect<LengthPercentage> border;
    /// The item's margin style
    public Rect<LengthPercentageAuto> margin;
    /// The item's align_self property, or the parent's align_items property if it is `None`
    public AlignItems alignSelf;
    /// The item's justify_self property, or the parent's justify_items property if it is `None`
    public AlignItems justifySelf;
    /// The item's first baseline (horizontal), measured from the top of its border box. Set from the item's
    /// intrinsic layout when resolving baseline alignment during track sizing, then from its final layout.
    public @Opt float baseline;
    /// Shim for baseline alignment that acts like an extra top margin
    /// TODO: Support last baseline and vertical text baselines
    public float baselineShim;

    /// The item's definite row-start and row-end (same as `row` field, except in a different coordinate system)
    /// (as indexes into the Vec<GridTrack> stored in a grid's AbstractAxisTracks)
    public Line<Integer> rowIndexes;
    /// The items definite column-start and column-end (same as `column` field, except in a different coordinate system)
    /// (as indexes into the Vec<GridTrack> stored in a grid's AbstractAxisTracks)
    public Line<Integer> columnIndexes;

    /// Whether the item crosses a flexible row
    public boolean crossesFlexibleRow;
    /// Whether the item crosses a flexible column
    public boolean crossesFlexibleColumn;
    /// Whether the item crosses a intrinsic row
    public boolean crossesIntrinsicRow;
    /// Whether the item crosses a intrinsic column
    public boolean crossesIntrinsicColumn;

    // Caches for intrinsic size computation. These caches are only valid for a single run of the track-sizing algorithm.
    /// Cache for the known_dimensions input to intrinsic sizing computation
    public @Nullable SizeOptF32 gridAreaSizeCache;
    /// Cache for the known_dimensions passed to the child when computing its intrinsic contributions, stored along with
    /// the grid area size it was computed for. Must be cleared whenever `baseline_shim` changes.
    public @Nullable KnownDimensionsCacheEntry knownDimensionsCache;
    /// Cache for the min-content size
    public SizeOptF32 minContentContributionCache;
    /// Cache for the minimum contribution
    public SizeOptF32 minimumContributionCache;
    /// Cache for the max-content size
    public SizeOptF32 maxContentContributionCache;

    /// Final y position, excluding any relative offset (which does not affect the container's baseline).
    /// Used to compute baseline alignment for the container.
    public float yPosition;
    /// Final height. Used to compute baseline alignment for the container.
    public float height;

    /// Out-of-flow candidates bubbled out of this item's subtree (anchors relative to the
    /// container's border box)
    public OofCandidates oofCandidates;

    private GridItem() {}

    /// JAFFY support type: `impl Fn(&GridTrack, Option<f32>) -> Option<f32>` —
    /// produces a size estimate for a track given the available space in the
    /// track's axis.
    @FunctionalInterface
    public interface GetTrackSizeEstimate {
        @Opt float estimate(GridTrack track, @Opt float axisAvailableSpace);
    }

    /// Create a new item from its style. Its placement (`row`/`column`) is filled in by the placement algorithm.
    public static GridItem newWithStyleAndOrder(
            long node,
            GridItemStyle style,
            AlignItems parentAlignItems,
            AlignItems parentJustifyItems,
            int sourceOrder) {
        Line<OriginZeroLine> unplaced = new Line<>(new OriginZeroLine(0), new OriginZeroLine(0));
        GridItem item = new GridItem();
        item.node = node;
        item.sourceOrder = sourceOrder;
        item.row = unplaced;
        item.column = unplaced;
        item.isCompressibleReplaced = style.isCompressibleReplaced();
        item.isReplaced = style.isReplaced();
        item.overflow = style.overflow();
        item.boxSizing = style.boxSizing();
        item.size = style.size();
        item.minSize = style.minSize();
        item.maxSize = style.maxSize();
        item.aspectRatio = style.aspectRatio();
        item.padding = style.padding();
        item.border = style.border();
        item.margin = style.margin();
        item.alignSelf = style.alignSelf() != null ? style.alignSelf() : parentAlignItems;
        item.justifySelf = style.justifySelf() != null ? style.justifySelf() : parentJustifyItems;
        item.baseline = OptF32.NONE;
        item.baselineShim = 0.0f;
        item.rowIndexes = new Line<>(0, 0); // Properly initialised later
        item.columnIndexes = new Line<>(0, 0); // Properly initialised later
        item.crossesFlexibleRow = false; // Properly initialised later
        item.crossesFlexibleColumn = false; // Properly initialised later
        item.crossesIntrinsicRow = false; // Properly initialised later
        item.crossesIntrinsicColumn = false; // Properly initialised later
        item.gridAreaSizeCache = null;
        item.minContentContributionCache = SizeOptF32.NONE;
        item.maxContentContributionCache = SizeOptF32.NONE;
        item.knownDimensionsCache = null;
        item.minimumContributionCache = SizeOptF32.NONE;
        item.yPosition = 0.0f;
        item.height = 0.0f;
        item.oofCandidates = OofCandidates.none();
        return item;
    }

    /// Whether the item has an auto margin in the block axis
    public boolean hasAutoBlockMargin() {
        return margin.top().isAuto() || margin.bottom().isAuto();
    }

    /// Whether the item's block size depends on the size of its row(s), creating a cyclic
    /// dependency with baseline alignment (which affects row sizing). Per
    /// <https://www.w3.org/TR/css-grid-1/#row-align> such items do not participate in baseline
    /// alignment and are aligned using their fallback alignment instead.
    public boolean hasCyclicBlockSizeDependency() {
        // `self.size.height.0.uses_percentage()` — Dimension does not extend CompactLength;
        // same bit test via the tag (cf. `isSizingKeyword`)
        return CompactBits.usesPercentage(size.height().tag()) && (crossesIntrinsicRow || crossesFlexibleRow);
    }

    /// Returns true if the item participates in baseline alignment: it has `align-self: baseline`
    /// and neither of its block-axis margins are `auto`.
    /// See <https://www.w3.org/TR/css-align-3/#baseline-align-self>
    public boolean participatesInBaselineAlignment() {
        return alignSelf.keyword() == AlignItemsKeyword.BASELINE
                && !hasAutoBlockMargin()
                && !hasCyclicBlockSizeDependency();
    }

    /// This item's placement in the specified axis in OriginZero coordinates
    public Line<OriginZeroLine> placement(AbstractAxis axis) {
        return switch (axis) {
            case BLOCK -> row;
            case INLINE -> column;
        };
    }

    /// This item's placement in the specified axis as GridTrackVec indices
    public Line<Integer> placementIndexes(AbstractAxis axis) {
        return switch (axis) {
            case BLOCK -> rowIndexes;
            case INLINE -> columnIndexes;
        };
    }

    /// Returns a range which can be used as an index into the GridTrackVec in the specified axis
    /// which will produce a sub-slice of covering all the tracks and lines that this item spans
    /// excluding the lines that bound it.
    /// `Range<usize>` -> {@link TrackCounts.TrackRange}
    public TrackCounts.TrackRange trackRangeExcludingLines(AbstractAxis axis) {
        Line<Integer> indexes = placementIndexes(axis);
        return new TrackCounts.TrackRange(indexes.start() + 1, indexes.end());
    }

    /// Whether any track spanned by this item in the specified axis satisfies `predicate`
    public boolean spansTrackMatching(AbstractAxis axis, List<GridTrack> axisTracks, Predicate<GridTrack> predicate) {
        TrackCounts.TrackRange range = trackRangeExcludingLines(axis);
        for (int i = range.start(); i < range.end(); i++) {
            if (predicate.test(axisTracks.get(i))) {
                return true;
            }
        }
        return false;
    }

    /// Returns the number of tracks that this item spans in the specified axis
    public int span(AbstractAxis axis) {
        return switch (axis) {
            case BLOCK -> LineOriginZeroLineExt.span(row);
            case INLINE -> LineOriginZeroLineExt.span(column);
        };
    }

    /// Returns the pre-computed value indicating whether the grid item crosses a flexible track in
    /// the specified axis
    public boolean crossesFlexibleTrack(AbstractAxis axis) {
        return switch (axis) {
            case INLINE -> crossesFlexibleColumn;
            case BLOCK -> crossesFlexibleRow;
        };
    }

    /// Returns the pre-computed value indicating whether the grid item crosses an intrinsic track in
    /// the specified axis
    public boolean crossesIntrinsicTrack(AbstractAxis axis) {
        return switch (axis) {
            case INLINE -> crossesIntrinsicColumn;
            case BLOCK -> crossesIntrinsicRow;
        };
    }

    /// For an item spanning multiple tracks, the upper limit used to calculate its limited min-/max-content contribution is the
    /// sum of the fixed max track sizing functions of any tracks it spans, and is applied if it only spans such tracks.
    /// `Option<f32>` -> @Opt float
    public @Opt float spannedTrackLimit(
            AbstractAxis axis, List<GridTrack> axisTracks, @Opt float axisParentSize, CalcResolver resolveCalcValue) {
        TrackCounts.TrackRange range = trackRangeExcludingLines(axis);
        boolean tracksAllFixed = true;
        for (int i = range.start(); i < range.end(); i++) {
            if (OptF32.isNone(axisTracks.get(i).maxTrackSizingFunction.definiteLimit(axisParentSize, resolveCalcValue))) {
                tracksAllFixed = false;
                break;
            }
        }
        if (tracksAllFixed) {
            float limit = 0.0f;
            for (int i = range.start(); i < range.end(); i++) {
                limit += OptF32.unwrap(axisTracks.get(i).maxTrackSizingFunction.definiteLimit(axisParentSize, resolveCalcValue));
            }
            return limit;
        } else {
            return OptF32.NONE;
        }
    }

    /// Similar to the spanned_track_limit, but excludes FitContent arguments from the limit.
    /// Used to clamp the automatic minimum contributions of an item
    public @Opt float spannedFixedTrackLimit(
            AbstractAxis axis, List<GridTrack> axisTracks, @Opt float axisParentSize, CalcResolver resolveCalcValue) {
        TrackCounts.TrackRange range = trackRangeExcludingLines(axis);
        boolean tracksAllFixed = true;
        for (int i = range.start(); i < range.end(); i++) {
            if (OptF32.isNone(axisTracks.get(i).maxTrackSizingFunction.definiteValue(axisParentSize, resolveCalcValue))) {
                tracksAllFixed = false;
                break;
            }
        }
        if (tracksAllFixed) {
            float limit = 0.0f;
            for (int i = range.start(); i < range.end(); i++) {
                limit += OptF32.unwrap(axisTracks.get(i).maxTrackSizingFunction.definiteValue(axisParentSize, resolveCalcValue));
            }
            return limit;
        } else {
            return OptF32.NONE;
        }
    }

    /// Retrieve the known_dimensions for the given grid area size from the cache or compute them.
    /// The min-content and max-content contributions of an item are both computed from the same known_dimensions.
    SizeOptF32 knownDimensionsCached(LayoutPartialTree tree, SizeOptF32 gridAreaSize) {
        if (knownDimensionsCache != null && knownDimensionsCache.gridAreaSize().rustEq(gridAreaSize)) {
            return knownDimensionsCache.knownDimensions();
        }
        SizeOptF32 knownDimensions = knownDimensions(tree, gridAreaSize);
        knownDimensionsCache = new KnownDimensionsCacheEntry(gridAreaSize, knownDimensions);
        return knownDimensions;
    }

    /// Whether the given self-alignment stretches the item: `stretch`, or `normal` unless the item is replaced
    private boolean isStretchAligned(AlignItems alignment) {
        return switch (alignment.keyword()) {
            case STRETCH -> true;
            case NORMAL -> !isReplaced;
            default -> false;
        };
    }

    /// Compute the known_dimensions to be passed to the child sizing functions
    /// The key thing that is being done here is applying stretch alignment, which is necessary to
    /// allow percentage sizes further down the tree to resolve properly in some cases
    private SizeOptF32 knownDimensions(LayoutPartialTree tree, SizeOptF32 gridAreaSize) {
        SizeF32 margins = marginsAxisSumsWithBaselineShims(gridAreaSize.width(), tree);

        @Opt float aspectRatio = this.aspectRatio;
        // CSS resolves percentage padding and border against the inline size of the containing
        // block. For a grid item under intrinsic measurement, that inline-size basis is the grid
        // area's width when it is definite.
        // Spec:
        // https://www.w3.org/TR/css-grid-1/#item-margins
        // https://www.w3.org/TR/CSS22/box.html#padding-properties
        RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(this.padding, gridAreaSize.width(), tree::calc);
        RectF32 border = RectLengthPercentageExt.resolveOrZeroO(this.border, gridAreaSize.width(), tree::calc);
        SizeF32 paddingBorderSize = padding.plus(border).sumAxes();
        SizeF32 boxSizingAdjustment = boxSizing == BoxSizing.CONTENT_BOX ? paddingBorderSize : SizeF32.ZERO;
        SizeOptF32 inherentSize = SizeDimensionExt.maybeResolveO(size, gridAreaSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 minSize = SizeLengthPercentageAutoExt.maybeResolveO(this.minSize, gridAreaSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment)
                // The size of the item is floored by its padding and border
                .or(new SizeOptF32(paddingBorderSize.width(), paddingBorderSize.height()))
                .maybeMaxF(paddingBorderSize);
        SizeOptF32 maxSize = SizeLengthPercentageAutoExt.maybeResolveO(this.maxSize, gridAreaSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);

        SizeOptF32 gridAreaMinusItemMarginsSize = gridAreaSize.maybeSubF(margins).maybeMaxF(SizeF32.ZERO);

        // If node is absolutely positioned and width is not set explicitly, then deduce it
        // from left, right and container_content_box if both are set.
        @Opt float width = inherentSize.width();
        if (OptF32.isNone(width)) {
            // A width that is a sizing keyword is not auto, so it does not stretch. The stretch
            // keyword resolves to an exact width; the others resolve during content measurement.
            if (size.width().isSizingKeyword()) {
                SizingKeywordResolution res = SizingKeyword.resolveSizingKeyword(
                        size.width(), gridAreaMinusItemMarginsSize.width(), gridAreaSize.width(), tree::calc);
                width = res instanceof SizingKeywordResolution.Exact e ? e.value() : OptF32.NONE;
            } else if (!margin.left().isAuto()
                    && !margin.right().isAuto()
                    && isStretchAligned(justifySelf)) {
                // Apply width based on stretch alignment if:
                //  - Alignment style is "stretch" or "normal". Note that "normal" is treated as "stretch"
                //    here regardless of whether the item has a preferred size or aspect ratio in this axis,
                //    which differs from the rule used when the item is finally aligned (see `align_and_position_item`).
                //    Replaced elements are not stretched by "normal".
                //  - The node is not absolutely positioned
                //  - The node does not have auto margins in this axis.
                width = gridAreaMinusItemMarginsSize.width();
            }
        }

        // Reapply aspect ratio after stretch and absolute position width adjustments
        SizeOptF32 reapplied = new SizeOptF32(width, inherentSize.height()).maybeApplyAspectRatio(aspectRatio);
        width = reapplied.width();
        @Opt float height = reapplied.height();
        if (OptF32.isNone(height)) {
            // A height that is a sizing keyword is not auto, so it does not stretch. The stretch
            // keyword resolves to an exact height; the others resolve during content measurement.
            if (size.height().isSizingKeyword()) {
                SizingKeywordResolution res = SizingKeyword.resolveSizingKeyword(
                        size.height(), gridAreaMinusItemMarginsSize.height(), gridAreaSize.height(), tree::calc);
                height = res instanceof SizingKeywordResolution.Exact e ? e.value() : OptF32.NONE;
            } else if (!margin.top().isAuto()
                    && !margin.bottom().isAuto()
                    && isStretchAligned(alignSelf)) {
                // Apply height based on stretch alignment if:
                //  - Alignment style is "stretch" or "normal" (see the note on width above)
                //  - The node is not absolutely positioned
                //  - The node does not have auto margins in this axis.
                height = gridAreaMinusItemMarginsSize.height();
            }
        }

        // Reapply aspect ratio after stretch and absolute position height adjustments
        reapplied = new SizeOptF32(width, height).maybeApplyAspectRatio(aspectRatio);

        // Clamp size by min and max width/height
        return reapplied.maybeClampO(minSize, maxSize);
    }

    /// Returns the grid area's size in the specified axis when every spanned track has a definite fixed size.
    ///
    /// During intrinsic sizing, percentages on grid items resolve against the size of the grid area,
    /// not the grid container. If the spanned tracks in an axis are not all definite yet, the grid
    /// area is still indefinite in that axis and percentage-dependent values must stay unresolved here.
    ///
    /// Spec:
    /// https://www.w3.org/TR/css-grid-1/#grid-item-sizing
    /// https://www.w3.org/TR/css-grid-1/#algo-overview
    ///
    /// Compute the available_space to be passed to the child sizing functions
    /// These are estimates based on either the max track sizing function or the provisional base size in the opposite
    /// axis to the one currently being sized.
    /// https://www.w3.org/TR/css-grid-1/#algo-overview
    public SizeOptF32 gridAreaSize(
            AbstractAxis axis,
            List<GridTrack> axisTracks,
            List<GridTrack> otherAxisTracks,
            SizeOptF32 availableSpace,
            GetTrackSizeEstimate getTrackSizeEstimate,
            CalcResolver resolveCalcValue) {
        SizeOptF32 size = SizeOptF32.NONE;

        // `.map(...).sum::<Option<f32>>()`: the sum is None if any track yields None
        {
            @Opt float sum = 0.0f;
            TrackCounts.TrackRange range = trackRangeExcludingLines(axis);
            for (int i = range.start(); i < range.end(); i++) {
                GridTrack track = axisTracks.get(i);
                @Opt float minSize = track.minTrackSizingFunction.definiteValue(availableSpace.get(axis), resolveCalcValue);
                if (OptF32.isNone(minSize)) {
                    sum = OptF32.NONE;
                    break;
                }
                @Opt float maxSize = track.maxTrackSizingFunction.definiteValue(availableSpace.get(axis), resolveCalcValue);
                if (OptF32.isNone(maxSize)) {
                    sum = OptF32.NONE;
                    break;
                }
                if (minSize == maxSize) { // Some == Some: IEEE eq (GAP-004)
                    sum += track.baseSize;
                } else {
                    sum = OptF32.NONE;
                    break;
                }
            }
            size = size.with(axis, sum);
        }

        {
            AbstractAxis otherAxis = axis.other();
            @Opt float sum = 0.0f;
            TrackCounts.TrackRange range = trackRangeExcludingLines(otherAxis);
            for (int i = range.start(); i < range.end(); i++) {
                GridTrack track = otherAxisTracks.get(i);
                @Opt float estimate = getTrackSizeEstimate.estimate(track, availableSpace.get(otherAxis));
                if (OptF32.isNone(estimate)) {
                    sum = OptF32.NONE;
                    break;
                }
                sum += estimate + track.contentAlignmentAdjustment;
            }
            size = size.with(otherAxis, sum);
        }

        return size;
    }

    /// Retrieve the available_space from the cache or compute them using the passed parameters
    public SizeOptF32 gridAreaSizeCached(
            AbstractAxis axis,
            List<GridTrack> axisTracks,
            List<GridTrack> otherAxisTracks,
            SizeOptF32 availableSpace,
            GetTrackSizeEstimate getTrackSizeEstimate,
            CalcResolver resolveCalcValue) {
        if (gridAreaSizeCache == null) {
            gridAreaSizeCache = gridAreaSize(
                    axis, axisTracks, otherAxisTracks, availableSpace, getTrackSizeEstimate, resolveCalcValue);
        }
        return gridAreaSizeCache;
    }

    /// Compute the item's resolved margins for size contributions. Horizontal percentage margins always resolve
    /// to zero if the container size is indefinite as otherwise this would introduce a cyclic dependency.
    public SizeF32 marginsAxisSumsWithBaselineShims(@Opt float innerNodeWidth, LayoutPartialTree tree) {
        return new RectF32(
                        margin.left().resolveOrZero(0.0f, tree::calc),
                        margin.right().resolveOrZero(0.0f, tree::calc),
                        margin.top().resolveOrZero(innerNodeWidth, tree::calc) + baselineShim,
                        margin.bottom().resolveOrZero(innerNodeWidth, tree::calc))
                .sumAxes();
    }

    /// Compute the item's min content contribution from the provided parameters
    public float minContentContribution(
            AbstractAxis axis, LayoutPartialTree tree, SizeOptF32 gridAreaSize, SizeOptF32 availableSpace) {
        SizeOptF32 knownDimensions = knownDimensionsCached(tree, gridAreaSize);
        // If the item's size in the axis being measured is already known then that size is its contribution,
        // and we can avoid calling into the child entirely.
        @Opt float known = knownDimensions.get(axis);
        if (OptF32.isSome(known)) {
            return known;
        }
        // The child sees the grid area as its containing block during intrinsic measurement, so
        // percentage box properties resolve against the grid area when that size is definite.
        // Spec:
        // https://www.w3.org/TR/css-grid-1/#grid-item-sizing
        // https://www.w3.org/TR/css-grid-1/#algo-overview
        return tree.measureChildSize(
                node,
                knownDimensions,
                gridAreaSize,
                keywordAdjustedAvailableSpace(
                        gridAreaSize,
                        mapToAvailableSpaceMin(availableSpaceMinusMargins(gridAreaSize, availableSpace, tree)),
                        tree),
                SizingMode.INHERENT_SIZE,
                axis.asAbsNaive(),
                LineBool.FALSE);
    }

    /// `Size<Option<f32>>::map(|opt| Some -> Definite / None -> MinContent)`
    private static Size<AvailableSpace> mapToAvailableSpaceMin(SizeOptF32 space) {
        return new Size<>(
                OptF32.isSome(space.width()) ? new AvailableSpace.Definite(space.width()) : AvailableSpace.MIN_CONTENT,
                OptF32.isSome(space.height()) ? new AvailableSpace.Definite(space.height()) : AvailableSpace.MIN_CONTENT);
    }

    /// `Size<Option<f32>>::map(|opt| Some -> Definite / None -> MaxContent)`
    private static Size<AvailableSpace> mapToAvailableSpaceMax(SizeOptF32 space) {
        return new Size<>(
                OptF32.isSome(space.width()) ? new AvailableSpace.Definite(space.width()) : AvailableSpace.MAX_CONTENT,
                OptF32.isSome(space.height()) ? new AvailableSpace.Definite(space.height()) : AvailableSpace.MAX_CONTENT);
    }

    /// Retrieve the item's min content contribution from the cache or compute it using the provided parameters
    public float minContentContributionCached(
            AbstractAxis axis, LayoutPartialTree tree, SizeOptF32 gridAreaSize, SizeOptF32 availableSpace) {
        @Opt float cached = minContentContributionCache.get(axis);
        if (OptF32.isSome(cached)) {
            return cached;
        }
        float size = minContentContribution(axis, tree, gridAreaSize, availableSpace);
        minContentContributionCache = minContentContributionCache.with(axis, size);
        return size;
    }

    /// Compute the item's max content contribution from the provided parameters
    public float maxContentContribution(
            AbstractAxis axis, LayoutPartialTree tree, SizeOptF32 gridAreaSize, SizeOptF32 availableSpace) {
        SizeOptF32 knownDimensions = knownDimensionsCached(tree, gridAreaSize);
        // If the item's size in the axis being measured is already known then that size is its contribution,
        // and we can avoid calling into the child entirely.
        @Opt float known = knownDimensions.get(axis);
        if (OptF32.isSome(known)) {
            return known;
        }
        // See the min-content path above. Max-content measurement uses the same containing-block
        // basis so percentage-dependent item geometry is measured from the grid area rather than
        // from the container.
        return tree.measureChildSize(
                node,
                knownDimensions,
                gridAreaSize,
                keywordAdjustedAvailableSpace(
                        gridAreaSize,
                        mapToAvailableSpaceMax(availableSpaceMinusMargins(gridAreaSize, availableSpace, tree)),
                        tree),
                SizingMode.INHERENT_SIZE,
                axis.asAbsNaive(),
                LineBool.FALSE);
    }

    /// Subtract the item's margins (and baseline shim) from the definite axes of the space available
    /// to the item's margin box, giving the available space that is passed to the item.
    /// As when the item is laid out into its final position, margins resolve against the width
    /// of the grid area and `auto` margins are treated as zero.
    private SizeOptF32 availableSpaceMinusMargins(
            SizeOptF32 gridAreaSize, SizeOptF32 availableSpace, LayoutPartialTree tree) {
        if (OptF32.isNone(availableSpace.width()) && OptF32.isNone(availableSpace.height())) {
            return availableSpace;
        }
        RectF32 margin = RectLengthPercentageAutoExt.resolveOrZeroO(this.margin, gridAreaSize.width(), tree::calc);
        return new SizeOptF32(
                OptF32.map(availableSpace.width(), width -> F32.max(width - margin.horizontalAxisSum(), 0.0f)),
                OptF32.map(availableSpace.height(),
                        height -> F32.max(height - margin.verticalAxisSum() - baselineShim, 0.0f)));
    }

    /// Override the available space in each axis whose size style is a sizing keyword that
    /// measures the item under a specific available space constraint
    /// (min-content, max-content, fit-content, fit-content(...))
    private Size<AvailableSpace> keywordAdjustedAvailableSpace(
            SizeOptF32 gridAreaSize, Size<AvailableSpace> availableSpace, LayoutPartialTree tree) {
        if (!size.width().isSizingKeyword() && !size.height().isSizingKeyword()) {
            return availableSpace;
        }
        SizeF32 margins = marginsAxisSumsWithBaselineShims(gridAreaSize.width(), tree);
        Size<AvailableSpace> adjusted = availableSpace;
        for (AbstractAxis axis : new AbstractAxis[] {AbstractAxis.INLINE, AbstractAxis.BLOCK}) {
            Dimension sizeStyle = size.get(axis);
            if (!sizeStyle.isSizingKeyword()) {
                continue;
            }
            @Opt float stretchSize =
                    OptF32.maybeMaxF(OptF32.maybeSubF(gridAreaSize.get(axis), margins.get(axis)), 0.0f);
            if (SizingKeyword.resolveSizingKeyword(sizeStyle, stretchSize, gridAreaSize.get(axis), tree::calc)
                    instanceof SizingKeywordResolution.Measure(AvailableSpace available)) {
                adjusted = adjusted.with(axis, available);
            }
        }
        return adjusted;
    }

    /// Retrieve the item's max content contribution from the cache or compute it using the provided parameters
    public float maxContentContributionCached(
            AbstractAxis axis, LayoutPartialTree tree, SizeOptF32 gridAreaSize, SizeOptF32 availableSpace) {
        @Opt float cached = maxContentContributionCache.get(axis);
        if (OptF32.isSome(cached)) {
            return cached;
        }
        float size = maxContentContribution(axis, tree, gridAreaSize, availableSpace);
        maxContentContributionCache = maxContentContributionCache.with(axis, size);
        return size;
    }

    /// The minimum contribution of an item is the smallest outer size it can have.
    /// Specifically:
    ///   - If the item’s computed preferred size behaves as auto or depends on the size of its containing block in the relevant axis:
    ///     Its minimum contribution is the outer size that would result from assuming the item’s used minimum size as its preferred size;
    ///   - Else the item’s minimum contribution is its min-content contribution.
    ///
    /// Because the minimum contribution often depends on the size of the item's content, it is considered a type of intrinsic size contribution.
    /// See: https://www.w3.org/TR/css-grid-1/#min-size-auto
    public float minimumContribution(
            LayoutPartialTree tree,
            AbstractAxis axis,
            List<GridTrack> axisTracks,
            SizeOptF32 gridAreaSize,
            SizeOptF32 innerNodeSize) {
        RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(this.padding, gridAreaSize.width(), tree::calc);
        RectF32 border = RectLengthPercentageExt.resolveOrZeroO(this.border, gridAreaSize.width(), tree::calc);
        SizeF32 paddingBorderSize = padding.plus(border).sumAxes();
        SizeF32 boxSizingAdjustment = boxSizing == BoxSizing.CONTENT_BOX ? paddingBorderSize : SizeF32.ZERO;

        // `self.size.maybe_resolve(...).maybe_apply_aspect_ratio(...).maybe_add(adjustment).get(axis)`
        // `.map(...).or_else(...).or_else(...).or_else(...).unwrap_or_else(...)`
        @Opt float result = SizeDimensionExt.maybeResolveO(size, gridAreaSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment)
                .get(axis);
        if (OptF32.isSome(result)) {
            // A definite preferred size is clamped by the minimum and maximum sizes. For a replaced element (but
            // not a form control), an indefinite percentage maximum size is resolved against zero. The spec also
            // applies this to form controls (https://drafts.csswg.org/css-sizing-3/#min-content-zero) but no
            // browser does (https://github.com/w3c/csswg-drafts/issues/6348).
            LengthPercentageAuto minSizeStyle = minSize.get(axis);
            LengthPercentageAuto maxSizeStyle = maxSize.get(axis);
            if (!minSizeStyle.isAuto() || !maxSizeStyle.isAuto()) {
                @Opt float basis = gridAreaSize.get(axis);
                @Opt float maxSizeBasis = isReplaced ? OptF32.or(basis, 0.0f) : basis;
                float adjustment = boxSizingAdjustment.get(axis);
                @Opt float resolvedMin = minSizeStyle.maybeResolveO(basis, tree::calc);
                @Opt float resolvedMax = maxSizeStyle.maybeResolveO(maxSizeBasis, tree::calc);
                result = F32.maybeClampO(
                        result, OptF32.maybeAddF(resolvedMin, adjustment), OptF32.maybeAddF(resolvedMax, adjustment));
            }
        }
        if (OptF32.isNone(result)) {
            // A preferred size that is a sizing keyword other than `stretch` does not behave as auto,
            // so the minimum contribution is the min-content contribution.
            Dimension sizeStyle = size.get(axis);
            if (sizeStyle.isSizingKeyword() && !sizeStyle.isStretch()) {
                result = minContentContributionCached(axis, tree, gridAreaSize, gridAreaSize);
            }
        }
        if (OptF32.isNone(result)) {
            result = SizeLengthPercentageAutoExt.maybeResolveF(
                            minSize, gridAreaSize.mapF(v -> OptF32.unwrapOr(v, 0.0f)), tree::calc)
                    .maybeApplyAspectRatio(aspectRatio)
                    .maybeAddF(boxSizingAdjustment)
                    .get(axis);
        }
        if (OptF32.isNone(result)) {
            result = overflow.get(axis).maybeIntoAutomaticMinSize();
        }
        if (OptF32.isNone(result)) {
            // Automatic minimum size. See https://www.w3.org/TR/css-grid-1/#min-size-auto

            // To provide a more reasonable default minimum size for grid items, the used value of its automatic minimum size
            // in a given axis is the content-based minimum size if all of the following are true:
            TrackCounts.TrackRange range = trackRangeExcludingLines(axis);
            List<GridTrack> itemAxisTracks = axisTracks.subList(range.start(), range.end());

            // it is not a scroll container (handled above)

            // it spans at least one track in that axis whose min track sizing function is auto
            boolean spansAutoMinTrack = false;
            for (GridTrack track : itemAxisTracks) {
                if (track.minTrackSizingFunction.behavesAsAuto(innerNodeSize.get(axis))) {
                    spansAutoMinTrack = true;
                    break;
                }
            }

            // if it spans more than one track in that axis, none of those tracks are flexible
            boolean onlySpanOneTrack = itemAxisTracks.size() == 1;
            boolean spansFrTrack = false;
            for (GridTrack track : itemAxisTracks) {
                if (track.maxTrackSizingFunction.isFr()) {
                    spansFrTrack = true;
                    break;
                }
            }
            boolean useContentBasedMinimum = spansAutoMinTrack && (onlySpanOneTrack || !spansFrTrack);

            // Otherwise, the automatic minimum size is zero, as usual.
            if (useContentBasedMinimum) {
                float minimumContribution = minContentContributionCached(axis, tree, gridAreaSize, gridAreaSize);

                // If the item is a compressible replaced element, and has a definite preferred size or maximum size in the
                // relevant axis, the size suggestion is capped by those sizes; for this purpose, any indefinite percentages
                // in these sizes are resolved against zero (and considered definite).
                if (isCompressibleReplaced) {
                    @Opt float preferredSize = size.get(axis).maybeResolveO(0.0f, tree::calc);
                    @Opt float resolvedMaxSize = maxSize.get(axis).maybeResolveO(0.0f, tree::calc);
                    minimumContribution = F32.maybeMinO(F32.maybeMinO(minimumContribution, preferredSize), resolvedMaxSize);
                }

                // The content-based minimum size is additionally clamped by the sum of any fixed max track sizing
                // functions of the tracks the item spans. Note that this clamp does not apply to explicitly specified
                // preferred or minimum sizes, and that the argument to fit-content() does not clamp the content-based
                // minimum size in the same way as a fixed max track sizing function. The clamp is a stretch fit of
                // the item's margin box (including its baseline shim) into the limit, so it does not shrink the
                // item below its padding + border.
                @Opt float limit = spannedFixedTrackLimit(axis, axisTracks, innerNodeSize.get(axis), tree::resolveCalcValue);
                SizeF32 margins = marginsAxisSumsWithBaselineShims(gridAreaSize.width(), tree);
                result = F32.maybeMinO(
                        minimumContribution,
                        OptF32.map(limit, l -> F32.max(l - margins.get(axis), paddingBorderSize.get(axis))));
            } else {
                result = 0.0f;
            }
        }
        return result;
    }

    /// Retrieve the item's minimum contribution from the cache or compute it using the provided parameters
    public float minimumContributionCached(
            LayoutPartialTree tree,
            AbstractAxis axis,
            List<GridTrack> axisTracks,
            SizeOptF32 gridAreaSize,
            SizeOptF32 innerNodeSize) {
        @Opt float cached = minimumContributionCache.get(axis);
        if (OptF32.isSome(cached)) {
            return cached;
        }
        float size = minimumContribution(tree, axis, axisTracks, gridAreaSize, innerNodeSize);
        minimumContributionCache = minimumContributionCache.with(axis, size);
        return size;
    }

    /// `#[derive(Debug)]` — best-effort Rust field format
    @Override
    public String toString() {
        return "GridItem { node: NodeId(" + node + "), source_order: " + sourceOrder
                + ", row: Line { start: " + row.start() + ", end: " + row.end() + " }"
                + ", column: Line { start: " + column.start() + ", end: " + column.end() + " }"
                + ", is_compressible_replaced: " + isCompressibleReplaced
                + ", is_replaced: " + isReplaced
                + ", overflow: " + overflow
                + ", box_sizing: " + boxSizing
                + ", size: " + size
                + ", min_size: " + minSize
                + ", max_size: " + maxSize
                + ", aspect_ratio: " + (OptF32.isSome(aspectRatio) ? "Some(" + RustFmt.debug(aspectRatio) + ")" : "None")
                + ", padding: " + padding
                + ", border: " + border
                + ", margin: " + margin
                + ", align_self: " + alignSelf
                + ", justify_self: " + justifySelf
                + ", baseline: " + (OptF32.isSome(baseline) ? "Some(" + RustFmt.debug(baseline) + ")" : "None")
                + ", baseline_shim: " + RustFmt.debug(baselineShim)
                + ", row_indexes: Line { start: " + rowIndexes.start() + ", end: " + rowIndexes.end() + " }"
                + ", column_indexes: Line { start: " + columnIndexes.start() + ", end: " + columnIndexes.end() + " }"
                + ", crosses_flexible_row: " + crossesFlexibleRow
                + ", crosses_flexible_column: " + crossesFlexibleColumn
                + ", crosses_intrinsic_row: " + crossesIntrinsicRow
                + ", crosses_intrinsic_column: " + crossesIntrinsicColumn
                + ", grid_area_size_cache: " + (gridAreaSizeCache != null ? "Some(...)" : "None")
                + ", known_dimensions_cache: " + (knownDimensionsCache != null ? "Some(...)" : "None")
                + ", min_content_contribution_cache: " + minContentContributionCache
                + ", minimum_contribution_cache: " + minimumContributionCache
                + ", max_content_contribution_cache: " + maxContentContributionCache
                + ", y_position: " + RustFmt.debug(yPosition)
                + ", height: " + RustFmt.debug(height)
                + ", oof_candidates: " + oofCandidates
                + " }";
    }
}
