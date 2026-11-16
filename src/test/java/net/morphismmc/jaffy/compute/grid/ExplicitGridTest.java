// JAFFY: port of upstream src/compute/grid/explicit_grid.rs `mod test`.
// `length(v)` -> TrackSizingFunction.fromLength(v) in template context
// (upstream generic helper infers the target type);
// `percent(v)`/`fr(v)`/`auto()`/`minmax(min,max)`/`repeat(count, tracks)`
// likewise; `Size{width,height}` -> new Size<>(...); Size<Option<f32>> ->
// SizeOptF32; `|_, _| 42.42` -> CalcResolver lambda.
package net.morphismmc.jaffy.compute.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.morphismmc.jaffy.compute.grid.ExplicitGrid.AutoRepeatStrategy;
import net.morphismmc.jaffy.compute.grid.ExplicitGrid.ExplicitGridSize;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrackKind;
import net.morphismmc.jaffy.compute.grid.util.TestHelpers;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.RepetitionCount;
import net.morphismmc.jaffy.style.grid.TrackSizingFunction;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.F32;
import org.junit.jupiter.api.Test;

class ExplicitGridTest {

    private static final CalcResolver CALC_42 = (calc, context) -> 42.42f;

    /// `(GenericGridTemplateComponent x4)::repeat(...)` helper sugar
    private static GenericGridTemplateComponent<GridTemplateRepetition> repeat(
            RepetitionCount count, List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> tracks) {
        return GridTemplateComponent.repeat(new GridTemplateRepetition(count, tracks, List.of()));
    }

    private static GenericGridTemplateComponent<GridTemplateRepetition> length(double v) {
        return GridTemplateComponent.single(TrackSizingFunction.fromLength(v));
    }

    private static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> trackLength(double v) {
        return TrackSizingFunction.fromLength(v);
    }

    private static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> trackPercent(double v) {
        return TrackSizingFunction.fromPercent(v);
    }

    private static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> trackFr(double v) {
        return TrackSizingFunction.fromFr(v);
    }

    private static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> minmax(
            MinTrackSizingFunction min, MaxTrackSizingFunction max) {
        return new MinMax<>(min, max);
    }

    private static Size<Dimension> size(double w, double h) {
        return new Size<>(Dimension.fromLength(w), Dimension.fromLength(h));
    }

    private static Size<LengthPercentageAuto> minSize(double w, double h) {
        return new Size<>(LengthPercentageAuto.fromLength(w), LengthPercentageAuto.fromLength(h));
    }

    private static Size<LengthPercentage> gap(double v) {
        return new Size<>(LengthPercentage.fromLength(v), LengthPercentage.fromLength(v));
    }

    private static ExplicitGridSize cols(
            Style style, @net.morphismmc.just.math.Opt float containerSize, AutoRepeatStrategy strategy) {
        return ExplicitGrid.computeExplicitGridSizeInAxis(
                style, containerSize, strategy, CALC_42, AbsoluteAxis.HORIZONTAL);
    }

    private static ExplicitGridSize rows(
            Style style, @net.morphismmc.just.math.Opt float containerSize, AutoRepeatStrategy strategy) {
        return ExplicitGrid.computeExplicitGridSizeInAxis(
                style, containerSize, strategy, CALC_42, AbsoluteAxis.VERTICAL);
    }

    private static SizeOptF32 preferredSize(Style style) {
        return style.size().mapO(Dimension::intoOption);
    }

