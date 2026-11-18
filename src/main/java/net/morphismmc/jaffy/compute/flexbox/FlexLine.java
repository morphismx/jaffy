// JAFFY-UPSTREAM: src/compute/flexbox.rs
// JAFFY-NOTE: `FlexLine`

package net.morphismmc.jaffy.compute.flexbox;

final class FlexLine {
    /// The slice of items to iterate over during computation of this line
    java.util.List<FlexItem> items;
    /// The dimensions of the cross-axis
    float crossSize;
    /// The relative offset of the cross-axis
    float offsetCross;
}
