// JAFFY: port of upstream src/tree/traits.rs — `LayoutFlexboxContainer`
// trait (`flexbox` feature).
//
// GATs `FlexboxContainerStyle<'a>`/`FlexboxItemStyle<'a>` -> `Style` (GAP-017:
// both upstream implementors use `&'a Style`).
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.style.Style;

/// Extends {@link LayoutPartialTree} with getters for the styles required
/// for Flexbox layout.
public interface LayoutFlexboxContainer extends LayoutPartialTree {

    /// Get the container's styles
    Style getFlexboxContainerStyle(long nodeId);

    /// Get the child's styles
    ///
    /// Interpreted in the container's axes (see
    /// {@link LayoutPartialTree#getCoreContainerStyle}).
    Style getFlexboxChildStyle(long childNodeId);
}