    @Test
    void explicitGridSizingNoRepeats() {
        Style gridStyle = TestHelpers.intoGrid(600f, 600f, 2, 4);
        SizeOptF32 preferredSize = preferredSize(gridStyle);
        ExplicitGridSize col = cols(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        assertEquals(2, col.trackCount());
        assertEquals(4, row.trackCount());
        assertEquals(0, col.autoRepetitionCount());
        assertEquals(0, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingAutoFillExactFit() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withSize(size(120, 80))
                .withGridTemplateColumns(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(40)))))
                .withGridTemplateRows(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))));
        SizeOptF32 preferredSize = preferredSize(gridStyle);
        ExplicitGridSize col = cols(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        assertEquals(3, col.trackCount());
        assertEquals(4, row.trackCount());
        assertEquals(3, col.autoRepetitionCount());
        assertEquals(4, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingAutoFillNonExactFit() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withSize(size(140, 90))
                .withGridTemplateColumns(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(40)))))
                .withGridTemplateRows(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))));
        SizeOptF32 preferredSize = preferredSize(gridStyle);
        ExplicitGridSize col = cols(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        assertEquals(3, col.trackCount());
        assertEquals(4, row.trackCount());
        assertEquals(3, col.autoRepetitionCount());
        assertEquals(4, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingAutoFillMinSizeExactFit() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withMinSize(minSize(120, 80))
                .withGridTemplateColumns(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(40)))))
                .withGridTemplateRows(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))));
        SizeOptF32 innerContainerSize = new SizeOptF32(120f, 80f);
        ExplicitGridSize col = cols(
                gridStyle, innerContainerSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, innerContainerSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW);
        assertEquals(3, col.trackCount());
        assertEquals(4, row.trackCount());
        assertEquals(3, col.autoRepetitionCount());
        assertEquals(4, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingAutoFillMinSizeNonExactFit() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withMinSize(minSize(140, 90))
                .withGridTemplateColumns(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(40)))))
                .withGridTemplateRows(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))));
        SizeOptF32 innerContainerSize = new SizeOptF32(140f, 90f);
        ExplicitGridSize col = cols(
                gridStyle, innerContainerSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, innerContainerSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW);
        assertEquals(4, col.trackCount());
        assertEquals(5, row.trackCount());
        assertEquals(4, col.autoRepetitionCount());
        assertEquals(5, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingAutoFillMultipleRepeatedTracks() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withSize(size(140, 100))
                .withGridTemplateColumns(List.of(
                        repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(40), trackLength(20)))))
                .withGridTemplateRows(List.of(
                        repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20), trackLength(10)))));
        SizeOptF32 preferredSize = preferredSize(gridStyle);
        ExplicitGridSize col = cols(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        // 2 repetitions * 2 repeated tracks = 4 tracks in total
        assertEquals(4, col.trackCount());
        // 3 repetitions * 2 repeated tracks = 6 tracks in total
        assertEquals(6, row.trackCount());
        assertEquals(2, col.autoRepetitionCount());
        assertEquals(3, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingAutoFillGap() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withSize(size(140, 100))
                .withGridTemplateColumns(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(40)))))
                .withGridTemplateRows(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))))
                .withGap(gap(20));
        SizeOptF32 preferredSize = preferredSize(gridStyle);
        ExplicitGridSize col = cols(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        assertEquals(2, col.trackCount()); // 2 tracks + 1 gap
        assertEquals(3, row.trackCount()); // 3 tracks + 2 gaps
        assertEquals(2, col.autoRepetitionCount());
        assertEquals(3, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingNoDefinedSize() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withGridTemplateColumns(List.of(repeat(RepetitionCount.AUTO_FILL,
                        List.of(trackLength(40), trackPercent(0.5), trackLength(20)))))
                .withGridTemplateRows(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))))
                .withGap(gap(20));
        SizeOptF32 preferredSize = preferredSize(gridStyle);
        ExplicitGridSize col = cols(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MIN_REPETITIONS_THAT_DO_OVERFLOW);
        assertEquals(3, col.trackCount());
        assertEquals(1, row.trackCount());
        assertEquals(1, col.autoRepetitionCount());
        assertEquals(1, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingMixRepeatedAndNonRepeated() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withSize(size(140, 100))
                .withGridTemplateColumns(List.of(
                        length(20), repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(40)))))
                .withGridTemplateRows(List.of(
                        length(40), repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))))
                .withGap(gap(20));
        SizeOptF32 preferredSize = preferredSize(gridStyle);
        ExplicitGridSize col = cols(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, preferredSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        assertEquals(3, col.trackCount()); // 3 tracks + 2 gaps
        assertEquals(2, row.trackCount()); // 2 tracks + 1 gap
        assertEquals(2, col.autoRepetitionCount());
        assertEquals(1, row.autoRepetitionCount());
    }

    @Test
    void explicitGridSizingMixWithPadding() {
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withSize(size(120, 120))
                .withPadding(new Rect<>(
                        LengthPercentage.fromLength(10), LengthPercentage.fromLength(10),
                        LengthPercentage.fromLength(20), LengthPercentage.fromLength(20)))
                .withGridTemplateColumns(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))))
                .withGridTemplateRows(
                        List.of(repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(20)))));
        SizeOptF32 innerContainerSize = new SizeOptF32(100f, 80f);
        ExplicitGridSize col = cols(
                gridStyle, innerContainerSize.getAbs(AbsoluteAxis.HORIZONTAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        ExplicitGridSize row = rows(
                gridStyle, innerContainerSize.getAbs(AbsoluteAxis.VERTICAL),
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW);
        assertEquals(5, col.trackCount()); // 40px horizontal padding
        assertEquals(4, row.trackCount()); // 20px vertical padding
        assertEquals(5, col.autoRepetitionCount());
        assertEquals(4, row.autoRepetitionCount());
    }

    @Test
    void autoRepeatClampingPreservesTemplateSourceOrder() {
        Style autoRepeatFirst = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withGridTemplateColumns(List.of(
                        repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(2))),
                        repeat(new RepetitionCount.Count(10_000), List.of(trackLength(37)))));
        ExplicitGridSize first = ExplicitGrid.computeExplicitGridSizeInAxis(
                autoRepeatFirst, net.morphismmc.just.math.OptF32.NONE,
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW, CALC_42, AbsoluteAxis.HORIZONTAL);
        assertEquals(new ExplicitGridSize(1, 10_000), first);
        List<GridTrack> tracks = new ArrayList<>();
        ExplicitGrid.initializeGridTracks(
                tracks, TrackCounts.fromRaw(0, first.trackCount(), 0), autoRepeatFirst,
                AbsoluteAxis.HORIZONTAL, first.autoRepetitionCount(), idx -> false);
        assertEquals(MinTrackSizingFunction.fromLength(2.0), tracks.get(1).minTrackSizingFunction);
        assertEquals(MinTrackSizingFunction.fromLength(37.0), tracks.get(3).minTrackSizingFunction);

        Style autoRepeatLast = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withGridTemplateColumns(List.of(
                        repeat(new RepetitionCount.Count(10_000), List.of(trackLength(37))),
                        repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(2)))));
        ExplicitGridSize last = ExplicitGrid.computeExplicitGridSizeInAxis(
                autoRepeatLast, net.morphismmc.just.math.OptF32.NONE,
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW, CALC_42, AbsoluteAxis.HORIZONTAL);
        assertEquals(new ExplicitGridSize(0, 10_000), last);
    }

    /// `repeat(auto-fit, minmax(min(100%, 300px), 1fr))`: a `calc()` min track sizing function is a
    /// fixed sizing function, so the template is valid and the repetition count uses its resolved
    /// value (here two 300px tracks and a 8px gap fit in 800px).
    /// (upstream `#[cfg(feature = "calc")]` — calc is unconditional in JAFFY)
    @Test
    void explicitGridSizingAutoFitCalcMinTrack() {
        Object calcPtr = new Object();
        CalcResolver resolveCalc = (ptr, basis) -> {
            assertEquals(calcPtr, ptr);
            return F32.min(basis, 300f);
        };

        for (RepetitionCount count : List.of(RepetitionCount.AUTO_FIT, RepetitionCount.AUTO_FILL)) {
            Style gridStyle = Style.DEFAULT
                    .withDisplay(Display.GRID)
                    .withSize(new Size<>(Dimension.fromLength(800), Dimension.AUTO))
                    .withGap(new Size<>(LengthPercentage.fromLength(8), LengthPercentage.ZERO))
                    .withGridTemplateColumns(List.of(repeat(count, List.of(
                            minmax(MinTrackSizingFunction.calc(calcPtr), MaxTrackSizingFunction.fromFr(1.0))))));
            // The calc() resolves to min(800, 300) = 300px -> two tracks + one gap fit
            assertEquals(
                    new ExplicitGridSize(2, 2),
                    ExplicitGrid.computeExplicitGridSizeInAxis(
                            gridStyle, 800f, AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW,
                            resolveCalc, AbsoluteAxis.HORIZONTAL),
                    count.toString());

            // The calc() resolves against the container: in a 250px container it is 250px, one track
            assertEquals(
                    new ExplicitGridSize(1, 1),
                    ExplicitGrid.computeExplicitGridSizeInAxis(
                            gridStyle, 250f, AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW,
                            resolveCalc, AbsoluteAxis.HORIZONTAL),
                    count.toString());
        }
    }

    @Test
    void explicitTrackCountAccumulationSaturates() {
        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> repeatedTracks =
                Collections.nCopies(32_769, trackLength(1));
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withGridTemplateColumns(List.of(
                        repeat(new RepetitionCount.Count(65_535), repeatedTracks),
                        repeat(new RepetitionCount.Count(65_535), repeatedTracks),
                        repeat(RepetitionCount.AUTO_FILL, List.of(trackLength(1)))));
        ExplicitGridSize result = ExplicitGrid.computeExplicitGridSizeInAxis(
                gridStyle, net.morphismmc.just.math.OptF32.NONE,
                AutoRepeatStrategy.MAX_REPETITIONS_THAT_DO_NOT_OVERFLOW, CALC_42, AbsoluteAxis.HORIZONTAL);
        assertEquals(new ExplicitGridSize(0, 10_000), result);
    }

    /// `(GridTrackKind, MinTrackSizingFunction, MaxTrackSizingFunction)` expectation triple
    private record ExpectedTrack(
            GridTrackKind kind, MinTrackSizingFunction min, MaxTrackSizingFunction max) {}

    @Test
    void initializeGridTracks() {
        MinTrackSizingFunction minpx0 = MinTrackSizingFunction.fromLength(0.0);
        MinTrackSizingFunction minpx20 = MinTrackSizingFunction.fromLength(20.0);
        MinTrackSizingFunction minpx100 = MinTrackSizingFunction.fromLength(100.0);
        MinTrackSizingFunction minAuto = MinTrackSizingFunction.AUTO;

        MaxTrackSizingFunction maxpx0 = MaxTrackSizingFunction.fromLength(0.0);
        MaxTrackSizingFunction maxpx20 = MaxTrackSizingFunction.fromLength(20.0);
        MaxTrackSizingFunction maxpx100 = MaxTrackSizingFunction.fromLength(100.0);
        MaxTrackSizingFunction maxAuto = MaxTrackSizingFunction.AUTO;

        // Setup test
        Style gridStyle = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withGap(gap(20))
                .withGridTemplateColumns(List.of(
                        length(100),
                        GridTemplateComponent.single(minmax(
                                MinTrackSizingFunction.fromLength(100), MaxTrackSizingFunction.fromFr(2.0))),
                        GridTemplateComponent.single(trackFr(1))))
                .withGridAutoColumns(List.of(TrackSizingFunction.AUTO, trackLength(100)));
        TrackCounts trackCounts = new TrackCounts(3, 3, 3);

        // Call function
        List<GridTrack> tracks = new ArrayList<>();
        ExplicitGrid.initializeGridTracks(
                tracks, trackCounts, gridStyle, AbsoluteAxis.HORIZONTAL, 0, idx -> false);

        // Assertions
        List<ExpectedTrack> expected = List.of(
                // Gutter
                new ExpectedTrack(GridTrackKind.GUTTER, minpx0, maxpx0),
                // Negative implicit tracks
                new ExpectedTrack(GridTrackKind.TRACK, minpx100, maxpx100),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                new ExpectedTrack(GridTrackKind.TRACK, minAuto, maxAuto),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                new ExpectedTrack(GridTrackKind.TRACK, minpx100, maxpx100),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                // Explicit tracks
                new ExpectedTrack(GridTrackKind.TRACK, minpx100, maxpx100),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                // Note: separate min-max functions
                new ExpectedTrack(GridTrackKind.TRACK, minpx100, MaxTrackSizingFunction.fromFr(2.0)),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                // Note: min sizing function of flex sizing functions is AUTO
                new ExpectedTrack(GridTrackKind.TRACK, minAuto, MaxTrackSizingFunction.fromFr(1.0)),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                // Positive implicit tracks
                new ExpectedTrack(GridTrackKind.TRACK, minAuto, maxAuto),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                new ExpectedTrack(GridTrackKind.TRACK, minpx100, maxpx100),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx20, maxpx20),
                new ExpectedTrack(GridTrackKind.TRACK, minAuto, maxAuto),
                new ExpectedTrack(GridTrackKind.GUTTER, minpx0, maxpx0));

        assertEquals(expected.size(), tracks.size(), "Number of tracks doesn't match");

        for (int idx = 0; idx < tracks.size(); idx++) {
            GridTrack actual = tracks.get(idx);
            ExpectedTrack exp = expected.get(idx);
            String msg = "Track " + idx + " (0-based index)";
            assertEquals(exp.kind(), actual.kind, msg);
            assertEquals(exp.min(), actual.minTrackSizingFunction, msg);
            assertEquals(exp.max(), actual.maxTrackSizingFunction, msg);
        }
    }
}
