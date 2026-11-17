// JAFFY-UPSTREAM: src/compute/grid/explicit_grid.rs
//
// The file's two free functions + `pub(crate) enum AutoRepeatStrategy` map
// to static methods + a nested enum (single Java top-level per manifest
// target `compute.grid.ExplicitGrid`, cf. ImplicitGrid.MinMaxSpan).
// `impl GridContainerStyle` -> GridContainerStyle; `Option<f32>` -> @Opt;
// `impl Fn(*const (), f32) -> f32` -> CalcResolver (GAP-019);
// `impl Fn(usize) -> bool` -> java.util.function.IntPredicate;
// `impl Iterator<Item = TrackSizingFunction>` -> Supplier<MinMax>;
// `TrackSizingFunction` concretised to MinMax<Min,Max> (SPECIALIZATIONS);
// (u16,u16) return -> ExplicitGridSize record; `as u32` casts saturate via
// u32 helpers below (u32 <-> long, never negative in these paths);
// iterator clone()/cycle()/skip()/take() -> index arithmetic on List.
package net.morphismmc.jaffy.compute.grid;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.Coordinates;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrackKind;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridContainerStyle;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.RepetitionCount;
import net.morphismmc.jaffy.style.grid.TrackSizingFunction;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.jaffy.util.Sys;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

/// Helper functions for initialising GridTracks from styles
/// This mainly consists of evaluating GridAutoTracks
public final class ExplicitGrid {
    private ExplicitGrid() {}

    /// The auto-repeat fit strategy to use
    public enum AutoRepeatStrategy {
        /// If the grid container has a definite size or max size in the relevant axis:
        ///   - then the number of repetitions is the largest possible positive integer that does not cause the grid to
        ///     overflow the content box of its grid container.
        MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW,
        /// Otherwise, if the grid container has a definite min size in the relevant axis:
        ///   - then the number of repetitions is the smallest possible positive integer that fulfills that minimum
        ///     requirement
        MIN_REPETITIONS_THAT_DO_OVERFLOW,
    }

    /// `(u16, u16)` return of `compute_explicit_grid_size_in_axis`:
    /// (auto_repetition_count, explicit track count)
    public record ExplicitGridSize(int autoRepetitionCount, int trackCount) {}

    /// `f32 as u32` cast: NaN/negative -> 0, overflow -> u32::MAX
    private static long f32ToU32(float value) {
        long bits = (long) value;
        if (Float.isNaN(value) || bits < 0) {
            return 0;
        }
        return Long.min(bits, 0xFFFF_FFFFL);
    }

    /// `u32::saturating_add` (args non-negative in all call sites)
    private static long saturatingAdd(long a, long b) {
        return Long.min(a + b, 0xFFFF_FFFFL);
    }

    /// `u32::clamp` on a long value
    private static long clampU32(long value, long min, long max) {
        return Long.max(min, Long.min(value, max));
    }

