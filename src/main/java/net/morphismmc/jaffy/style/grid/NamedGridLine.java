// JAFFY-UPSTREAM: src/style/grid.rs
// JAFFY-NOTE: `NamedGridLine<CustomIdent>`
// `CustomIdent` -> `String` (GAP-017); `u16` -> `int` (GAP-014).
package net.morphismmc.jaffy.style.grid;

/// Defines a named grid line
///
/// @param name the name of the grid area
/// @param index the index of the line in grid coordinates
public record NamedGridLine(String name, int index) {}
