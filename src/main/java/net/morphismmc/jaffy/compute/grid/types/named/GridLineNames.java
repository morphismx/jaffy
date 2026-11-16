// JAFFY: port of upstream src/compute/grid/types/named.rs — `GridLineNames`.
//
// `S: CheapCloneStr` -> `String` (DefaultCheapStr = String, GAP-017); `u32`/
// `usize` -> int (GAP-014). `Vec<S>` -> List<String>; `Vec<u32>` ->
// IntArrayList; `NamedGridLinesMap` -> `Map<String, IntArrayList>`.
// `&[S]` returns -> unmodifiable List<String> views.
// Clone -> copy() (deep, incl. the per-name position lists) (GAP-005).
package net.morphismmc.jaffy.compute.grid.types.named;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GridAreaAxis;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import org.jspecify.annotations.Nullable;


/// The names of each explicit grid line in a single axis, stored in CSR (compressed sparse
/// row) format: a single flat `List` of names plus an `IntArrayList` of offsets into it (one more offset
/// than there are lines). The names of line `i` (0-indexed) are `names[offsets[i]..offsets[i + 1]]`.
///
/// Line indices here are relative to the explicit grid;
/// [`DetailedGridTracksInfo`](crate::DetailedGridTracksInfo) provides accessors with indices
/// relative to the full grid (including implicit tracks).
///
/// Iterate over per-line name groups with [`GridLineNames::iter`], or access a single line's
/// names with [`GridLineNames::line`]. A grid with no named lines is represented by two empty
/// `List`s (see [`GridLineNames::is_empty`]).
public final class GridLineNames implements Iterable<List<String>> {
    /// The names of every grid line in the axis, concatenated in line order
    private List<String> names;
    /// Offsets into `names`: line `i`'s names are `names[offsets[i]..offsets[i + 1]]`.
    /// Either empty (no named lines) or of length `line count + 1`.
    private IntArrayList offsets;
    /// Named line lookup used to resolve arbitrary grid placements
    /// (package-private: `NamedLineResolver::populate_detailed_line_resolvers` assigns it)
    Map<String, IntArrayList> resolver;

    /// `#[derive(Default)]` — a grid with no named lines (two empty lists)
    public GridLineNames() {
        this.names = new ArrayList<>();
        this.offsets = new IntArrayList();
        this.resolver = new HashMap<>();
    }

    private GridLineNames(int nameCapacity, int offsetCapacity) {
        this.names = new ArrayList<>(nameCapacity);
        this.offsets = new IntArrayList(offsetCapacity);
        this.resolver = new HashMap<>();
    }

    /// Create an empty `GridLineNames` with pre-allocated capacity
    /// (`pub(crate)` upstream -> public)
    public static GridLineNames withCapacity(int nameCapacity, int offsetCapacity) {
        GridLineNames lineNames = new GridLineNames(nameCapacity, offsetCapacity);
        lineNames.offsets.add(0);
        return lineNames;
    }

    /// Start a new (initially empty) line
    /// (`pub(crate)` upstream -> public)
    public void startLine() {
        offsets.add(names.size());
    }

    /// Append a name to the current (last) line
    /// (`pub(crate)` upstream -> public)
    public void pushName(String name) {
        names.add(name);
        if (offsets.isEmpty()) {
            throw new IllegalStateException(); // `offsets.last_mut().unwrap()` (GAP-010)
        }
        offsets.set(offsets.size() - 1, names.size());
    }

    /// Whether the current (last) line already contains the passed name
    /// (`pub(crate)` upstream -> public)
    public boolean currentLineContains(String name) {
        // `line_count().wrapping_sub(1)`: -1 when 0, which `line()` maps to an empty list
        return line(lineCount() - 1).contains(name);
    }

    /// Resolve named lines and spans using the retained line-name map and the passed grid area map
    /// (`pub(crate)` upstream -> public)
    public Line<GenericGridPlacement<GridLine>> resolveLineNames(
            Line<GridPlacement> line, Map<String, AreaLines> areas, GridAreaAxis axis,
            int explicitTrackCount) {
        return new NamedLineResolverAxis(resolver, areas, axis, explicitTrackCount).resolveLineNames(line);
    }

    /// Whether the axis has any named lines at all
    public boolean isEmpty() {
        return names.isEmpty();
    }

    /// The number of grid lines represented (zero if the grid has no named lines)
    public int lineCount() {
        return Integer.max(0, offsets.size() - 1); // `offsets.len().saturating_sub(1)`
    }

    /// The names of the line with the passed 0-indexed line index.
    /// Returns an empty list if the line has no names or the index is out of range.
    /// `&[S]` -> unmodifiable view (`Collections.unmodifiableList` wrapper).
    public List<String> line(int lineIndex) {
        if (lineIndex >= 0 && lineIndex + 1 < offsets.size()) {
            return Collections.unmodifiableList(
                    names.subList(offsets.getInt(lineIndex), offsets.getInt(lineIndex + 1)));
        }
        return List.of();
    }

    /// Iterate over the name group of each grid line in line order
    public GridLineNamesIter iter() {
        return iterPadded(0, 0);
    }

    /// Iterate over the name group of each grid line in line order, additionally
    /// yielding `leadingEmpty` empty groups before the stored lines and `trailingEmpty`
    /// empty groups after them (representing unnamed implicit grid lines)
    /// (`pub(crate)` upstream -> public)
    public GridLineNamesIter iterPadded(int leadingEmpty, int trailingEmpty) {
        return new GridLineNamesIter(names, offsets, leadingEmpty, trailingEmpty);
    }

    /// `IntoIterator for &GridLineNames` — iterates per-line name groups in line order
    @Override
    public Iterator<List<String>> iterator() {
        return iter();
    }

    /// `Clone` — deep copy including the per-name position lists (GAP-005)
    public GridLineNames copy() {
        GridLineNames copy = new GridLineNames();
        copy.names = new ArrayList<>(names);
        copy.offsets = new IntArrayList(offsets);
        Map<String, IntArrayList> resolverCopy = new HashMap<>(resolver.size());
        resolver.forEach((name, lines) -> resolverCopy.put(name, new IntArrayList(lines)));
        copy.resolver = resolverCopy;
        return copy;
    }

    /// `PartialEq`
    @Override
    public boolean equals(Object o) {
        return o instanceof GridLineNames other
                && names.equals(other.names)
                && offsets.equals(other.offsets)
                && resolver.equals(other.resolver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(names, offsets, resolver);
    }

    /// `#[derive(Debug)]` — `Vec<S>` prints `["a", "b"]`; the map prints
    /// `{"a": [1, 2]}` with unspecified entry order (same as Rust `HashMap`).
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GridLineNames { names: ");
        debugStrList(sb, names);
        sb.append(", offsets: ");
        debugIntList(sb, offsets);
        sb.append(", resolver: {");
        boolean first = true;
        for (Map.Entry<String, IntArrayList> e : resolver.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\": ");
            debugIntList(sb, e.getValue());
        }
        return sb.append(" }").toString();
    }

    private static void debugStrList(StringBuilder sb, List<String> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(list.get(i)).append('"');
        }
        sb.append(']');
    }

    private static void debugIntList(StringBuilder sb, IntArrayList list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.getInt(i));
        }
        sb.append(']');
    }
}