    /// Compute the number of rows and columns in the explicit grid
    public static ExplicitGridSize computeExplicitGridSizeInAxis(
            GridContainerStyle style,
            @Opt float autoFitContainerSize,
            AutoRepeatStrategy autoFitStrategy,
            CalcResolver resolveCalcValue,
            AbsoluteAxis axis) {
        List<GenericGridTemplateComponent<GridTemplateRepetition>> template = switch (axis) {
            case HORIZONTAL -> style.gridTemplateColumns();
            case VERTICAL -> style.gridTemplateRows();
        };
        if (template == null) {
            return new ExplicitGridSize(0, 0);
        }

        // If template contains no tracks, then there are trivially zero explicit tracks
        if (template.isEmpty()) {
            return new ExplicitGridSize(0, 0);
        }

        // If there are any repetitions that contains no tracks, then the whole definition should be considered invalid
        // and we default to no explicit tracks
        for (GenericGridTemplateComponent<GridTemplateRepetition> trackDef : template) {
            if (trackDef instanceof GenericGridTemplateComponent.Repeat<GridTemplateRepetition> repeat
                    && repeat.repetition().trackCount() == 0) {
                return new ExplicitGridSize(0, 0);
            }
        }

        // Compute that number of track generated by single track definition and repetitions with a fixed repetition
        // count (clamping to the maximum track limit to prevent arithmetic overflow)
        long nonAutoRepeatingTrackCountLong = 0;
        for (GenericGridTemplateComponent<GridTemplateRepetition> trackDef : template) {
            long additionalTracks;
            if (trackDef instanceof GenericGridTemplateComponent.Single<GridTemplateRepetition>) {
                additionalTracks = 1;
            } else {
                GridTemplateRepetition repeat =
                        ((GenericGridTemplateComponent.Repeat<GridTemplateRepetition>) trackDef).repetition();
                if (repeat.count() instanceof RepetitionCount.Count c) {
                    additionalTracks = (long) c.count() * repeat.trackCount();
                } else {
                    additionalTracks = 0;
                }
            }
            nonAutoRepeatingTrackCountLong = saturatingAdd(nonAutoRepeatingTrackCountLong, additionalTracks);
        }
        int nonAutoRepeatingTrackCount = (int) Long.min(nonAutoRepeatingTrackCountLong, Coordinates.MAX_GRID_TRACKS);

        int autoRepetitionCount = 0;
        boolean allTrackDefsHaveFixedComponent = true;
        for (GenericGridTemplateComponent<GridTemplateRepetition> trackDef : template) {
            if (trackDef.isAutoRepetition()) {
                autoRepetitionCount++;
            }
            if (trackDef instanceof GenericGridTemplateComponent.Single<GridTemplateRepetition> single) {
                if (!TrackSizingFunction.hasFixedComponent(single.size())) {
                    allTrackDefsHaveFixedComponent = false;
                }
            } else {
                GridTemplateRepetition repeat =
                        ((GenericGridTemplateComponent.Repeat<GridTemplateRepetition>) trackDef).repetition();
                for (MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> sizingFunction : repeat.tracks()) {
                    if (!TrackSizingFunction.hasFixedComponent(sizingFunction)) {
                        allTrackDefsHaveFixedComponent = false;
                    }
                }
            }
        }

        boolean templateIsValid =
                autoRepetitionCount == 0 || (autoRepetitionCount == 1 && allTrackDefsHaveFixedComponent);

        // If the template is invalid because it contains multiple auto-repetition definitions or it combines an
        // auto-repetition definition with non-fixed-size track sizing functions, then disregard it entirely and
        // default to zero explicit tracks
        if (!templateIsValid) {
            return new ExplicitGridSize(0, 0);
        }

        // If there are no repetitions, then the number of explicit tracks is simply equal to the lengths of the track
        // definition vector (as each item in the Vec represents one track).
        if (autoRepetitionCount == 0) {
            return new ExplicitGridSize(0, nonAutoRepeatingTrackCount);
        }

        long autoRepeatInsertionPoint = 0;
        GridTemplateRepetition repetitionDefinition = null;
        for (GenericGridTemplateComponent<GridTemplateRepetition> def : template) {
            if (def instanceof GenericGridTemplateComponent.Single<GridTemplateRepetition>) {
                autoRepeatInsertionPoint = saturatingAdd(autoRepeatInsertionPoint, 1);
            } else {
                GridTemplateRepetition repeat =
                        ((GenericGridTemplateComponent.Repeat<GridTemplateRepetition>) def).repetition();
                if (repeat.count() instanceof RepetitionCount.Count c) {
                    autoRepeatInsertionPoint =
                            saturatingAdd(autoRepeatInsertionPoint, (long) c.count() * repeat.trackCount());
                } else {
                    // AutoFit | AutoFill
                    repetitionDefinition = repeat;
                    break;
                }
            }
        }
        // `.unwrap()` — templateIsValid guarantees exactly one auto-repetition exists here (GAP-010)
        if (repetitionDefinition == null) {
            throw new IllegalStateException("called `Option::unwrap()` on a `None` value");
        }
        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> repetitionDefinitionIter =
                repetitionDefinition.tracks();
        int repetitionTrackCount = repetitionDefinition.trackCount();

        // Determine the number of repetitions
        long numRepetitions;
        if (OptF32.isNone(autoFitContainerSize)) {
            numRepetitions = 1;
        } else {
            float innerContainerSize = autoFitContainerSize;

            float nonRepeatingTrackUsedSpace = 0f;
            for (GenericGridTemplateComponent<GridTemplateRepetition> trackDef : template) {
                if (trackDef instanceof GenericGridTemplateComponent.Single<GridTemplateRepetition> single) {
                    nonRepeatingTrackUsedSpace +=
                            trackDefiniteValue(single.size(), innerContainerSize, resolveCalcValue);
                } else {
                    GridTemplateRepetition repeat =
                            ((GenericGridTemplateComponent.Repeat<GridTemplateRepetition>) trackDef).repetition();
                    if (repeat.count() instanceof RepetitionCount.Count c) {
                        float sum = 0f;
                        for (MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> sizingFunction : repeat.tracks()) {
                            sum += trackDefiniteValue(sizingFunction, innerContainerSize, resolveCalcValue);
                        }
                        nonRepeatingTrackUsedSpace += sum * c.count();
                    }
                    // AutoFit | AutoFill -> 0.0
                }
            }
            float gapSize = style.gap().getAbs(axis).resolveOrZero(innerContainerSize, resolveCalcValue);

            // Compute the amount of space that a single repetition of the repeated track list takes
            float perRepetitionTrackUsedSpace = 0f;
            for (MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> sizingFunction : repetitionDefinitionIter) {
                perRepetitionTrackUsedSpace += trackDefiniteValue(sizingFunction, innerContainerSize, resolveCalcValue);
            }

            // We special case the first repetition here because the number of gaps in the first repetition
            // depends on the number of non-repeating tracks in the template
            float firstRepetitionAndNonRepeatingTracksUsedSpace = nonRepeatingTrackUsedSpace
                    + perRepetitionTrackUsedSpace
                    + (Integer.max(0, nonAutoRepeatingTrackCount + repetitionTrackCount - 1) * gapSize);

            // If a single repetition already overflows the container then we return 1 as the repetition count
            // (the number of repetitions is floored at 1)
            if (firstRepetitionAndNonRepeatingTracksUsedSpace > innerContainerSize) {
                numRepetitions = 1;
            } else {
                float perRepetitionGapUsedSpace = repetitionTrackCount * gapSize;
                float perRepetitionUsedSpace = perRepetitionTrackUsedSpace + perRepetitionGapUsedSpace;
                float numRepetitionThatFit =
                        (innerContainerSize - firstRepetitionAndNonRepeatingTracksUsedSpace) / perRepetitionUsedSpace;

                // If the container size is a preferred or maximum size:
                //   Then we return the maximum number of repetitions that fit into the container without overflowing.
                // If the container size is a minimum size:
                //   - Then we return the minimum number of repetitions required to overflow the size.
                //
                // In all cases we add the additional repetition that was already accounted for in the special-case
                // computation above.
                // Note: as-casts from f32 to u32 saturate, so a huge or infinite number of repetitions
                // (e.g. due to zero-sized tracks) resolves to u32::MAX and is clamped below
                numRepetitions = switch (autoFitStrategy) {
                    case MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW ->
                        saturatingAdd(f32ToU32(Sys.floor(numRepetitionThatFit)), 1);
                    case MIN_REPETITIONS_THAT_DO_OVERFLOW -> saturatingAdd(f32ToU32(Sys.ceil(numRepetitionThatFit)), 1);
                };
            }
        }

        // Clamp the number of repetitions based on the auto-repeat's insertion point so that tracks
        // before it are preserved and tracks after it are truncated in source order.
        // https://www.w3.org/TR/css-grid-1/#overlarge-grids
        long remainingTracks = Long.max(0, Coordinates.MAX_GRID_TRACKS - autoRepeatInsertionPoint);
        int numRepetitionsClamped;
        if (remainingTracks == 0) {
            numRepetitionsClamped = 0;
        } else {
            long maxRepetitions = (remainingTracks + repetitionTrackCount - 1) / repetitionTrackCount;
            numRepetitionsClamped = (int) clampU32(numRepetitions, 1, maxRepetitions);
        }

        int gridTemplateTrackCount = (int) Long.min(
                nonAutoRepeatingTrackCount + (long) repetitionTrackCount * numRepetitionsClamped,
                Coordinates.MAX_GRID_TRACKS);
        return new ExplicitGridSize(numRepetitionsClamped, gridTemplateTrackCount);
    }

