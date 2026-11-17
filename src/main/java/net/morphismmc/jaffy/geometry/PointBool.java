// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Point<bool>` specialization
// (SPECIALIZATIONS.md).
package net.morphismmc.jaffy.geometry;

public record PointBool(boolean x, boolean y) {

    /// A `Point<bool>` with both axes set to `true`
    public static final PointBool TRUE = new PointBool(true, true);
    /// A `Point<bool>` with both axes set to `false`
    public static final PointBool FALSE = new PointBool(false, false);

    /// Swap the horizontal and vertical axes
    public PointBool transpose() {
        return new PointBool(y, x);
    }
}
