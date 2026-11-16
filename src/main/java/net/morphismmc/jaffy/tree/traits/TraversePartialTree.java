// JAFFY: port of upstream src/tree/traits.rs — `TraversePartialTree` trait.
//
// GAT `ChildIter<'a>: Iterator<Item = NodeId>` -> fastutil `LongIterator`
// (GAP-017); `NodeId` -> `long` (GAP-015); `usize` -> `int` (GAP-014).
// `&self`/`&mut self` are both plain instance methods in Java (GAP-018).
package net.morphismmc.jaffy.tree.traits;

import it.unimi.dsi.fastutil.longs.LongIterator;


/// Taffy's abstraction for downward tree traversal.
///
/// However, this trait does *not* require access to any nodes other than a
/// single container node's immediate children unless you also intend to
/// implement {@link TraverseTree}.
public interface TraversePartialTree {

    /// Get the list of children IDs for the given node
    LongIterator childIds(long parentNodeId);

    /// Get the number of children for the given node
    int childCount(long parentNodeId);

    /// Get a specific child of a node, where the index represents the nth child
    long getChildId(long parentNodeId, int childIndex);
}
