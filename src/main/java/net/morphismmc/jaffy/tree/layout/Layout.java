// JAFFY-UPSTREAM: src/tree/layout.rs
// JAFFY-NOTE: `Layout` struct
// `order: u32` -> `int` (GAP-014). serde impl deferred.
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.just.math.F32;

/// The final result of a layout algorithm for a single node.
///
/// @param order the relative ordering of the node. Nodes with a higher order should be rendered
///        on top of those with a lower order. This is effectively a topological sort of each tree.
/// @param location the top-left corner of the node
/// @param size the width and height of the node
/// @param scrollableOverflowRect the scrollable overflow rectangle of the node: the axis-aligned
///        rectangle containing the content of the node (the border boxes of its descendants plus
///        their non-clipped overflow), corresponding to the CSS "scrollable overflow rectangle"
///        (<https://www.w3.org/TR/css-overflow-3/#scrollable>), except that transforms are not
///        accounted for. See `ScrollableOverflow` for physical coordinates and reversed-origin
///        flags. A normal axis has range `0..scrollWidth()` (or `scrollHeight()`); a reversed
///        axis has range `-scrollWidth()..0` (or `-scrollHeight()..0`). The initial offset is
///        zero.
/// @param scrollbarSize the size of the scrollbars in each dimension. If there is no scrollbar
///        then the size will be zero.
/// @param border the size of the borders of the node
/// @param padding the size of the padding of the node
/// @param margin the size of the margin of the node
public record Layout(
        int order,
        PointF32 location,
        SizeF32 size,
        ScrollableOverflow scrollableOverflowRect,
        SizeF32 scrollbarSize,
        RectF32 border,
        RectF32 padding,
        RectF32 margin) {

    /// Creates a new zero-`Layout`.
    ///
    /// The Zero-layout has size and location set to ZERO.
    /// The `order` value of this layout is set to the minimum value of 0.
    /// This means it should be rendered below all other `Layout`s.
    public static Layout of() {
        return new Layout(
                0,
                PointF32.ZERO,
                SizeF32.ZERO,
                ScrollableOverflow.ZERO,
                SizeF32.ZERO,
                RectF32.ZERO,
                RectF32.ZERO,
                RectF32.ZERO);
    }

    /// A zero-`Layout` (`Default::default` / `Layout::new`)
    public static final Layout DEFAULT = of();

    /// Creates a new zero-`Layout` with the supplied `order` value.
    /// Nodes with a higher order should be rendered on top of those with a lower order.
    /// The Zero-layout has size and location set to ZERO.
    public static Layout withOrder(int order) {
        return new Layout(
                order,
                PointF32.ZERO,
                SizeF32.ZERO,
                ScrollableOverflow.ZERO,
                SizeF32.ZERO,
                RectF32.ZERO,
                RectF32.ZERO,
                RectF32.ZERO);
    }

    /// Get the width of the node's content box
    public float contentBoxWidth() {
        return size.width() - padding.left() - padding.right() - border.left() - border.right();
    }

    /// Get the height of the node's content box
    public float contentBoxHeight() {
        return size.height() - padding.top() - padding.bottom() - border.top() - border.bottom();
    }

    /// Get the size of the node's content box
    public SizeF32 contentBoxSize() {
        return new SizeF32(contentBoxWidth(), contentBoxHeight());
    }

    /// Get x offset of the node's content box relative to it's parent's border box
    public float contentBoxX() {
        return location.x() + border.left() + padding.left();
    }

    /// Get y offset of the node's content box relative to it's parent's border box
    public float contentBoxY() {
        return location.y() + border.top() + padding.top();
    }

    // ---- `#[cfg(feature = "content_size")]` impl block ----

    /// Return the magnitude of the reachable horizontal scroll range.
    /// This is the reachable extent of the content less the width of the padding box, floored at zero.
    public float scrollWidth() {
        if (scrollableOverflowRect.isReversed().x()) {
            return -scrollableOverflowRect.left();
        }
        return F32.max(
                0.0f,
                scrollableOverflowRect.right()
                        + F32.min(scrollbarSize.width(), size.width())
                        - size.width()
                        + border.left()
                        + border.right());
    }

    /// Return the magnitude of the reachable vertical scroll range.
    /// This is the reachable extent of the content less the height of the padding box, floored at zero.
    public float scrollHeight() {
        if (scrollableOverflowRect.isReversed().y()) {
            return -scrollableOverflowRect.top();
        }
        return F32.max(
                0.0f,
                scrollableOverflowRect.bottom()
                        + F32.min(scrollbarSize.height(), size.height())
                        - size.height()
                        + border.top()
                        + border.bottom());
    }
}
