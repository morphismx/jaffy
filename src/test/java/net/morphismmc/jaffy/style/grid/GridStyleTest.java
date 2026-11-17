// JAFFY: upstream=src/style/grid.rs
// JAFFY-NOTE: tests for the upstream  port
// Upstream `expand_tests` cases are ported 1:1; `#[cfg(feature = "parse")]`
// tests are deferred with the feature.
package net.morphismmc.jaffy.style.grid;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class GridStyleTest {

    // ---- expand_tests::max_track_sizing_function_round_trips ----

    @Test
    void maxTrackSizingFunctionRoundTrips() {
        List<MaxTrackSizingFunction> cases = List.of(
                MaxTrackSizingFunction.length(12.0f),
                MaxTrackSizingFunction.percent(0.5f),
                MaxTrackSizingFunction.auto(),
                MaxTrackSizingFunction.minContent(),
                MaxTrackSizingFunction.maxContent(),
                MaxTrackSizingFunction.fitContentPx(30.0f),
                MaxTrackSizingFunction.fitContentPercent(0.75f),
                MaxTrackSizingFunction.fr(2.0f));
        for (MaxTrackSizingFunction value : cases) {
            assertTrue(value.rustEq(MaxTrackSizingFunction.from(value.expand())));
        }
        assertEquals(
                new ExpandedMaxTrackSizingFunction.Fr(2.0f),
                MaxTrackSizingFunction.fr(2.0f).expand());
        assertEquals(
                new ExpandedMaxTrackSizingFunction.FitContentPx(30.0f),
                MaxTrackSizingFunction.fitContentPx(30.0f).expand());
    }

    // ---- expand_tests::min_track_sizing_function_round_trips ----

    @Test
    void minTrackSizingFunctionRoundTrips() {
        List<MinTrackSizingFunction> cases = List.of(
                MinTrackSizingFunction.length(12.0f),
                MinTrackSizingFunction.percent(0.5f),
                MinTrackSizingFunction.auto(),
                MinTrackSizingFunction.minContent(),
                MinTrackSizingFunction.maxContent());
        for (MinTrackSizingFunction value : cases) {
            assertTrue(value.rustEq(MinTrackSizingFunction.from(value.expand())));
        }
        assertEquals(
                new ExpandedMinTrackSizingFunction.MaxContent(),
                MinTrackSizingFunction.maxContent().expand());
    }

    // ---- expand_tests::track_sizing_function_calc_round_trips (calc feature on) ----

    @Test
    void trackSizingFunctionCalcRoundTrips() {
        Object handle = new Object();

        assertEquals(
                new ExpandedMaxTrackSizingFunction.Calc(handle),
                MaxTrackSizingFunction.calc(handle).expand());
        assertEquals(
                new ExpandedMaxTrackSizingFunction.FitContentCalc(handle),
                MaxTrackSizingFunction.fitContentCalc(handle).expand());
        assertTrue(MaxTrackSizingFunction.fitContentCalc(handle)
                .rustEq(MaxTrackSizingFunction.from(new ExpandedMaxTrackSizingFunction.FitContentCalc(handle))));
        assertTrue(MaxTrackSizingFunction.fitContentCalc(handle)
                .rustEq(MaxTrackSizingFunction.fitContent(LengthPercentage.calc(handle))));
        assertTrue(MinTrackSizingFunction.AUTO.rustEq(
                MinTrackSizingFunction.from(MaxTrackSizingFunction.fitContentCalc(handle))));
        assertTrue(MinTrackSizingFunction.AUTO.rustEq(MinTrackSizingFunction.from(Dimension.fitContentCalc(handle))));
        assertTrue(MaxTrackSizingFunction.calc(handle)
                .rustEq(MaxTrackSizingFunction.from(new ExpandedMaxTrackSizingFunction.Calc(handle))));
        assertEquals(
                new ExpandedMinTrackSizingFunction.Calc(handle),
                MinTrackSizingFunction.calc(handle).expand());
    }

    // ---- tests::repetition_track_count_saturates ----

    @Test
    void repetitionTrackCountSaturates() {
        var repetition = new GridTemplateRepetition(
                new RepetitionCount.Count(1), Collections.nCopies(65536, TrackSizingFunction.AUTO), List.of());
        assertEquals(65535, repetition.trackCount());
    }

    // ---- behavior matrix for placement helpers (upstream has no dedicated tests) ----

    @Test
    void gridPlacementFactories() {
        var line = LineGridPlacementExt.fromLineIndex(3);
        assertInstanceOf(GridPlacement.Line.class, line.start());
        assertEquals(3, ((GridPlacement.Line) line.start()).line().asI16());
        assertInstanceOf(GridPlacement.Auto.class, line.end());

        var span = LineGridPlacementExt.fromSpan(4);
        assertInstanceOf(GridPlacement.Span.class, span.start());
        assertEquals(4, ((GridPlacement.Span) span.start()).span());

        assertInstanceOf(GridPlacement.Auto.class, GridPlacement.AUTO);
    }

    @Test
    void lineIsDefinite() {
        assertTrue(LineGridPlacementExt.isDefinite(LineGridPlacementExt.fromLineIndex(2)));
        assertTrue(LineGridPlacementExt.isDefinite(
                new Line<>(GridPlacement.AUTO, new GridPlacement.NamedLine("name", 1))));
        // Line 0 is invalid and falls back to auto -> indefinite
        assertFalse(LineGridPlacementExt.isDefinite(LineGridPlacementExt.fromLineIndex(0)));
        assertFalse(LineGridPlacementExt.isDefinite(LineGridPlacementExt.DEFAULT));
    }

    @Test
    void indefiniteSpan() {
        assertEquals(
                1,
                LineGenericGridPlacementExt.indefiniteSpan(new Line<>(
                        new GenericGridPlacement.Auto<GridLine>(), new GenericGridPlacement.Auto<GridLine>())));
        var spanned = LineNonNamedGridPlacementExt.intoOriginZero(
                new Line<>(new GenericGridPlacement.Auto<GridLine>(), new GenericGridPlacement.Span<GridLine>(3)), 4);
        assertEquals(3, LineGenericGridPlacementExt.indefiniteSpan(spanned));
        // (Line, Line) is definite -> panic
        var definite = LineNonNamedGridPlacementExt.intoOriginZero(
                new Line<>(
                        new GenericGridPlacement.Line<>(GridLine.from(1)),
                        new GenericGridPlacement.Line<>(GridLine.from(3))),
                4);
        assertThrows(IllegalStateException.class, () -> LineGenericGridPlacementExt.indefiniteSpan(definite));
    }

    @Test
    void resolveDefiniteGridLines() {
        // (Line 2, Line 1) sorts to (1, 2); equal lines bump the end
        var resolved = LineOriginZeroGridPlacementExt.resolveDefiniteGridLines(new Line<>(
                new GenericGridPlacement.Line<>(new OriginZeroLine(2)),
                new GenericGridPlacement.Line<>(new OriginZeroLine(1))));
        assertEquals(1, resolved.start().value());
        assertEquals(2, resolved.end().value());

        var equal = LineOriginZeroGridPlacementExt.resolveDefiniteGridLines(new Line<>(
                new GenericGridPlacement.Line<>(new OriginZeroLine(3)),
                new GenericGridPlacement.Line<>(new OriginZeroLine(3))));
        assertEquals(3, equal.start().value());
        assertEquals(4, equal.end().value());
    }

    @Test
    void sizingFunctionResolution() {
        var tsf = new MinMax<>(MinTrackSizingFunction.length(10.0f), MaxTrackSizingFunction.fr(1.0f));
        assertEquals(10.0f, tsf.min().definiteValue(OptF32.NONE, null));
        assertEquals(OptF32.NONE, tsf.max().definiteValue(OptF32.NONE, null));
        assertTrue(TrackSizingFunction.hasFixedComponent(tsf));
        assertFalse(TrackSizingFunction.hasFixedComponent(TrackSizingFunction.AUTO));

        assertTrue(MinTrackSizingFunction.percent(0.5f).behavesAsAuto(OptF32.NONE));
        assertFalse(MinTrackSizingFunction.percent(0.5f).behavesAsAuto(100.0f));
        assertEquals(50.0f, MinTrackSizingFunction.percent(0.5f).resolvedPercentageSize(100.0f, null));
        assertEquals(OptF32.NONE, MinTrackSizingFunction.length(5.0f).resolvedPercentageSize(100.0f, null));
    }

    @Test
    void gridTemplateComponentPredicates() {
        assertFalse(GridTemplateComponent.AUTO.isAutoRepetition());
        var rep = new GridTemplateRepetition(RepetitionCount.AUTO_FILL, List.of(TrackSizingFunction.AUTO), List.of());
        var repComponent = new GenericGridTemplateComponent.Repeat<>(rep);
        assertTrue(repComponent.isAutoRepetition());
        assertTrue(GridTemplateComponent.asComponentRef(repComponent).isAutoRepetition());

        var repCount = new GenericGridTemplateComponent.Repeat<>(
                new GridTemplateRepetition(new RepetitionCount.Count(3), List.of(), List.of()));
        assertFalse(repCount.isAutoRepetition());

        assertTrue(RepetitionCount.tryFrom("auto-fill").unwrap() instanceof RepetitionCount.AutoFill);
        assertTrue(RepetitionCount.tryFrom("auto-fit").unwrap() instanceof RepetitionCount.AutoFit);
        assertTrue(RepetitionCount.tryFrom("3").isErr());
    }
}
