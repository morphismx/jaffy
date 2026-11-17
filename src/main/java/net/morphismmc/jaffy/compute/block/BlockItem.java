// JAFFY: upstream=src/compute/block.rs
// JAFFY-NOTE: private `BlockItem` struct
//
// `Option<f32>` -> `@Opt float`; `Option<AlignSelf>`/reference Options ->
// `@Nullable`; `NodeId`/`u32`/`u64` -> `long`/`int` (GAP-001/006/014/017).
// `float` is a Java keyword -> `floatValue`; the Rust `float` field type
// (`style.float_.Float`) shadows `java.lang.Float` inside this file.
package net.morphismmc.jaffy.compute.block;

import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.Float;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.just.math.Opt;

import org.jspecify.annotations.Nullable;

/// Per-child data that is accumulated and modified over the course of the
/// layout algorithm.
final class BlockItem {

    /// The identifier for the associated node.
    final long nodeId;
    /// The "source order" of the item. This is the index of the item within
    /// the children iterator, and controls the order in which the nodes are
    /// placed.
    final int order;
    /// Items that are tables don't have stretch sizing applied to them.
    final boolean isTable;
    /// Compressible replaced items resolve an auto width to their intrinsic
    /// size rather than being stretch-sized.
    /// <https://www.w3.org/TR/CSS22/visudet.html#block-replaced-width>
    final boolean isCompressibleReplaced;
    /// For compressible replaced items, the cap on their min-content
    /// contribution from a percentage `width` or `max-width` resolved against
    /// zero.
    /// <https://drafts.csswg.org/css-sizing-3/#replaced-percentage-min-contribution>
    final @Opt float minContentWidthCap;
    /// Whether the child is a non-independent block or inline node.
    final boolean isInSameBfc;
    /// The `float` style of the node.
    final Float floatValue;
    /// The `clear` style of the node.
    final Clear clear;
    /// The size style of this item. Used to detect and resolve sizing keywords
    /// (`min-content`, `max-content`, `fit-content`, `fit-content(...)`, and
    /// `stretch`). In the height (block) axis only `stretch` requires
    /// resolution: the intrinsic sizing keywords are all equal to the content
    /// size, which is what an auto height already resolves to.
    final Size<Dimension> sizeStyle;
    /// The base size of this item.
    final SizeOptF32 size;
    /// The minimum allowable size of this item.
    final SizeOptF32 minSize;
    /// The maximum allowable size of this item.
    final SizeOptF32 maxSize;
    /// The overflow style of the item.
    final net.morphismmc.jaffy.geometry.Point<Overflow> overflow;
    /// The contain style of the item.
    final Contain contain;
    /// The width of the item's scrollbars (if it has scrollbars).
    final float scrollbarWidth;
    /// The position style of the item.
    final Position position;
    /// The `direction` style of the item (used to resolve
    /// `self-start`/`self-end`).
    final Direction direction;
    /// The `align-self` style of the item (used to derive the static position
    /// of out-of-flow items).
    final @Nullable AlignItems alignSelf;
    /// The `justify-self` style of the item (used to align in-flow items and
    /// to derive the static position of out-of-flow items).
    final @Nullable AlignItems justifySelf;
    /// The final offset of this item.
    final net.morphismmc.jaffy.geometry.Rect<LengthPercentageAuto> inset;
    /// The margin of this item.
    final net.morphismmc.jaffy.geometry.Rect<LengthPercentageAuto> margin;
    /// The padding of this item.
    final RectF32 padding;
    /// The border of this item.
    final RectF32 border;
    /// The sum of padding and border for this item.
    final SizeF32 paddingBorderSum;

    /// The computed border box size of this item.
    SizeF32 computedSize = SizeF32.ZERO;
    /// The computed "static position" of this item. The static position is the
    /// position taking into account padding, border, margins, and
    /// scrollbar_gutters but not inset.
    PointF32 staticPosition = PointF32.ZERO;
    /// Whether margins can be collapsed through this item.
    boolean canBeCollapsedThrough = false;
    /// Pending layout for in-flow non-floated items. Held back from
    /// `setUnroundedLayout` so the post-loop `align-content` pass in
    /// `computeInner` can shift `location.y` before commit.
    @Nullable
    Layout finalLayout = null;
    /// Out-of-flow candidates bubbled out of this (in-flow) item's subtree.
    /// Anchors are relative to the item's border box until they are translated
    /// at merge time.
    OofCandidates oofCandidates = OofCandidates.create();

    BlockItem(
            long nodeId,
            int order,
            boolean isTable,
            boolean isCompressibleReplaced,
            @Opt float minContentWidthCap,
            boolean isInSameBfc,
            Float floatValue,
            Clear clear,
            Size<Dimension> sizeStyle,
            SizeOptF32 size,
            SizeOptF32 minSize,
            SizeOptF32 maxSize,
            net.morphismmc.jaffy.geometry.Point<Overflow> overflow,
            Contain contain,
            float scrollbarWidth,
            Position position,
            Direction direction,
            @Nullable AlignItems alignSelf,
            @Nullable AlignItems justifySelf,
            net.morphismmc.jaffy.geometry.Rect<LengthPercentageAuto> inset,
            net.morphismmc.jaffy.geometry.Rect<LengthPercentageAuto> margin,
            RectF32 padding,
            RectF32 border,
            SizeF32 paddingBorderSum) {
        this.nodeId = nodeId;
        this.order = order;
        this.isTable = isTable;
        this.isCompressibleReplaced = isCompressibleReplaced;
        this.minContentWidthCap = minContentWidthCap;
        this.isInSameBfc = isInSameBfc;
        this.floatValue = floatValue;
        this.clear = clear;
        this.sizeStyle = sizeStyle;
        this.size = size;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.overflow = overflow;
        this.contain = contain;
        this.scrollbarWidth = scrollbarWidth;
        this.position = position;
        this.direction = direction;
        this.alignSelf = alignSelf;
        this.justifySelf = justifySelf;
        this.inset = inset;
        this.margin = margin;
        this.padding = padding;
        this.border = border;
        this.paddingBorderSum = paddingBorderSum;
    }
}
