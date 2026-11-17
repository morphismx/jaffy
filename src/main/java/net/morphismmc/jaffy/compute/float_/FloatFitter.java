// JAFFY: upstream=src/compute/float.rs
// JAFFY-NOTE: `FloatFitter` (module-private)
//
// `slot_height` is `f64` upstream (`as f64` widening) -> `double` (GAP-008).
package net.morphismmc.jaffy.compute.float_;

import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.just.math.F32;

/// Helper type for placing a single floated box
///
/// Given a pinned starting y position, this type helps determine if there is any x position
/// such that there is sufficient horizontal space for the box accross it's entire height.
final class FloatFitter {

    /// The overall width of the Block Formatting Context
    final float bfcWidth;
    /// The total height of the set of segments currently being considered
    double slotHeight;
    /// The union of the float insets of the set of segments currently being considered
    FloatInsets floatInsets;
    /// The insets of the box's containing block from the edges of the Block Formatting Context
    final FloatInsets cbInsets;

    /// Create a new `FloatFitter`
    FloatFitter(float bfcWidth, float slotHeight, FloatInsets cbInsets) {
        this.bfcWidth = bfcWidth;
        this.slotHeight = slotHeight;
        this.floatInsets = FloatInsets.ZERO;
        this.cbInsets = cbInsets;
    }

    // Horizontal fitting

    /// Union the insets of another segment. This is a "max" of the insets on each side.
    void unionInsets(FloatInsets insets) {
        floatInsets = floatInsets.max(insets);
    }

    /// The inset from the edge of the BFC (on the side the box is floated towards) that the
    /// box would be placed at given the currently accounted for insets
    float placedInset(FloatDirection direction) {
        return F32.max(
                FloatInsetsFloatDirectionExt.get(floatInsets, direction),
                FloatInsetsFloatDirectionExt.get(cbInsets, direction));
    }

    /// Given the currently accounted for insets, check whether there is an x position
    /// such that the box fits horizontally.
    ///
    /// See {@link FloatContext#floatFitsHorizontally} for the details of the fit rules.
    ///
    /// JAFFY: upstream typo `fits_horiontally` preserved in the method name.
    boolean fitsHoriontally(float width, FloatDirection direction) {
        return FloatContext.floatFitsHorizontally(width, direction, bfcWidth, floatInsets, cbInsets);
    }

    // Vertical fitting

    /// Add the height of another segment.
    void addHeight(float height) {
        slotHeight += height;
    }

    /// Given the currently accounted for height, check whether the box fits vertically
    boolean fitsVertically(float height) {
        return slotHeight >= height;
    }
}
