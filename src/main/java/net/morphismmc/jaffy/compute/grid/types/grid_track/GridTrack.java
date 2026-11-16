// JAFFY: port of upstream src/compute/grid/types/grid_track.rs — `GridTrack`.
//
// `pub(in super::super)` -> public; Clone -> copy() (all fields are
// immutable/value types so a field-wise copy suffices) (GAP-005).
// `axis_inner_node_size: Option<f32>` -> @Opt float (GAP-001).
package net.morphismmc.jaffy.compute.grid.types.grid_track;

import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;


/// Internal sizing information for a single grid track (row/column)
/// Gutters between tracks are sized similarly to actual tracks, so they
/// are also represented by this struct
public final class GridTrack {

    /// Whether the track is a full track, a gutter, or a placeholder that has not yet been initialised
    public GridTrackKind kind;

    /// Whether the track is a collapsed track/gutter. Collapsed tracks are effectively treated as if
    /// they don't exist for the purposes of grid sizing. Gutters between collapsed tracks are also collapsed.
    public boolean isCollapsed;

    /// The minimum track sizing function of the track
    public MinTrackSizingFunction minTrackSizingFunction;

    /// The maximum track sizing function of the track
    public MaxTrackSizingFunction maxTrackSizingFunction;

    /// The distance of the start of the track from the start of the grid container
    public float offset;

    /// The size (width/height as applicable) of the track
    public float baseSize;

    /// A temporary scratch value when sizing tracks
    /// Note: can be infinity
    public float growthLimit;

    /// A temporary scratch value when sizing tracks. Is used as an additional amount to add to the
    /// estimate for the available space in the opposite axis when content sizing items
    public float contentAlignmentAdjustment;

    /// A temporary scratch value when "distributing space" to avoid clobbering planned increase variable
    public float itemIncurredIncrease;
    /// A temporary scratch value when "distributing space" to avoid clobbering the main variable
    public float baseSizePlannedIncrease;
    /// A temporary scratch value when "distributing space" to avoid clobbering the main variable
    public float growthLimitPlannedIncrease;
    /// A temporary scratch value when "distributing space"
    /// See: https://www.w3.org/TR/css3-grid-layout/#infinitely-growable
    public boolean infinitelyGrowable;

    /// The resolved `fit-content()` limit of the max track sizing function (infinity if it is not `fit-content()`
    /// or the limit cannot be resolved). Resolved once per track sizing pass by `resolve_fit_content_limit`.
    public float fitContentLimit;

    /// GridTrack constructor with all configuration parameters for the other constructors exposed
    private GridTrack(GridTrackKind kind, MinTrackSizingFunction minTrackSizingFunction,
            MaxTrackSizingFunction maxTrackSizingFunction) {
        this.kind = kind;
        this.isCollapsed = false;
        this.minTrackSizingFunction = minTrackSizingFunction;
        this.maxTrackSizingFunction = maxTrackSizingFunction;
        this.offset = 0.0f;
        this.baseSize = 0.0f;
        this.growthLimit = 0.0f;
        this.contentAlignmentAdjustment = 0.0f;
        this.itemIncurredIncrease = 0.0f;
        this.baseSizePlannedIncrease = 0.0f;
        this.growthLimitPlannedIncrease = 0.0f;
        this.infinitelyGrowable = false;
        this.fitContentLimit = Float.POSITIVE_INFINITY;
    }

    /// Create new GridTrack representing an actual track (not a gutter)
    public static GridTrack newGridTrack(MinTrackSizingFunction minTrackSizingFunction,
            MaxTrackSizingFunction maxTrackSizingFunction) {
        return new GridTrack(GridTrackKind.TRACK, minTrackSizingFunction, maxTrackSizingFunction);
    }

    /// Create a new GridTrack representing a gutter
    public static GridTrack gutter(LengthPercentage size) {
        return new GridTrack(GridTrackKind.GUTTER, MinTrackSizingFunction.from(size),
                MaxTrackSizingFunction.from(size));
    }

    /// Mark a GridTrack as collapsed. Also sets both of the track's sizing functions
    /// to fixed zero-sized sizing functions.
    public void collapse() {
        isCollapsed = true;
        minTrackSizingFunction = MinTrackSizingFunction.ZERO;
        maxTrackSizingFunction = MaxTrackSizingFunction.ZERO;
        fitContentLimit = Float.POSITIVE_INFINITY;
    }

    /// Returns true if the track is flexible (has a Flex MaxTrackSizingFunction), else false.
    public boolean isFlexible() {
        return maxTrackSizingFunction.isFr();
    }

    /// Returns true if the track uses a percentage sizing function on either end
    public boolean usesPercentage() {
        return minTrackSizingFunction.usesPercentage() || maxTrackSizingFunction.usesPercentage();
    }

    /// Returns true if the track has an intrinsic min and or max sizing function
    public boolean hasIntrinsicSizingFunction() {
        return minTrackSizingFunction.isIntrinsic() || maxTrackSizingFunction.isIntrinsic();
    }

    /// Cache the `fit-content()` limit of the max track sizing function (if any), resolved against the
    /// container's inner size in this axis. Percentage and calc() limits resolve to infinity if that size is
    /// indefinite, as do tracks which are not `fit-content()`.
    public void resolveFitContentLimit(@Opt float axisInnerNodeSize, CalcResolver calcResolver) {
        fitContentLimit = OptF32.unwrapOr(
                maxTrackSizingFunction.fitContentLimit(axisInnerNodeSize, calcResolver),
                Float.POSITIVE_INFINITY);
    }

    /// Returns the resolved `fit-content()` limit of the track (infinity if the track is not `fit-content()`)
    public float fitContentLimit() {
        return fitContentLimit;
    }

    /// Returns the track's growth limit, capped by its `fit-content()` limit (if any)
    public float fitContentLimitedGrowthLimit() {
        return F32.min(growthLimit, fitContentLimit);
    }

    /// Returns the track's flex factor if it is a flex track, else 0.
    public float flexFactor() {
        return maxTrackSizingFunction.isFr() ? maxTrackSizingFunction.value() : 0.0f;
    }

    /// `Clone` — all fields are value types/immutables (GAP-005)
    public GridTrack copy() {
        GridTrack copy = new GridTrack(kind, minTrackSizingFunction, maxTrackSizingFunction);
        copy.isCollapsed = isCollapsed;
        copy.offset = offset;
        copy.baseSize = baseSize;
        copy.growthLimit = growthLimit;
        copy.contentAlignmentAdjustment = contentAlignmentAdjustment;
        copy.itemIncurredIncrease = itemIncurredIncrease;
        copy.baseSizePlannedIncrease = baseSizePlannedIncrease;
        copy.growthLimitPlannedIncrease = growthLimitPlannedIncrease;
        copy.infinitelyGrowable = infinitelyGrowable;
        copy.fitContentLimit = fitContentLimit;
        return copy;
    }
}
