// JAFFY-UPSTREAM: src/compute/grid/types/grid_track.rs
// JAFFY-NOTE: behavior tests for upstream
// Upstream has no inline test module for this file; these cover the public
// surface (constructors, collapse, predicates, fit-content limit, copy).
package net.morphismmc.jaffy.compute.grid.types.grid_track;

import static org.junit.jupiter.api.Assertions.*;

import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class GridTrackTest {

    @Test
    void newTrackDefaults() {
        GridTrack track = GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.ZERO);
        assertEquals(GridTrackKind.TRACK, track.kind);
        assertFalse(track.isCollapsed);
        assertEquals(0.0f, track.offset);
        assertEquals(0.0f, track.baseSize);
        assertEquals(0.0f, track.growthLimit);
        assertEquals(0.0f, track.contentAlignmentAdjustment);
        assertEquals(0.0f, track.itemIncurredIncrease);
        assertEquals(0.0f, track.baseSizePlannedIncrease);
        assertEquals(0.0f, track.growthLimitPlannedIncrease);
        assertFalse(track.infinitelyGrowable);
        assertEquals(Float.POSITIVE_INFINITY, track.fitContentLimit());
    }

    @Test
    void gutterUsesSizingFunctionsFromLengthPercentage() {
        GridTrack gutter = GridTrack.gutter(LengthPercentage.length(8.0f));
        assertEquals(GridTrackKind.GUTTER, gutter.kind);
        assertFalse(gutter.minTrackSizingFunction.isAuto());
        assertEquals(8.0f, gutter.minTrackSizingFunction.value());
        assertEquals(8.0f, gutter.maxTrackSizingFunction.value());
    }

    @Test
    void collapseResetsSizingFunctionsToZero() {
        GridTrack track = GridTrack.newGridTrack(
                MinTrackSizingFunction.fromLength(10.0), MaxTrackSizingFunction.fitContentPx(50.0f));
        track.fitContentLimit = 50.0f;
        track.collapse();
        assertTrue(track.isCollapsed);
        assertSame(MinTrackSizingFunction.ZERO, track.minTrackSizingFunction);
        assertSame(MaxTrackSizingFunction.ZERO, track.maxTrackSizingFunction);
        assertEquals(Float.POSITIVE_INFINITY, track.fitContentLimit());
    }

    @Test
    void flexibilityAndFlexFactor() {
        GridTrack fixed = GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.length(20.0f));
        assertFalse(fixed.isFlexible());
        assertEquals(0.0f, fixed.flexFactor());

        GridTrack flex = GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.fr(2.5f));
        assertTrue(flex.isFlexible());
        assertEquals(2.5f, flex.flexFactor());
    }

    @Test
    void percentageDetection() {
        GridTrack fixed =
                GridTrack.newGridTrack(MinTrackSizingFunction.fromLength(10.0), MaxTrackSizingFunction.length(20.0f));
        assertFalse(fixed.usesPercentage());

        GridTrack percentMin =
                GridTrack.newGridTrack(MinTrackSizingFunction.fromPercent(0.5), MaxTrackSizingFunction.length(20.0f));
        assertTrue(percentMin.usesPercentage());

        GridTrack percentMax =
                GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.percent(0.5f));
        assertTrue(percentMax.usesPercentage());
    }

    @Test
    void intrinsicDetection() {
        GridTrack fixed =
                GridTrack.newGridTrack(MinTrackSizingFunction.fromLength(10.0), MaxTrackSizingFunction.length(20.0f));
        assertFalse(fixed.hasIntrinsicSizingFunction());

        GridTrack intrinsicMax =
                GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.maxContent());
        assertTrue(intrinsicMax.hasIntrinsicSizingFunction());
    }

    @Test
    void resolveFitContentLimitVariants() {
        // fit-content(px) resolves to its literal limit
        GridTrack px = GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.fitContentPx(42.0f));
        px.resolveFitContentLimit(100.0f, (calc, ctx) -> ctx);
        assertEquals(42.0f, px.fitContentLimit());

        // fit-content(percent) resolves against the definite parent size
        GridTrack pct =
                GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.fitContentPercent(0.5f));
        pct.resolveFitContentLimit(200.0f, (calc, ctx) -> ctx);
        assertEquals(100.0f, pct.fitContentLimit());

        // percentage limit with indefinite parent size resolves to infinity
        GridTrack indefinite =
                GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.fitContentPercent(0.5f));
        indefinite.resolveFitContentLimit(OptF32.NONE, (calc, ctx) -> ctx);
        assertEquals(Float.POSITIVE_INFINITY, indefinite.fitContentLimit());

        // non-fit-content functions resolve to infinity
        GridTrack plain = GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.length(30.0f));
        plain.resolveFitContentLimit(200.0f, (calc, ctx) -> ctx);
        assertEquals(Float.POSITIVE_INFINITY, plain.fitContentLimit());
    }

    @Test
    void fitContentLimitedGrowthLimitIsCapped() {
        GridTrack track =
                GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.fitContentPx(40.0f));
        track.growthLimit = 60.0f;
        // fit_content_limit still at default infinity -> growth limit wins
        assertEquals(60.0f, track.fitContentLimitedGrowthLimit());
        track.fitContentLimit = 40.0f;
        assertEquals(40.0f, track.fitContentLimitedGrowthLimit());
        track.fitContentLimit = 80.0f;
        assertEquals(60.0f, track.fitContentLimitedGrowthLimit());
    }

    @Test
    void copyIsIndependentFieldWiseCopy() {
        GridTrack track = GridTrack.newGridTrack(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.fr(1.0f));
        track.baseSize = 10.0f;
        track.growthLimit = 30.0f;
        track.infinitelyGrowable = true;

        GridTrack copy = track.copy();
        assertEquals(track.kind, copy.kind);
        assertEquals(track.baseSize, copy.baseSize);
        assertEquals(track.growthLimit, copy.growthLimit);
        assertTrue(copy.infinitelyGrowable);
        assertSame(track.maxTrackSizingFunction, copy.maxTrackSizingFunction);

        copy.baseSize = 99.0f;
        assertEquals(10.0f, track.baseSize);
    }
}
