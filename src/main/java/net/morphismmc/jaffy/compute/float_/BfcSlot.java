// JAFFY: port of upstream src/compute/float.rs — `BfcSlot`.
package net.morphismmc.jaffy.compute.float_;

import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

/// An empty "slot" that avoids floats that is suitable for a box that establishes
/// an independent formatting context (and therefore must not overlap floats) to be
/// laid out into. Unlike {@link ContentSlot}, this accounts for the box's own margins,
/// which are resolved against the containing block's edges (not float edges) and
/// may therefore overlap floats.
public record BfcSlot(
        /// The id of the segment that the slot starts in
        @Opt @Unsigned int segmentId,
        /// The x position of the start of the slot (border box edge)
        float x,
        /// The y position of the start of the slot
        float y,
        /// The space available for the box's border box: the space between float edges
        /// and the margin-inset containing block edges. The box fits in the slot if its
        /// border box width does not exceed this.
        float borderWidth,
        /// The width that an auto width resolves to (before applying min/max constraints and
        /// the negative-margin lower bound). This differs from `border_width` in that the
        /// trailing margin is subtracted (except for any part of it that overlaps a float).
        float stretchWidth,
        /// How far down from the slot's top its widths stay valid: a box taller than this may be
        /// beside further floats.
        float height) {

    /// `Default` impl — `segment_id: None`, all fields `0.0`
    public static final BfcSlot DEFAULT = new BfcSlot(OptI32.NONE, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
}
