// JAFFY: port of upstream src/compute/grid/types/named.rs — `GridLineNamesIter`.
//
// `&'a [S]` yields -> unmodifiable List<String> views; `slice::Windows<u32>`
// -> IntList + index; `Option` in `next()` -> Iterator contract
// (hasNext/next, NoSuchElementException). ExactSizeIterator -> `sizeHint()`
// is exact. Clone -> copy() (GAP-005).
package net.morphismmc.jaffy.compute.grid.types.named;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.ints.IntList;

/// Iterator over the per-line name groups of a [`GridLineNames`]. Yields one
/// `List<String>` per grid line (which is empty for unnamed lines)
public final class GridLineNamesIter implements Iterator<List<String>> {
    /// The flat name storage being iterated over
    private final List<String> names;
    /// Adjacent pairs of offsets into `names` (`slice::Windows<'_, u32>`)
    private final IntList offsets;
    /// Index of the next offsets pair
    private int offsetsIndex;
    /// Number of (unnamed, implicit) lines remaining before the stored lines
    private int leadingEmpty;
    /// Number of (unnamed, implicit) lines remaining after the stored lines
    private int trailingEmpty;

    /// Constructed only via `GridLineNames::iter`/`iter_padded`
    GridLineNamesIter(List<String> names, IntList offsets, int leadingEmpty, int trailingEmpty) {
        this.names = names;
        this.offsets = offsets;
        this.leadingEmpty = leadingEmpty;
        this.trailingEmpty = trailingEmpty;
    }

    private GridLineNamesIter(
            List<String> names, IntList offsets, int offsetsIndex, int leadingEmpty, int trailingEmpty) {
        this.names = names;
        this.offsets = offsets;
        this.offsetsIndex = offsetsIndex;
        this.leadingEmpty = leadingEmpty;
        this.trailingEmpty = trailingEmpty;
    }

    /// `Iterator::next` — `Option` -> `NoSuchElementException` when exhausted
    @Override
    public List<String> next() {
        if (leadingEmpty > 0) {
            leadingEmpty -= 1;
            return List.of();
        }
        if (offsetsIndex + 1 < offsets.size()) {
            List<String> group = names.subList(offsets.getInt(offsetsIndex), offsets.getInt(offsetsIndex + 1));
            offsetsIndex++;
            return Collections.unmodifiableList(group);
        }
        if (trailingEmpty > 0) {
            trailingEmpty -= 1;
            return List.of();
        }
        throw new NoSuchElementException();
    }

    /// `Iterator::next` returning `Some` (Java `Iterator` contract)
    @Override
    public boolean hasNext() {
        return leadingEmpty > 0 || offsetsIndex + 1 < offsets.size() || trailingEmpty > 0;
    }

    /// `Iterator::size_hint` — always `(len, Some(len))` (`ExactSizeIterator`)
    public int sizeHint() {
        return leadingEmpty + Integer.max(0, offsets.size() - 1 - offsetsIndex) + trailingEmpty;
    }

    /// `#[derive(Clone)]` (GAP-005)
    public GridLineNamesIter copy() {
        return new GridLineNamesIter(names, offsets, offsetsIndex, leadingEmpty, trailingEmpty);
    }

    /// `#[derive(Debug)]`
    @Override
    public String toString() {
        return "GridLineNamesIter { names: " + names + ", leading_empty: " + leadingEmpty + ", trailing_empty: "
                + trailingEmpty + " }";
    }
}
