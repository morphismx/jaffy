// JAFFY: port of upstream src/compute/float.rs —
// `impl Index<FloatDirection> for FloatInsets` and
// `impl IndexMut<FloatDirection> for FloatInsets`.
//
// `FloatInsets` is an immutable record, so the `IndexMut` form cannot hand back
// `&mut f32`: `set` returns the updated `FloatInsets` and callers reassign
// (GAP-018).
package net.morphismmc.jaffy.compute.float_;

import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.style.float_.FloatDirection;

/// `Index`/`IndexMut` for `FloatInsets` keyed by `FloatDirection`.
public final class FloatInsetsFloatDirectionExt {

    private FloatInsetsFloatDirectionExt() {}

    /// The inset on the side that a box floated in `direction` is floated towards
    public static float get(FloatInsets insets, FloatDirection direction) {
        return switch (direction) {
            case LEFT -> insets.left();
            case RIGHT -> insets.right();
        };
    }

    /// `index_mut` — returns a new `FloatInsets` with the `direction` side
    /// replaced by `value` (GAP-018)
    public static FloatInsets set(FloatInsets insets, FloatDirection direction, float value) {
        return switch (direction) {
            case LEFT -> new FloatInsets(value, insets.right());
            case RIGHT -> new FloatInsets(insets.left(), value);
        };
    }
}
