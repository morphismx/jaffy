// JAFFY: upstream=src/style/grid.rs
// JAFFY-NOTE: `InvalidStringRepetitionValue`
// (Display message preserved as `toString`; `std::error::Error` has no Java
// counterpart).
package net.morphismmc.jaffy.style.grid;

/// Error returned when trying to convert a string to a GridTrackRepetition and that string is not
/// either "auto-fit" or "auto-fill"
public final class InvalidStringRepetitionValue {

    @Override
    public String toString() {
        return "&str can only be converted to GridTrackRepetition if it's value is 'auto-fit' or 'auto-fill'";
    }
}
