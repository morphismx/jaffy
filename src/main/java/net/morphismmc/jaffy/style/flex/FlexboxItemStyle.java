// JAFFY: port of upstream src/style/flex.rs — `FlexboxItemStyle` trait
// (`flexbox`). `impl ... for &T` has no Java counterpart.
package net.morphismmc.jaffy.style.flex;

import org.jspecify.annotations.Nullable;
import net.morphismmc.jaffy.style.CoreStyle;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.Dimension;

/// The set of styles required for a flexbox item (child of a flexbox container)
public interface FlexboxItemStyle extends CoreStyle {

    /// Sets the initial main axis size of the item
    default Dimension flexBasis() {
        return Style.DEFAULT.flexBasis();
    }

    /// The relative rate at which this item grows when it is expanding to fill space
    ///
    /// 0.0 is the default value, and this value must be positive.
    default float flexGrow() {
        return Style.DEFAULT.flexGrow();
    }

    /// The relative rate at which this item shrinks when it is contracting to fit into space
    ///
    /// 1.0 is the default value, and this value must be positive.
    default float flexShrink() {
        return Style.DEFAULT.flexShrink();
    }

    /// How this node should be aligned in the cross/block axis
    /// Falls back to the parents `alignItems` if not set (`Option<AlignSelf>` -> `@Nullable AlignItems`)
    default @Nullable AlignItems alignSelf() {
        return Style.DEFAULT.alignSelf();
    }
}
