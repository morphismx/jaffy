// JAFFY: port of upstream src/style/grid.rs — `GridAutoFlow` enum.
// The `parse` impl is deferred with the feature.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;

/// The [grid-auto-flow](https://developer.mozilla.org/en-US/docs/Web/CSS/grid-auto-flow) property controls how the
/// auto-placement algorithm works.
public enum GridAutoFlow {

    /// Items are placed by filling each row in turn, adding new rows as necessary
    ROW,
    /// Items are placed by filling each column in turn, adding new columns as necessary.
    COLUMN,
    /// Combines `Row` with the dense packing algorithm.
    ROW_DENSE,
    /// Combines `Column` with the dense packing algorithm.
    COLUMN_DENSE;

    /// Rust `#[default]` variant
    public static final GridAutoFlow DEFAULT = ROW;

    /// Whether grid auto placement uses the sparse placement algorithm or the dense placement algorithm
    /// See: <https://developer.mozilla.org/en-US/docs/Web/CSS/grid-auto-flow#values>
    public boolean isDense() {
        return this == ROW_DENSE || this == COLUMN_DENSE;
    }

    /// Whether grid auto placement fills areas row-wise or column-wise
    /// See: <https://developer.mozilla.org/en-US/docs/Web/CSS/grid-auto-flow#values>
    public AbsoluteAxis primaryAxis() {
        return switch (this) {
            case ROW, ROW_DENSE -> AbsoluteAxis.HORIZONTAL;
            case COLUMN, COLUMN_DENSE -> AbsoluteAxis.VERTICAL;
        };
    }
}
