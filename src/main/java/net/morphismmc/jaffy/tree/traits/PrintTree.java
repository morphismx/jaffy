// JAFFY: port of upstream src/tree/traits.rs — `PrintTree` trait.
//
// `&'static str` -> `String`.
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.tree.layout.Layout;

/// Trait used by the `print_tree` method which prints a debug representation.
///
/// As indicated by its dependence on {@link TraverseTree}, it requires full
/// recursive access to the tree.
public interface PrintTree extends TraverseTree {

    /// Get a debug label for the node (typically the type of node: flexbox,
    /// grid, text, image, etc)
    String getDebugLabel(long nodeId);

    /// Get the node's final layout
    Layout getFinalLayout(long nodeId);
}
