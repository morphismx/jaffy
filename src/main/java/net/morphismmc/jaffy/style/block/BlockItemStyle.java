// JAFFY: upstream=src/style/block.rs
// JAFFY-NOTE: `BlockItemStyle` trait
// (`block_layout`). The `float`/`clear` methods are `float_layout`-gated
// upstream; `float_layout` is a default feature (Features.FLOAT_LAYOUT) so
// they are always present. `float` is a Java keyword -> `floatValue`
// (GAP-017). `impl ... for &T` has no Java counterpart.
package net.morphismmc.jaffy.style.block;

import net.morphismmc.jaffy.style.CoreStyle;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.Float;

import org.jspecify.annotations.Nullable;

/// The set of styles required for a block item (child of a block container)
public interface BlockItemStyle extends CoreStyle {

    /// Whether the item is a table. Table children are handled specially in block layout.
    default boolean isTable() {
        return false;
    }

    /// How an out-of-flow (absolutely positioned) child is aligned in the block axis of its
    /// static-position rectangle (`align-self`). `null` corresponds to `normal`/`auto`.
    default @Nullable AlignItems alignSelf() {
        return null;
    }

    /// How an out-of-flow (absolutely positioned) child is aligned in the inline axis of its
    /// static-position rectangle (`justify-self`). `null` corresponds to `normal`/`auto`.
    default @Nullable AlignItems justifySelf() {
        return null;
    }

    /// The `align-content` of the item (if it is itself a block container). A non-`normal`
    /// value makes the item establish an independent formatting context, so that it is laid
    /// out next to (rather than underneath) floats and does not collapse margins with its
    /// parent (<https://www.w3.org/TR/css-align-3/#distribution-block>).
    default AlignContent alignContent() {
        return AlignContent.NORMAL;
    }

    /// Whether the item is floated
    /// (`float` is a Java keyword -> `floatValue`)
    default Float floatValue() {
        return Float.NONE;
    }

    /// Whether the item clears floats
    default Clear clear() {
        return Clear.NONE;
    }
}
