// JAFFY: upstream=src/compute/grid/implicit_grid.rs
// JAFFY-NOTE: `mod tests`
// (test_child_min_max_line + test_initial_grid_sizing).
// `line(i)` -> GridPlacement.fromLineIndex; `span(n)` -> GridPlacement.fromSpan;
// `auto()` -> GridPlacement.AUTO.
package net.morphismmc.jaffy.compute.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import net.morphismmc.jaffy.compute.grid.ImplicitGrid.MinMaxSpan;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.named.NamedLineResolver;
import net.morphismmc.jaffy.compute.grid.util.TestHelpers;
import net.morphismmc.jaffy.geometry.InBothAbsAxis;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.LineGridPlacementExt;

import org.junit.jupiter.api.Test;

class ImplicitGridTest {

    private static InBothAbsAxis<TrackCounts> estimate(
            int explicitColCount, int explicitRowCount, List<Style> childStyles) {
        NamedLineResolver nameResolver = NamedLineResolver.newNamedLineResolver(Style.DEFAULT, 0, 0);
        nameResolver.setExplicitColumnCount(explicitColCount);
        nameResolver.setExplicitRowCount(explicitRowCount);
        List<InBothAbsAxis<Line<GenericGridPlacement<OriginZeroLine>>>> placements =
                TestHelpers.resolveNamedLines(childStyles, nameResolver, explicitColCount, explicitRowCount);
        return ImplicitGrid.computeGridSizeEstimate(explicitColCount, explicitRowCount, placements);
    }

    @Test
    void childMinMaxLineAuto() {
        MinMaxSpan result = ImplicitGrid.childMinLineMaxLineSpan(LineGridPlacementExt.intoOriginZeroIgnoringNamed(
                new Line<>(GridPlacement.fromLineIndex(5), GridPlacement.fromSpan(6)), 6));
        assertEquals(new OriginZeroLine(4), result.min());
        assertEquals(new OriginZeroLine(10), result.max());
        assertEquals(1, result.span());
    }

    @Test
    void childMinMaxLineNegativeTrack() {
        MinMaxSpan result = ImplicitGrid.childMinLineMaxLineSpan(LineGridPlacementExt.intoOriginZeroIgnoringNamed(
                new Line<>(GridPlacement.fromLineIndex(-5), GridPlacement.fromSpan(3)), 6));
        assertEquals(new OriginZeroLine(2), result.min());
        assertEquals(new OriginZeroLine(5), result.max());
        assertEquals(1, result.span());
    }

    @Test
    void explicitGridSizingWithChildren() {
        int explicitColCount = 6;
        int explicitRowCount = 8;
        List<Style> childStyles = List.of(
                TestHelpers.intoGridChild(
                        GridPlacement.fromLineIndex(1),
                        GridPlacement.fromSpan(2),
                        GridPlacement.fromLineIndex(2),
                        GridPlacement.AUTO),
                TestHelpers.intoGridChild(
                        GridPlacement.fromLineIndex(-4), GridPlacement.AUTO,
                        GridPlacement.fromLineIndex(-2), GridPlacement.AUTO));
        InBothAbsAxis<TrackCounts> result = estimate(explicitColCount, explicitRowCount, childStyles);
        assertEquals(0, result.horizontal().negativeImplicit());
        assertEquals(explicitColCount, result.horizontal().explicit());
        assertEquals(0, result.horizontal().positiveImplicit());
        assertEquals(0, result.vertical().negativeImplicit());
        assertEquals(explicitRowCount, result.vertical().explicit());
        assertEquals(0, result.vertical().positiveImplicit());
    }

    @Test
    void negativeImplicitGridSizing() {
        int explicitColCount = 4;
        int explicitRowCount = 4;
        List<Style> childStyles = List.of(
                TestHelpers.intoGridChild(
                        GridPlacement.fromLineIndex(-6),
                        GridPlacement.fromSpan(2),
                        GridPlacement.fromLineIndex(-8),
                        GridPlacement.AUTO),
                TestHelpers.intoGridChild(
                        GridPlacement.fromLineIndex(4), GridPlacement.AUTO,
                        GridPlacement.fromLineIndex(3), GridPlacement.AUTO));
        InBothAbsAxis<TrackCounts> result = estimate(explicitColCount, explicitRowCount, childStyles);
        assertEquals(1, result.horizontal().negativeImplicit());
        assertEquals(explicitColCount, result.horizontal().explicit());
        assertEquals(0, result.horizontal().positiveImplicit());
        assertEquals(3, result.vertical().negativeImplicit());
        assertEquals(explicitRowCount, result.vertical().explicit());
        assertEquals(0, result.vertical().positiveImplicit());
    }
}
