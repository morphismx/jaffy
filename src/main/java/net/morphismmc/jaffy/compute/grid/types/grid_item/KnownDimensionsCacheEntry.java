// JAFFY: upstream=src/compute/grid/types/grid_item.rs
// `KnownDimensionsCacheEntry` (`pub(in super::super)` with private fields ->
// package-private record; `Size<Option<f32>>` -> SizeOptF32, GAP-001).
package net.morphismmc.jaffy.compute.grid.types.grid_item;

import net.morphismmc.jaffy.geometry.SizeOptF32;

/// The known_dimensions computed for a grid item for a given grid area size
record KnownDimensionsCacheEntry(SizeOptF32 gridAreaSize, SizeOptF32 knownDimensions) {}
