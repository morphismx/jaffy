// JAFFY: port of upstream src/tree/traits.rs — `LayoutGridContainer` trait
// (`grid` feature).
//
// GATs `GridContainerStyle<'a>`/`GridItemStyle<'a>` -> `Style` (GAP-017);
// `DetailedGridInfo<Self::CustomIdent>` -> `DetailedGridInfo` (String, GAP-017).
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.Features;
import net.morphismmc.jaffy.compute.grid.DetailedGridInfo;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.util.DebugLogger;

/// Extends {@link LayoutPartialTree} with getters for the styles required
/// for CSS Grid layout.
public interface LayoutGridContainer extends LayoutPartialTree {

    /// Get the container's styles
    Style getGridContainerStyle(long nodeId);

    /// Get the child's styles
    ///
    /// Interpreted in the container's axes (see
    /// {@link LayoutPartialTree#getCoreContainerStyle}).
    Style getGridChildStyle(long childNodeId);

    /// Set the grid's detailed grid info (`set_detailed_grid_info`). The default
    /// implementation only logs, matching upstream (`debug_log!` call site ->
    /// `if (Features.DEBUG)` gate).
    default void setDetailedGridInfo(long nodeId, DetailedGridInfo detailedGridInfo) {
        if (Features.DEBUG) {
            DebugLogger.NODE_LOGGER.log("LayoutGridContainer::set_detailed_grid_info called");
        }
    }
}
