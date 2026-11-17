// JAFFY: upstream=src/style/grid.rs
// JAFFY-NOTE: `GridAreaAxis`
// upstream; `public` here as there is no Java crate visibility).
package net.morphismmc.jaffy.style.grid;

/// Axis as `Row` or `Column`
public enum GridAreaAxis {
    /// The `Row` axis
    ROW,
    /// The `Column` axis
    COLUMN;
}
