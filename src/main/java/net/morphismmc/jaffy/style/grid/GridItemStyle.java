// JAFFY: port of upstream src/style/grid.rs — `GridItemStyle` trait (`grid`).
// `Option<AlignSelf>`/aliases are `AlignItems` + nullable (GAP-001/017).
// `impl ... for &T` has no Java counterpart.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.Line;
import org.jspecify.annotations.Nullable;
import net.morphismmc.jaffy.style.CoreStyle;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignItems;

/// The set of styles required for a CSS Grid item (child of a CSS Grid container)
public interface GridItemStyle extends CoreStyle {

    /// Defines which row in the grid the item should start and end at
    default Line<GridPlacement> gridRow() {
        return LineGridPlacementExt.DEFAULT;
    }

    /// Defines which column in the grid the item should start and end at
    default Line<GridPlacement> gridColumn() {
        return LineGridPlacementExt.DEFAULT;
    }

    /// How this node should be aligned in the cross/block axis
    /// Falls back to the parents `alignItems` if not set (nullable).
    default @Nullable AlignItems alignSelf() {
        return Style.DEFAULT.alignSelf();
    }

    /// How this node should be aligned in the inline axis
    /// Falls back to the parents `justifyItems` if not set (nullable).
    default @Nullable AlignItems justifySelf() {
        return Style.DEFAULT.justifySelf();
    }

    /// Get a grid item's row or column placement depending on the axis passed
    default Line<GridPlacement> gridPlacement(AbsoluteAxis axis) {
        return switch (axis) {
            case HORIZONTAL -> gridColumn();
            case VERTICAL -> gridRow();
        };
    }
}
