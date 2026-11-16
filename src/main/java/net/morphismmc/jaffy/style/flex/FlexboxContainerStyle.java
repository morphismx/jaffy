// JAFFY: port of upstream src/style/flex.rs — `FlexboxContainerStyle` trait
// (`flexbox`; `flexLineCount` is `flexbox_balance`, in the default set).
// `impl ... for &T` has no Java counterpart.
package net.morphismmc.jaffy.style.flex;

import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.CoreStyle;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;

/// The set of styles required for a flexbox container
public interface FlexboxContainerStyle extends CoreStyle {

    /// Which direction does the main axis flow in?
    default FlexDirection flexDirection() {
        return Style.DEFAULT.flexDirection();
    }

    /// Should elements wrap, or stay in a single line?
    default FlexWrap flexWrap() {
        return Style.DEFAULT.flexWrap();
    }

    /// The minimum number of flex lines requested for a multi-line container. When items are
    /// balanced (`BALANCE`/`BALANCE_REVERSE`) they are balanced into
    /// at least this many lines. For any multi-line container, definite cross-axis available
    /// space for measuring items is divided between this many lines. (`u16` -> `int`, GAP-014)
    default int flexLineCount() {
        return Style.DEFAULT.flexLineCount();
    }

    /// How large should the gaps between items in a grid or flex container be?
    default Size<LengthPercentage> gap() {
        return Style.DEFAULT.gap();
    }

    // Alignment properties

    /// How should content contained within this item be aligned in the cross/block axis
    default AlignContent alignContent() {
        return Style.DEFAULT.alignContent();
    }

    /// How this node's children aligned in the cross/block axis?
    default AlignItems alignItems() {
        return Style.DEFAULT.alignItems();
    }

    /// How this node's children should be aligned in the inline axis
    default AlignContent justifyContent() {
        return Style.DEFAULT.justifyContent();
    }
}
