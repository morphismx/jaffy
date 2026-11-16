// JAFFY: port of upstream src/tree/traits.rs — `TraverseTree` marker trait.
package net.morphismmc.jaffy.tree.traits;

/// A marker trait which extends {@link TraversePartialTree}.
///
/// Implementing this trait implies the additional guarantee that the
/// child/children methods can be used to recurse infinitely down the tree.
/// It is required by the {@link RoundTree} and {@link PrintTree} traits.
public interface TraverseTree extends TraversePartialTree {}
