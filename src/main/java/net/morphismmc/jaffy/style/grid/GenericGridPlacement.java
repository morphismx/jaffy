// JAFFY-UPSTREAM: src/style/grid.rs
// JAFFY-NOTE: `GenericGridPlacement<LineType>`
// and its type aliases OriginZeroGridPlacement/NonNamedGridPlacement
// (Java has no aliases — instantiate the generic directly).
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.compute.grid.types.coordinates.GridCoordinate;

/// A grid line placement specification which is generic over the coordinate
/// system that it uses to define grid line positions.
///
/// `GenericGridPlacement<GridLine>` is aliased as `NonNamedGridPlacement` and
/// `GenericGridPlacement<OriginZeroLine>` as `OriginZeroGridPlacement`
/// upstream.
public sealed interface GenericGridPlacement<T extends GridCoordinate> {

    /// Place item according to the auto-placement algorithm, and the parent's
    /// grid_auto_flow property
    record Auto<T extends GridCoordinate>() implements GenericGridPlacement<T> {}

    /// Place item at specified line (column or row) index
    record Line<T extends GridCoordinate>(T line) implements GenericGridPlacement<T> {}

    /// Item should span specified number of tracks (columns or rows)
    record Span<T extends GridCoordinate>(int span) implements GenericGridPlacement<T> {}
}
