// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Rect<bool>` specialization
// (SPECIALIZATIONS.md). The `impl Rect<T>` flexbox side pickers
// (`main_start`/`main_end`/`cross_start`/`cross_end`, `pub(crate)` upstream)
// become primitive-boolean accessors.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.flex.FlexDirection;

public record RectBool(boolean left, boolean right, boolean top, boolean bottom) {

    /// `main_start` — start/top value from the perspective of the main axis
    public boolean mainStart(FlexDirection direction) {
        return direction.isRow() ? left : top;
    }

    /// `main_end` — end/bottom value from the perspective of the main axis
    public boolean mainEnd(FlexDirection direction) {
        return direction.isRow() ? right : bottom;
    }

    /// `cross_start` — start/top value from the perspective of the cross axis
    public boolean crossStart(FlexDirection direction) {
        return direction.isRow() ? top : left;
    }

    /// `cross_end` — end/bottom value from the perspective of the cross axis
    public boolean crossEnd(FlexDirection direction) {
        return direction.isRow() ? bottom : right;
    }
}
