// JAFFY: port of upstream src/compute/grid/types/named.rs —
// `NamedLineResolverAxis` (module-private in Rust -> package-private).
//
// `lines`/`areas` are `&'a` borrows of the parent's maps; `LinePositions =
// SmallVec<[u32; 4]>` -> IntArrayList (values always fit in int, GAP-014).
// `partition_point` -> the private static binary search at the bottom.
package net.morphismmc.jaffy.compute.grid.types.named;

import java.util.Map;
import java.util.function.IntPredicate;

import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridAreaAxis;
import net.morphismmc.jaffy.style.grid.GridAreaEnd;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/// Resolver for named placements in one grid axis
final class NamedLineResolverAxis {
    /// Named lines and their one-indexed positions (`&'a NamedGridLinesMap<S>`)
    private final Map<String, IntArrayList> lines;
    /// The edges of named grid areas (in both axes) (`&'a GridAreasMap<S>`)
    private final Map<String, AreaLines> areas;
    /// The axis being resolved
    private final GridAreaAxis axis;
    /// Number of explicit tracks in this axis
    private final int explicitTrackCount;

    NamedLineResolverAxis(
            Map<String, IntArrayList> lines, Map<String, AreaLines> areas, GridAreaAxis axis, int explicitTrackCount) {
        this.lines = lines;
        this.areas = areas;
        this.axis = axis;
        this.explicitTrackCount = explicitTrackCount;
    }

    /// The line for the passed edge of the named grid area in this axis (if there is one)
    /// `Option<&u32>` -> @Opt @Unsigned int, OptI32.NONE
    private @Opt @Unsigned int areaLine(String areaName, GridAreaEnd end) {
        AreaLines area = areas.get(areaName);
        return area == null ? OptI32.NONE : area.line(axis, end);
    }

    /// Resolve named lines and spans into numeric placements
    /// `Line<GridPlacement<S>>` -> `Line<GridPlacement>`; `NonNamedGridPlacement`
    /// is a `GenericGridPlacement<GridLine>` alias (no Java alias type).
    Line<GenericGridPlacement<GridLine>> resolveLineNames(Line<GridPlacement> line) {
        GridPlacement startResolved = line.start();
        if (startResolved instanceof GridPlacement.NamedLine nl) {
            startResolved = new GridPlacement.Line(findNamedLine(nl.name(), nl.index(), GridAreaEnd.START));
        }
        GridPlacement endResolved = line.end();
        if (endResolved instanceof GridPlacement.NamedLine nl) {
            endResolved = new GridPlacement.Line(findNamedLine(nl.name(), nl.index(), GridAreaEnd.END));
        }

        // If both the *-start and *-end values of its grid-placement properties specify a line, its grid span is
        // implicit.
        // If it has an explicit span value, its grid span is explicit.
        // Otherwise, its grid span is automatic:
        //   - if it is subgridded in that axis, its grid span is determined from its <line-name-list>;
        //   - otherwise its grid span is 1.
        //
        // <https://drafts.csswg.org/css-grid-2/#grid-span>
        if (startResolved instanceof GridPlacement.Line startLine
                && endResolved instanceof GridPlacement.NamedSpan endSpan
                && startLine.line().asI16() != 0) {
            OriginZeroLine afterStartLine =
                    startLine.line().intoOriginZeroLine(explicitTrackCount).add(1);
            OriginZeroLine endLine =
                    nthLineForwards(linesNamed(endSpan.name()), afterStartLine, Integer.max(endSpan.span(), 1));
            return new Line<>(
                    new GenericGridPlacement.Line<>(startLine.line()),
                    new GenericGridPlacement.Line<>(endLine.intoGridLine(explicitTrackCount)));
        }
        if (startResolved instanceof GridPlacement.NamedSpan startSpan
                && endResolved instanceof GridPlacement.Line endLine
                && endLine.line().asI16() != 0) {
            OriginZeroLine beforeEndLine =
                    endLine.line().intoOriginZeroLine(explicitTrackCount).sub(1);
            OriginZeroLine startLine =
                    nthLineBackwards(linesNamed(startSpan.name()), beforeEndLine, Integer.max(startSpan.span(), 1));
            return new Line<>(
                    new GenericGridPlacement.Line<>(startLine.intoGridLine(explicitTrackCount)),
                    new GenericGridPlacement.Line<>(endLine.line()));
        }
        GenericGridPlacement<GridLine> start = switch (startResolved) {
            case GridPlacement.Auto a -> new GenericGridPlacement.Auto<>();
            case GridPlacement.Line l -> new GenericGridPlacement.Line<>(l.line());
            case GridPlacement.Span s -> new GenericGridPlacement.Span<>(s.span());
            case GridPlacement.NamedSpan n -> new GenericGridPlacement.Span<>(1);
            case GridPlacement.NamedLine n -> throw new IllegalStateException("unreachable");
        };
        GenericGridPlacement<GridLine> end = switch (endResolved) {
            case GridPlacement.Auto a -> new GenericGridPlacement.Auto<>();
            case GridPlacement.Line l -> new GenericGridPlacement.Line<>(l.line());
            case GridPlacement.Span s -> new GenericGridPlacement.Span<>(s.span());
            case GridPlacement.NamedSpan n -> new GenericGridPlacement.Span<>(1);
            case GridPlacement.NamedLine n -> throw new IllegalStateException("unreachable");
        };
        return new Line<>(start, end);
    }

