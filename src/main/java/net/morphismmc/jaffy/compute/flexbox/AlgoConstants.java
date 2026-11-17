// JAFFY: upstream=src/compute/flexbox.rs
// JAFFY-NOTE: the `Constants`-style algorithm constants struct

package net.morphismmc.jaffy.compute.flexbox;

import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

final class AlgoConstants {
    /// The direction of the current segment being laid out
    FlexDirection dir;
    /// The layout direction of the current segment being laid out
    Direction layoutDirection;
    /// Is this segment a row
    boolean isRow;
    /// Is this segment a column
    boolean isColumn;
    /// Is wrapping enabled (in either direction)
    boolean isWrap;
    /// Is the wrap direction inverted
    boolean isWrapReverse;
    /// Are items balanced across lines (`flex-wrap: balance`)?
    /// (`flexbox_balance`-gated in Rust; unconditional here)
    boolean isBalance;
    /// The requested minimum number of lines (`flex-line-count`). `Some`
    /// for every multi-line container, `None` for `nowrap`.
    /// (`flexbox_balance`-gated; `Option<u16>` -> `@Opt @Unsigned int`,
    /// `OptI32.NONE`)
    @Opt
    @Unsigned
    int lineCount;

    /// The item's min_size style
    SizeOptF32 minSize;
    /// The item's max_size style
    SizeOptF32 maxSize;
    /// The border of this section
    RectF32 border;
    /// The space between the content box and the border box. This consists
    /// of padding + border + scrollbar_gutter.
    RectF32 contentBoxInset;
    /// The border and scrollbar gutters surrounding the padding box.
    RectF32 paddingBoxInset;
    /// The resolved padding used for scrollable overflow contributions.
    /// (`content_size`-gated in Rust; unconditional here)
    RectF32 padding;
    /// The size reserved for scrollbar gutters in each axis
    PointF32 scrollbarGutter;
    /// Whether the node being laid out is a scroll container
    /// (`content_size`-gated in Rust; unconditional here)
    boolean isScrollContainer;
    /// The gap of this section
    SizeF32 gap;
    /// The align_items property of this node
    AlignItems alignItems;
    /// The align_content property of this node
    AlignContent alignContent;
    /// The justify_content property of this node (`JustifyContent` ->
    /// `AlignContent`)
    AlignContent justifyContent;

    /// The border-box size of the node being laid out (if known)
    SizeOptF32 nodeOuterSize;
    /// The content-box size of the node being laid out (if known)
    SizeOptF32 nodeInnerSize;
    /// Whether the known main size of the node (if any) is definite. This
    /// is `false` when a parent imposes a main size on this node that is
    /// derived from the node's own content, in which case it is indefinite
    /// for the purposes of resolving percentage sizes of items and
    /// collecting items into flex lines.
    /// See <https://www.w3.org/TR/css-flexbox-1/#definite-sizes>.
    boolean knownMainSizeIsDefinite;
    /// Whether the node has a known main size which is definite (as of the
    /// start of layout, before the main size is determined from the node's
    /// contents)
    boolean hasDefiniteMainSize;
    /// Whether the node has a known cross size which is definite
    boolean hasDefiniteCrossSize;
    /// Whether the cross-axis space that non-stretched items are
    /// fit-content sized into is definite (either the node's cross size is
    /// definite, or the cross-axis available space is). A size resolved by
    /// fit-content sizing against definite available space is itself
    /// definite (see <https://www.w3.org/TR/css-sizing-3/#definite>).
    boolean crossAxisAvailableSpaceIsDefinite;

    /// The size of the virtual container containing the flex items.
    SizeF32 containerSize;
    /// The size of the internal container
    SizeF32 innerContainerSize;

    /// When a multi-line container requests a minimum number of lines
    /// (`flex-line-count`), definite cross-axis available space for
    /// measuring items is divided between the requested number of lines
    /// (after subtracting the cross-axis gaps between them).
    /// See <https://github.com/w3c/csswg-drafts/issues/13414>
    float dividedCrossSpace(float crossAvailableSpace) {
        if (lineCount != OptI32.NONE && lineCount > 1) {
            float lc = lineCount;
            return (crossAvailableSpace - (lc - 1.0f) * gap.cross(dir)) / lc;
        }
        return crossAvailableSpace;
    }

    /// Map main/cross reversal to physical axes, including RTL inline
    /// progression.
    PointBool scrollOriginReversed() {
        boolean main = dir.isReverse();
        boolean cross = isWrapReverse;
        boolean x = isColumn ? cross : main;
        boolean y = isColumn ? main : cross;
        return new PointBool(x ^ layoutDirection.isRtl(), y);
    }
}
