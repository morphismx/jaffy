package net.morphismmc.jaffy.compute.flexbox;

import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.RectBool;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.just.math.Opt;

final class FlexItem {
    /// The identifier for the associated node
    long node;

    /// The order of the node relative to it's siblings
    int order;

    /// The base size of this item
    SizeOptF32 size;
    /// The raw size style of this item. Used to detect and resolve sizing
    /// keywords (`min-content`, `max-content`, `fit-content`,
    /// `fit-content(...)`, and `stretch`)
    Size<Dimension> sizeStyle;
    /// The minimum allowable size of this item
    SizeOptF32 minSize;
    /// The maximum allowable size of this item
    SizeOptF32 maxSize;
    /// The aspect ratio of this item
    @Opt
    float aspectRatio;
    /// The cross-alignment of this item
    AlignItems alignSelf;

    /// The overflow style of the item
    Point<Overflow> overflow;
    /// The contain style of the item
    Contain contain;
    /// The width of the scrollbars (if it has any)
    float scrollbarWidth;
    /// The flex shrink style of the item
    float flexShrink;
    /// The flex grow style of the item
    float flexGrow;
    /// Whether the item's used flex basis is definite (rather than derived
    /// from the item's content)
    boolean flexBasisIsDefinite;

    /// The minimum size of the item. This differs from min_size above
    /// because it also takes into account content based automatic minimum
    /// sizes
    float resolvedMinimumMainSize;

    /// The `position: relative` offset of this item, resolved to
    /// (width, height) in the container's coordinate space (zero for
    /// non-relative items)
    SizeF32 relativeInset;
    /// The margin of this item
    RectF32 margin;
    /// Whether each margin is an auto margin or not (`Rect<bool>` ->
    /// `RectBool` primitive specialization, SPECIALIZATIONS.md)
    RectBool marginIsAuto;
    /// The padding of this item
    RectF32 padding;
    /// The border of this item
    RectF32 border;

    /// The default size of this item
    float flexBasis;
    /// The default size of this item, minus padding and border
    float innerFlexBasis;
    /// The amount by which this item has deviated from its target size
    float violation;
    /// Is the size of this item locked
    boolean frozen;

    /// Either the max- or min- content flex fraction
    /// See https://www.w3.org/TR/css-flexbox-1/#intrinsic-main-sizes
    float contentFlexFraction;

    /// The proposed inner size of this item
    SizeF32 hypotheticalInnerSize;
    /// The proposed outer size of this item
    SizeF32 hypotheticalOuterSize;
    /// The size that this item wants to be
    SizeF32 targetSize;
    /// The size that this item wants to be, plus any padding and border
    SizeF32 outerTargetSize;

    /// The position of the bottom edge of this item
    float baseline;

    /// A temporary value for the main offset
    ///
    /// Offset is the relative position from the item's natural flow
    /// position based on relative position values, alignment, and
    /// justification. Does not include margin/padding/border.
    float offsetMain;
    /// A temporary value for the cross offset
    ///
    /// Offset is the relative position from the item's natural flow
    /// position based on relative position values, alignment, and
    /// justification. Does not include margin/padding/border.
    float offsetCross;

    /// Out-of-flow candidates bubbled out of this item's subtree. Anchors
    /// are relative to the container's border box (translated when the
    /// item's final layout is computed).
    OofCandidates oofCandidates;

    /// The size that the item's size styles exclude: padding + border for
    /// `box-sizing: content-box`, otherwise zero
    SizeF32 boxSizingAdjustment(Style style) {
        if (style.boxSizing() == BoxSizing.CONTENT_BOX) {
            return padding.plus(border).sumAxes();
        }
        return SizeF32.ZERO;
    }

    /// Returns true if the item is a
    /// <https://www.w3.org/TR/css-overflow-3/#scroll-container>
    boolean isScrollContainer() {
        return overflow.x().isScrollContainer() | overflow.y().isScrollContainer();
    }

    /// Whether the used cross size of the item is the cross size of its
    /// flex line: it has a cross size of `stretch`, or of `auto` with
    /// `align-self: stretch`, and neither of its cross-axis margins is
    /// `auto`.
    boolean isStretched(FlexDirection dir) {
        Dimension crossSize = sizeStyle.cross(dir);
        return !marginIsAuto.crossStart(dir)
                && !marginIsAuto.crossEnd(dir)
                && (crossSize.isStretch() || (alignSelf.equals(AlignItems.STRETCH) && crossSize.isAuto()));
    }

    /// Returns true if the item participates in baseline alignment: it has
    /// `align-self: baseline` and neither of its cross-axis margins are
    /// `auto`. See <https://www.w3.org/TR/css-flexbox-1/#baseline-participation>
    boolean participatesInBaselineAlignment(FlexDirection dir) {
        return alignSelf.equals(AlignItems.BASELINE) && !marginIsAuto.crossStart(dir) && !marginIsAuto.crossEnd(dir);
    }
}
