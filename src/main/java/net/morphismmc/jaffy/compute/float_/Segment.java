// JAFFY: upstream=src/compute/float.rs
// JAFFY-NOTE: `Segment` (module-private)
//
// `y: Range<f32>` is stored as mutable `yStart`/`yEnd` fields;
// `has_float: [bool; 2]` is a `boolean[2]` (cloned on `copy()`).
package net.morphismmc.jaffy.compute.float_;

import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.just.fmt.RustFmt;

/// A non-overlapping horizontal segment of the Block Formatting Context container
final class Segment {

    /// The vertical start and end points of the segment
    float yStart;
    float yEnd;
    /// The space taken up by floats on each side
    FloatInsets insets;
    /// Whether a float actually occupies the left (slot 0) / right (slot 1) inset.
    /// The insets of a segment created for a float are seeded with the float's containing
    /// block insets on both sides, so a non-zero inset alone does not imply a float.
    boolean[] hasFloat;

    Segment(float yStart, float yEnd, FloatInsets insets, boolean[] hasFloat) {
        this.yStart = yStart;
        this.yEnd = yEnd;
        this.insets = insets;
        this.hasFloat = hasFloat;
    }

    /// Whether the segment can fit the passed floated box (in the horizontal axis)
    ///
    /// See {@link FloatContext#floatFitsHorizontally} for the details of the fit rules.
    boolean fitsFloatWidth(SizeF32 floatedBox, FloatDirection direction, float bfcWidth, FloatInsets cbInsets) {
        return FloatContext.floatFitsHorizontally(floatedBox.width(), direction, bfcWidth, insets, cbInsets);
    }

    /// Widen this segment's float insets and occupied sides to cover `other`'s
    void union(Segment other) {
        insets = insets.max(other.insets);
        hasFloat = new boolean[] {hasFloat[0] || other.hasFloat[0], hasFloat[1] || other.hasFloat[1]};
    }

    /// Whether this segment is narrower than `other` on either side: a larger inset, or a float
    /// on a side where `other` has none. The latter counts even if the inset doesn't grow, since
    /// `find_bfc_slot` only applies the insets on sides that have a float.
    boolean isNarrowerThan(Segment other) {
        return insets.left() > other.insets.left()
                || insets.right() > other.insets.right()
                || (hasFloat[0] && !other.hasFloat[0])
                || (hasFloat[1] && !other.hasFloat[1]);
    }

    /// `Clone` — deep-copies `has_float` (GAP-005)
    Segment copy() {
        return new Segment(yStart, yEnd, insets, hasFloat.clone());
    }

    /// `#[derive(Debug)]` — `Segment { y: 0.0..1.0, insets: FloatInsets { left: 0.0,
    /// right: 0.0 }, has_float: [false, false] }` (GAP-016)
    @Override
    public String toString() {
        return "Segment { y: " + RustFmt.debug(yStart) + ".." + RustFmt.debug(yEnd)
                + ", insets: FloatInsets { left: " + RustFmt.debug(insets.left())
                + ", right: " + RustFmt.debug(insets.right()) + " }, has_float: ["
                + hasFloat[0] + ", " + hasFloat[1] + "] }";
    }
}
