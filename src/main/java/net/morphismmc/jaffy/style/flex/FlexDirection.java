// JAFFY-UPSTREAM: src/style/flex.rs
// JAFFY-NOTE: `FlexDirection` enum
//
// `#[cfg(feature = "parse")]` FromCss impl is feature-gated: deferred.
package net.morphismmc.jaffy.style.flex;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;

public enum FlexDirection {
    /// Defines +x as the main axis (default)
    ROW,
    /// Defines +y as the main axis
    COLUMN,
    /// Defines -x as the main axis
    ROW_REVERSE,
    /// Defines -y as the main axis
    COLUMN_REVERSE;

    /// `Default` impl — `#[default] Row`
    public static final FlexDirection DEFAULT = ROW;

    /// Is the direction `Row` or `RowReverse`?
    public boolean isRow() {
        return this == ROW || this == ROW_REVERSE;
    }

    /// Is the direction `Column` or `ColumnReverse`?
    public boolean isColumn() {
        return this == COLUMN || this == COLUMN_REVERSE;
    }

    /// Is the direction `RowReverse` or `ColumnReverse`?
    public boolean isReverse() {
        return this == ROW_REVERSE || this == COLUMN_REVERSE;
    }

    /// Is the main axis the (single) axis that a size was requested for?
    public boolean mainAxisIsRequestedAxis(RequestedAxis axis) {
        return switch (axis) {
            case HORIZONTAL -> isRow();
            case VERTICAL -> isColumn();
            case BOTH -> false;
        };
    }

    /// The `AbsoluteAxis` that corresponds to the main axis
    public AbsoluteAxis mainAxis() {
        return isRow() ? AbsoluteAxis.HORIZONTAL : AbsoluteAxis.VERTICAL;
    }

    /// The `AbsoluteAxis` that corresponds to the cross axis
    public AbsoluteAxis crossAxis() {
        return isRow() ? AbsoluteAxis.VERTICAL : AbsoluteAxis.HORIZONTAL;
    }
}
