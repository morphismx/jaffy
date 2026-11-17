// JAFFY: upstream=src/tree/layout.rs
// JAFFY-NOTE: `OofPositioningArea` struct
// serde impl deferred.
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.SizeF32;

/// The area of a container against which the insets of its out-of-flow (absolute/fixed) boxes
/// are resolved: the container's border box minus its borders and scrollbar gutters.
///
/// Container layout algorithms record this on `LayoutOutput` (for `RunMode.PERFORM_LAYOUT` runs)
/// so that the out-of-flow positioning pass (`compute_oof_layout`) can run after the algorithm
/// has finished. When the container is a grid, per-box grid areas are resolved within this area
/// by the positioning pass itself (from the container's detailed grid info).
///
/// @param size the size of the area
/// @param offset the offset of the area from the container's border box origin
public record OofPositioningArea(SizeF32 size, PointF32 offset) {

    /// Swap the horizontal and vertical axes (`transpose` used by `LayoutOutput.transpose`)
    public OofPositioningArea transpose() {
        return new OofPositioningArea(size.transpose(), offset.transpose());
    }
}
