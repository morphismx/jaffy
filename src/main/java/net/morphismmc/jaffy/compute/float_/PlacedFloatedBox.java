// JAFFY: upstream=src/compute/float.rs
// JAFFY-NOTE: `PlacedFloatedBox`
package net.morphismmc.jaffy.compute.float_;

/// A floated box
public record PlacedFloatedBox(
        /// The caller-defined id that the box was placed with
        long id,
        /// The width of the box
        float width,
        /// The height of the box
        float height,
        /// Horizontal distance from the edge of the container that the box is floated towards
        /// (distance from the left for left floats, from the right for right floats)
        float xInset,
        /// Vertical distance from top edge of the container
        float y) {

    /// `Default` impl — all fields zeroed
    public static final PlacedFloatedBox DEFAULT = new PlacedFloatedBox(0L, 0.0f, 0.0f, 0.0f, 0.0f);
}
