// JAFFY: port of upstream src/compute/common/scrollable_overflow.rs —
// generic CSS scrollable overflow code shared by all CSS algorithms
// (`content_size` feature).
//
// Named `ScrollableOverflow` per manifest despite colliding with
// `tree.ScrollableOverflow` — that type is referenced fully qualified below
// (Java forbids a same-simple-name single-type import).
// `pub(crate)` -> public (Java has no crate visibility; called from
// `compute.{flexbox,grid,block}`). `f32_min`/`f32_max` -> `F32.min`/`F32.max`
// (Rust NaN semantics).
package net.morphismmc.jaffy.compute.common;

import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.just.math.F32;

public final class ScrollableOverflow {
    private ScrollableOverflow() {}

    /// Container geometry shared by its children's scrollable overflow
    /// contributions.
    public record ScrollableOverflowContainer(
            /// Whether the parent clips unreachable overflow and includes end
            /// padding.
            boolean parentIsScrollContainer,
            /// The extent of the parent's padding-box frame, excluding
            /// scrollbar gutters.
            SizeF32 parentSize,
            /// The scroll origin in each physical axis.
            PointBool reversed,
            /// The parent's resolved padding.
            RectF32 padding) {}

    /// Determine the rectangle that a given node contributes to its parent's
    /// scrollable overflow rectangle.
    ///
    /// `location`, child overflow, and the result use physical axes in the
    /// parent's padding-box frame. Child overflow is relative to its inner
    /// border edge, so the child's border is added first. `parentSize` is the
    /// extent of the parent frame (excluding scrollbar gutters). The node's
    /// own `scrollableOverflowRect` propagates to the parent only where the
    /// node's overflow allows it to escape: a scroll container
    /// (`HIDDEN`/`SCROLL` in either axis) clips both axes and contributes only
    /// its border box, while for non-scroll-containers `CLIP` clips just its
    /// own axis and `VISIBLE` propagates.
    ///
    /// Boxes positioned wholly in the unreachable scrollable overflow region
    /// (entirely before the scroll origin in either axis) must be clipped by a
    /// scroll container and are excluded from its scrollable overflow region
    /// (<https://www.w3.org/TR/css-overflow-3/#scrollable>). This exclusion
    /// only applies when the parent is a scroll container: boxes with
    /// `overflow: visible` have no unreachable region of their own, so all of
    /// their content contributes.
    ///
    /// A box whose containment contains its scrollable overflow (layout or
    /// paint containment) contributes only its border box, regardless of its
    /// overflow style.
    ///
    /// End padding extends the in-flow child's margin edge, not its
    /// propagated overflow. Union with the border/overflow contribution
    /// prevents negative margins from shrinking it.
    public static RectF32 computeScrollableOverflowContribution(
            PointF32 location,
            SizeF32 size,
            RectF32 scrollableOverflowRect,
            RectF32 border,
            Point<Overflow> overflow,
            Contain contain,
            ScrollableOverflowContainer container,
            RectF32 margin) {
        boolean parentIsScrollContainer = container.parentIsScrollContainer();
        SizeF32 parentSize = container.parentSize();
        PointBool reversed = container.reversed();
        RectF32 padding = container.padding();

        scrollableOverflowRect = new RectF32(
                scrollableOverflowRect.left() + border.left(),
                scrollableOverflowRect.right() + border.left(),
                scrollableOverflowRect.top() + border.top(),
                scrollableOverflowRect.bottom() + border.top());
        boolean isScrollContainer =
                overflow.x().isScrollContainer() || overflow.y().isScrollContainer();
        boolean overflowIsContained = contain.containsScrollableOverflow();
        boolean propagatesX = !isScrollContainer && !overflowIsContained && overflow.x() == Overflow.VISIBLE;
        boolean propagatesY = !isScrollContainer && !overflowIsContained && overflow.y() == Overflow.VISIBLE;
        SizeF32 endExtent = new SizeF32(
                propagatesX ? F32.max(size.width(), scrollableOverflowRect.right()) : size.width(),
                propagatesY ? F32.max(size.height(), scrollableOverflowRect.bottom()) : size.height());
        if (endExtent.width() <= 0.0f || endExtent.height() <= 0.0f) {
            return RectF32.ZERO;
        }
        PointF32 startExtent = new PointF32(
                propagatesX ? F32.min(0.0f, scrollableOverflowRect.left()) : 0.0f,
                propagatesY ? F32.min(0.0f, scrollableOverflowRect.top()) : 0.0f);
        RectF32 contribution = new RectF32(
                location.x() + startExtent.x(),
                location.x() + endExtent.width(),
                location.y() + startExtent.y(),
                location.y() + endExtent.height());
        boolean isWhollyUnreachable =
                (reversed.x() ? contribution.left() >= parentSize.width() : contribution.right() <= 0.0f)
                        || (reversed.y() ? contribution.top() >= parentSize.height() : contribution.bottom() <= 0.0f);
        if (parentIsScrollContainer && isWhollyUnreachable) {
            return RectF32.ZERO;
        }
        if (!parentIsScrollContainer) {
            return contribution;
        }
        float left = contribution.left();
        float right = contribution.right();
        float top = contribution.top();
        float bottom = contribution.bottom();
        if (reversed.x()) {
            left = F32.min(left, location.x() - F32.max(0.0f, padding.left() + margin.left()));
        } else {
            right = F32.max(right, location.x() + size.width() + F32.max(0.0f, padding.right() + margin.right()));
        }
        if (reversed.y()) {
            top = F32.min(top, location.y() - F32.max(0.0f, padding.top() + margin.top()));
        } else {
            bottom = F32.max(bottom, location.y() + size.height() + F32.max(0.0f, padding.bottom() + margin.bottom()));
        }
        return new RectF32(left, right, top, bottom);
    }

    /// Pack an algorithm's physical accumulator and clip unreachable
    /// overflow only for scroll containers. All geometry is unpacked until
    /// this output boundary.
    public static net.morphismmc.jaffy.tree.layout.ScrollableOverflow finalizeScrollableOverflow(
            RectF32 rect, SizeF32 size, PointBool reversed, boolean isScrollContainer) {
        size = new SizeF32(F32.max(size.width(), 0.0f), F32.max(size.height(), 0.0f));
        PointBool outReversed;
        if (isScrollContainer) {
            float left = rect.left();
            float right = rect.right();
            float top = rect.top();
            float bottom = rect.bottom();
            if (reversed.x()) {
                right = F32.min(right, size.width());
            } else {
                left = 0.0f;
            }
            if (reversed.y()) {
                bottom = F32.min(bottom, size.height());
            } else {
                top = 0.0f;
            }
            rect = new RectF32(left, right, top, bottom);
            outReversed = reversed;
        } else {
            outReversed = PointBool.FALSE;
        }
        return net.morphismmc.jaffy.tree.layout.ScrollableOverflow.of(rect, outReversed);
    }
}