    /// ...treating each track as its max track sizing function if that is definite or as its minimum track sizing
    /// function otherwise, flooring the max track sizing function by the min track sizing function if both are definite
    /// (upstream: private fn inside `compute_explicit_grid_size_in_axis`)
    private static float trackDefiniteValue(
            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> sizingFunction,
            @Opt float parentSize,
            CalcResolver calcResolver) {
        float maxSize = sizingFunction.max().definiteValue(parentSize, calcResolver);
        float minSize = sizingFunction.min().definiteValue(parentSize, calcResolver);
        return OptF32.unwrap(OptF32.or(OptF32.map(maxSize, max -> F32.maybeMaxO(max, minSize)), minSize));
    }

    /// Resolve the track sizing functions of explicit tracks, automatically created tracks, and gutters
    /// given a set of track counts and all of the relevant styles
    /// `track_has_items`: `impl Fn(usize) -> bool`
    public static void initializeGridTracks(
            List<GridTrack> tracks,
            TrackCounts counts,
            GridContainerStyle style,
            AbsoluteAxis axis,
            int autoRepetitionCount,
            IntPredicate trackHasItems) {
        // Extract styles
        List<GenericGridTemplateComponent<GridTemplateRepetition>> trackTemplate;
        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> autoTracks;
        LengthPercentage gap;
        switch (axis) {
            case HORIZONTAL -> {
                trackTemplate = style.gridTemplateColumns();
                autoTracks = style.gridAutoColumns();
                gap = style.gap().width();
            }
            case VERTICAL -> {
                trackTemplate = style.gridTemplateRows();
                autoTracks = style.gridAutoRows();
                gap = style.gap().height();
            }
            default -> throw new IllegalStateException("unreachable");
        }

        // Clear vector (in case this is a re-layout), reserve space for all tracks ahead of time to reduce
        // allocations, and push the initial gutter
        tracks.clear();
        if (tracks instanceof java.util.ArrayList<GridTrack> list) {
            list.ensureCapacity((counts.len() * 2) + 1);
        }
        tracks.add(GridTrack.gutter(gap));

        int autoTrackCount = autoTracks.size();

        // Create negative implicit tracks
        if (counts.negativeImplicit() > 0) {
            if (autoTrackCount == 0) {
                createImplicitTracks(tracks, counts.negativeImplicit(), () -> TrackSizingFunction.AUTO, gap);
            } else {
                int offset = autoTrackCount - (counts.negativeImplicit() % autoTrackCount);
                // `auto_tracks.clone().cycle().skip(offset)` -> cyclic index iteration
                int[] idx = {offset};
                createImplicitTracks(
                        tracks, counts.negativeImplicit(), () -> autoTracks.get(idx[0]++ % autoTrackCount), gap);
            }
        }

        int currentTrackIndex = counts.negativeImplicit();

        // The maximum index up to which explicit tracks may be created. The explicit track count is clamped
        // to MAX_GRID_TRACKS, so track creation from the template must be truncated to match.
        int explicitTrackLimit = counts.negativeImplicit() + counts.explicit();

        // Create explicit tracks
        // An explicit check against the count (rather than just relying on track_template being empty) is required
        // here because a count of zero can result from the track_template being invalid, in which case it should be
        // ignored.
        if (counts.explicit() > 0 && trackTemplate != null) {
            for (GenericGridTemplateComponent<GridTemplateRepetition> trackSizingFunction : trackTemplate) {
                if (trackSizingFunction instanceof GenericGridTemplateComponent.Single<GridTemplateRepetition> single) {
                    if (currentTrackIndex < explicitTrackLimit) {
                        tracks.add(GridTrack.newGridTrack(
                                TrackSizingFunction.minSizingFunction(single.size()),
                                TrackSizingFunction.maxSizingFunction(single.size())));
                        tracks.add(GridTrack.gutter(gap));
                        currentTrackIndex += 1;
                    }
                } else {
                    GridTemplateRepetition repeat = ((GenericGridTemplateComponent.Repeat<GridTemplateRepetition>)
                                    trackSizingFunction)
                            .repetition();
                    if (repeat.count() instanceof RepetitionCount.Count c) {
                        // `repeat.tracks().cycle().take(repeated_track_count)` -> cyclic index iteration
                        int repeatedTrackCount =
                                Integer.min(repeat.trackCount() * c.count(), explicitTrackLimit - currentTrackIndex);
                        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> repeatTracks = repeat.tracks();
                        for (int i = 0; i < repeatedTrackCount; i++) {
                            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> sizingFunction =
                                    repeatTracks.get(i % repeatTracks.size());
                            tracks.add(GridTrack.newGridTrack(
                                    TrackSizingFunction.minSizingFunction(sizingFunction),
                                    TrackSizingFunction.maxSizingFunction(sizingFunction)));
                            tracks.add(GridTrack.gutter(gap));
                            currentTrackIndex += 1;
                        }
                    } else {
                        // AutoFit | AutoFill
                        int autoRepeatedTrackCount = Integer.min(
                                repeat.trackCount() * autoRepetitionCount, explicitTrackLimit - currentTrackIndex);
                        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> repeatTracks = repeat.tracks();
                        for (int i = 0; i < autoRepeatedTrackCount; i++) {
                            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> trackDef =
                                    repeatTracks.get(i % repeatTracks.size());
                            GridTrack track = GridTrack.newGridTrack(
                                    TrackSizingFunction.minSizingFunction(trackDef),
                                    TrackSizingFunction.maxSizingFunction(trackDef));
                            GridTrack gutter = GridTrack.gutter(gap);

                            // Auto-fit tracks that don't contain should be collapsed.
                            if (repeat.count() instanceof RepetitionCount.AutoFit
                                    && !trackHasItems.test(currentTrackIndex)) {
                                track.collapse();
                                gutter.collapse();
                            }

                            tracks.add(track);
                            tracks.add(gutter);

                            currentTrackIndex += 1;
                        }

                        // Is this auto-repeat at the very end of the track list?
                        boolean isLast = currentTrackIndex == counts.len();

                        // When collapsing auto-fit tracks (in the loop just above), we collapse the gutter after
                        // collapsed tracks but not the gutter before. This is correct so long as there is another
                        // non-collapsed track somewhere in the track list after the collapsed track.
                        //
                        // However, if the auto-fit repeat is at the very end of the track list, then there will be no
                        // such track. In this case we need to iterate backwards through the track list until we find
                        // a non-collapsed track and collapse the gutter just after that track.
                        //
                        // As all of the other tracks and gutters we find along the way will already be collapsed, we
                        // simplify the implementation to collapsing all tracks and gutters until we find the first
                        // non-collapsed track.
                        if (repeat.count() instanceof RepetitionCount.AutoFit && isLast) {
                            for (int i = tracks.size() - 1; i >= 0; i--) {
                                GridTrack previousTrack = tracks.get(i);
                                if (previousTrack.kind == GridTrackKind.TRACK && !previousTrack.isCollapsed) {
                                    break;
                                }
                                previousTrack.collapse();
                            }
                        }
                    }
                }
            }
        }

        int gridAreaTracks = (counts.negativeImplicit() + counts.explicit()) - currentTrackIndex;

        // Create positive implicit tracks
        if (autoTrackCount == 0) {
            createImplicitTracks(
                    tracks, counts.positiveImplicit() + gridAreaTracks, () -> TrackSizingFunction.AUTO, gap);
        } else {
            // `auto_tracks.clone().cycle()` -> cyclic index iteration
            int[] idx = {0};
            createImplicitTracks(
                    tracks,
                    counts.positiveImplicit() + gridAreaTracks,
                    () -> autoTracks.get(idx[0]++ % autoTrackCount),
                    gap);
        }

        // Mark first and last grid lines as collapsed
        tracks.getFirst().collapse();
        tracks.getLast().collapse();
    }

    /// Utility function for repeating logic of creating implicit tracks
    /// `auto_tracks_iter: impl Iterator<Item = TrackSizingFunction>` -> Supplier (next().unwrap() is infallible
    /// upstream: `iter::repeat`/`cycle` never end)
    private static void createImplicitTracks(
            List<GridTrack> tracks,
            int count,
            Supplier<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> autoTracksIter,
            LengthPercentage gap) {
        for (int i = 0; i < count; i++) {
            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> trackDef = autoTracksIter.get();
            tracks.add(GridTrack.newGridTrack(
                    TrackSizingFunction.minSizingFunction(trackDef), TrackSizingFunction.maxSizingFunction(trackDef)));
            tracks.add(GridTrack.gutter(gap));
        }
    }
}
