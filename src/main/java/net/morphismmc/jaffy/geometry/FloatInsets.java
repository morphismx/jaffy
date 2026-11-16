// JAFFY: port of upstream src/geometry.rs — `FloatInsets`.
//
// The distances that a horizontal band of space is inset from the left and
// right edges of a container. Used for the space taken up by floats, and for
// the position of a containing block within a block formatting context.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.just.math.F32;

public record FloatInsets(float left, float right) {

    /// Zero insets on both sides
    public static final FloatInsets ZERO = new FloatInsets(0.0f, 0.0f);

    /// `FloatInsets::new(left, right)` — the record ctor maps directly.

    /// The sum of the left and right insets
    public float sum() {
        return left + right;
    }

    /// The larger of the two insets on each side (`f32_max`)
    public FloatInsets max(FloatInsets other) {
        return new FloatInsets(F32.max(left, other.left), F32.max(right, other.right));
    }

    /// `impl Add` — `self + rhs` field-wise
    public FloatInsets plus(FloatInsets rhs) {
        return new FloatInsets(left + rhs.left, right + rhs.right);
    }

    /// `PartialEq` -> rustEq: f32 fields compare IEEE (`==`) (GAP-004)
    public boolean rustEq(FloatInsets other) {
        return left == other.left && right == other.right;
    }
}
