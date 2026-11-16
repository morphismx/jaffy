// JAFFY: port of upstream src/compute/grid/alignment.rs.
//
// Three `pub(super)` functions + helpers map to static methods on this
// class; (f32, Line<f32>) / (Rect,f32,f32,Option) tuples -> records
// AxisAlignment/AlignAndPositionResult; `#[cfg(feature="content_size")]`
// params/statements are unconditional (Features.CONTENT_SIZE = true);
// Option<f32> -> @Opt float / LineOptF32 / RectOptF32 / SizeOptF32;
// `Option<Option<SizingKeywordResolution>>` collapses to @Nullable (the
// outer-vs-inner None cases are indistinguishable at every use site);
// `Line<Option<f32>>` inset/margin -> LineOptF32; `Rect<Option<f32>>` ->
// RectOptF32; `&mut OofCandidates` append -> OofCandidates.append;
// `unreachable!()` -> IllegalStateException (GAP-010); `f32_max` -> F32.max;
// `&mut [GridTrack]` -> List<GridTrack> (rev().enumerate() keeps the
// reversed-iterator index i, matching upstream).
package net.morphismmc.jaffy.compute.grid;

import java.util.List;
import net.morphismmc.jaffy.compute.common.ScrollableOverflow;
import net.morphismmc.jaffy.compute.common.ScrollableOverflow.ScrollableOverflowContainer;
import net.morphismmc.jaffy.compute.common.SizingKeyword;
import net.morphismmc.jaffy.compute.common.SizingKeyword.SizingKeywordResolution;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.geometry.InBothAbsAxis;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.LineOptF32;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.traits.LayoutGridContainer;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.just.math.F32;
import net.morphismmc.jaffy.util.MaybeResolve;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;


/// Alignment of tracks and final positioning of items
public final class Alignment {
    private Alignment() {}

    /// `(f32, Line<f32>)` return of `align_item_within_area`:
    /// (offset within the axis, resolved margin)
    public record AxisAlignment(float offset, LineF32 margin) {}

    /// `(Rect<f32>, f32, f32, Option<f32>)` return of `align_and_position_item`:
    /// (scrollable-overflow contribution, y without relative offset, height, first baseline)
    public record AlignAndPositionResult(
            RectF32 contribution,
            float yWithoutRelativeOffset,
            float height,
            @Opt float baseline) {}

    /// Align the grid tracks within the grid according to the align-content (rows) or
    /// justify-content (columns) property. This only does anything if the size of the
    /// grid is not equal to the size of the grid container in the axis being aligned.
    public static void alignTracks(
            float gridContainerContentBoxSize,
            LineF32 padding,
            LineF32 border,
            List<GridTrack> tracks,
            AlignContent trackAlignmentStyle,
            boolean axisIsReversed) {
        float usedSize = 0f;
        for (GridTrack track : tracks) {
            usedSize += track.baseSize;
        }
        float freeSpace = gridContainerContentBoxSize - usedSize;
        float origin = padding.start() + border.start();

        // Count the number of non-collapsed tracks (not counting gutters)
        int numTracks = 0;
        for (int i = 1; i < tracks.size(); i += 2) {
            if (!tracks.get(i).isCollapsed) {
                numTracks++;
            }
        }

        // Grid layout treats gaps as full tracks rather than applying them at alignment so we
        // simply pass zero here. Grid layout is never reversed.
        float gap = 0.0f;
        boolean layoutIsReversed = false;
        AlignContentKeyword trackAlignment =
                net.morphismmc.jaffy.compute.common.Alignment.applyAlignmentFallback(
                        freeSpace, numTracks, trackAlignmentStyle);
        trackAlignment = axisIsReversed ? trackAlignment.reversed() : trackAlignment;

        // If every track is collapsed then no track receives the alignment offset below, but the
        // grid's lines should still be aligned within the container (e.g. at the inline-start edge
        // for RTL), so apply the offset to the origin instead.
        float emptyGridOffset;
        if (numTracks == 0) {
            emptyGridOffset = net.morphismmc.jaffy.compute.common.Alignment.computeAlignmentOffset(
                    freeSpace, numTracks, gap, trackAlignment, layoutIsReversed, true);
        } else {
            emptyGridOffset = 0.0f;
        }

        // Compute offsets. Tracks are stored in logical order; when the axis is reversed (RTL)
        // physical offsets are assigned right-to-left by iterating the tracks in reverse.
        float[] totalOffset = {origin + emptyGridOffset};
        boolean[] seenNonCollapsedTrack = {false};
        int n = tracks.size();
        for (int i = 0; i < n; i++) {
            GridTrack track = tracks.get(axisIsReversed ? n - 1 - i : i);
            // Odd tracks are gutters (but slices are zero-indexed, so odd tracks have even indices)
            boolean isGutter = i % 2 == 0;
            boolean isNonCollapsedTrack = !isGutter && !track.isCollapsed;

            // Alignment offsets should be applied only to non-collapsed tracks.
            boolean isFirst = isNonCollapsedTrack && !seenNonCollapsedTrack[0];

            float offset = isNonCollapsedTrack
                    ? net.morphismmc.jaffy.compute.common.Alignment.computeAlignmentOffset(
                            freeSpace, numTracks, gap, trackAlignment, layoutIsReversed, isFirst)
                    : 0.0f;

            track.offset = totalOffset[0] + offset;
            totalOffset[0] = totalOffset[0] + offset + track.baseSize;
            if (isNonCollapsedTrack) {
                seenNonCollapsedTrack[0] = true;
            }
        }
    }

