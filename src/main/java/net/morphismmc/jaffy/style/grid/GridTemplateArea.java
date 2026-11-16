// JAFFY: port of upstream src/style/grid.rs — `GridTemplateArea<CustomIdent>`.
// `CustomIdent` -> `String` (GAP-017); `u16` -> `int` (GAP-014).
package net.morphismmc.jaffy.style.grid;

/// Defines a grid area
///
/// @param name the name of the grid area
/// @param rowStart the index of the row at which the grid area starts in grid coordinates
/// @param rowEnd the index of the row at which the grid area ends in grid coordinates
/// @param columnStart the index of the column at which the grid area starts in grid coordinates
/// @param columnEnd the index of the column at which the grid area end in grid coordinates
public record GridTemplateArea(String name, int rowStart, int rowEnd, int columnStart, int columnEnd) {}
