// JAFFY: upstream=src/compute/grid/types/named.rs
// JAFFY-NOTE: `mod tests`
// `DefaultCheapStr` -> `String`; `NonNamedGridPlacement` ->
// `GenericGridPlacement<GridLine>`; `panic!` in tests -> `fail()`.
package net.morphismmc.jaffy.compute.grid.types.named;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.coordinates.Coordinates;
import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateArea;
import net.morphismmc.jaffy.style.grid.GridTemplateAreas;

import org.junit.jupiter.api.Test;

class NamedLineResolverTest {

    private static NamedLineResolver resolver(int explicitTrackCount) {
        NamedLineResolver resolver = NamedLineResolver.newNamedLineResolver(Style.DEFAULT, 0, 0);
        resolver.setExplicitColumnCount(explicitTrackCount);
        return resolver;
    }

    private static int resolvedStartLine(NamedLineResolver resolver, GridPlacement placement) {
        var resolved = resolver.resolveColumnNames(new Line<>(placement, GridPlacement.AUTO));
        if (resolved.start() instanceof GenericGridPlacement.Line<GridLine> l) {
            return l.line().asI16();
        }
        return fail("expected a resolved line");
    }

    private static int resolvedEndLine(NamedLineResolver resolver, GridPlacement placement) {
        var resolved = resolver.resolveColumnNames(new Line<>(GridPlacement.AUTO, placement));
        if (resolved.end() instanceof GenericGridPlacement.Line<GridLine> l) {
            return l.line().asI16();
        }
        return fail("expected a resolved line");
    }

    private static GridPlacement namedLine(String name, int index) {
        return new GridPlacement.NamedLine(name, index);
    }

    /// A resolver for a grid with 4 explicit columns, the passed names for each of the 5 column lines, and a
    /// single grid area named "area" which spans columns 2 and 3 (lines 2 to 4)
    private static NamedLineResolver areaResolver(List<List<String>> columnLineNames) {
        Style style = Style.DEFAULT
                .withGridTemplateColumnNames(columnLineNames)
                .withGridTemplateAreas(new GridTemplateAreas(List.of(new GridTemplateArea("area", 1, 2, 2, 4)), 1, 4));
        NamedLineResolver resolver = NamedLineResolver.newNamedLineResolver(style, 0, 0);
        resolver.setExplicitColumnCount(4);
        resolver.setExplicitRowCount(1);
        return resolver;
    }

    @Test
    void extremeMissingNamedLineIndicesDoNotOverflow() {
        // Lines are clamped to the limits of the grid, in the same way as `GridLine::into_origin_zero_line`
        NamedLineResolver resolver = resolver(10_000);
        assertEquals(
                new OriginZeroLine(Coordinates.MAX_OZ_LINE).intoGridLine(10_000).asI16(),
                resolvedStartLine(resolver, namedLine("missing", Short.MAX_VALUE)));
        assertEquals(
                new OriginZeroLine(Coordinates.MIN_OZ_LINE).intoGridLine(10_000).asI16(),
                resolvedStartLine(resolver, namedLine("missing", Short.MIN_VALUE)));
    }

    @Test
    void largeNamedSpanDoesNotWrapNegative() {
        NamedLineResolver resolver = resolver(10_000);
        var resolved = resolver.resolveColumnNames(
                new Line<>(new GridPlacement.Line(GridLine.from(1)), new GridPlacement.NamedSpan("missing", 65535)));
        if (resolved.end() instanceof GenericGridPlacement.Line<GridLine> l) {
            assertEquals(new OriginZeroLine(Coordinates.MAX_OZ_LINE).intoGridLine(10_000), l.line());
        } else {
            fail("expected a resolved line");
        }
    }

    @Test
    void areaLinesSaturateWhenConvertedToGridLines() {
        Style style = Style.DEFAULT.withGridTemplateAreas(
                new GridTemplateAreas(List.of(new GridTemplateArea("area", 1, 2, 65535, 65535)), 1, 65535));
        NamedLineResolver resolver = NamedLineResolver.newNamedLineResolver(style, 0, 0);
        assertEquals(
                new OriginZeroLine(Coordinates.MAX_OZ_LINE).intoGridLine(0).asI16(),
                resolvedStartLine(resolver, namedLine("area-start", 1)));
        assertEquals(
                new OriginZeroLine(Coordinates.MAX_OZ_LINE).intoGridLine(0).asI16(),
                resolvedStartLine(resolver, namedLine("area", 0)));
    }

    @Test
    void bareIdentResolvesToAreaEdge() {
        NamedLineResolver resolver = areaResolver(List.of(List.of(), List.of(), List.of(), List.of(), List.of()));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area", 0)));
        assertEquals(4, resolvedEndLine(resolver, namedLine("area", 0)));

        var rows = resolver.resolveRowNames(new Line<>(namedLine("area", 0), namedLine("area", 0)));
        assertEquals(new GenericGridPlacement.Line<>(GridLine.from(1)), rows.start());
        assertEquals(new GenericGridPlacement.Line<>(GridLine.from(2)), rows.end());
    }