    /// Align and size a grid item into it's final position
    // (Rust #[allow(clippy::too_many_arguments)] — signature kept verbatim)
    public static AlignAndPositionResult alignAndPositionItem(
            LayoutGridContainer tree,
            long node,
            int order,
            RectF32 gridArea,
            // Percentage basis for the item's styles. Grid passes the grid area size; grid lanes (css-grid-3) items
            // have the container's content box as their containing block in the stacking axis.
            SizeF32 containingBlockSize,
            InBothAbsAxis<AlignItems> containerAlignmentStyles,
            float baselineShim,
            Direction direction,
            RectF32 paddingBoxInset,
            ScrollableOverflowContainer overflowContainer,
            OofCandidates bubbledCandidates) {
        SizeF32 gridAreaSize =
                new SizeF32(gridArea.right() - gridArea.left(), gridArea.bottom() - gridArea.top());

        Style style = tree.getGridChildStyle(node);

        Point<Overflow> overflow = style.overflow();
        Contain contain = style.contain();
        float scrollbarWidth = style.scrollbarWidth();
        float aspectRatio = style.aspectRatio();
        boolean isReplaced = style.isReplaced();
        // Fall back to the container's justify-items/align-items if unset (`None`), and then resolve
        // writing-mode-relative self-start/self-end keywords against the item's own direction.
        // The horizontal axis is the inline axis (Taffy only supports horizontal-tb); the vertical
        // (block) axis resolves them to plain start/end.
        Direction itemDirection = style.direction();
        AlignItems styleJustifySelf = style.justifySelf();
        AlignItems justifySelf = (styleJustifySelf != null ? styleJustifySelf
                        : containerAlignmentStyles.horizontal())
                .resolveSelfRelative(itemDirection, direction, true);
        AlignItems styleAlignSelf = style.alignSelf();
        AlignItems alignSelf = (styleAlignSelf != null ? styleAlignSelf
                        : containerAlignmentStyles.vertical())
                .resolveSelfRelative(itemDirection, direction, false);

        Position position = style.position();
        LineOptF32 insetHorizontal = style.inset()
                .horizontalComponents()
                .mapO(size -> size.resolveToOption(containingBlockSize.width(), tree::calc));
        LineOptF32 insetVertical = style.inset()
                .verticalComponents()
                .mapO(size -> size.resolveToOption(containingBlockSize.height(), tree::calc));
        RectF32 padding = style.padding()
                .mapF(p -> p.resolveOrZero(containingBlockSize.width(), tree::calc));
        RectF32 border = style.border()
                .mapF(p -> p.resolveOrZero(containingBlockSize.width(), tree::calc));
        SizeF32 paddingBorderSize = padding.plus(border).sumAxes();

        SizeF32 boxSizingAdjustment =
                style.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSize : SizeF32.ZERO;

        Size<Dimension> sizeStyle = style.size();
        SizeOptF32 inherentSize = MaybeResolve.SizeDimensionExt
                .maybeResolveF(sizeStyle, containingBlockSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 minSize = MaybeResolve.SizeLengthPercentageAutoExt
                .maybeResolveF(style.minSize(), containingBlockSize, tree::calc)
                .maybeAddF(boxSizingAdjustment)
                .or(new SizeOptF32(paddingBorderSize.width(), paddingBorderSize.height()))
                .maybeMaxF(paddingBorderSize)
                .maybeApplyAspectRatio(aspectRatio);
        SizeOptF32 maxSize = MaybeResolve.SizeLengthPercentageAutoExt
                .maybeResolveF(style.maxSize(), containingBlockSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);

        // Resolve `normal` alignment (the default if alignment is set on neither the parent or the node itself)
        // Note: if the child has a preferred aspect ratio but neither width or height are set, then the width is
        // stretched and the then height is calculated from the width according the aspect ratio
        // Replaced elements are not stretched: they are sized as for `start`.
        // See: https://www.w3.org/TR/css-grid-1/#grid-item-sizing
        InBothAbsAxis<AlignItems> alignmentStyles = new InBothAbsAxis<>(
                switch (justifySelf.keyword()) {
                    case NORMAL -> (OptF32.isSome(inherentSize.width())
                                    || sizeStyle.width().isSizingKeyword()
                                    || isReplaced)
                            ? AlignItems.START
                            : AlignItems.STRETCH;
                    default -> justifySelf;
                },
                switch (alignSelf.keyword()) {
                    case NORMAL -> (OptF32.isSome(inherentSize.height())
                                    || sizeStyle.height().isSizingKeyword()
                                    || OptF32.isSome(aspectRatio)
                                    || isReplaced)
                            ? AlignItems.START
                            : AlignItems.STRETCH;
                    default -> alignSelf;
                });

        // Note: This is not a bug. It is part of the CSS spec that both horizontal and vertical margins
        // resolve against the WIDTH of the grid area.
        net.morphismmc.jaffy.geometry.RectOptF32 margin = style.margin()
                .mapO(m -> m.resolveToOption(containingBlockSize.width(), tree::calc));

        // drop(style) — no-op (Java GC)

        SizeF32 gridAreaMinusItemMarginsSize = new SizeF32(
                F32.max(
                        F32.maybeSubO(F32.maybeSubO(gridAreaSize.width(), margin.left()), margin.right()),
                        0.0f),
                F32.max(
                        F32.maybeSubO(F32.maybeSubO(gridAreaSize.height(), margin.top()), margin.bottom())
                                - baselineShim,
                        0.0f));

        // A size that is a sizing keyword (min-content, max-content, fit-content,
        // fit-content(...), stretch) either resolves to an exact size or is resolved
        // by measuring the item under the corresponding available space constraint
        SizingKeywordResolution keywordWidth = OptF32.isNone(inherentSize.width())
                ? SizingKeyword.resolveSizingKeyword(
                        sizeStyle.width(), gridAreaMinusItemMarginsSize.width(),
                        containingBlockSize.width(), tree::calc)
                : null;
        SizingKeywordResolution keywordHeight = OptF32.isNone(inherentSize.height())
                ? SizingKeyword.resolveSizingKeyword(
                        sizeStyle.height(), gridAreaMinusItemMarginsSize.height(),
                        containingBlockSize.height(), tree::calc)
                : null;

        // If both axes need to be measured then resolve them with a single measure call
        SizeOptF32 keywordMeasuredSize;
        if (keywordWidth instanceof SizingKeywordResolution.Measure mw
                && keywordHeight instanceof SizingKeywordResolution.Measure mh
                && !position.isOutOfFlow()) {
            SizeF32 measured = tree.measureChildSizeBoth(
                    node, SizeOptF32.NONE,
                    new SizeOptF32(containingBlockSize.width(), containingBlockSize.height()),
                    new Size<>(mw.availableSpace(), mh.availableSpace()),
                    SizingMode.INHERENT_SIZE, LineBool.FALSE);
            keywordMeasuredSize = new SizeOptF32(measured.width(), measured.height());
        } else {
            keywordMeasuredSize = SizeOptF32.NONE;
        }

        // If node is absolutely positioned and width is not set explicitly, then deduce it
        // from left, right and container_content_box if both are set.
        float width = inherentSize.width();
        if (OptF32.isNone(width)) {
            width = OptF32.NONE;
            // Apply width derived from both the left and right properties of an absolutely
            // positioned element being set
            if (position.isOutOfFlow()) {
                if (OptF32.isSome(insetHorizontal.start()) && OptF32.isSome(insetHorizontal.end())) {
                    width = F32.max(
                            gridAreaMinusItemMarginsSize.width()
                                    - insetHorizontal.start() - insetHorizontal.end(),
                            0.0f);
                }
            }

            if (OptF32.isNone(width) && keywordWidth != null) {
                if (keywordWidth instanceof SizingKeywordResolution.Exact e) {
                    width = e.value();
                } else if (keywordWidth instanceof SizingKeywordResolution.Measure m) {
                    float availableWidth = ((AvailableSpace.Definite) m.availableSpace()).value();
                    width = OptF32.isSome(keywordMeasuredSize.width())
                            ? keywordMeasuredSize.width()
                            : tree.measureChildSize(
                                    node, SizeOptF32.NONE,
                                    new SizeOptF32(containingBlockSize.width(), containingBlockSize.height()),
                                    new Size<>(
                                            m.availableSpace(),
                                            new AvailableSpace.Definite(
                                                    gridAreaMinusItemMarginsSize.height())),
                                    SizingMode.INHERENT_SIZE, AbsoluteAxis.HORIZONTAL, LineBool.FALSE);
                }
            }

            // Apply width based on stretch alignment if:
            //  - Alignment style is "stretch"
            //  - The node is not absolutely positioned
            //  - The node does not have auto margins in this axis.
            if (OptF32.isNone(width)
                    && OptF32.isSome(margin.left())
                    && OptF32.isSome(margin.right())
                    && alignmentStyles.horizontal() == AlignItems.STRETCH
                    && !position.isOutOfFlow()) {
                width = gridAreaMinusItemMarginsSize.width();
            }
        }

        // Reapply aspect ratio after stretch and absolute position width adjustments
        SizeOptF32 wh = new SizeOptF32(width, inherentSize.height()).maybeApplyAspectRatio(aspectRatio);
        width = wh.width();
        float height = wh.height();

        if (OptF32.isNone(height)) {
            if (position.isOutOfFlow()) {
                if (OptF32.isSome(insetVertical.start()) && OptF32.isSome(insetVertical.end())) {
                    height = F32.max(
                            gridAreaMinusItemMarginsSize.height()
                                    - insetVertical.start() - insetVertical.end(),
                            0.0f);
                }
            }

            if (OptF32.isNone(height) && keywordHeight != null) {
                if (keywordHeight instanceof SizingKeywordResolution.Exact e) {
                    height = e.value();
                } else if (keywordHeight instanceof SizingKeywordResolution.Measure m) {
                    float w = width;
                    height = OptF32.isSome(keywordMeasuredSize.height())
                            ? keywordMeasuredSize.height()
                            : tree.measureChildSize(
                                    node, new SizeOptF32(w, OptF32.NONE),
                                    new SizeOptF32(containingBlockSize.width(), containingBlockSize.height()),
                                    new Size<>(
                                            OptF32.isSome(w)
                                                    ? new AvailableSpace.Definite(w)
                                                    : new AvailableSpace.Definite(
                                                            gridAreaMinusItemMarginsSize.width()),
                                            m.availableSpace()),
                                    SizingMode.INHERENT_SIZE, AbsoluteAxis.VERTICAL, LineBool.FALSE);
                }
            }

            // Apply height based on stretch alignment if:
            //  - Alignment style is "stretch"
            //  - The node is not absolutely positioned
            //  - The node does not have auto margins in this axis.
            if (OptF32.isNone(height)
                    && OptF32.isSome(margin.top())
                    && OptF32.isSome(margin.bottom())
                    && alignmentStyles.vertical() == AlignItems.STRETCH
                    && !position.isOutOfFlow()) {
                height = gridAreaMinusItemMarginsSize.height();
            }
        }
        // Reapply aspect ratio after stretch and absolute position height adjustments
        wh = new SizeOptF32(width, height).maybeApplyAspectRatio(aspectRatio);
        width = wh.width();
        height = wh.height();

        // Clamp size by min and max width/height
        wh = new SizeOptF32(width, height).maybeClampO(minSize, maxSize);
        width = wh.width();
        height = wh.height();

        // Layout node
        SizeOptF32 size;
        if (position.isOutOfFlow() && (OptF32.isNone(width) || OptF32.isNone(height))) {
            SizeF32 measured = tree.measureChildSizeBoth(
                    node, new SizeOptF32(width, height),
                    new SizeOptF32(containingBlockSize.width(), containingBlockSize.height()),
                    new Size<>(
                            new AvailableSpace.Definite(gridAreaMinusItemMarginsSize.width()),
                            new AvailableSpace.Definite(gridAreaMinusItemMarginsSize.height())),
                    SizingMode.INHERENT_SIZE, LineBool.FALSE);
            size = new SizeOptF32(measured.width(), measured.height());
        } else {
            size = new SizeOptF32(width, height);
        }

        LayoutOutput layoutOutput = tree.performChildLayout(
                node, size,
                new SizeOptF32(containingBlockSize.width(), containingBlockSize.height()),
                new Size<>(
                        new AvailableSpace.Definite(gridAreaMinusItemMarginsSize.width()),
                        new AvailableSpace.Definite(gridAreaMinusItemMarginsSize.height())),
                SizingMode.INHERENT_SIZE, LineBool.FALSE);

        // Resolve final size (`size.unwrap_or(layout_output.size)` yields Size<f32>)
        SizeF32 finalSize = size.unwrapOr(layoutOutput.size()).maybeClampO(minSize, maxSize);
        width = finalSize.width();
        height = finalSize.height();

        AxisAlignment xAlignment = alignItemWithinArea(
                new LineF32(gridArea.left(), gridArea.right()),
                alignmentStyles.horizontal(), width, position, insetHorizontal,
                margin.horizontalComponents(), 0.0f, direction);
        AxisAlignment yAlignment = alignItemWithinArea(
                new LineF32(gridArea.top(), gridArea.bottom()),
                alignmentStyles.vertical(), height, position, insetVertical,
                margin.verticalComponents(), baselineShim, Direction.LTR);
        float x = xAlignment.offset();
        float yWithoutRelativeOffset = yAlignment.offset();
        // A relative offset moves the item, but does not affect the container's baseline (which is computed from the
        // returned y position, without the offset)
        x += relativeOffset(position, insetHorizontal, direction);
        float y = yWithoutRelativeOffset + relativeOffset(position, insetVertical, Direction.LTR);

        SizeF32 scrollbarSize = new SizeF32(
                overflow.y() == Overflow.SCROLL ? scrollbarWidth : 0.0f,
                overflow.x() == Overflow.SCROLL ? scrollbarWidth : 0.0f);

        RectF32 resolvedMargin = new RectF32(
                xAlignment.margin().start(), xAlignment.margin().end(),
                yAlignment.margin().start(), yAlignment.margin().end());

        // Bubble out-of-flow candidates from the item's subtree, translating anchors from
        // item-relative to container-relative coordinates
        OofCandidates itemCandidates = layoutOutput.oofCandidates().take();
        if (!itemCandidates.isEmpty()) {
            itemCandidates.translate(new PointF32(x, y));
            bubbledCandidates.append(itemCandidates);
        }

        tree.setUnroundedLayout(
                node,
                new Layout(
                        order, new PointF32(x, y), new SizeF32(width, height),
                        layoutOutput.scrollableOverflowRect(), scrollbarSize,
                        border, padding, resolvedMargin));

        RectF32 contribution = ScrollableOverflow.computeScrollableOverflowContribution(
                new PointF32(x - paddingBoxInset.left(), y - paddingBoxInset.top()),
                new SizeF32(width, height),
                layoutOutput.scrollableOverflowRect().rect(),
                border, overflow, contain, overflowContainer, resolvedMargin);

        float baseline =
                net.morphismmc.jaffy.compute.common.Alignment.childFirstBaseline(
                        layoutOutput.baselines().first(), height, overflow.y());
        return new AlignAndPositionResult(contribution, yWithoutRelativeOffset, height, baseline);
    }

    /// Align and size a grid item along a single axis
    public static AxisAlignment alignItemWithinArea(
            LineF32 gridArea,
            AlignItems alignmentStyle,
            float resolvedSize,
            Position position,
            LineOptF32 inset,
            LineOptF32 margin,
            float baselineShim,
            Direction direction) {
        // Calculate grid area dimension in the axis
        LineF32 nonAutoMargin = new LineF32(
                OptF32.unwrapOr(margin.start(), 0.0f) + baselineShim,
                OptF32.unwrapOr(margin.end(), 0.0f));
        float gridAreaSize = F32.max(gridArea.end() - gridArea.start(), 0.0f);
        float freeSpace = F32.max(gridAreaSize - resolvedSize - nonAutoMargin.sum(), 0.0f);

        // Expand auto margins to fill available space
        int autoMarginCount = (OptF32.isNone(margin.start()) ? 1 : 0) + (OptF32.isNone(margin.end()) ? 1 : 0);
        float autoMarginSize = autoMarginCount > 0 ? freeSpace / autoMarginCount : 0.0f;
        LineF32 resolvedMargin = new LineF32(
                OptF32.unwrapOr(margin.start(), autoMarginSize) + baselineShim,
                OptF32.unwrapOr(margin.end(), autoMarginSize));

        boolean overflows = resolvedSize + nonAutoMargin.sum() > gridAreaSize;
        AlignItemsKeyword alignmentKeyword =
                net.morphismmc.jaffy.compute.common.Alignment.resolveSelfAlignmentSafety(
                        alignmentStyle, overflows);

        // Compute offset in the axis
        float alignmentBasedOffset = switch (alignmentKeyword) {
            // TODO: Add support for baseline alignment. For now we treat it as "start".
            case START, FLEX_START, BASELINE, STRETCH -> direction.isRtl()
                    ? gridAreaSize - resolvedSize - resolvedMargin.end()
                    : resolvedMargin.start();
            case END, FLEX_END -> direction.isRtl()
                    ? resolvedMargin.start()
                    : gridAreaSize - resolvedSize - resolvedMargin.end();
            case CENTER -> (gridAreaSize - resolvedSize + resolvedMargin.start() - resolvedMargin.end()) / 2.0f;
            // Normal is resolved, and SelfStart/SelfEnd are resolved to Start/End against
            // the item's own direction, in `align_and_position_item`.
            case NORMAL, SELF_START, SELF_END ->
                throw new IllegalStateException("internal error: entered unreachable code");
        };

        float offsetWithinArea;
        if (position.isOutOfFlow()) {
            boolean hasStart = OptF32.isSome(inset.start());
            boolean hasEnd = OptF32.isSome(inset.end());
            if (hasStart && hasEnd) {
                offsetWithinArea = direction.isRtl()
                        ? gridAreaSize - inset.end() - resolvedSize - nonAutoMargin.end()
                        : inset.start() + nonAutoMargin.start();
            } else if (hasStart) {
                offsetWithinArea = inset.start() + nonAutoMargin.start();
            } else if (hasEnd) {
                offsetWithinArea = gridAreaSize - inset.end() - resolvedSize - nonAutoMargin.end();
            } else {
                offsetWithinArea = alignmentBasedOffset;
            }
        } else {
            offsetWithinArea = alignmentBasedOffset;
        }

        return new AxisAlignment(gridArea.start() + offsetWithinArea, resolvedMargin);
    }

    /// The offset applied to an item in a single axis by its `inset` if it is `position: relative`
    /// (module-private upstream; package-private so the behavior test can reach it)
    static float relativeOffset(Position position, LineOptF32 inset, Direction direction) {
        if (position != Position.RELATIVE) {
            return 0.0f;
        }
        float relativeInset = direction.isRtl()
                ? OptF32.or(OptF32.map(inset.end(), pos -> -pos), inset.start())
                : OptF32.or(inset.start(), OptF32.map(inset.end(), pos -> -pos));
        return OptF32.unwrapOr(relativeInset, 0.0f);
    }
}
