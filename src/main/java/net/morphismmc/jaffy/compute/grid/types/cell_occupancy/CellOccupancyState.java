// JAFFY-UPSTREAM: src/compute/grid/types/cell_occupancy.rs
// `CellOccupancyState`.
//
// `pub(crate)` -> public (callers cross package boundaries); `#[default]
// Unoccupied` -> first enum constant; `Debug` -> toString reproduces the Rust
// variant name (GAP-016).
package net.morphismmc.jaffy.compute.grid.types.cell_occupancy;

/// The occupancy state of a single grid cell
public enum CellOccupancyState {
    /// Indicates that a grid cell is unoccupied (Rust `#[default]`)
    UNOCCUPIED("Unoccupied"),
    /// Indicates that a grid cell is occupied by a definitely placed item
    DEFINITELY_PLACED("DefinitelyPlaced"),
    /// Indicates that a grid cell is occupied by an item that was placed by the auto placement algorithm
    AUTO_PLACED("AutoPlaced");

    private final String rustName;

    CellOccupancyState(String rustName) {
        this.rustName = rustName;
    }

    /// `#[derive(Debug)]` — prints the Rust variant name
    @Override
    public String toString() {
        return rustName;
    }
}
