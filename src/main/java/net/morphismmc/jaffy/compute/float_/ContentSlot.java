// JAFFY: port of upstream src/compute/float.rs — `ContentSlot`.
package net.morphismmc.jaffy.compute.float_;

import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

/// An empty "slot" that avoids floats that is suitable for non-floated content
/// to be laid out into
public record ContentSlot(
        /// The id of the segment that the slot starts in
        @Opt @Unsigned int segmentId,
        /// The x position of the start of the slot
        float x,
        /// The y position of the start of the slot
        float y,
        /// The width of the slot
        float width,
        /// The height of the slot
        float height) {

    /// `Default` impl — `segment_id: None`, all coordinates `0.0`
    public static final ContentSlot DEFAULT = new ContentSlot(OptI32.NONE, 0.0f, 0.0f, 0.0f, 0.0f);
}
