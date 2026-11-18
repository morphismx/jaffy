// JAFFY-UPSTREAM: src/style/block.rs
// JAFFY-NOTE: `BlockContainerStyle` trait
// (`block_layout`). `impl ... for &T` has no Java counterpart.
package net.morphismmc.jaffy.style.block;

import net.morphismmc.jaffy.style.CoreStyle;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;

/// The set of styles required for a block container
public interface BlockContainerStyle extends CoreStyle {

    /// Defines which row in the grid the item should start and end at
    default TextAlign textAlign() {
        return Style.DEFAULT.textAlign();
    }

    /// How children of this block container are aligned in the block (cross) axis
    default AlignContent alignContent() {
        return Style.DEFAULT.alignContent();
    }

    /// The default inline-axis alignment of this block container's children (`justify-items`).
    /// Out-of-flow children with `justify-self: auto` are aligned within their static-position
    /// rectangle according to this value.
    default AlignItems justifyItems() {
        return Style.DEFAULT.justifyItems();
    }
}