    /// Resolve the grid line for a named grid line (`<custom-ident>` or `<integer> <custom-ident>`).
    /// An index of 0 is used to represent "no index specified".
    ///
    /// <https://drafts.csswg.org/css-grid-2/#line-placement>
    private GridLine findNamedLine(String name, int idx, GridAreaEnd end) {
        // A `<custom-ident>` on its own first attempts to match the edge of a named grid area:
        // the first line named `<custom-ident>-start` (or `<custom-ident>-end` for an end line).
        // Otherwise it is treated as if the integer 1 had been specified along with it.
        if (idx == 0) {
            @Opt
            @Unsigned
            int firstLine = areaLine(name, end);
            if (firstLine != OptI32.NONE) {
                return OriginZeroLine.clamped(NamedLineResolver.ozLine(firstLine))
                        .intoGridLine(explicitTrackCount);
            }
        }

        IntList lines = linesNamed(name);
        OriginZeroLine line = idx >= 0
                ? nthLineForwards(lines, new OriginZeroLine(0), Integer.max(idx, 1))
                : nthLineBackwards(lines, lastExplicitLine(), -idx);
        return line.intoGridLine(explicitTrackCount);
    }

    /// The sorted line numbers of the lines with the passed name
    ///
    /// A line generated by a grid area is included in the lines of its name if any line has been explicitly
    /// given the same name. Otherwise it is found from the area's edges.
    /// `&[u32]` -> IntList (read-only view).
    private IntList linesNamed(String name) {
        IntArrayList lines = this.lines.get(name);
        if (lines != null) {
            return lines;
        }
        NamedLineResolver.AreaEdgeName edge = NamedLineResolver.splitAreaEdgeName(name);
        if (edge != null) {
            @Opt
            @Unsigned
            int areaLine = areaLine(edge.areaName(), edge.end());
            if (areaLine != OptI32.NONE) {
                return IntLists.singleton(areaLine);
            }
        }
        return IntLists.EMPTY_LIST;
    }

    /// The line at the end edge of the explicit grid
    private OriginZeroLine lastExplicitLine() {
        return new OriginZeroLine(explicitTrackCount);
    }

    /// Find the `n`th line in `lines` (searching forwards) that is not before `first_line`.
    ///
    /// If there are not enough such lines, then all implicit lines after the explicit grid are
    /// assumed to be in `lines` for the purpose of counting
    ///
    /// <https://drafts.csswg.org/css-grid-2/#grid-placement-int>
    private OriginZeroLine nthLineForwards(IntList lines, OriginZeroLine firstLine, int n) {
        int first = Integer.max(firstLine.value(), 0);
        int startIdx = partitionPoint(lines, line -> NamedLineResolver.ozLine(line) < first);
        int slicedLen = lines.size() - startIdx;
        int line;
        if (n - 1 >= 0 && n - 1 < slicedLen) {
            line = NamedLineResolver.ozLine(lines.getInt(startIdx + n - 1));
        } else {
            line = Integer.max(first, lastExplicitLine().value() + 1) + (n - slicedLen) - 1;
        }
        return OriginZeroLine.clamped(line);
    }

    /// Find the `n`th line in `lines` (searching backwards) that is not after `last_line`.
    ///
    /// If there are not enough such lines, then all implicit lines before the explicit grid are
    /// assumed to be in `lines` for the purpose of counting
    ///
    /// <https://drafts.csswg.org/css-grid-2/#grid-placement-int>
    private OriginZeroLine nthLineBackwards(IntList lines, OriginZeroLine lastLine, int n) {
        int last = Integer.min(lastLine.value(), lastExplicitLine().value());
        int endIdx = partitionPoint(lines, line -> NamedLineResolver.ozLine(line) <= last);
        int line;
        // `lines.len().checked_sub(n)` on the sliced prefix
        if (endIdx >= n) {
            line = NamedLineResolver.ozLine(lines.getInt(endIdx - n));
        } else {
            line = Integer.min(last, -1) - (n - endIdx) + 1;
        }
        return OriginZeroLine.clamped(line);
    }

    /// JAFFY: `<[u32]>::partition_point` — binary search for the index of the
    /// first element for which `pred` returns false (input is sorted).
    private static int partitionPoint(IntList lines, IntPredicate pred) {
        int lo = 0;
        int hi = lines.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (pred.test(lines.getInt(mid))) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