    @Test
    void bareIdentResolvesToEdgeOfImplicitlyNamedArea() {
        NamedLineResolver resolver = areaResolver(
                List.of(List.of(), List.of("foo-start"), List.of("foo-start"), List.of("foo-end"), List.of("foo-end")));
        assertEquals(2, resolvedStartLine(resolver, namedLine("foo", 0)));
        assertEquals(4, resolvedEndLine(resolver, namedLine("foo", 0)));
    }

    @Test
    void areaEdgesAreLinesNamedWithStartAndEndSuffixes() {
        NamedLineResolver resolver = areaResolver(List.of(List.of(), List.of(), List.of(), List.of(), List.of()));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area-start", 0)));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area-start", 1)));
        assertEquals(4, resolvedStartLine(resolver, namedLine("area-end", 1)));
        assertEquals(4, resolvedStartLine(resolver, namedLine("area-end", -1)));
        // There is only one such line, so the second is the first implicit line after the grid
        assertEquals(6, resolvedStartLine(resolver, namedLine("area-start", 2)));
    }

    @Test
    void areaEdgesAreMergedWithExplicitlyNamedLines() {
        // The area's start edge is line 2 and its end edge is line 4
        NamedLineResolver resolver = areaResolver(List.of(
                List.of("area-start"), List.of(), List.of("area-start"), List.of("area-end"), List.of("area-end")));
        assertEquals(1, resolvedStartLine(resolver, namedLine("area-start", 1)));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area-start", 2)));
        assertEquals(3, resolvedStartLine(resolver, namedLine("area-start", 3)));
        assertEquals(3, resolvedStartLine(resolver, namedLine("area-start", -1)));
        assertEquals(6, resolvedStartLine(resolver, namedLine("area-start", 4)));
        // The area's end edge has the same line number as an explicitly named line
        assertEquals(4, resolvedStartLine(resolver, namedLine("area-end", 1)));
        assertEquals(5, resolvedStartLine(resolver, namedLine("area-end", 2)));
        assertEquals(6, resolvedStartLine(resolver, namedLine("area-end", 3)));
        // A bare ident matches the first line of the edge
        assertEquals(1, resolvedStartLine(resolver, namedLine("area", 0)));
        assertEquals(4, resolvedEndLine(resolver, namedLine("area", 0)));

        var span = resolver.resolveColumnNames(
                new Line<>(new GridPlacement.Line(GridLine.from(1)), new GridPlacement.NamedSpan("area-start", 2)));
        assertEquals(new GenericGridPlacement.Line<>(GridLine.from(3)), span.end());
    }

    @Test
    void areaEdgesAreOnlyMatchedByBareIdents() {
        NamedLineResolver resolver = areaResolver(List.of(List.of(), List.of(), List.of(), List.of(), List.of()));
        // There are no lines named "area", so these resolve to implicit lines after the grid
        assertEquals(6, resolvedStartLine(resolver, namedLine("area", 1)));
        assertEquals(7, resolvedStartLine(resolver, namedLine("area", 2)));

        var span = resolver.resolveColumnNames(
                new Line<>(new GridPlacement.Line(GridLine.from(1)), new GridPlacement.NamedSpan("area", 0)));
        assertEquals(new GenericGridPlacement.Line<>(GridLine.from(6)), span.end());
    }

    @Test
    void areaEdgeIsTheFirstLineWithTheEdgeName() {
        // Explicit lines named after the area's edges which come after the area's generated lines (lines 2 and
        // 4) don't replace them
        NamedLineResolver resolver =
                areaResolver(List.of(List.of(), List.of(), List.of("area-start"), List.of(), List.of("area-end")));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area", 0)));
        assertEquals(4, resolvedEndLine(resolver, namedLine("area", 0)));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area-start", 1)));
        assertEquals(3, resolvedStartLine(resolver, namedLine("area-start", 2)));
        assertEquals(5, resolvedStartLine(resolver, namedLine("area-end", 2)));

        // An explicit line with the same number as the generated line is the same line
        resolver = areaResolver(List.of(List.of(), List.of("area-start"), List.of(), List.of("area-end"), List.of()));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area", 0)));
        assertEquals(4, resolvedEndLine(resolver, namedLine("area", 0)));
        assertEquals(2, resolvedStartLine(resolver, namedLine("area-start", 1)));
        assertEquals(6, resolvedStartLine(resolver, namedLine("area-start", 2)));
        assertEquals(6, resolvedStartLine(resolver, namedLine("area-end", 2)));
    }
}
