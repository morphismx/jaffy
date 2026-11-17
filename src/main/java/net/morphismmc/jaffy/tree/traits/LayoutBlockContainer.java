// JAFFY: upstream=src/tree/traits.rs
// JAFFY-NOTE: `LayoutBlockContainer` trait
// (`block_layout` feature).
//
// GATs `BlockContainerStyle<'a>`/`BlockItemStyle<'a>` -> `Style` (GAP-017).
// `Option<&mut BlockContext>` -> `@Nullable BlockContext` (shared object
// reference).
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.compute.block.BlockContext;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;

import org.jspecify.annotations.Nullable;

/// Extends {@link LayoutPartialTree} with getters for the styles required
/// for CSS Block layout.
public interface LayoutBlockContainer extends LayoutPartialTree {

    /// Get the container's styles
    Style getBlockContainerStyle(long nodeId);

    /// Get the child's styles
    ///
    /// Interpreted in the container's axes (see
    /// {@link LayoutPartialTree#getCoreContainerStyle}).
    Style getBlockChildStyle(long childNodeId);

    /// Compute the specified node's size or full layout given the specified
    /// constraints (`compute_block_child_layout`; `block_layout` feature).
    /// The upstream default delegates to `compute_child_layout`.
    default LayoutOutput computeBlockChildLayout(long nodeId, LayoutInput inputs, @Nullable BlockContext blockCtx) {
        return computeChildLayout(nodeId, inputs);
    }
}
