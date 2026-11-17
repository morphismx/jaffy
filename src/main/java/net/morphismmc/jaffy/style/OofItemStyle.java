// JAFFY: upstream=src/style/mod.rs
// JAFFY-NOTE: `OofItemStyle` trait
// `AlignSelf` is an alias of `AlignItems` upstream; `Option<AlignSelf>` is a
// `@Nullable AlignItems` (GAP-001). `impl OofItemStyle for &T` has no Java
// counterpart.
package net.morphismmc.jaffy.style;

import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.LineGridPlacementExt;

import org.jspecify.annotations.Nullable;

/// The styles of an out-of-flow (`position: absolute` / `position: fixed`) box that are read by
/// its containing block's out-of-flow positioning pass
public interface OofItemStyle extends CoreStyle {

    /// How the box is aligned in the block axis of its inset-modified containing block
    /// (`align-self`). `null` corresponds to `normal`/`auto`.
    default @Nullable AlignItems alignSelf() {
        return null;
    }

    /// How the box is aligned in the inline axis of its inset-modified containing block
    /// (`justify-self`). `null` corresponds to `normal`/`auto`.
    default @Nullable AlignItems justifySelf() {
        return null;
    }

    /// Whether the box is a table wrapper box. A `normal` self-alignment does not stretch
    /// tables to fill their inset-modified containing block.
    default boolean isTable() {
        return false;
    }

    /// Defines which row in the grid the box should start and end at, when the box's containing
    /// block is a grid container (`grid`)
    default Line<GridPlacement> gridRow() {
        return LineGridPlacementExt.DEFAULT;
    }

    /// Defines which column in the grid the box should start and end at, when the box's
    /// containing block is a grid container (`grid`)
    default Line<GridPlacement> gridColumn() {
        return LineGridPlacementExt.DEFAULT;
    }
}
