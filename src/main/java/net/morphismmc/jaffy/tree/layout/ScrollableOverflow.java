// JAFFY: port of upstream src/tree/layout.rs — `ScrollableOverflow`.
// The reversal flags are packed into the sign bits of the stored right/bottom.
// serde impl deferred; `debug_assert!` omitted (release behaviour, GAP-010
// convention). `PartialEq` compares `to_bits` — `equals` uses
// `Float.floatToRawIntBits` accordingly (non-canonical NaN payloads differ).
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.RectF32;

/// Scrollable overflow and the physical directions in which scrolling starts at the end edge.
///
/// The rectangle uses physical x/y coordinates relative to the client area's top-left edge.
/// To obtain border-box coordinates, add `border.left`/`border.top`, plus the horizontal
/// scrollbar gutter in RTL (where the vertical scrollbar is on the left). RTL does not mirror
/// these coordinates. For a scroll container, unreachable overflow is clipped: on normal axes
/// the start is zero, while on reversed axes the end is at most the client extent.
/// Non-scroll containers retain all overflow, for propagation and hit testing, and have no flags.
///
/// Rectangles contain `(0, 0)`: left/top are non-positive and right/bottom non-negative.
/// Algorithms accumulate ordinary rectangles from `RectF32.ZERO` and pack them only at output.
/// The sign bits of the stored right/bottom encode reversal, including when an extent is zero.
/// Always use {@link #rect} before doing geometry with this value.
public final class ScrollableOverflow {

    /// Empty overflow with normal scroll origins.
    public static final ScrollableOverflow ZERO = new ScrollableOverflow(RectF32.ZERO);

    private final RectF32 packed;

    private ScrollableOverflow(RectF32 packed) {
        this.packed = packed;
    }

    /// Pack an origin-containing rectangle and its physical scroll-origin directions.
    public static ScrollableOverflow of(RectF32 rect, PointBool reversed) {
        // debug_assert!(rect.left <= 0 && rect.top <= 0 && rect.right >= 0 && rect.bottom >= 0)
        return new ScrollableOverflow(new RectF32(
                rect.left(),
                Math.copySign(rect.right(), reversed.x() ? -1.0f : 1.0f),
                rect.top(),
                Math.copySign(rect.bottom(), reversed.y() ? -1.0f : 1.0f)));
    }

    /// `From<(Rect<f32>, Point<bool>)>`
    public static ScrollableOverflow from(RectF32 rect, PointBool reversed) {
        return of(rect, reversed);
    }

    /// The physical overflow rectangle, with the packed signs restored.
    public RectF32 rect() {
        return new RectF32(packed.left(), Math.abs(packed.right()), packed.top(), Math.abs(packed.bottom()));
    }

    /// The physical left overflow edge.
    public float left() {
        return packed.left();
    }

    /// The physical right overflow edge, with its sign restored.
    public float right() {
        return Math.abs(packed.right());
    }

    /// The physical top overflow edge.
    public float top() {
        return packed.top();
    }

    /// The physical bottom overflow edge, with its sign restored.
    public float bottom() {
        return Math.abs(packed.bottom());
    }

    /// Whether the physical x/y scroll origins are at the right/bottom edges.
    /// `is_sign_negative()` — checks the sign bit, so `-0.0` counts as reversed.
    public PointBool isReversed() {
        return new PointBool(
                java.lang.Float.floatToRawIntBits(packed.right()) < 0,
                java.lang.Float.floatToRawIntBits(packed.bottom()) < 0);
    }

    /// Swap the physical axes, preserving origin directions and signed zeroes.
    public ScrollableOverflow transpose() {
        return new ScrollableOverflow(new RectF32(packed.top(), packed.bottom(), packed.left(), packed.right()));
    }

    /// `PartialEq` — compares `to_bits` on all four components, so sign
    /// bits of zeroes and non-canonical NaN payloads are significant.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ScrollableOverflow o)) {
            return false;
        }
        return Float.floatToRawIntBits(packed.left()) == Float.floatToRawIntBits(o.packed.left())
                && Float.floatToRawIntBits(packed.right()) == Float.floatToRawIntBits(o.packed.right())
                && Float.floatToRawIntBits(packed.top()) == Float.floatToRawIntBits(o.packed.top())
                && Float.floatToRawIntBits(packed.bottom()) == Float.floatToRawIntBits(o.packed.bottom());
    }

    @Override
    public int hashCode() {
        return Float.floatToRawIntBits(packed.left()) * 31 * 31 * 31
                + Float.floatToRawIntBits(packed.right()) * 31 * 31
                + Float.floatToRawIntBits(packed.top()) * 31
                + Float.floatToRawIntBits(packed.bottom());
    }

    @Override
    public String toString() {
        return "ScrollableOverflow(" + packed + ")";
    }
}
