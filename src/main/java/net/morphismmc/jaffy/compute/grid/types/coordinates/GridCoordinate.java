// JAFFY: port of upstream src/compute/grid/types/coordinates.rs —
// `GridCoordinate` marker trait. `Copy` bound erases; Java record
// implementers are already value types.
package net.morphismmc.jaffy.compute.grid.types.coordinates;

/// A trait for the different coordinates used to define grid lines.
public interface GridCoordinate {}
