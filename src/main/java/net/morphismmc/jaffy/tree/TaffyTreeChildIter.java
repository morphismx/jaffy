// JAFFY: port of upstream src/tree/taffy_tree.rs — `TaffyTreeChildIter`.
//
// `impl Iterator<Item = NodeId>` over `core::slice::Iter<NodeId>` -> a
// `LongIterator` wrapping a fastutil `LongIterator` (primitive, no boxing).
package net.morphismmc.jaffy.tree;

import it.unimi.dsi.fastutil.longs.LongIterator;

/// Iterator that wraps a slice of nodes, lazily converting them to u64
public final class TaffyTreeChildIter implements LongIterator {

    private final LongIterator inner;

    TaffyTreeChildIter(LongIterator inner) {
        this.inner = inner;
    }

    @Override
    public long nextLong() {
        return inner.nextLong();
    }

    @Override
    public boolean hasNext() {
        return inner.hasNext();
    }

    @Override
    public Long next() {
        return nextLong();
    }

    @Override
    public int skip(int n) {
        return inner.skip(n);
    }
}
