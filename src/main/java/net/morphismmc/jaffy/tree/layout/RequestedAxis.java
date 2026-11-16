// JAFFY: port of upstream src/tree/layout.rs — `RequestedAxis` enum.
//
// `From<AbsoluteAxis>` -> static `from`; `TryFrom<RequestedAxis> for
// AbsoluteAxis` -> `AbsoluteAxis.tryFrom` (lives on the impl target type).
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;

public enum RequestedAxis {
    /// The horizontal axis
    HORIZONTAL,
    /// The vertical axis
    VERTICAL,
    /// Both axes
    BOTH;

    /// Swap the horizontal and vertical axes (`Both` is unchanged)
    public RequestedAxis transpose() {
        return switch (this) {
            case HORIZONTAL -> VERTICAL;
            case VERTICAL -> HORIZONTAL;
            case BOTH -> BOTH;
        };
    }

    /// `From<AbsoluteAxis> for RequestedAxis`
    public static RequestedAxis from(AbsoluteAxis value) {
        return switch (value) {
            case HORIZONTAL -> HORIZONTAL;
            case VERTICAL -> VERTICAL;
        };
    }
}
