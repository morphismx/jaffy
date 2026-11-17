// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Size<bool>` specialization
// (SPECIALIZATIONS.md).
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;

public record SizeBool(boolean width, boolean height) {

    /// A `Size<bool>` with both axes set to `true`
    public static final SizeBool TRUE = new SizeBool(true, true);
    /// A `Size<bool>` with both axes set to `false`
    public static final SizeBool FALSE = new SizeBool(false, false);

    /// Swap the horizontal and vertical axes
    public SizeBool transpose() {
        return new SizeBool(height, width);
    }

    /// Gets the extent of the main layout axis
    public boolean main(FlexDirection direction) {
        return direction.isRow() ? width : height;
    }

    /// Gets the extent of the cross layout axis
    public boolean cross(FlexDirection direction) {
        return direction.isRow() ? height : width;
    }
}
