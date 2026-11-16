// JAFFY: port of upstream src/compute/flexbox.rs — the flexbox layout
// algorithm (spec: <https://www.w3.org/TR/css-flexbox-1/>).
//
// Upstream `#[cfg(feature = "flexbox_balance")]` items (`is_balance`,
// `line_count`, `mod balance`) are unconditional: JAFFY builds with every
// feature enabled (see Features). `u32`/`u16`/`usize` -> `int` (GAP-014).
// `&'a mut [FlexItem]` -> `List<FlexItem>` (`subList` views).
package net.morphismmc.jaffy.compute.flexbox;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.LongIterator;
import org.jspecify.annotations.Nullable;

import net.morphismmc.jaffy.compute.common.ScrollableOverflow.ScrollableOverflowContainer;
import net.morphismmc.jaffy.compute.common.SizingKeyword;

import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectBool;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.RectOptF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.BoxGenerationMode;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.StyleHelpers;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.available_space.SizeAvailableSpaceExt;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.AxisStaticAlign;
import net.morphismmc.jaffy.tree.layout.Baselines;
import net.morphismmc.jaffy.tree.layout.AxisStaticEdge;
import net.morphismmc.jaffy.tree.layout.AxisStaticPosition;
import net.morphismmc.jaffy.tree.layout.OofCandidate;
import net.morphismmc.jaffy.tree.layout.OofPositioningArea;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutFlexboxContainer;
import net.morphismmc.just.math.F32;
import net.morphismmc.jaffy.util.MaybeResolve;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.just.math.Unsigned;
import net.morphismmc.jaffy.util.Sys;

/// Computes the
/// [flexbox](https://css-tricks.com/snippets/css/a-guide-to-flexbox/) layout
/// algorithm on `TaffyTree` according to the
/// [spec](https://www.w3.org/TR/css-flexbox-1/)
public final class Flexbox {
    private Flexbox() {}

    /// Resolve the `normal` alignment keyword, which behaves as `stretch` for
    /// flex items. <https://www.w3.org/TR/css-align-3/#align-flex>
    private static AlignItems resolveNormalAlignment(AlignItems alignment) {
        return alignment.keyword() == AlignItemsKeyword.NORMAL ? AlignItems.STRETCH : alignment;
    }

    /// Compute constants that can be reused during the flexbox algorithm.
    private static AlgoConstants computeConstants(
            LayoutFlexboxContainer tree,
            Style style,
            SizeOptF32 knownDimensions,
            SizeBool knownDimensionsAreDefinite,
            SizeOptF32 parentSize,
            Size<AvailableSpace> availableSpace) {
        FlexDirection dir = style.flexDirection();
        boolean isRow = dir.isRow();
        boolean isColumn = dir.isColumn();
        FlexWrap flexWrap = style.flexWrap();
        boolean isWrap = flexWrap.isMultiLine();
        boolean isWrapReverse = flexWrap.isReverse();
        boolean isBalance = flexWrap.isBalance();
        @Opt @Unsigned int lineCount = isWrap ? Integer.max(style.flexLineCount(), 1) : OptI32.NONE;

        float aspectRatio = style.aspectRatio();
        RectF32 padding =
                MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(
                        style.padding(), parentSize.width(), tree::calc);
        RectF32 border =
                MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(
                        style.border(), parentSize.width(), tree::calc);
        SizeF32 paddingBorderSum = padding.sumAxes().plus(border.sumAxes());
        SizeF32 boxSizingAdjustment =
                style.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSum : SizeF32.ZERO;

        // `normal` behaves as `stretch` for flex items. This is resolved once
        // here so that items with an `align_self` of `None`, which defer to
        // this value, are also resolved.
        AlignItems alignItems = resolveNormalAlignment(style.alignItems());
        // `normal` behaves as `stretch` for flex containers. In the main axis
        // `stretch` behaves as `flex-start`, so a `normal` justify-content is
        // resolved straight to `flex-start`.
        // <https://www.w3.org/TR/css-align-3/#distribution-flex>
        AlignContent alignContent =
                style.alignContent().keyword() == AlignContentKeyword.NORMAL
                        ? AlignContent.STRETCH
                        : style.alignContent();
        AlignContent justifyContent =
                style.justifyContent().keyword() == AlignContentKeyword.NORMAL
                        ? AlignContent.FLEX_START
                        : style.justifyContent();
        Direction layoutDirection = style.direction();

        // Scrollbar gutters are reserved when the `overflow` property is set
        // to `Overflow::Scroll`. However, the axis are switched (transposed)
        // because a node that scrolls vertically needs *horizontal* space to
        // be reserved for a scrollbar
        float scrollbarWidth = style.scrollbarWidth();
        Point<Overflow> transposedOverflow = style.overflow().transpose();
        PointF32 scrollbarGutter =
                new PointF32(
                        transposedOverflow.x() == Overflow.SCROLL ? scrollbarWidth : 0.0f,
                        transposedOverflow.y() == Overflow.SCROLL ? scrollbarWidth : 0.0f);
        boolean isScrollContainer =
                style.overflow().x().isScrollContainer() || style.overflow().y().isScrollContainer();
        boolean rtl = layoutDirection.isRtl();
        RectF32 contentBoxInset =
                new RectF32(
                        padding.left() + border.left() + (rtl ? scrollbarGutter.x() : 0.0f),
                        padding.right() + border.right() + (rtl ? 0.0f : scrollbarGutter.x()),
                        padding.top() + border.top(),
                        padding.bottom() + border.bottom() + scrollbarGutter.y());

        RectF32 paddingBoxInset =
                border.plus(
                        new RectF32(
                                rtl ? scrollbarGutter.x() : 0.0f,
                                rtl ? 0.0f : scrollbarGutter.x(),
                                0.0f,
                                scrollbarGutter.y()));
        RectF32 resolvedPadding =
                new RectF32(
                        contentBoxInset.left() - border.left() - (rtl ? scrollbarGutter.x() : 0.0f),
                        contentBoxInset.right() - border.right() - (rtl ? 0.0f : scrollbarGutter.x()),
                        contentBoxInset.top() - border.top(),
                        contentBoxInset.bottom() - border.bottom() - scrollbarGutter.y());

        SizeOptF32 nodeOuterSize = knownDimensions;
        SizeOptF32 nodeInnerSize = nodeOuterSize.maybeSubF(contentBoxInset.sumAxes());
        boolean knownMainSizeIsDefinite = knownDimensionsAreDefinite.main(dir);
        boolean hasDefiniteMainSize =
                knownMainSizeIsDefinite && OptF32.isSome(knownDimensions.main(dir));
        boolean hasDefiniteCrossSize =
                knownDimensionsAreDefinite.cross(dir) && OptF32.isSome(knownDimensions.cross(dir));
        boolean crossAxisAvailableSpaceIsDefinite =
                hasDefiniteCrossSize || availableSpace.cross(dir) instanceof AvailableSpace.Definite;
        SizeF32 gap =
                MaybeResolve.SizeLengthPercentageExt.resolveOrZeroO(
                        style.gap(), nodeInnerSize.or(new SizeOptF32(0.0f, 0.0f)), tree::calc);

        AlgoConstants constants = new AlgoConstants();
        constants.dir = dir;
        constants.layoutDirection = layoutDirection;
        constants.isRow = isRow;
        constants.isColumn = isColumn;
        constants.isWrap = isWrap;
        constants.isWrapReverse = isWrapReverse;
        constants.isBalance = isBalance;
        constants.lineCount = lineCount;
        constants.minSize =
                MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                                style.minSize(), parentSize, tree::calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
        constants.maxSize =
                MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                                style.maxSize(), parentSize, tree::calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
        constants.border = border;
        constants.gap = gap;
        constants.contentBoxInset = contentBoxInset;
        constants.paddingBoxInset = paddingBoxInset;
        constants.padding = resolvedPadding;
        constants.scrollbarGutter = scrollbarGutter;
        constants.isScrollContainer = isScrollContainer;
        constants.alignItems = alignItems;
        constants.alignContent = alignContent;
        constants.justifyContent = justifyContent;
        constants.nodeOuterSize = nodeOuterSize;
        constants.nodeInnerSize = nodeInnerSize;
        constants.knownMainSizeIsDefinite = knownMainSizeIsDefinite;
        constants.hasDefiniteMainSize = hasDefiniteMainSize;
        constants.hasDefiniteCrossSize = hasDefiniteCrossSize;
        constants.crossAxisAvailableSpaceIsDefinite = crossAxisAvailableSpaceIsDefinite;
        constants.containerSize = SizeF32.ZERO;
        constants.innerContainerSize = SizeF32.ZERO;
        return constants;
    }

    /// Resolve a `position: relative` item's inset to a single offset in the
    /// container's flex axes, accounting for flex direction and writing
    /// direction.
    private static SizeF32 resolveRelativeInset(RectOptF32 inset, AlgoConstants constants) {
        FlexDirection direction = constants.dir;
        boolean isRtlRow = direction.isRow() && constants.layoutDirection.isRtl();
        boolean isRtlColumn = direction.isColumn() && constants.layoutDirection.isRtl();
        float mainStart = inset.mainStart(direction);
        float mainEnd = inset.mainEnd(direction);
        float crossStart = inset.crossStart(direction);
        float crossEnd = inset.crossEnd(direction);
        float main =
                isRtlRow
                        ? (OptF32.isSome(mainEnd)
                                ? mainEnd
                                : (OptF32.isSome(mainStart) ? -mainStart : 0.0f))
                        : (OptF32.isSome(mainStart)
                                ? mainStart
                                : (OptF32.isSome(mainEnd) ? -mainEnd : 0.0f));
        float cross =
                isRtlColumn
                        ? (OptF32.isSome(crossEnd)
                                ? -crossEnd
                                : OptF32.unwrapOr(crossStart, 0.0f))
                        : (OptF32.isSome(crossStart)
                                ? crossStart
                                : (OptF32.isSome(crossEnd) ? -crossEnd : 0.0f));
        return SizeF32.ZERO.withMain(direction, main).withCross(direction, cross);
    }

    /// `sum_axis_gaps` — gaps only exist between items, so if there are less
    /// than 2 items then there are no gaps; otherwise there are
    /// (num_items - 1) gaps.
    private static float sumAxisGaps(float gap, int numItems) {
        return numItems <= 1 ? 0.0f : gap * (numItems - 1);
    }

    /// `determine_available_space`. Note: min/max/preferred size styles have
    /// already been applied to `knownDimensions` in the `compute` function.
    private static Size<AvailableSpace> determineAvailableSpace(
            SizeOptF32 knownDimensions,
            Size<AvailableSpace> outerAvailableSpace,
            AlgoConstants constants) {
        AvailableSpace width =
                OptF32.isSome(knownDimensions.width())
                        ? new AvailableSpace.Definite(
                                Sys.f32Max(
                                        knownDimensions.width()
                                                - constants.contentBoxInset.horizontalAxisSum(),
                                        0.0f))
                        : outerAvailableSpace
                                .width()
                                .maybeSubF(constants.contentBoxInset.horizontalAxisSum())
                                .maybeMaxF(0.0f);
        AvailableSpace height =
                OptF32.isSome(knownDimensions.height())
                        ? new AvailableSpace.Definite(
                                Sys.f32Max(
                                        knownDimensions.height()
                                                - constants.contentBoxInset.verticalAxisSum(),
                                        0.0f))
                        : outerAvailableSpace
                                .height()
                                .maybeSubF(constants.contentBoxInset.verticalAxisSum())
                                .maybeMaxF(0.0f);
        return new Size<>(width, height);
    }

    /// `item_known_dimension_definiteness`. An item's cross size is definite
    /// if it is stretched (it stretches to the flex line, whose size is known
    /// by the time the item's final layout is performed), or if its cross
    /// size style resolved to a definite size. Additionally, a non-stretched
    /// item's cross *width* is fit-content sized, and a size resolved by
    /// fit-content sizing against definite available space is itself definite
    /// (<https://www.w3.org/TR/css-sizing-3/#definite>). The same does not
    /// apply to cross heights, which are content-based and therefore
    /// indefinite when not stretched.
    private static SizeBool itemKnownDimensionDefiniteness(AlgoConstants constants, FlexItem item) {
        FlexDirection dir = constants.dir;
        boolean mainIsDefinite = constants.hasDefiniteMainSize || item.flexBasisIsDefinite;

        boolean hasCrossAutoMargins =
                item.marginIsAuto.crossStart(dir) || item.marginIsAuto.crossEnd(dir);
        Dimension crossSize = item.sizeStyle.cross(dir);
        boolean isStretched =
                !hasCrossAutoMargins
                        && (crossSize.isStretch()
                                || (item.alignSelf.equals(AlignItems.STRETCH) && crossSize.isAuto()));
        boolean crossIsDefinite =
                isStretched
                        || OptF32.isSome(item.size.cross(dir))
                        || (!dir.isRow() && constants.crossAxisAvailableSpaceIsDefinite);

        return new SizeBool(
                dir.isRow() ? mainIsDefinite : crossIsDefinite,
                dir.isRow() ? crossIsDefinite : mainIsDefinite);
    }

    /// `handle_align_content_stretch`
    private static void handleAlignContentStretch(
            List<FlexLine> flexLines, SizeOptF32 nodeSize, AlgoConstants constants) {
        if (constants.alignContent.equals(AlignContent.STRETCH)) {
            float crossAxisPaddingBorder = constants.contentBoxInset.crossAxisSum(constants.dir);
            float crossMinSize = constants.minSize.cross(constants.dir);
            float crossMaxSize = constants.maxSize.cross(constants.dir);
            float containerMinInnerCross =
                    OptF32.unwrapOr(
                            OptF32.maybeMaxF(
                                    OptF32.maybeSubF(
                                            OptF32.maybeClampO(
                                                    OptF32.or(
                                                            nodeSize.cross(constants.dir),
                                                            crossMinSize),
                                                    crossMinSize,
                                                    crossMaxSize),
                                            crossAxisPaddingBorder),
                                    0.0f),
                            0.0f);

            float totalCrossAxisGap = sumAxisGaps(constants.gap.cross(constants.dir), flexLines.size());
            float linesTotalCross = totalCrossAxisGap;
            for (FlexLine line : flexLines) {
                linesTotalCross += line.crossSize;
            }

            if (linesTotalCross < containerMinInnerCross) {
                float remaining = containerMinInnerCross - linesTotalCross;
                float addition = remaining / flexLines.size();
                for (FlexLine line : flexLines) {
                    line.crossSize += addition;
                }
            }
        }
    }

    /// Determine the container's cross size (if not already known).
    /// `determine_container_cross_size`; returns the total line cross size.
    private static float determineContainerCrossSize(
            List<FlexLine> flexLines, SizeOptF32 nodeSize, AlgoConstants constants) {
        float totalCrossAxisGap = sumAxisGaps(constants.gap.cross(constants.dir), flexLines.size());
        float totalLineCrossSize = 0.0f;
        for (FlexLine line : flexLines) {
            totalLineCrossSize += line.crossSize;
        }

        float paddingBorderSum = constants.contentBoxInset.crossAxisSum(constants.dir);
        float crossScrollbarGutter = constants.scrollbarGutter.cross(constants.dir);
        float minCrossSize = constants.minSize.cross(constants.dir);
        float maxCrossSize = constants.maxSize.cross(constants.dir);
        float outerContainerSize =
                Sys.f32Max(
                        OptF32.maybeClampO(
                                OptF32.unwrapOr(
                                        nodeSize.cross(constants.dir),
                                        totalLineCrossSize + totalCrossAxisGap + paddingBorderSum),
                                minCrossSize,
                                maxCrossSize),
                        paddingBorderSum - crossScrollbarGutter);
        float innerContainerSize = Sys.f32Max(outerContainerSize - paddingBorderSum, 0.0f);

        constants.containerSize = constants.containerSize.withCross(constants.dir, outerContainerSize);
        constants.innerContainerSize =
                constants.innerContainerSize.withCross(constants.dir, innerContainerSize);

        return totalLineCrossSize;
    }

    /// Align all flex lines per `align-content`.
    /// `align_flex_lines_per_align_content`
    private static void alignFlexLinesPerAlignContent(
            List<FlexLine> flexLines, AlgoConstants constants, float totalCrossSize) {
        int numLines = flexLines.size();
        float gap = constants.gap.cross(constants.dir);
        float totalCrossAxisGap = sumAxisGaps(gap, numLines);
        float freeSpace =
                constants.innerContainerSize.cross(constants.dir) - totalCrossSize - totalCrossAxisGap;

        AlignContentKeyword alignContentMode =
                net.morphismmc.jaffy.compute.common.Alignment.applyAlignmentFallback(
                        freeSpace, numLines, constants.alignContent);

        if (constants.isWrapReverse) {
            for (int i = flexLines.size() - 1; i >= 0; i--) {
                FlexLine line = flexLines.get(i);
                line.offsetCross =
                        net.morphismmc.jaffy.compute.common.Alignment.computeAlignmentOffset(
                                freeSpace,
                                numLines,
                                gap,
                                alignContentMode,
                                constants.isWrapReverse,
                                i == 0);
            }
        } else {
            for (int i = 0; i < flexLines.size(); i++) {
                FlexLine line = flexLines.get(i);
                line.offsetCross =
                        net.morphismmc.jaffy.compute.common.Alignment.computeAlignmentOffset(
                                freeSpace,
                                numLines,
                                gap,
                                alignContentMode,
                                constants.isWrapReverse,
                                i == 0);
            }
        }
    }

    /// Calculates the layout for a flex-item (`calculate_flex_item`).
    /// `&mut f32` accumulators -> `float[1]`.
    private static void calculateFlexItem(
            LayoutFlexboxContainer tree,
            FlexItem item,
            float[] totalOffsetMain,
            float totalOffsetCross,
            float lineOffsetCross,
            RectF32[] totalOverflowRect,
            ScrollableOverflowContainer overflowContainer,
            AlgoConstants constants) {
        SizeF32 containerSize = constants.containerSize;
        SizeOptF32 nodeInnerSize = constants.nodeInnerSize;
        FlexDirection direction = constants.dir;
        Direction layoutDirection = constants.layoutDirection;
        SizeBool knownDimensionDefiniteness = itemKnownDimensionDefiniteness(constants, item);
        SizeF32 marginSum = item.margin.sumAxes();
        LayoutOutput layoutOutput =
                tree.computeChildLayout(
                        item.node,
                        new LayoutInput(
                                RunMode.PERFORM_LAYOUT,
                                SizingMode.CONTENT_SIZE,
                                RequestedAxis.BOTH,
                                new SizeOptF32(item.targetSize.width(), item.targetSize.height()),
                                knownDimensionDefiniteness,
                                nodeInnerSize,
                                // The available space passed to the child
                                // excludes the child's margins
                                new Size<>(
                                        new AvailableSpace.Definite(
                                                Sys.f32Max(
                                                        containerSize.width() - marginSum.width(),
                                                        0.0f)),
                                        new AvailableSpace.Definite(
                                                Sys.f32Max(
                                                        containerSize.height() - marginSum.height(),
                                                        0.0f))),
                                LineBool.FALSE));

        SizeF32 size = layoutOutput.size();
        net.morphismmc.jaffy.tree.layout.ScrollableOverflow scrollableOverflowRect =
                layoutOutput.scrollableOverflowRect();

        boolean isRtlRow = direction.isRow() && layoutDirection.isRtl();
        boolean isRtlColumn = direction.isColumn() && layoutDirection.isRtl();
        float mainRelativeInset = item.relativeInset.main(direction);
        float crossRelativeInset = item.relativeInset.cross(direction);
        float effectiveLineOffsetCross = isRtlColumn ? 0.0f : lineOffsetCross;

        float offsetMain =
                isRtlRow
                        ? totalOffsetMain[0]
                                - item.offsetMain
                                - item.margin.mainEnd(direction)
                                - mainRelativeInset
                                - size.width()
                        : totalOffsetMain[0]
                                + item.offsetMain
                                + item.margin.mainStart(direction)
                                + mainRelativeInset;

        float offsetCross =
                totalOffsetCross
                        + item.offsetCross
                        + effectiveLineOffsetCross
                        + item.margin.crossStart(direction)
                        + crossRelativeInset;

        float innerBaseline =
                OptF32.unwrapOr(
                        net.morphismmc.jaffy.compute.common.Alignment.childFirstBaseline(
                                layoutOutput.baselines().first(), size.height(), item.overflow.y()),
                        size.height());
        if (direction.isRow()) {
            float baselineOffsetCross =
                    totalOffsetCross
                            + item.offsetCross
                            + effectiveLineOffsetCross
                            + item.margin.crossStart(direction);
            item.baseline = baselineOffsetCross + innerBaseline;
        } else {
            float baselineOffsetMain =
                    totalOffsetMain[0] + item.offsetMain + item.margin.mainStart(direction);
            item.baseline = baselineOffsetMain + innerBaseline;
        }

        PointF32 location =
                direction.isRow()
                        ? new PointF32(offsetMain, offsetCross)
                        : new PointF32(offsetCross, offsetMain);
        SizeF32 scrollbarSize =
                new SizeF32(
                        item.overflow.y() == Overflow.SCROLL ? item.scrollbarWidth : 0.0f,
                        item.overflow.x() == Overflow.SCROLL ? item.scrollbarWidth : 0.0f);

        tree.setUnroundedLayout(
                item.node,
                new Layout(
                        item.order,
                        location,
                        size,
                        scrollableOverflowRect,
                        scrollbarSize,
                        item.border,
                        item.padding,
                        item.margin));

        // Keep out-of-flow candidates from the item's subtree, translating
        // anchors from item-relative to container-relative coordinates
        item.oofCandidates = layoutOutput.oofCandidates().take();
        if (!item.oofCandidates.isEmpty()) {
            item.oofCandidates.translate(location);
        }

        if (isRtlRow) {
            totalOffsetMain[0] -=
                    item.offsetMain + item.margin.mainAxisSum(direction) + size.main(direction);
        } else {
            totalOffsetMain[0] +=
                    item.offsetMain + item.margin.mainAxisSum(direction) + size.main(direction);
        }

        PointF32 contributionLocation =
                new PointF32(
                        location.x() - constants.paddingBoxInset.left(),
                        location.y() - constants.paddingBoxInset.top());
        RectF32 contribution =
                net.morphismmc.jaffy.compute.common.ScrollableOverflow
                        .computeScrollableOverflowContribution(
                        contributionLocation,
                        size,
                        scrollableOverflowRect.rect(),
                        item.border,
                        item.overflow,
                        item.contain,
                        overflowContainer,
                        item.margin);
        totalOverflowRect[0] = totalOverflowRect[0].union(contribution);
    }

    /// Calculates the layout line (`calculate_layout_line`).
    /// `&mut f32` accumulators -> `float[1]`.
    private static void calculateLayoutLine(
            LayoutFlexboxContainer tree,
            FlexLine line,
            float[] totalOffsetCross,
            RectF32[] overflowRect,
            ScrollableOverflowContainer overflowContainer,
            AlgoConstants constants) {
        SizeF32 containerSize = constants.containerSize;
        RectF32 paddingBorder = constants.contentBoxInset;
        FlexDirection direction = constants.dir;
        Direction layoutDirection = constants.layoutDirection;
        float[] totalOffsetMain = {
            layoutDirection.isRtl() && direction.isRow()
                    ? containerSize.width() - paddingBorder.mainEnd(direction)
                    : paddingBorder.mainStart(direction)
        };
        float lineOffsetCross = line.offsetCross;

        boolean isRtlColumn = layoutDirection.isRtl() && direction.isColumn();
        if (isRtlColumn) {
            totalOffsetCross[0] -= lineOffsetCross + line.crossSize;
        }

        if (direction.isReverse()) {
            for (int i = line.items.size() - 1; i >= 0; i--) {
                calculateFlexItem(
                        tree,
                        line.items.get(i),
                        totalOffsetMain,
                        totalOffsetCross[0],
                        lineOffsetCross,
                        overflowRect,
                        overflowContainer,
                        constants);
            }
        } else {
            for (FlexItem item : line.items) {
                calculateFlexItem(
                        tree,
                        item,
                        totalOffsetMain,
                        totalOffsetCross[0],
                        lineOffsetCross,
                        overflowRect,
                        overflowContainer,
                        constants);
            }
        }

        if (!isRtlColumn) {
            totalOffsetCross[0] += lineOffsetCross + line.crossSize;
        }
    }

    /// Determine the used cross size of each flex item
    /// (`determine_used_cross_size`).
    ///
    /// - If a flex item has align-self: stretch, its computed cross size
    ///   property is auto, and neither of its cross-axis margins are auto, the
    ///   used outer cross size is the used cross size of its flex line,
    ///   clamped according to the item's used min and max cross sizes.
    ///   Otherwise, the used cross size is the item's hypothetical cross size.
    private static void determineUsedCrossSize(
            LayoutFlexboxContainer tree, List<FlexLine> flexLines, AlgoConstants constants) {
        for (FlexLine line : flexLines) {
            float lineCrossSize = line.crossSize;

            for (FlexItem child : line.items) {
                Style childStyle = tree.getFlexboxChildStyle(child.node);
                // A cross size of `stretch` stretches to the flex line like
                // align-self: stretch (but regardless of the alignment style)
                float usedCross;
                if (child.isStretched(constants.dir)) {
                    // For some reason this particular usage of max_width is an
                    // exception to the rule that max_width's transfer using
                    // the aspect_ratio (if set). Both Chrome and Firefox agree
                    // on this. And reading the spec, it seems like a
                    // reasonable interpretation. Although it seems like the
                    // spec *should* apply aspect_ratio here. Percentage
                    // padding and border resolve against the container's
                    // inline size (its width) on all four sides, not against
                    // its height for the top/bottom sides
                    SizeF32 boxSizingAdjustment = child.boxSizingAdjustment(childStyle);

                    SizeOptF32 maxSizeIgnoringAspectRatio =
                            MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                                            childStyle.maxSize(), constants.nodeInnerSize, tree::calc)
                                    .maybeAddF(boxSizingAdjustment);

                    usedCross =
                            F32.maybeClampO(
                                    Sys.f32Max(
                                            lineCrossSize
                                                    - child.margin.crossAxisSum(constants.dir),
                                            0.0f),
                                    child.minSize.cross(constants.dir),
                                    maxSizeIgnoringAspectRatio.cross(constants.dir));
                } else {
                    usedCross = child.hypotheticalInnerSize.cross(constants.dir);
                }
                child.targetSize = child.targetSize.withCross(constants.dir, usedCross);

                child.outerTargetSize =
                        child.outerTargetSize.withCross(
                                constants.dir,
                                child.targetSize.cross(constants.dir)
                                        + child.margin.crossAxisSum(constants.dir));
            }
        }
    }

    /// Distribute any remaining free space
    /// (`resolve_cross_axis_auto_margins`).
    ///
    /// For each flex line: if the remaining free space is positive and at
    /// least one main-axis margin on this line is `auto`, distribute the free
    /// space equally among these margins; otherwise set all `auto` margins to
    /// zero. Then align the items along the cross-axis per `align-self`.
    private static void resolveCrossAxisAutoMargins(
            List<FlexLine> flexLines, AlgoConstants constants) {
        for (FlexLine line : flexLines) {
            float lineCrossSize = line.crossSize;
            float maxBaseline = 0.0f;
            for (FlexItem child : line.items) {
                maxBaseline = Sys.f32Max(maxBaseline, child.baseline);
            }
            float maxBaselineToBottomDistance = 0.0f;
            for (FlexItem child : line.items) {
                if (child.participatesInBaselineAlignment(constants.dir)) {
                    maxBaselineToBottomDistance =
                            Sys.f32Max(
                                    maxBaselineToBottomDistance,
                                    child.outerTargetSize.cross(constants.dir) - child.baseline);
                }
            }

            for (FlexItem child : line.items) {
                float freeSpace = lineCrossSize - child.outerTargetSize.cross(constants.dir);
                boolean autoStart = child.marginIsAuto.crossStart(constants.dir);
                boolean autoEnd = child.marginIsAuto.crossEnd(constants.dir);
                if (autoStart && autoEnd) {
                    if (constants.isRow) {
                        child.margin =
                                new RectF32(
                                        child.margin.left(),
                                        child.margin.right(),
                                        freeSpace / 2.0f,
                                        freeSpace / 2.0f);
                    } else {
                        child.margin =
                                new RectF32(
                                        freeSpace / 2.0f,
                                        freeSpace / 2.0f,
                                        child.margin.top(),
                                        child.margin.bottom());
                    }
                } else if (autoStart) {
                    child.margin =
                            constants.isRow
                                    ? new RectF32(
                                            child.margin.left(),
                                            child.margin.right(),
                                            freeSpace,
                                            child.margin.bottom())
                                    : new RectF32(
                                            freeSpace,
                                            child.margin.right(),
                                            child.margin.top(),
                                            child.margin.bottom());
                } else if (autoEnd) {
                    child.margin =
                            constants.isRow
                                    ? new RectF32(
                                            child.margin.left(),
                                            child.margin.right(),
                                            child.margin.top(),
                                            freeSpace)
                                    : new RectF32(
                                            child.margin.left(),
                                            freeSpace,
                                            child.margin.top(),
                                            child.margin.bottom());
                } else {
                    // 14. Align all flex items along the cross-axis.
                    child.offsetCross =
                            alignFlexItemsAlongCrossAxis(
                                    child,
                                    freeSpace,
                                    maxBaseline,
                                    maxBaselineToBottomDistance,
                                    constants);
                }
            }
        }
    }

    /// Align all flex items along the cross-axis
    /// (`align_flex_items_along_cross_axis`) per `align-self`, if neither of
    /// the item's cross-axis margins are `auto`.
    private static float alignFlexItemsAlongCrossAxis(
            FlexItem child,
            float freeSpace,
            float maxBaseline,
            float maxBaselineToBottomDistance,
            AlgoConstants constants) {
        boolean crossAxisShouldReverse = constants.isColumn && constants.layoutDirection.isRtl();

        // If align-self uses a "safe" overflow-position keyword and the item
        // would overflow its line cross size, fall back to logical Start to
        // avoid data loss. See CSS Box Alignment 3 §4.3
        // <https://www.w3.org/TR/css-align-3/#overflow-values>. Otherwise,
        // drop the safety field so the match below operates on a bare keyword
        // and stays exhaustive.
        AlignItemsKeyword alignKeyword =
                child.alignSelf.isSafe() && freeSpace < 0.0f
                        ? AlignItemsKeyword.START
                        : child.alignSelf.keyword();

        switch (alignKeyword) {
            case START:
                return crossAxisShouldReverse ? freeSpace : 0.0f;
            case FLEX_START:
                return constants.isWrapReverse ^ crossAxisShouldReverse ? freeSpace : 0.0f;
            case END:
                return crossAxisShouldReverse ? 0.0f : freeSpace;
            case FLEX_END:
                return constants.isWrapReverse ^ crossAxisShouldReverse ? 0.0f : freeSpace;
            case CENTER:
                return freeSpace / 2.0f;
            case BASELINE:
                if (constants.isRow) {
                    if (constants.isWrapReverse) {
                        // In a wrap-reverse container the cross axis is
                        // flipped, so the baseline-aligned group of items is
                        // aligned to the cross-start edge, which is the bottom
                        // of the line.
                        float lineCrossSize =
                                freeSpace + child.outerTargetSize.cross(constants.dir);
                        return lineCrossSize - maxBaselineToBottomDistance - child.baseline;
                    } else {
                        return maxBaseline - child.baseline;
                    }
                } else {
                    // Until we support vertical writing modes, baseline
                    // alignment only makes sense if the constants.direction is
                    // row, so we treat it as flex-start alignment in columns.
                    boolean baselineColumnShouldReverse =
                            crossAxisShouldReverse && !constants.isWrap;
                    return constants.isWrapReverse ^ baselineColumnShouldReverse
                            ? freeSpace
                            : 0.0f;
                }
            case STRETCH:
                return constants.isWrapReverse ^ crossAxisShouldReverse ? freeSpace : 0.0f;
            // Normal is resolved to Stretch, and SelfStart/SelfEnd are
            // resolved to Start/End against the item's own direction when flex
            // items are generated.
            default:
                throw new IllegalStateException("unreachable AlignItemsKeyword variant");
        }
    }

    /// Calculate the cross size of each flex line (`calculate_cross_size`).
    ///
    /// If the flex container is single-line and has a definite cross size, the
    /// cross size of the flex line is the flex container's inner cross size.
    /// Otherwise, for each flex line: the used cross-size is the largest of
    /// (a) the largest baseline-group distance sum and (b) the largest outer
    /// hypothetical cross size among non-baseline items, and zero.
    private static void calculateCrossSize(
            List<FlexLine> flexLines, SizeOptF32 nodeSize, AlgoConstants constants) {
        if (!constants.isWrap && OptF32.isSome(nodeSize.cross(constants.dir))) {
            float crossAxisPaddingBorder = constants.contentBoxInset.crossAxisSum(constants.dir);
            float crossMinSize = constants.minSize.cross(constants.dir);
            float crossMaxSize = constants.maxSize.cross(constants.dir);
            flexLines.get(0).crossSize =
                    OptF32.unwrapOr(
                            OptF32.maybeMaxF(
                                    OptF32.maybeSubF(
                                            OptF32.maybeClampO(
                                                    nodeSize.cross(constants.dir),
                                                    crossMinSize,
                                                    crossMaxSize),
                                            crossAxisPaddingBorder),
                                    0.0f),
                            0.0f);
        } else {
            for (FlexLine line : flexLines) {
                float maxBaseline = 0.0f;
                for (FlexItem child : line.items) {
                    maxBaseline = Sys.f32Max(maxBaseline, child.baseline);
                }
                float crossSize = 0.0f;
                for (FlexItem child : line.items) {
                    float contribution =
                            child.participatesInBaselineAlignment(constants.dir)
                                    ? maxBaseline
                                            - child.baseline
                                            + child.hypotheticalOuterSize.cross(constants.dir)
                                    : child.hypotheticalOuterSize.cross(constants.dir);
                    crossSize = Sys.f32Max(crossSize, contribution);
                }
                line.crossSize = crossSize;
            }

            // If the flex container is single-line, then clamp the line's
            // cross-size to be within the container's computed min and max
            // cross sizes. Note that if CSS 2.1's definition of min/max-width/
            // height applied more generally, this behavior would fall out
            // automatically.
            if (!constants.isWrap) {
                float crossAxisPaddingBorder = constants.contentBoxInset.crossAxisSum(constants.dir);
                float crossMinSize =
                        OptF32.maybeSubF(constants.minSize.cross(constants.dir), crossAxisPaddingBorder);
                float crossMaxSize =
                        OptF32.maybeSubF(constants.maxSize.cross(constants.dir), crossAxisPaddingBorder);
                FlexLine first = flexLines.get(0);
                first.crossSize = F32.maybeClampO(first.crossSize, crossMinSize, crossMaxSize);
            }
        }
    }

    /// Generate anonymous flex items
    /// (`generate_anonymous_flex_items`).
    ///
    /// # [9.1. Initial Setup](https://www.w3.org/TR/css-flexbox-1/#box-manip)
    ///
    /// - [**Generate anonymous flex items**](https://www.w3.org/TR/css-flexbox-1/#algo-anon-box)
    ///   as described in [§4 Flex Items](https://www.w3.org/TR/css-flexbox-1/#flex-items).
    private static List<FlexItem> generateAnonymousFlexItems(
            LayoutFlexboxContainer tree, long node, AlgoConstants constants) {
        // Percentage sizes of items resolve against the container's inner
        // size, but only if that size is definite. A known main size which is
        // derived from the container's own content is treated as indefinite
        // here.
        SizeOptF32 percentResolutionSize =
                constants.knownMainSizeIsDefinite
                        ? constants.nodeInnerSize
                        : constants.nodeInnerSize.withMain(constants.dir, OptF32.NONE);

        // Allocate once, for the child count (some children may not generate
        // an item); upstream reserves lazily on first push, which is
        // equivalent.
        int childCount = tree.childCount(node);
        List<FlexItem> flexItems = new ArrayList<>(childCount);
        LongIterator childIds = tree.childIds(node);
        int index = 0;
        while (childIds.hasNext()) {
            long child = childIds.nextLong();
            Style childStyle = tree.getFlexboxChildStyle(child);
            // `order` uses the pre-filter index (upstream `enumerate`)
            int order = index;
            index++;
            if (childStyle.position().isOutOfFlow()
                    || childStyle.boxGenerationMode() == BoxGenerationMode.NONE) {
                continue;
            }

            float aspectRatio = childStyle.aspectRatio();
            RectF32 padding =
                    MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(
                            childStyle.padding(), constants.nodeInnerSize.width(), tree::calc);
            RectF32 border =
                    MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(
                            childStyle.border(), constants.nodeInnerSize.width(), tree::calc);
            SizeF32 boxSizingAdjustment =
                    childStyle.boxSizing() == BoxSizing.CONTENT_BOX
                            ? padding.plus(border).sumAxes()
                            : SizeF32.ZERO;

            FlexItem item = new FlexItem();
            item.node = child;
            item.order = order;
            item.size =
                    MaybeResolve.SizeDimensionExt.maybeResolveO(
                                    childStyle.size(), percentResolutionSize, tree::calc)
                            .maybeApplyAspectRatio(aspectRatio)
                            .maybeAddF(boxSizingAdjustment);
            item.sizeStyle = childStyle.size();
            item.minSize =
                    MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                                    childStyle.minSize(), percentResolutionSize, tree::calc)
                            .maybeAddF(boxSizingAdjustment);
            item.maxSize =
                    MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                                    childStyle.maxSize(), percentResolutionSize, tree::calc)
                            .maybeAddF(boxSizingAdjustment);
            item.aspectRatio = aspectRatio;

            if (childStyle.position() == Position.RELATIVE) {
                RectOptF32 inset =
                        new RectOptF32(
                                childStyle
                                        .inset()
                                        .left()
                                        .maybeResolveO(constants.nodeInnerSize.width(), tree::calc),
                                childStyle
                                        .inset()
                                        .right()
                                        .maybeResolveO(constants.nodeInnerSize.width(), tree::calc),
                                childStyle
                                        .inset()
                                        .top()
                                        .maybeResolveO(constants.nodeInnerSize.height(), tree::calc),
                                childStyle
                                        .inset()
                                        .bottom()
                                        .maybeResolveO(constants.nodeInnerSize.height(), tree::calc));
                item.relativeInset = resolveRelativeInset(inset, constants);
            } else {
                item.relativeInset = SizeF32.ZERO;
            }
            item.margin =
                    MaybeResolve.RectLengthPercentageAutoExt.resolveOrZeroO(
                            childStyle.margin(), constants.nodeInnerSize.width(), tree::calc);
            // `margin.map(LengthPercentageAuto::is_auto)` — `Rect<bool>` ->
            // `RectBool` primitive specialization (no Function dispatch or
            // Boolean boxing)
            item.marginIsAuto =
                    new RectBool(
                            childStyle.margin().left().isAuto(),
                            childStyle.margin().right().isAuto(),
                            childStyle.margin().top().isAuto(),
                            childStyle.margin().bottom().isAuto());
            item.padding = padding;
            item.border = border;
            item.alignSelf =
                    resolveNormalAlignment(
                                    childStyle.alignSelf() != null
                                            ? childStyle.alignSelf()
                                            : constants.alignItems)
                            .resolveSelfRelative(
                                    childStyle.direction(),
                                    constants.layoutDirection,
                                    constants.isColumn);
            item.overflow = childStyle.overflow();
            item.contain = childStyle.contain();
            item.scrollbarWidth = childStyle.scrollbarWidth();
            item.flexGrow = childStyle.flexGrow();
            item.flexShrink = childStyle.flexShrink();
            item.flexBasisIsDefinite = false;
            item.flexBasis = 0.0f;
            item.innerFlexBasis = 0.0f;
            item.violation = 0.0f;
            item.frozen = false;

            item.resolvedMinimumMainSize = 0.0f;
            item.hypotheticalInnerSize = SizeF32.ZERO;
            item.hypotheticalOuterSize = SizeF32.ZERO;
            item.targetSize = SizeF32.ZERO;
            item.outerTargetSize = SizeF32.ZERO;
            item.contentFlexFraction = 0.0f;

            item.baseline = 0.0f;

            item.offsetMain = 0.0f;
            item.offsetCross = 0.0f;

            item.oofCandidates = OofCandidates.none();
            flexItems.add(item);
        }
        return flexItems;
    }

    /// Determine the flex base size and hypothetical main size of each item
    /// (`determine_flex_base_size`).
    ///
    /// # [9.2. Line Length Determination](https://www.w3.org/TR/css-flexbox-1/#line-sizing)
    ///
    /// - [**Determine the flex base size and hypothetical main size of each
    ///   item**](https://www.w3.org/TR/css-flexbox-1/#algo-main-item):
    ///   A. definite used flex basis -> base size;
    ///   B. intrinsic aspect ratio + `content` basis + definite cross ->
    ///      transfer through the ratio;
    ///   C./E. otherwise size into available space using the used flex basis
    ///      in place of the main size, `content` treated as max-content.
    ///
    ///   Min and max main sizes are ignored for the base size (no clamping),
    ///   as are the calculations that floor the content box size at zero when
    ///   applying box-sizing.
    private static void determineFlexBaseSize(
            LayoutFlexboxContainer tree,
            AlgoConstants constants,
            Size<AvailableSpace> availableSpace,
            List<FlexItem> flexItems) {
        FlexDirection dir = constants.dir;

        for (FlexItem child : flexItems) {
            Style childStyle = tree.getFlexboxChildStyle(child.node);

            // Parent size for child sizing
            float crossAxisParentSize = constants.nodeInnerSize.cross(dir);
            SizeOptF32 childParentSize =
                    new SizeOptF32(
                            dir.isRow() ? OptF32.NONE : crossAxisParentSize,
                            dir.isRow() ? crossAxisParentSize : OptF32.NONE);

            // Available space for child sizing (excluding the child's
            // margins). Min/max sizes transferred through the aspect ratio
            // are taken into account here
            // https://github.com/w3c/csswg-drafts/issues/10997
            SizeOptF32 transferredMinSize =
                    child.minSize.maybeApplyAspectRatio(child.aspectRatio);
            SizeOptF32 transferredMaxSize =
                    child.maxSize.maybeApplyAspectRatio(child.aspectRatio);
            float childMinCross = transferredMinSize.cross(dir);
            float childMaxCross = transferredMaxSize.cross(dir);

            // Clamp available space by min- and max- size
            AvailableSpace crossAxisAvailableSpace;
            if (availableSpace.cross(dir) instanceof AvailableSpace.Definite definite) {
                crossAxisAvailableSpace =
                        new AvailableSpace.Definite(
                                F32.maybeClampO(
                                        Sys.f32Max(
                                                constants.dividedCrossSpace(
                                                                OptF32.unwrapOr(
                                                                        crossAxisParentSize,
                                                                        definite.value()))
                                                        - child.margin.crossAxisSum(dir),
                                                0.0f),
                                        childMinCross,
                                        childMaxCross));
            } else if (availableSpace.cross(dir) instanceof AvailableSpace.MinContent) {
                crossAxisAvailableSpace =
                        OptF32.isSome(childMinCross)
                                ? new AvailableSpace.Definite(childMinCross)
                                : AvailableSpace.MIN_CONTENT;
            } else {
                crossAxisAvailableSpace =
                        OptF32.isSome(childMaxCross)
                                ? new AvailableSpace.Definite(childMaxCross)
                                : AvailableSpace.MAX_CONTENT;
            }

            // Known dimensions for child sizing
            boolean childCrossSizeIsDefinite = OptF32.isSome(child.size.cross(dir));
            SizeOptF32 childKnownDimensions = child.size.withMain(dir, OptF32.NONE);
            // Clamp the definite cross size by the cross min/max sizes so
            // that sizes transferred through an intrinsic aspect ratio (e.g.
            // for replaced elements) are based on the used cross size.
            childKnownDimensions =
                    childKnownDimensions.withCross(
                            dir,
                            OptF32.maybeClampO(
                                    childKnownDimensions.cross(dir),
                                    transferredMinSize.cross(dir),
                                    transferredMaxSize.cross(dir)));
            if (child.alignSelf.equals(AlignItems.STRETCH)
                    && !child.marginIsAuto.crossStart(constants.dir)
                    && !child.marginIsAuto.crossEnd(constants.dir)
                    && OptF32.isNone(childKnownDimensions.cross(dir))) {
                childKnownDimensions =
                        childKnownDimensions.withCross(
                                dir, crossAxisAvailableSpace.intoOption());
                // The cross size of a stretched item is definite if the
                // container has a definite cross size
                // (https://www.w3.org/TR/css-flexbox-1/#definite-sizes)
                childCrossSizeIsDefinite =
                        !constants.isWrap
                                && constants.hasDefiniteCrossSize
                                && OptF32.isSome(crossAxisParentSize);
            }

            float boxSizingAdjustment = child.boxSizingAdjustment(childStyle).main(dir);
            // Percentage flex basis values resolve against the container's
            // inner main size, but only if that size is definite. A known
            // main size which is derived from the container's own content is
            // treated as indefinite here.
            float percentResolutionMainSize =
                    constants.knownMainSizeIsDefinite
                            ? constants.nodeInnerSize.main(dir)
                            : OptF32.NONE;
            Dimension flexBasisStyle = childStyle.flexBasis();
            float resolvedFlexBasis =
                    OptF32.maybeAddF(
                            flexBasisStyle.maybeResolveO(percentResolutionMainSize, tree::calc),
                            boxSizingAdjustment);

            float flexBasis;
            basis:
            {
                float mainSize = child.size.main(dir);
                float mainStretchSize =
                        OptF32.maybeMaxF(
                                OptF32.maybeSubF(
                                        percentResolutionMainSize, child.margin.mainAxisSum(dir)),
                                0.0f);

                // A flex basis that is a sizing keyword (min-content,
                // max-content, fit-content, fit-content(...), stretch) is
                // used in place of the main size property: `stretch` resolves
                // to an exact (definite) size while the other keywords
                // determine the available space constraint the item is
                // measured under. A keyword that cannot be resolved in the
                // current context behaves as `content`.
                AvailableSpace keywordMainAvailableSpace = null;
                if (flexBasisStyle.isContent()) {
                    // A flex basis of `content` indicates an automatic size
                    // based on the item's content: the item is measured
                    // (ignoring its main size property) under the default
                    // constraint below
                    keywordMainAvailableSpace = null;
                } else if (flexBasisStyle.isSizingKeyword()) {
                    SizingKeyword.SizingKeywordResolution resolution =
                            SizingKeyword.resolveSizingKeyword(
                                    flexBasisStyle,
                                    mainStretchSize,
                                    percentResolutionMainSize,
                                    tree::calc);
                    if (resolution instanceof SizingKeyword.SizingKeywordResolution.Exact exact) {
                        child.flexBasisIsDefinite = true;
                        flexBasis = exact.value();
                        break basis;
                    } else if (resolution
                            instanceof SizingKeyword.SizingKeywordResolution.Measure measure) {
                        keywordMainAvailableSpace = measure.availableSpace();
                    }
                } else {
                    float basis =
                            OptF32.isSome(resolvedFlexBasis)
                                    ? resolvedFlexBasis
                                    : (flexBasisStyle.isAuto() ? mainSize : OptF32.NONE);
                    if (OptF32.isSome(basis)) {
                        child.flexBasisIsDefinite = true;
                        flexBasis = basis;
                        break basis;
                    }

                    // A main size that is a sizing keyword either resolves to
                    // an exact size or determines the available space
                    // constraint the item is measured under
                    if (flexBasisStyle.isAuto()) {
                        SizingKeyword.SizingKeywordResolution resolution =
                                SizingKeyword.resolveSizingKeyword(
                                        child.sizeStyle.main(dir),
                                        mainStretchSize,
                                        percentResolutionMainSize,
                                        tree::calc);
                        if (resolution
                                instanceof SizingKeyword.SizingKeywordResolution.Exact exact) {
                            child.flexBasisIsDefinite = true;
                            flexBasis = exact.value();
                            break basis;
                        } else if (resolution
                                instanceof SizingKeyword.SizingKeywordResolution.Measure measure) {
                            keywordMainAvailableSpace = measure.availableSpace();
                        }
                    }
                }

                // If the item has an aspect ratio and a definite cross size
                // then the flex base size is calculated by transferring that
                // cross size through the aspect ratio (case B above), and is
                // therefore definite.
                if (childCrossSizeIsDefinite
                        && OptF32.isSome(child.aspectRatio)
                        && OptF32.isSome(childKnownDimensions.cross(dir))) {
                    child.flexBasisIsDefinite = true;
                    float cross = childKnownDimensions.cross(dir);
                    flexBasis =
                            dir.isRow()
                                    ? cross * child.aspectRatio
                                    : cross / child.aspectRatio;
                    break basis;
                }

                // E. Otherwise, size the item into the available space using
                //    its used flex basis in place of its main size, treating
                //    a value of content as max-content. If a cross size is
                //    needed to determine the main size (e.g. when the flex
                //    item's main size is in its block axis) and the flex
                //    item's cross size is auto and not definite, in this
                //    calculation use fit-content as the flex item's cross
                //    size. The flex base size is the item's resulting main
                //    size.
                Size<AvailableSpace> childAvailableSpace =
                        StyleHelpers.SizeAvailableSpaceExt.maxContent()
                                .withMain(
                                        dir,
                                        keywordMainAvailableSpace != null
                                                ? keywordMainAvailableSpace
                                                // Map AvailableSpace::Definite to
                                                // AvailableSpace::MaxContent
                                                : availableSpace.main(dir)
                                                                instanceof AvailableSpace.MinContent
                                                        ? AvailableSpace.MIN_CONTENT
                                                        : AvailableSpace.MAX_CONTENT)
                                .withCross(dir, crossAxisAvailableSpace);

                flexBasis =
                        tree.measureChildSize(
                                child.node,
                                childKnownDimensions,
                                childParentSize,
                                childAvailableSpace,
                                SizingMode.CONTENT_SIZE,
                                dir.mainAxis(),
                                LineBool.FALSE);
            }
            child.flexBasis = flexBasis;

            // Floor flex-basis by the padding_border_sum (floors
            // inner_flex_basis at zero). This seems to be in violation of the
            // spec which explicitly states that the content box should not be
            // floored at zero (like it usually is) when calculating the
            // flex-basis. But including this matches both Chrome and
            // Firefox's behaviour.
            //
            // TODO: resolve spec violation
            // Spec: https://www.w3.org/TR/css-flexbox-1/#intrinsic-item-contributions
            // Spec: https://www.w3.org/TR/css-flexbox-1/#change-2016-max-contribution
            float paddingBorderSum =
                    child.padding.mainAxisSum(constants.dir) + child.border.mainAxisSum(constants.dir);
            child.flexBasis = Sys.f32Max(child.flexBasis, paddingBorderSum);

            // The hypothetical main size is the item's flex base size clamped
            // according to its used min and max main sizes (and flooring the
            // content box size at zero).
            child.innerFlexBasis = child.flexBasis - paddingBorderSum;

            SizeOptF32 paddingBorderAxesSums =
                    new SizeOptF32(
                            child.padding.plus(child.border).sumAxes().width(),
                            child.padding.plus(child.border).sumAxes().height());

            // Note that it is important that the `parent_size` parameter in
            // the main axis is not set for this function call as it is used
            // for resolving percentages, and percentage size in an axis
            // should not contribute to a min-content contribution in that
            // same axis. However the `parent_size` and `available_space`
            // *should* be set to their usual values in the cross axis so that
            // wrapping content can wrap correctly.
            //
            // See https://drafts.csswg.org/css-sizing-3/#min-percentage-contribution
            float styleMinMainSize =
                    child.minSize
                            .or(
                                    new SizeOptF32(
                                            child.overflow.x().maybeIntoAutomaticMinSize(),
                                            child.overflow.y().maybeIntoAutomaticMinSize()))
                            .main(dir);

            if (OptF32.isSome(styleMinMainSize)) {
                child.resolvedMinimumMainSize = styleMinMainSize;
            } else {
                float minContentMainSize;
                {
                    Size<AvailableSpace> childAvailableSpace =
                            StyleHelpers.SizeAvailableSpaceExt.minContent()
                                    .withCross(dir, crossAxisAvailableSpace);
                    minContentMainSize =
                            tree.measureChildSize(
                                    child.node,
                                    childKnownDimensions,
                                    childParentSize,
                                    childAvailableSpace,
                                    SizingMode.CONTENT_SIZE,
                                    dir.mainAxis(),
                                    LineBool.FALSE);
                }

                // 4.5. Automatic Minimum Size of Flex Items
                // https://www.w3.org/TR/css-flexbox-1/#min-size-auto
                float clampedMinContentSize =
                        F32.maybeMaxO(
                                F32.maybeMinO(
                                        F32.maybeMinO(
                                                minContentMainSize, child.size.main(dir)),
                                        transferredMaxSize.main(dir)),
                                paddingBorderAxesSums.main(dir));
                child.resolvedMinimumMainSize = clampedMinContentSize;
            }

            // Sizes transferred through the aspect ratio clamp the
            // hypothetical main size, but do not participate in resolving
            // flexible lengths or clamping the final size.
            // https://github.com/w3c/csswg-drafts/issues/10997
            float hypotheticalInnerMinMain =
                    F32.maybeMaxO(
                            F32.maybeMaxO(
                                    child.resolvedMinimumMainSize,
                                    transferredMinSize.main(constants.dir)),
                            paddingBorderAxesSums.main(constants.dir));
            float hypotheticalInnerSize =
                    F32.maybeClampO(
                            child.flexBasis,
                            hypotheticalInnerMinMain,
                            transferredMaxSize.main(constants.dir));
            float hypotheticalOuterSize =
                    hypotheticalInnerSize + child.margin.mainAxisSum(constants.dir);

            child.hypotheticalInnerSize =
                    child.hypotheticalInnerSize.withMain(constants.dir, hypotheticalInnerSize);
            child.hypotheticalOuterSize =
                    child.hypotheticalOuterSize.withMain(constants.dir, hypotheticalOuterSize);
        }
    }

    /// Balanced line breaking for `flex-wrap: balance` (`mod balance`).
    ///
    /// Implements the balancing algorithm from the CSS Flexbox Level 2 draft
    /// (<https://drafts.csswg.org/css-flexbox-2/#balancing>). Items are
    /// divided into exactly `line_count` contiguous sequences (lines), where
    /// `line_count` is the number of lines that greedy line breaking would
    /// produce (with item sizes floored at zero), raised to the minimum flex
    /// line count (`flex-line-count`, clamped to the number of items), such
    /// that:
    ///
    /// - every line holds at least one item;
    /// - no line's size exceeds the container's inner main size, unless the
    ///   line holds a single (overflowing) item;
    /// - a zero-sized item is assigned to the end of the preceding line
    ///   rather than the beginning of a line, unless no valid division can
    ///   glue it there: because extending the preceding line through the item
    ///   would make it exceed the container's inner main size (which covers a
    ///   preceding single overflowing item), or because gluing leaves the
    ///   remaining lines without a valid division of the remaining items;
    /// - calling the difference between a line's size and the container's
    ///   inner main size the line's *error*, the sum of the squared errors of
    ///   all lines is minimized;
    /// - ties are broken by assigning the most items to the first line, then
    ///   the most items to the second line, and so on.
    ///
    /// Because every division has the same line count, total item size, and
    /// total gap size, minimizing the sum of squared errors is equivalent to
    /// minimizing the sum of squared *line sizes*, which is what is actually
    /// computed: it needs no target size, so an indefinite (infinite)
    /// container size needs no special handling — no line overflows, and the
    /// minimization equalizes the line sizes. All arithmetic uses `f64`:
    /// finite `f32` sizes convert exactly, and the squares and sums involved
    /// are far from `f64`'s limits, so equally-balanced divisions compare
    /// equal and are resolved by the tie-break rather than by rounding noise.
    ///
    /// The minimization is a dynamic program over (line count, first item of
    /// suffix) accelerated with the divide-and-conquer optimization, running
    /// in `O(line_count * item_count * log(item_count))` time; the naive
    /// `O(line_count * item_count²)` dynamic program is kept in the test
    /// class as an oracle.

    /// Collect flex items into flex lines (`collect_flex_lines`).
    ///
    /// # [9.3. Main Size Determination](https://www.w3.org/TR/css-flexbox-1/#main-sizing)
    ///
    /// - [**Collect flex items into flex lines**](https://www.w3.org/TR/css-flexbox-1/#algo-line-break):
    ///   greedy collect consecutive items until the first time the next item
    ///   would not fit the container's inner main size. `&mut Vec<FlexItem>`
    ///   -> `List<FlexItem>`; `split_at_mut` -> `subList` views.
    private static List<FlexLine> collectFlexLines(
            AlgoConstants constants,
            Size<AvailableSpace> availableSpace,
            List<FlexItem> flexItems) {
        // Wrapping into multiple lines requires a definite main size. If the
        // container's known main size is derived from its own content (and is
        // thus indefinite) then all items are collected into a single flex
        // line, matching how the container was sized under a min/max-content
        // constraint.
        if (!constants.isWrap || !constants.knownMainSizeIsDefinite) {
            List<FlexLine> lines = new ArrayList<>(1);
            lines.add(singleLine(flexItems));
            return lines;
        }

        AvailableSpace mainAxisAvailableSpace;
        float maxMainSize = constants.maxSize.main(constants.dir);
        if (OptF32.isSome(maxMainSize)) {
            float available =
                    OptF32.unwrapOr(availableSpace.main(constants.dir).intoOption(), maxMainSize);
            // If the container's main size is not definite then it is at
            // most the max main size, so the max size (and not the available
            // space) is the limit that items wrap against.
            if (!constants.hasDefiniteMainSize) {
                available = F32.min(available, maxMainSize);
            }
            mainAxisAvailableSpace =
                    new AvailableSpace.Definite(
                            F32.maybeMaxO(available, constants.minSize.main(constants.dir)));
        } else if (!constants.dir.isRow()
                && !constants.hasDefiniteMainSize
                && availableSpace.main(constants.dir).isDefinite()) {
            // A column container's automatic main size is content-based, so
            // definite available space handed down by an ancestor does not
            // constrain where its lines wrap. Automatic widths resolve
            // against available space, so rows do wrap against it.
            mainAxisAvailableSpace = AvailableSpace.MAX_CONTENT;
        } else {
            mainAxisAvailableSpace = availableSpace.main(constants.dir);
        }

        if (mainAxisAvailableSpace instanceof AvailableSpace.MaxContent) {
            // If we're sizing under a max-content constraint then the flex
            // items will never wrap (at least for now - future extensions to
            // the CSS spec may add provisions for forced wrap points)
            List<FlexLine> lines = new ArrayList<>(1);
            lines.add(singleLine(flexItems));
            return lines;
        }
        if (mainAxisAvailableSpace instanceof AvailableSpace.MinContent) {
            // If flex-wrap is Wrap and we're sizing under a min-content
            // constraint, then we take every possible wrapping opportunity
            // and place each item in it's own line
            List<FlexLine> lines = new ArrayList<>(flexItems.size());
            for (int i = 0; i < flexItems.size(); i++) {
                lines.add(singleLine(flexItems.subList(i, i + 1)));
            }
            return lines;
        }

        float limit = ((AvailableSpace.Definite) mainAxisAvailableSpace).value();
        List<FlexLine> lines = new ArrayList<>(1);
        float mainAxisGap = constants.gap.main(constants.dir);
        int offset = 0;
        while (offset < flexItems.size()) {
            // Find index of the first item in the next line (or the last item
            // if all remaining items are in the current line)
            float lineLength = 0.0f;
            int index = flexItems.size() - offset;
            for (int i = offset; i < flexItems.size(); i++) {
                // Gaps only occur between items (not before the first one or
                // after the last one), so the first item in the line does not
                // contribute a gap to the line length
                float gapContribution = i == offset ? 0.0f : mainAxisGap;
                lineLength += flexItems.get(i).hypotheticalOuterSize.main(constants.dir)
                        + gapContribution;
                if (lineLength > limit && i != offset) {
                    index = i - offset;
                    break;
                }
            }
            lines.add(singleLine(flexItems.subList(offset, offset + index)));
            offset += index;
        }
        return lines;
    }

    private static FlexLine singleLine(List<FlexItem> items) {
        FlexLine line = new FlexLine();
        line.items = items;
        line.crossSize = 0.0f;
        line.offsetCross = 0.0f;
        return line;
    }

    /// Collect flex items into balanced flex lines (`flex-wrap: balance`),
    /// such that the largest line is as small as possible
    /// (`collect_balanced_flex_lines`, `flexbox_balance`-gated in Rust;
    /// unconditional here).
    ///
    /// See <https://drafts.csswg.org/css-flexbox-2/#balance-values>
    private static List<FlexLine> collectBalancedFlexLines(
            AlgoConstants constants,
            Size<AvailableSpace> availableSpace,
            List<FlexItem> flexItems) {
        if (flexItems.isEmpty()) {
            return new ArrayList<>(0);
        }

        // If the container's known main size is derived from its own content
        // (and is thus indefinite) then items are balanced without a size
        // limit, matching how the container was sized under a min/max-content
        // constraint.
        AvailableSpace mainAxisAvailableSpace;
        if (constants.knownMainSizeIsDefinite) {
            float maxMainSize = constants.maxSize.main(constants.dir);
            if (OptF32.isSome(maxMainSize)) {
                float available =
                        OptF32.unwrapOr(
                                availableSpace.main(constants.dir).intoOption(), maxMainSize);
                // If the container's main size is not definite then it is at
                // most the max main size, so the max size (and not the
                // available space) is the limit that items wrap against.
                if (!constants.hasDefiniteMainSize) {
                    available = F32.min(available, maxMainSize);
                }
                mainAxisAvailableSpace =
                        new AvailableSpace.Definite(
                                F32.maybeMaxO(available, constants.minSize.main(constants.dir)));
            } else if (!constants.dir.isRow()
                    && !constants.hasDefiniteMainSize
                    && availableSpace.main(constants.dir).isDefinite()) {
                // A column container's automatic main size is content-based,
                // so definite available space handed down by an ancestor
                // does not constrain where its lines wrap. Automatic widths
                // resolve against available space, so rows do wrap against
                // it.
                mainAxisAvailableSpace = AvailableSpace.MAX_CONTENT;
            } else {
                mainAxisAvailableSpace = availableSpace.main(constants.dir);
            }
        } else {
            mainAxisAvailableSpace = AvailableSpace.MAX_CONTENT;
        }

        // If we're sizing under a min-content constraint then we take every
        // possible wrapping opportunity and place each item in its own line,
        // the same as greedy wrapping (the min-content main size of a
        // multi-line container is the size of its largest item)
        if (mainAxisAvailableSpace instanceof AvailableSpace.MinContent) {
            List<FlexLine> lines = new ArrayList<>(flexItems.size());
            for (int i = 0; i < flexItems.size(); i++) {
                lines.add(singleLine(flexItems.subList(i, i + 1)));
            }
            return lines;
        }

        float lineBreakSize =
                OptF32.unwrapOr(
                        mainAxisAvailableSpace.intoOption(), Float.POSITIVE_INFINITY);
        int minLineCount = constants.lineCount != OptI32.NONE ? constants.lineCount : 1;
        float[] itemSizes = new float[flexItems.size()];
        for (int i = 0; i < flexItems.size(); i++) {
            itemSizes[i] = flexItems.get(i).hypotheticalOuterSize.main(constants.dir);
        }
        int[] itemCounts =
                Balance.balancedLineItemCounts(
                        itemSizes, lineBreakSize, constants.gap.main(constants.dir), minLineCount);

        List<FlexLine> lines = new ArrayList<>(itemCounts.length);
        int offset = 0;
        for (int count : itemCounts) {
            lines.add(singleLine(flexItems.subList(offset, offset + count)));
            offset += count;
        }
        return lines;
    }

    /// Determine the container's main size (`determine_container_main_size`).
    ///
    /// # [9.4. Main Size Determination](https://www.w3.org/TR/css-flexbox-1/#main-sizing)
    ///
    /// - [**Determine the main size of the flex container**](https://www.w3.org/TR/css-flexbox-1/#algo-main-container)
    ///   using the rules of the formatting context in which it participates:
    private static void determineContainerMainSize(
            LayoutFlexboxContainer tree,
            Size<AvailableSpace> availableSpace,
            List<FlexLine> lines,
            AlgoConstants constants) {
        FlexDirection dir = constants.dir;
        float mainContentBoxInset = constants.contentBoxInset.mainAxisSum(dir);

        // `node_outer_size.main().unwrap_or_else(...)` -> explicit isSome
        float outerMainSize;
        if (OptF32.isSome(constants.nodeOuterSize.main(dir))) {
            outerMainSize = constants.nodeOuterSize.main(dir);
        } else {
            AvailableSpace mainSpace = availableSpace.main(dir);
            if (mainSpace instanceof AvailableSpace.Definite definite) {
                float mainAxisAvailableSpace = definite.value();
                float mainAxisGap = constants.gap.main(dir);

                float longestLineLength = 0.0f;
                boolean any = false;
                for (FlexLine line : lines) {
                    float lineMainAxisGap = sumAxisGaps(mainAxisGap, line.items.size());
                    float totalTargetSize = 0.0f;
                    for (FlexItem child : line.items) {
                        totalTargetSize += itemMainLength(child, constants);
                    }
                    float lineLength = totalTargetSize + lineMainAxisGap;
                    if (!any || Float.compare(lineLength, longestLineLength) > 0) {
                        longestLineLength = lineLength;
                        any = true;
                    }
                }
                if (!any) {
                    longestLineLength = 0.0f;
                }
                float size = longestLineLength + mainContentBoxInset;

                // A balanced container can produce multiple lines that all
                // fit within the available space (via `flex-line-count`), in
                // which case fit-content sizing uses its max-content size:
                // the longest line when items are balanced across the minimum
                // line count without a size limit. (`flexbox_balance`-gated
                // in Rust; unconditional here.)
                if (constants.isBalance) {
                    int minLineCount = constants.lineCount != OptI32.NONE ? constants.lineCount : 1;
                    int itemCount = 0;
                    for (FlexLine line : lines) {
                        itemCount += line.items.size();
                    }
                    if (itemCount == 0) {
                        outerMainSize = size;
                    } else {
                        float[] itemLengths = new float[itemCount];
                        int i = 0;
                        for (FlexLine line : lines) {
                            for (FlexItem child : line.items) {
                                itemLengths[i++] = itemMainLength(child, constants);
                            }
                        }
                        int[] itemCounts =
                                Balance.balancedLineItemCounts(
                                        itemLengths,
                                        Float.POSITIVE_INFINITY,
                                        mainAxisGap,
                                        Balance.iMax(minLineCount, 1));
                        float widestLineLength = 0.0f;
                        int index = 0;
                        for (int count : itemCounts) {
                            float lineLength = sumAxisGaps(mainAxisGap, count);
                            for (int j = index; j < index + count; j++) {
                                lineLength += itemLengths[j];
                            }
                            widestLineLength = F32.max(widestLineLength, lineLength);
                            index += count;
                        }
                        float maxContentSize = widestLineLength + mainContentBoxInset;
                        outerMainSize =
                                F32.max(size, F32.min(maxContentSize, mainAxisAvailableSpace));
                    }
                } else if (lines.size() > 1) {
                    outerMainSize = F32.max(size, mainAxisAvailableSpace);
                } else {
                    outerMainSize = size;
                }
            } else if (mainSpace instanceof AvailableSpace.MinContent && constants.isWrap) {
                float longestLineLength = 0.0f;
                boolean any = false;
                for (FlexLine line : lines) {
                    float lineMainAxisGap =
                            sumAxisGaps(constants.gap.main(dir), line.items.size());
                    float totalTargetSize = 0.0f;
                    for (FlexItem child : line.items) {
                        totalTargetSize += itemMainLength(child, constants);
                    }
                    float lineLength = totalTargetSize + lineMainAxisGap;
                    if (!any || Float.compare(lineLength, longestLineLength) > 0) {
                        longestLineLength = lineLength;
                        any = true;
                    }
                }
                if (!any) {
                    longestLineLength = 0.0f;
                }
                outerMainSize = longestLineLength + mainContentBoxInset;
            } else {
                // MinContent (nowrap) | MaxContent. Define a base main_size
                // variable. This is mutated once for iteration over the outer
                // loop over the flex lines as:
                //   "The flex container’s max-content size is the largest sum
                //   of the afore-calculated sizes of all items within a
                //   single line."
                float mainSize = 0.0f;

                for (FlexLine line : lines) {
                    for (FlexItem item : line.items) {
                        float styleMin = item.minSize.main(dir);
                        float marginSum = item.margin.mainAxisSum(dir);
                        float stylePreferred = item.size.main(dir);
                        float styleMax = item.maxSize.main(dir);

                        // The spec seems a bit unclear on this point (my
                        // initial reading was that the `.maybe_max(style_preferred)`
                        // should not be included here), however for row
                        // containers this matches both Chrome and Firefox as
                        // of 9th March 2023. For column containers the flex
                        // base size alone is the clamping basis (as the spec
                        // says): a column item's `height` must not inflate
                        // the container's max-content size beyond the item's
                        // flex-basis.
                        //
                        // Spec: https://www.w3.org/TR/css-flexbox-1/#intrinsic-item-contributions
                        // Spec modification: https://www.w3.org/TR/css-flexbox-1/#change-2016-max-contribution
                        // Issue: https://github.com/w3c/csswg-drafts/issues/1435
                        // Gentest: padding_border_overrides_size_flex_basis_0.html
                        // Gentest: blockflex_min_content_ignores_own_height_as_percentage_basis.html
                        float clampingBasis =
                                constants.isRow
                                        ? OptF32.maybeMaxO(item.flexBasis, stylePreferred)
                                        : item.flexBasis;
                        float flexBasisMin =
                                OptF32.filter(clampingBasis, v -> item.flexShrink == 0.0f);
                        float flexBasisMax =
                                OptF32.filter(clampingBasis, v -> item.flexGrow == 0.0f);

                        float minMainSize =
                                F32.max(
                                        OptF32.unwrapOr(
                                                OptF32.or(
                                                        OptF32.maybeMaxO(styleMin, flexBasisMin),
                                                        flexBasisMin),
                                                item.resolvedMinimumMainSize),
                                        item.resolvedMinimumMainSize);
                        float maxMainSize =
                                OptF32.unwrapOr(
                                        OptF32.or(
                                                OptF32.maybeMinO(styleMax, flexBasisMax),
                                                flexBasisMax),
                                        Float.POSITIVE_INFINITY);

                        float contentContribution;
                        // `match (min_main_size, style_preferred, max_main_size)`
                        // -> if-chain (guards read top to bottom)
                        if (OptF32.isSome(stylePreferred)
                                && (maxMainSize <= minMainSize
                                        || maxMainSize <= stylePreferred)) {
                            // If the clamping values are such that max <=
                            // min, then we can avoid the expensive step of
                            // computing the content size as we know that the
                            // clamping values will override it anyway
                            contentContribution =
                                    F32.max(F32.min(stylePreferred, maxMainSize), minMainSize)
                                            + marginSum;
                        } else if (maxMainSize <= minMainSize) {
                            contentContribution = minMainSize + marginSum;
                        } else if (item.isScrollContainer()) {
                            contentContribution = item.flexBasis + marginSum;
                        } else if (OptF32.isSome(stylePreferred)) {
                            // If the item has a definite preferred main size
                            // then that is its content contribution (an
                            // inherent-size measure of the item would return
                            // it), floored by the item's main-axis
                            // padding+border, so measuring the item can be
                            // skipped. Min/max clamping is applied in the
                            // same way as for measured contributions.
                            float itemPbMain =
                                    item.padding.mainAxisSum(dir) + item.border.mainAxisSum(dir);
                            float innerMainSize = F32.max(stylePreferred, itemPbMain);
                            if (constants.isRow) {
                                contentContribution =
                                        F32.maybeClampO(
                                                innerMainSize + marginSum, styleMin, styleMax);
                            } else {
                                contentContribution =
                                        F32.maybeClampO(
                                                F32.max(innerMainSize, item.flexBasis) + marginSum,
                                                styleMin,
                                                styleMax);
                            }
                        } else {
                            // Parent size for child sizing
                            float crossAxisParentSize = constants.nodeInnerSize.cross(dir);

                            // Available space for child sizing (excluding the
                            // child's margins)
                            float childMinCross = item.minSize.cross(dir);
                            float childMaxCross = item.maxSize.cross(dir);
                            AvailableSpace crossAxisAvailableSpace =
                                    availableSpace
                                            .cross(dir)
                                            .mapDefiniteValue(
                                                    val ->
                                                            F32.max(
                                                                    constants.dividedCrossSpace(
                                                                                    OptF32
                                                                                            .unwrapOr(
                                                                                                    crossAxisParentSize,
                                                                                                    val))
                                                                            - item.margin
                                                                                    .crossAxisSum(
                                                                                            dir),
                                                                    0.0f))
                                            .maybeClampO(childMinCross, childMaxCross);

                            Size<AvailableSpace> childAvailableSpace =
                                    availableSpace.withCross(dir, crossAxisAvailableSpace);

                            // Known dimensions for child sizing
                            SizeOptF32 ckd = item.size.withMain(dir, OptF32.NONE);
                            // Clamp the definite cross size by the cross
                            // min/max sizes, as `determine_flex_base_size`
                            // does: an item measured wider than its max-width
                            // wraps onto fewer lines and under-reports its
                            // height.
                            SizeOptF32 transferredMinSize =
                                    item.minSize.maybeApplyAspectRatio(item.aspectRatio);
                            SizeOptF32 transferredMaxSize =
                                    item.maxSize.maybeApplyAspectRatio(item.aspectRatio);
                            ckd =
                                    ckd.withCross(
                                            dir,
                                            F32.maybeClampO(
                                                    ckd.cross(dir),
                                                    transferredMinSize.cross(dir),
                                                    transferredMaxSize.cross(dir)));
                            if (item.alignSelf.equals(AlignItems.STRETCH)
                                    && OptF32.isNone(ckd.cross(dir))) {
                                ckd =
                                        ckd.withCross(
                                                dir, crossAxisAvailableSpace.intoOption());
                            }
                            SizeOptF32 childKnownDimensions = ckd;

                            // Either the min- or max- content size depending
                            // on which constraint we are sizing under.
                            // TODO: Optimise by using already computed values
                            // where available
                            float measuredMainSize =
                                    tree.measureChildSize(
                                            item.node,
                                            childKnownDimensions,
                                            constants.nodeInnerSize,
                                            childAvailableSpace,
                                            SizingMode.CONTENT_SIZE,
                                            dir.mainAxis(),
                                            LineBool.FALSE);

                            // A known cross size is transferred through the
                            // item's aspect-ratio and floors the measured
                            // content size
                            float transferredMainSize;
                            if (OptF32.isSome(item.aspectRatio)
                                    && OptF32.isSome(childKnownDimensions.cross(dir))) {
                                float ratio = item.aspectRatio;
                                float cross = childKnownDimensions.cross(dir);
                                transferredMainSize =
                                        constants.isRow ? cross * ratio : cross / ratio;
                            } else {
                                transferredMainSize = OptF32.NONE;
                            }

                            float innerMainSize =
                                    F32.maybeMaxO(measuredMainSize, transferredMainSize);

                            // This is somewhat bizarre in that it's
                            // asymmetrical depending whether the flex
                            // container is a column or a row.
                            //
                            // I *think* this might relate to
                            // https://drafts.csswg.org/css-flexbox-1/#algo-main-container:
                            //
                            //    "The automatic block size of a block-level
                            //    flex container is its max-content size."
                            //
                            // Which could suggest that flex-basis defining a
                            // vertical size does not shrink because it is in
                            // the block axis, and the automatic size in the
                            // block axis is a MAX content size. Whereas a
                            // flex-basis defining a horizontal size does
                            // shrink because the automatic size in inline
                            // axis is MIN content size (although I don't have
                            // a reference for that).
                            //
                            // Ultimately, this was not found by reading the
                            // spec, but by trial and error fixing tests to
                            // align with Webkit/Firefox output. (see the
                            // `flex_basis_unconstraint_row` and
                            // `flex_basis_uncontraint_column` generated tests
                            // which demonstrate this)
                            if (constants.isRow) {
                                contentContribution =
                                        F32.maybeClampO(
                                                innerMainSize + marginSum, styleMin, styleMax);
                            } else {
                                contentContribution =
                                        F32.maybeClampO(
                                                F32.max(innerMainSize, item.flexBasis) + marginSum,
                                                styleMin,
                                                styleMax);
                            }
                        }

                        float diff = contentContribution - marginSum - item.flexBasis;
                        if (diff > 0.0f) {
                            item.contentFlexFraction = diff / F32.max(1.0f, item.flexGrow);
                        } else if (diff < 0.0f) {
                            float scaledShrinkFactor =
                                    F32.max(1.0f, item.flexShrink) * item.innerFlexBasis;
                            item.contentFlexFraction = diff / scaledShrinkFactor;
                        } else {
                            // We are assuming that diff is 0.0 here and that
                            // we haven't accidentally introduced a NaN
                            item.contentFlexFraction = 0.0f;
                        }
                    }

                    // TODO Spec says to scale everything by the line's max
                    // flex fraction. But neither Chrome nor firefox implement
                    // this so we don't either. But if we did want to, we'd
                    // need this computation here (and to use it below):
                    //
                    // Within each line, find the largest max-content flex
                    // fraction among all the flex items.
                    // let line_flex_fraction = line
                    //     .items
                    //     .iter()
                    //     .map(|item| item.content_flex_fraction)
                    //     .max_by(|a, b| a.total_cmp(b))
                    //     .unwrap_or(0.0); // Unwrap case never gets hit
                    //                       // because there is always at least
                    //                       // one item a line

                    // Add each item’s flex base size to the product of:
                    //   - its flex grow factor (or scaled flex shrink factor,
                    //     if the chosen max-content flex fraction was
                    //     negative)
                    //   - the chosen max-content flex fraction
                    // then clamp that result by the max main size floored by
                    // the min main size.
                    //
                    // The flex container’s max-content size is the largest
                    // sum of the afore-calculated sizes of all items within a
                    // single line. A wrapping row container is at least as
                    // large as its largest item (which matters when another
                    // item has a negative margin). This matches Chrome.
                    // Firefox does not apply this floor.
                    boolean floorByLargestItem = constants.isWrap && constants.isRow;
                    float largestItemSize = 0.0f;
                    float itemMainSizeSum = 0.0f;
                    for (FlexItem item : line.items) {
                        float flexFraction = item.contentFlexFraction;
                        float flexContribution;
                        if (item.contentFlexFraction > 0.0f) {
                            flexContribution = F32.max(1.0f, item.flexGrow) * flexFraction;
                        } else if (item.contentFlexFraction < 0.0f) {
                            float scaledShrinkFactor =
                                    F32.max(1.0f, item.flexShrink) * item.innerFlexBasis;
                            flexContribution =
                                    scaledShrinkFactor == 0.0f
                                            ? 0.0f
                                            : scaledShrinkFactor * flexFraction;
                        } else {
                            flexContribution = 0.0f;
                        }
                        float size =
                                item.flexBasis + flexContribution + item.margin.mainAxisSum(dir);
                        if (floorByLargestItem) {
                            largestItemSize = F32.max(largestItemSize, size);
                        }
                        itemMainSizeSum += size;
                    }

                    float gapSum = sumAxisGaps(constants.gap.main(dir), line.items.size());
                    mainSize =
                            F32.max(
                                    mainSize,
                                    F32.max(largestItemSize, itemMainSizeSum + gapSum));
                }

                outerMainSize = mainSize + mainContentBoxInset;
            }
        }

        outerMainSize =
                F32.max(
                        F32.maybeClampO(
                                outerMainSize,
                                constants.minSize.main(dir),
                                constants.maxSize.main(dir)),
                        mainContentBoxInset - constants.scrollbarGutter.main(dir));

        float innerMainSize = F32.max(outerMainSize - mainContentBoxInset, 0.0f);
        constants.containerSize = constants.containerSize.withMain(dir, outerMainSize);
        constants.innerContainerSize = constants.innerContainerSize.withMain(dir, innerMainSize);
        constants.nodeInnerSize = constants.nodeInnerSize.withMain(dir, innerMainSize);
    }

    /// `item_main_length` closure from `determine_container_main_size`:
    /// the item's flex base size (floored by its min main size), plus its
    /// margins, floored by its main-axis padding+border sum.
    private static float itemMainLength(FlexItem child, AlgoConstants constants) {
        float paddingBorderSum = child.padding.plus(child.border).mainAxisSum(constants.dir);
        return F32.max(
                F32.maybeMaxO(child.flexBasis, child.minSize.main(constants.dir))
                        + child.margin.mainAxisSum(constants.dir),
                paddingBorderSum);
    }

    /// Resolve the flexible lengths of the items within a flex line.
    /// Sets the `main` component of each item's `target_size` and
    /// `outer_target_size`
    ///
    /// # [9.7. Resolving Flexible Lengths](https://www.w3.org/TR/css-flexbox-1/#resolve-flexible-lengths)
    private static void resolveFlexibleLengths(FlexLine line, AlgoConstants constants) {
        float totalMainAxisGap = sumAxisGaps(constants.gap.main(constants.dir), line.items.size());

        // 1. Determine the used flex factor. Sum the outer hypothetical main
        //    sizes of all items on the line. If the sum is less than the flex
        //    container’s inner main size, use the flex grow factor for the
        //    rest of this algorithm; otherwise, use the flex shrink factor.

        float totalHypotheticalOuterMainSize = 0.0f;
        for (FlexItem child : line.items) {
            totalHypotheticalOuterMainSize += child.hypotheticalOuterSize.main(constants.dir);
        }
        float usedFlexFactor = totalMainAxisGap + totalHypotheticalOuterMainSize;
        float innerMain = OptF32.unwrapOr(constants.nodeInnerSize.main(constants.dir), 0.0f);
        boolean growing = usedFlexFactor < innerMain;
        boolean shrinking = usedFlexFactor > innerMain;
        boolean exactlySized = !growing & !shrinking;

        // 2. Size inflexible items. Freeze, setting its target main size to
        //    its hypothetical main size
        //    - Any item that has a flex factor of zero
        //    - If using the flex grow factor: any item that has a flex base
        //      size greater than its hypothetical main size
        //    - If using the flex shrink factor: any item that has a flex base
        //      size smaller than its hypothetical main size

        for (FlexItem child : line.items) {
            float innerTargetSize = child.hypotheticalInnerSize.main(constants.dir);
            child.targetSize = child.targetSize.withMain(constants.dir, innerTargetSize);

            if (exactlySized
                    || (child.flexGrow == 0.0f && child.flexShrink == 0.0f)
                    || (growing
                            && child.flexBasis > child.hypotheticalInnerSize.main(constants.dir))
                    || (shrinking
                            && child.flexBasis < child.hypotheticalInnerSize.main(constants.dir))) {
                child.frozen = true;
                float outerTargetSize = innerTargetSize + child.margin.mainAxisSum(constants.dir);
                child.outerTargetSize =
                        child.outerTargetSize.withMain(constants.dir, outerTargetSize);
            }
        }

        if (exactlySized) {
            return;
        }

        // 3. Calculate initial free space. Sum the outer sizes of all items
        //    on the line, and subtract this from the flex container’s inner
        //    main size. For frozen items, use their outer target main size;
        //    for other items, use their outer flex base size.

        float usedSpace = totalMainAxisGap;
        for (FlexItem child : line.items) {
            usedSpace +=
                    child.frozen
                            ? child.outerTargetSize.main(constants.dir)
                            : child.flexBasis + child.margin.mainAxisSum(constants.dir);
        }

        float initialFreeSpace =
                OptF32.unwrapOr(
                        OptF32.maybeSubF(constants.nodeInnerSize.main(constants.dir), usedSpace),
                        0.0f);

        // 4. Loop

        while (true) {
            // a. Check for flexible items. If all the flex items on the line
            //    are frozen, free space has been distributed; exit this loop.
            //    The space used by the items and the sums of the unfrozen
            //    items' flex factors (both needed for step b) are computed in
            //    the same pass.

            boolean allFrozen = true;
            float itemsSpace = 0.0f;
            float sumFlexGrow = 0.0f;
            float sumFlexShrink = 0.0f;
            for (FlexItem child : line.items) {
                if (child.frozen) {
                    itemsSpace += child.outerTargetSize.main(constants.dir);
                } else {
                    allFrozen = false;
                    itemsSpace += child.flexBasis + child.margin.mainAxisSum(constants.dir);
                    sumFlexGrow += child.flexGrow;
                    sumFlexShrink += child.flexShrink;
                }
            }
            if (allFrozen) {
                break;
            }

            // b. Calculate the remaining free space as for initial free
            //    space, above. If the sum of the unfrozen flex items’ flex
            //    factors is less than one, multiply the initial free space by
            //    this sum. If the magnitude of this value is less than the
            //    magnitude of the remaining free space, use this as the
            //    remaining free space.

            float innerUsedSpace = totalMainAxisGap + itemsSpace;

            float freeSpace;
            if (growing && sumFlexGrow < 1.0f) {
                freeSpace =
                        OptF32.maybeMinO(
                                initialFreeSpace * sumFlexGrow - totalMainAxisGap,
                                OptF32.maybeSubF(
                                        constants.nodeInnerSize.main(constants.dir),
                                        innerUsedSpace));
            } else if (shrinking && sumFlexShrink < 1.0f) {
                freeSpace =
                        OptF32.maybeMaxO(
                                initialFreeSpace * sumFlexShrink - totalMainAxisGap,
                                OptF32.maybeSubF(
                                        constants.nodeInnerSize.main(constants.dir),
                                        innerUsedSpace));
            } else {
                freeSpace =
                        OptF32.unwrapOr(
                                OptF32.maybeSubF(
                                        constants.nodeInnerSize.main(constants.dir),
                                        innerUsedSpace),
                                usedFlexFactor - innerUsedSpace);
            }

            // c. Distribute free space proportional to the flex factors.
            //    - If the remaining free space is zero
            //        Do Nothing
            //    - If using the flex grow factor
            //        Find the ratio of the item’s flex grow factor to the sum
            //        of the flex grow factors of all unfrozen items on the
            //        line. Set the item’s target main size to its flex base
            //        size plus a fraction of the remaining free space
            //        proportional to the ratio.
            //    - If using the flex shrink factor
            //        For every unfrozen item on the line, multiply its flex
            //        shrink factor by its inner flex base size, and note this
            //        as its scaled flex shrink factor. Find the ratio of the
            //        item’s scaled flex shrink factor to the sum of the
            //        scaled flex shrink factors of all unfrozen items on the
            //        line. Set the item’s target main size to its flex base
            //        size minus a fraction of the absolute value of the
            //        remaining free space proportional to the ratio. Note
            //        this may result in a negative inner main size; it will
            //        be corrected in the next step.
            //    - Otherwise
            //        Do Nothing

            // `f32::is_normal()` -> finite, non-zero, non-subnormal
            float absFree = Math.abs(freeSpace);
            if (absFree >= Float.MIN_NORMAL && absFree <= Float.MAX_VALUE) {
                if (growing && sumFlexGrow > 0.0f) {
                    for (FlexItem child : line.items) {
                        if (child.frozen) {
                            continue;
                        }
                        child.targetSize =
                                child.targetSize.withMain(
                                        constants.dir,
                                        child.flexBasis
                                                + freeSpace * (child.flexGrow / sumFlexGrow));
                    }
                } else if (shrinking && sumFlexShrink > 0.0f) {
                    float sumScaledShrinkFactor = 0.0f;
                    for (FlexItem child : line.items) {
                        if (!child.frozen) {
                            sumScaledShrinkFactor += child.innerFlexBasis * child.flexShrink;
                        }
                    }

                    if (sumScaledShrinkFactor > 0.0f) {
                        for (FlexItem child : line.items) {
                            if (child.frozen) {
                                continue;
                            }
                            float scaledShrinkFactor = child.innerFlexBasis * child.flexShrink;
                            child.targetSize =
                                    child.targetSize.withMain(
                                            constants.dir,
                                            child.flexBasis
                                                    + freeSpace
                                                            * (scaledShrinkFactor
                                                                    / sumScaledShrinkFactor));
                        }
                    }
                }
            }

            // d. Fix min/max violations. Clamp each non-frozen item’s target
            //    main size by its used min and max main sizes and floor its
            //    content-box size at zero. If the item’s target main size was
            //    made smaller by this, it’s a max violation. If the item’s
            //    target main size was made larger by this, it’s a min
            //    violation.

            float totalViolation = 0.0f;
            for (FlexItem child : line.items) {
                if (child.frozen) {
                    continue;
                }
                // `child.resolved_minimum_main_size.into()` — f32 -> Option<f32>
                float resolvedMinMain = child.resolvedMinimumMainSize;
                float maxMain = child.maxSize.main(constants.dir);
                float clamped =
                        F32.max(
                                F32.maybeClampO(
                                        child.targetSize.main(constants.dir),
                                        resolvedMinMain,
                                        maxMain),
                                0.0f);
                child.violation = clamped - child.targetSize.main(constants.dir);
                child.targetSize = child.targetSize.withMain(constants.dir, clamped);
                child.outerTargetSize =
                        child.outerTargetSize.withMain(
                                constants.dir,
                                child.targetSize.main(constants.dir)
                                        + child.margin.mainAxisSum(constants.dir));

                totalViolation += child.violation;
            }

            // e. Freeze over-flexed items. The total violation is the sum of
            //    the adjustments from the previous step ∑(clamped size -
            //    unclamped size). If the total violation is:
            //    - Zero
            //        Freeze all items.
            //    - Positive
            //        Freeze all the items with min violations.
            //    - Negative
            //        Freeze all the items with max violations.

            for (FlexItem child : line.items) {
                if (child.frozen) {
                    continue;
                }
                if (totalViolation > 0.0f) {
                    child.frozen = child.violation > 0.0f;
                } else if (totalViolation < 0.0f) {
                    child.frozen = child.violation < 0.0f;
                } else {
                    child.frozen = true;
                }
            }

            // f. Return to the start of this loop.
        }
    }

    /// Determine the hypothetical cross size of each item.
    ///
    /// # [9.4. Cross Size Determination](https://www.w3.org/TR/css-flexbox-1/#cross-sizing)
    ///
    /// - [**Determine the hypothetical cross size of each item**](https://www.w3.org/TR/css-flexbox-1/#algo-cross-item)
    ///   by performing layout with the used main size and the available
    ///   space, treating auto as fit-content.
    private static void determineHypotheticalCrossSize(
            LayoutFlexboxContainer tree,
            FlexLine line,
            AlgoConstants constants,
            SizeOptF32 nodeSize,
            Size<AvailableSpace> availableSpace) {
        // If the flex container is single-line and has a definite cross size
        // then that is the cross size of the line, whatever the hypothetical
        // cross sizes of the items are
        boolean lineCrossSizeIsKnown =
                !constants.isWrap && OptF32.isSome(nodeSize.cross(constants.dir));

        for (FlexItem child : line.items) {
            float paddingBorderSum =
                    child.padding.plus(child.border).crossAxisSum(constants.dir);

            // An item that will be stretched to a line whose cross size is
            // already known is not measured: its hypothetical cross size
            // would be used neither for the line nor for the item (see
            // `calculate_cross_size` and `determine_used_cross_size`)
            if (lineCrossSizeIsKnown
                    && child.isStretched(constants.dir)
                    && !child.participatesInBaselineAlignment(constants.dir)) {
                continue;
            }

            // The available space passed to the child excludes the child's
            // margins
            AvailableSpace childAvailableMain =
                    new AvailableSpace.Definite(
                            F32.max(
                                    constants.containerSize.main(constants.dir)
                                            - child.margin.mainAxisSum(constants.dir),
                                    0.0f));

            // Sizes transferred through the aspect ratio clamp the
            // hypothetical cross size
            // https://github.com/w3c/csswg-drafts/issues/10997
            float transferredMinCross =
                    child.minSize.maybeApplyAspectRatio(child.aspectRatio).cross(constants.dir);
            float transferredMaxCross =
                    child.maxSize.maybeApplyAspectRatio(child.aspectRatio).cross(constants.dir);

            float childCross =
                    OptF32.maybeMaxF(
                            F32.maybeClampO(
                                    child.size.cross(constants.dir),
                                    transferredMinCross,
                                    transferredMaxCross),
                            paddingBorderSum);

            AvailableSpace childAvailableCross =
                    availableSpace
                            .cross(constants.dir)
                            .mapDefiniteValue(
                                    val ->
                                            F32.max(
                                                    constants.dividedCrossSpace(val)
                                                            - child.margin.crossAxisSum(
                                                                    constants.dir),
                                                    0.0f))
                            .maybeClampO(transferredMinCross, transferredMaxCross)
                            .maybeMaxF(paddingBorderSum);

            // A cross size that is a sizing keyword (min-content, max-content,
            // fit-content, fit-content(...)) determines the available space
            // constraint the item is measured under. The `stretch` keyword is
            // not resolved here: it stretches to the flex line, which is
            // handled in `determine_used_cross_size`
            float crossStretchSize =
                    OptF32.maybeMaxF(
                            OptF32.maybeSubF(
                                    OptF32.map(
                                            constants.nodeInnerSize.cross(constants.dir),
                                            constants::dividedCrossSpace),
                                    child.margin.crossAxisSum(constants.dir)),
                            0.0f);
            SizingKeyword.SizingKeywordResolution crossResolution =
                    SizingKeyword.resolveSizingKeyword(
                            child.sizeStyle.cross(constants.dir),
                            crossStretchSize,
                            constants.nodeInnerSize.cross(constants.dir),
                            tree::calc);
            if (crossResolution
                    instanceof SizingKeyword.SizingKeywordResolution.Measure measure) {
                childAvailableCross = measure.availableSpace();
            }

            float childInnerCross;
            if (OptF32.isSome(childCross)) {
                childInnerCross = childCross;
            } else {
                LayoutOutput output =
                        tree.computeChildLayout(
                                child.node,
                                new LayoutInput(
                                        RunMode.COMPUTE_SIZE,
                                        SizingMode.CONTENT_SIZE,
                                        RequestedAxis.from(constants.dir.crossAxis()),
                                        new SizeOptF32(
                                                constants.isRow
                                                        ? child.targetSize.width()
                                                        : childCross,
                                                constants.isRow
                                                        ? childCross
                                                        : child.targetSize.height()),
                                        itemKnownDimensionDefiniteness(constants, child),
                                        constants.nodeInnerSize,
                                        new Size<>(
                                                constants.isRow
                                                        ? childAvailableMain
                                                        : childAvailableCross,
                                                constants.isRow
                                                        ? childAvailableCross
                                                        : childAvailableMain),
                                        LineBool.FALSE));
                childInnerCross =
                        F32.max(
                                F32.maybeClampO(
                                        output.size().getAbs(constants.dir.crossAxis()),
                                        transferredMinCross,
                                        transferredMaxCross),
                                paddingBorderSum);
            }
            float childOuterCross = childInnerCross + child.margin.crossAxisSum(constants.dir);

            child.hypotheticalInnerSize =
                    child.hypotheticalInnerSize.withCross(constants.dir, childInnerCross);
            child.hypotheticalOuterSize =
                    child.hypotheticalOuterSize.withCross(constants.dir, childOuterCross);
        }
    }

    /// Calculate the base lines of the children.
    private static void calculateChildrenBaseLines(
            LayoutFlexboxContainer tree,
            SizeOptF32 nodeSize,
            Size<AvailableSpace> availableSpace,
            List<FlexLine> flexLines,
            AlgoConstants constants) {
        // Only compute baselines for flex rows because we only support
        // baseline alignment in the cross axis where that axis is also the
        // inline axis
        // TODO: this may need revisiting if/when we support vertical writing
        // modes
        if (!constants.isRow) {
            return;
        }

        for (FlexLine line : flexLines) {
            // If a flex line has one or zero items participating in baseline
            // alignment then baseline alignment is a no-op so we skip
            int lineBaselineChildCount = 0;
            for (FlexItem child : line.items) {
                if (child.participatesInBaselineAlignment(constants.dir)) {
                    lineBaselineChildCount++;
                }
            }
            if (lineBaselineChildCount <= 1) {
                continue;
            }

            for (FlexItem child : line.items) {
                // Only calculate baselines for children participating in
                // baseline alignment
                if (!child.participatesInBaselineAlignment(constants.dir)) {
                    continue;
                }

                LayoutOutput measuredSizeAndBaselines =
                        tree.computeChildLayout(
                                child.node,
                                new LayoutInput(
                                        RunMode.PERFORM_LAYOUT,
                                        SizingMode.CONTENT_SIZE,
                                        RequestedAxis.BOTH,
                                        new SizeOptF32(
                                                constants.isRow
                                                        ? child.targetSize.width()
                                                        : child.hypotheticalInnerSize.width(),
                                                constants.isRow
                                                        ? child.hypotheticalInnerSize.height()
                                                        : child.targetSize.height()),
                                        itemKnownDimensionDefiniteness(constants, child),
                                        constants.nodeInnerSize,
                                        // The available space passed to the
                                        // child excludes the child's margins
                                        SizeAvailableSpaceExt.maybeSub(
                                                SizeAvailableSpaceExt.maybeMax(
                                                        new Size<>(
                                                                constants.isRow
                                                                        ? new AvailableSpace
                                                                                .Definite(
                                                                                constants
                                                                                        .containerSize
                                                                                        .width())
                                                                        : availableSpace
                                                                                .width()
                                                                                .maybeSet(
                                                                                        nodeSize
                                                                                                .width()),
                                                                constants.isRow
                                                                        ? availableSpace
                                                                                .height()
                                                                                .maybeSet(
                                                                                        nodeSize
                                                                                                .height())
                                                                        : new AvailableSpace
                                                                                .Definite(
                                                                                constants
                                                                                        .containerSize
                                                                                        .height())),
                                                        SizeF32.ZERO),
                                                child.margin.sumAxes()),
                                        LineBool.FALSE));

                float height = measuredSizeAndBaselines.size().height();
                float baseline =
                        OptF32.unwrapOr(
                                net.morphismmc.jaffy.compute.common.Alignment.childFirstBaseline(
                                        measuredSizeAndBaselines.baselines().first(),
                                        height,
                                        child.overflow.y()),
                                height);

                child.baseline = baseline + child.margin.top();
            }
        }
    }

    /// Distribute the remaining free space between the flex items/lines in
    /// each line (`distribute_remaining_free_space`)
    ///
    /// # [9.6. Main-Axis Alignment](https://www.w3.org/TR/css-flexbox-1/#main-alignment)
    ///
    /// - [**Distribute any remaining free space**](https://www.w3.org/TR/css-flexbox-1/#algo-main-align).
    ///   For each flex line:
    ///
    ///   1. If the remaining free space is positive and at least one
    ///      main-axis margin on this line is `auto`, distribute the free
    ///      space equally among these margins. Otherwise, set all `auto`
    ///      margins to zero.
    ///
    ///   2. Align the items along the main-axis per `justify-content`.
    private static void distributeRemainingFreeSpace(
            List<FlexLine> flexLines, AlgoConstants constants) {
        for (FlexLine line : flexLines) {
            float totalMainAxisGap =
                    sumAxisGaps(constants.gap.main(constants.dir), line.items.size());
            float usedSpace = totalMainAxisGap;
            for (FlexItem child : line.items) {
                usedSpace += child.outerTargetSize.main(constants.dir);
            }
            float freeSpace = constants.innerContainerSize.main(constants.dir) - usedSpace;
            int numAutoMargins = 0;

            for (FlexItem child : line.items) {
                if (child.marginIsAuto.mainStart(constants.dir)) {
                    numAutoMargins += 1;
                }
                if (child.marginIsAuto.mainEnd(constants.dir)) {
                    numAutoMargins += 1;
                }
            }

            if (freeSpace > 0.0f && numAutoMargins > 0) {
                float margin = freeSpace / numAutoMargins;

                for (FlexItem child : line.items) {
                    float left = child.margin.left();
                    float right = child.margin.right();
                    float top = child.margin.top();
                    float bottom = child.margin.bottom();
                    if (child.marginIsAuto.mainStart(constants.dir)) {
                        if (constants.isRow) {
                            left = margin;
                        } else {
                            top = margin;
                        }
                    }
                    if (child.marginIsAuto.mainEnd(constants.dir)) {
                        if (constants.isRow) {
                            right = margin;
                        } else {
                            bottom = margin;
                        }
                    }
                    child.margin = new RectF32(left, right, top, bottom);
                }

                // The auto margins have absorbed all of the free space,
                // leaving none for `justify-content`
                freeSpace = 0.0f;
            }

            int numItems = line.items.size();
            boolean layoutReverse = constants.dir.isReverse();
            float gap = constants.gap.main(constants.dir);
            AlignContentKeyword justifyContentMode =
                    net.morphismmc.jaffy.compute.common.Alignment.applyAlignmentFallback(
                            freeSpace, numItems, constants.justifyContent);

            // `iter_mut().rev().enumerate().for_each(justify_item)` — the
            // index is the position within the reversed sequence
            float finalFreeSpace = freeSpace;
            for (int i = 0; i < numItems; i++) {
                FlexItem child = line.items.get(layoutReverse ? numItems - 1 - i : i);
                child.offsetMain =
                        net.morphismmc.jaffy.compute.common.Alignment.computeAlignmentOffset(
                                finalFreeSpace,
                                numItems,
                                gap,
                                justifyContentMode,
                                layoutReverse,
                                i == 0);
            }
        }
    }

    /// Performs the actual layout algorithm (`final_layout_pass`).
    /// `&mut` accumulators -> `float[1]`/`RectF32[1]`. The
    /// `content_size`-gated scrollable-overflow bookkeeping is unconditional
    /// here and the overflow rect is returned.
    private static RectF32 finalLayoutPass(
            LayoutFlexboxContainer tree, List<FlexLine> flexLines, AlgoConstants constants) {
        ScrollableOverflowContainer overflowContainer =
                new ScrollableOverflowContainer(
                        constants.isScrollContainer,
                        constants
                                .containerSize
                                .minus(constants.border.sumAxes())
                                .minus(
                                        new SizeF32(
                                                constants.scrollbarGutter.x(),
                                                constants.scrollbarGutter.y())),
                        constants.scrollOriginReversed(),
                        constants.padding);
        float[] totalOffsetCross = {
            constants.isColumn && constants.layoutDirection.isRtl()
                    ? constants.containerSize.width()
                            - constants.contentBoxInset.crossEnd(constants.dir)
                    : constants.contentBoxInset.crossStart(constants.dir)
        };

        RectF32[] overflowRect = {RectF32.ZERO};

        if (constants.isWrapReverse) {
            for (int i = flexLines.size() - 1; i >= 0; i--) {
                calculateLayoutLine(
                        tree,
                        flexLines.get(i),
                        totalOffsetCross,
                        overflowRect,
                        overflowContainer,
                        constants);
            }
        } else {
            for (FlexLine line : flexLines) {
                calculateLayoutLine(
                        tree, line, totalOffsetCross, overflowRect, overflowContainer, constants);
            }
        }

        if (constants.layoutDirection.isRtl()) {
            float clientWidth =
                    F32.max(
                            constants.containerSize.width()
                                    - constants.border.horizontalAxisSum()
                                    - constants.scrollbarGutter.x(),
                            0.0f);
            overflowRect[0] =
                    overflowRect[0].union(new RectF32(0.0f, clientWidth, 0.0f, 0.0f));
        }

        if (constants.isScrollContainer) {
            float clientWidth =
                    F32.max(
                            constants.containerSize.width()
                                    - constants.border.horizontalAxisSum()
                                    - constants.scrollbarGutter.x(),
                            0.0f);
            overflowRect[0] =
                    overflowRect[0].union(
                            new RectF32(
                                    constants.layoutDirection.isRtl()
                                            ? F32.min(
                                                    clientWidth
                                                            - (constants.contentBoxInset.left()
                                                                    - constants.border.left()
                                                                    - constants.scrollbarGutter
                                                                            .x()),
                                                    0.0f)
                                            : 0.0f,
                                    constants.layoutDirection.isRtl()
                                            ? clientWidth
                                            : constants.contentBoxInset.right()
                                                    - constants.border.right()
                                                    - constants.scrollbarGutter.x(),
                                    0.0f,
                                    constants.contentBoxInset.bottom()
                                            - constants.border.bottom()
                                            - constants.scrollbarGutter.y()));
        }
        return overflowRect[0];
    }

    /// Collect out-of-flow candidates in document order: direct out-of-flow
    /// children (computing their static positions per the flexbox alignment
    /// rules) interleaved with the candidates bubbled out of each in-flow
    /// item's subtree. Final sizing and positioning happens in the shared
    /// out-of-flow positioning pass at the containing block
    /// (`collect_oof_candidates`).
    private static void collectOofCandidates(
            LayoutFlexboxContainer tree,
            long node,
            AlgoConstants constants,
            List<FlexLine> flexLines,
            OofCandidates candidates) {
        // Lines are contiguous slices of the items in document order
        // (reversal is applied to positions, not storage), so walking the
        // lines yields items sorted by `order`
        List<FlexItem> items = new ArrayList<>();
        for (FlexLine line : flexLines) {
            for (FlexItem item : line.items) {
                if (!item.oofCandidates.isEmpty()) {
                    items.add(item);
                }
            }
        }
        int itemIdx = 0;

        FlexDirection dir = constants.dir;
        SizeF32 containerSize = constants.containerSize;
        RectF32 contentBoxInset = constants.contentBoxInset;

        boolean mainAxisIsHorizontal = constants.isRow;
        boolean crossAxisIsHorizontal = !constants.isRow;
        boolean mainIsRtl = mainAxisIsHorizontal && constants.layoutDirection.isRtl();
        boolean crossIsRtl = crossAxisIsHorizontal && constants.layoutDirection.isRtl();
        boolean mainAxisFlexStartReversed = dir.isReverse() ^ mainIsRtl;
        boolean crossAxisFlexStartReversed = constants.isWrapReverse ^ crossIsRtl;

        // The static-position area in each axis is the container's content
        // box
        LineF32 mainArea =
                new LineF32(
                        contentBoxInset.mainStart(dir),
                        containerSize.main(dir) - contentBoxInset.mainEnd(dir));
        LineF32 crossArea =
                new LineF32(
                        contentBoxInset.crossStart(dir),
                        containerSize.cross(dir) - contentBoxInset.crossEnd(dir));

        for (int order = 0; order < tree.childCount(node); order++) {
            long child = tree.getChildId(node, order);
            Style childStyle = tree.getFlexboxChildStyle(child);
            Position position = childStyle.position();

            if (childStyle.boxGenerationMode() == BoxGenerationMode.NONE) {
                continue;
            }
            // In-flow item: merge the candidates bubbled out of its subtree
            if (!position.isOutOfFlow()) {
                if (itemIdx < items.size() && items.get(itemIdx).order == order) {
                    candidates.append(items.get(itemIdx).oofCandidates);
                    itemIdx++;
                }
                continue;
            }

            AlignItems alignSelf =
                    resolveNormalAlignment(
                                    childStyle.alignSelf() != null
                                            ? childStyle.alignSelf()
                                            : constants.alignItems)
                            .resolveSelfRelative(
                                    childStyle.direction(),
                                    constants.layoutDirection,
                                    constants.isColumn);

            // Main-axis static position (justify-content).
            //
            // Stretch is an invalid value for justify_content in the flexbox
            // algorithm, so we treat it as if it wasn't set (and thus we
            // default to FlexStart behaviour). Normal has already been
            // resolved to FlexStart.
            //
            // The `safe` overflow-position keyword is intentionally NOT
            // applied here, even when the abs-positioned item would overflow
            // the main axis: Chrome does not apply safe fallback to
            // `justify-content` on absolutely-positioned flex items (only the
            // cross-axis `align-self` does so). Matching the layout authority
            // over a strict spec read keeps gentest fixtures green;
            // reconsider if Chromium changes behavior.
            //
            // `start`/`end` are writing-mode relative (they flip for RTL but
            // not for reversed flex-directions), whereas `flex-start`/
            // `flex-end` and the distributed keywords' fallbacks are
            // flex-relative.
            AlignContentKeyword mainKeyword = constants.justifyContent.keyword();
            boolean mainStartPosition =
                    switch (mainKeyword) {
                        case START -> !mainIsRtl;
                        case END -> mainIsRtl;
                        default -> true;
                    };
            AxisStaticEdge mainEdge =
                    switch (mainKeyword) {
                        case SPACE_BETWEEN, NORMAL, STRETCH, FLEX_START ->
                                mainAxisFlexStartReversed
                                        ? AxisStaticEdge.END
                                        : AxisStaticEdge.START;
                        case FLEX_END -> mainAxisFlexStartReversed
                                ? AxisStaticEdge.START
                                : AxisStaticEdge.END;
                        case START, END -> mainStartPosition
                                ? AxisStaticEdge.START
                                : AxisStaticEdge.END;
                        case SPACE_EVENLY, SPACE_AROUND, CENTER -> AxisStaticEdge.CENTER;
                    };

            // Cross-axis static position (align-self).
            //
            // `start`/`end` (and `baseline`, whose static-position fallback
            // is `start`) are writing-mode relative: they flip for RTL but
            // not for `wrap-reverse`. `flex-start`/`flex-end` and the
            // `stretch` fallback are flex-relative.
            AxisStaticEdge crossEdge =
                    crossEdgeFor(alignSelf.keyword(), crossAxisFlexStartReversed, crossIsRtl);
            AxisStaticEdge crossFallback =
                    crossEdgeFor(
                            net.morphismmc.jaffy.compute.common.Alignment
                                    .resolveSelfAlignmentSafety(alignSelf, true),
                            crossAxisFlexStartReversed,
                            crossIsRtl);

            AxisStaticPosition cross =
                    new AxisStaticPosition(
                            crossArea,
                            new AxisStaticAlign(crossEdge, alignSelf.safety(), crossFallback));
            AxisStaticPosition main =
                    new AxisStaticPosition(mainArea, AxisStaticAlign.fromKeyword(mainEdge));

            Point<AxisStaticPosition> staticPosition =
                    constants.isRow ? new Point<>(main, cross) : new Point<>(cross, main);
            candidates.push(new OofCandidate(child, order, position, staticPosition));
        }
    }

    /// `cross_edge_for` closure from `collect_oof_candidates`
    private static AxisStaticEdge crossEdgeFor(
            AlignItemsKeyword keyword, boolean crossAxisFlexStartReversed, boolean crossIsRtl) {
        boolean startPosition =
                switch (keyword) {
                    case START, BASELINE -> !crossIsRtl;
                    case END -> crossIsRtl;
                    default -> true;
                };
        return switch (keyword) {
            // Stretch alignment does not apply to absolutely positioned items
            // See "Example 3" at https://www.w3.org/TR/css-flexbox-1/#abspos-items
            // Note: Stretch should be FlexStart not Start when we support
            // both
            case START, END, BASELINE -> startPosition ? AxisStaticEdge.START : AxisStaticEdge.END;
            case STRETCH, FLEX_START -> crossAxisFlexStartReversed
                    ? AxisStaticEdge.END
                    : AxisStaticEdge.START;
            case FLEX_END -> crossAxisFlexStartReversed
                    ? AxisStaticEdge.START
                    : AxisStaticEdge.END;
            case CENTER -> AxisStaticEdge.CENTER;
            // Normal is resolved to Stretch, and SelfStart/SelfEnd are
            // resolved to Start/End against the item's own direction where
            // `align_self` is read above.
            default -> throw new IllegalStateException("unresolved self/normal alignment");
        };
    }

    /// Computes the layout of a flexbox container (`compute_flexbox_layout`)
    ///
    /// This function implements the CSS Flexbox layout algorithm, based on
    /// <https://www.w3.org/TR/css-flexbox-1/>. The most authoritative
    /// description of the algorithm and its requirements is at
    /// <https://www.w3.org/TR/css-flexbox-1/#layout-algorithm>
    public static LayoutOutput computeFlexboxLayout(
            LayoutFlexboxContainer tree, long node, LayoutInput inputs) {
        SizeOptF32 knownDimensions = inputs.knownDimensions();
        SizeOptF32 parentSize = inputs.parentSize();
        RunMode runMode = inputs.runMode();
        Style style = tree.getFlexboxContainerStyle(node);

        // Pull these out earlier to avoid borrowing issues
        Contain contain = style.contain();
        float aspectRatio = style.aspectRatio();
        RectF32 padding =
                MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(
                        style.padding(), parentSize.width(), tree::calc);
        RectF32 border =
                MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(
                        style.border(), parentSize.width(), tree::calc);
        SizeF32 paddingBorderSum = padding.sumAxes().plus(border.sumAxes());
        SizeF32 boxSizingAdjustment =
                style.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSum : SizeF32.ZERO;

        SizeOptF32 minSize =
                MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                                style.minSize(), parentSize, tree::calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
        SizeOptF32 maxSize =
                MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveO(
                                style.maxSize(), parentSize, tree::calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
        SizeOptF32 clampedStyleSize =
                inputs.sizingMode() == SizingMode.INHERENT_SIZE
                        ? MaybeResolve.SizeDimensionExt.maybeResolveO(
                                        style.size(), parentSize, tree::calc)
                                .maybeApplyAspectRatio(aspectRatio)
                                .maybeAddF(boxSizingAdjustment)
                                .maybeClampO(minSize, maxSize)
                        : SizeOptF32.NONE;

        // If both min and max in a given axis are set and max <= min then
        // this determines the size in that axis
        SizeOptF32 minMaxDefiniteSize =
                minSize.zipMap(
                        maxSize,
                        (min, max) ->
                                OptF32.isSome(min) && max <= min ? min : OptF32.NONE);

        // The size of the container should be floored by the padding and
        // border
        SizeOptF32 styledBasedKnownDimensions =
                knownDimensions.or(
                        minMaxDefiniteSize.or(clampedStyleSize).maybeMaxF(paddingBorderSum));

        // Short-circuit layout if the container's size is fully determined by
        // the container's size and the run mode is ComputeSize (and thus the
        // container's size is all that we're interested in)
        if (runMode == RunMode.COMPUTE_SIZE) {
            if (OptF32.isSome(styledBasedKnownDimensions.width())
                    && OptF32.isSome(styledBasedKnownDimensions.height())) {
                return LayoutOutput.fromOuterSize(
                        new SizeF32(
                                styledBasedKnownDimensions.width(),
                                styledBasedKnownDimensions.height()));
            }

            // We can also short-circuit if the width is known and only the
            // width has been requested.
            if (inputs.axis() == RequestedAxis.HORIZONTAL) {
                if (OptF32.isSome(styledBasedKnownDimensions.width())) {
                    return LayoutOutput.fromOuterSize(
                            new SizeF32(styledBasedKnownDimensions.width(), 0.0f));
                }
            }

            if (OptF32.isSome(styledBasedKnownDimensions.width())
                    && OptF32.isSome(styledBasedKnownDimensions.height())) {
                return LayoutOutput.fromOuterSize(
                        new SizeF32(
                                styledBasedKnownDimensions.width(),
                                styledBasedKnownDimensions.height()));
            }
        }

        // Normalize the definiteness flags: they only apply to dimensions
        // which were passed in as known by the parent. Dimensions resolved
        // from the node's own style are always definite.
        SizeBool knownDimensionsAreDefinite =
                new SizeBool(
                        inputs.knownDimensionsAreDefinite().width()
                                || OptF32.isNone(knownDimensions.width()),
                        inputs.knownDimensionsAreDefinite().height()
                                || OptF32.isNone(knownDimensions.height()));

        LayoutOutput output =
                computePreliminary(
                        tree,
                        node,
                        new LayoutInput(
                                runMode,
                                inputs.sizingMode(),
                                inputs.axis(),
                                styledBasedKnownDimensions,
                                knownDimensionsAreDefinite,
                                parentSize,
                                inputs.availableSpace(),
                                inputs.verticalMarginsAreCollapsible()));

        // Layout containment suppresses the box's baseline for
        // baseline-alignment purposes
        if (contain.suppressesBaseline()) {
            output =
                    new LayoutOutput(
                            output.size(),
                            output.scrollableOverflowRect(),
                            Baselines.NONE,
                            output.topMargin(),
                            output.bottomMargin(),
                            output.marginsCanCollapseThrough(),
                            output.oofCandidates(),
                            output.oofPositioningArea());
        }

        return output;
    }

    /// Compute a preliminary size for an item (`compute_preliminary`)
    private static LayoutOutput computePreliminary(
            LayoutFlexboxContainer tree, long node, LayoutInput inputs) {
        SizeOptF32 knownDimensions = inputs.knownDimensions();
        SizeOptF32 parentSize = inputs.parentSize();
        Size<AvailableSpace> outerAvailableSpace = inputs.availableSpace();
        RunMode runMode = inputs.runMode();

        // Define some general constants we will need for the remainder of the
        // algorithm.
        AlgoConstants constants =
                computeConstants(
                        tree,
                        tree.getFlexboxContainerStyle(node),
                        knownDimensions,
                        inputs.knownDimensionsAreDefinite(),
                        parentSize,
                        outerAvailableSpace);

        // A container with no children has no flex items or lines, so its
        // size follows directly from its own style and the available space.
        if (tree.childCount(node) == 0) {
            return computeEmptyContainerLayout(tree, inputs, constants);
        }

        // 9. Flex Layout Algorithm

        // 9.1. Initial Setup

        // 1. Generate anonymous flex items as described in §4 Flex Items.
        List<FlexItem> flexItems = generateAnonymousFlexItems(tree, node, constants);

        // 9.2. Line Length Determination

        // 2. Determine the available main and cross space for the flex items
        Size<AvailableSpace> availableSpace =
                determineAvailableSpace(knownDimensions, outerAvailableSpace, constants);

        // 3. Determine the flex base size and hypothetical main size of each
        //    item.
        determineFlexBaseSize(tree, constants, availableSpace, flexItems);

        // 4. Determine the main size of the flex container
        // This has already been done as part of compute_constants. The inner
        // size is exposed as constants.node_inner_size.

        // 9.3. Main Size Determination

        // 5. Collect flex items into flex lines.
        // (`flexbox_balance`-gated dispatch in Rust; unconditional here)
        List<FlexLine> flexLines =
                constants.isBalance
                        ? collectBalancedFlexLines(constants, availableSpace, flexItems)
                        : collectFlexLines(constants, availableSpace, flexItems);

        // If container size is undefined, determine the container's main size
        // and then re-resolve gaps based on newly determined size
        if (OptF32.isSome(constants.nodeInnerSize.main(constants.dir))) {
            float innerMainSize = constants.nodeInnerSize.main(constants.dir);
            float outerMainSize =
                    innerMainSize + constants.contentBoxInset.mainAxisSum(constants.dir);
            constants.innerContainerSize =
                    constants.innerContainerSize.withMain(constants.dir, innerMainSize);
            constants.containerSize =
                    constants.containerSize.withMain(constants.dir, outerMainSize);
        } else {
            // Sets constants.container_size and constants.inner_container_size
            determineContainerMainSize(tree, availableSpace, flexLines, constants);
            constants.nodeInnerSize =
                    constants.nodeInnerSize.withMain(
                            constants.dir,
                            constants.innerContainerSize.main(constants.dir));
            constants.nodeOuterSize =
                    constants.nodeOuterSize.withMain(
                            constants.dir, constants.containerSize.main(constants.dir));

            // Re-resolve percentage gaps
            Style style = tree.getFlexboxContainerStyle(node);
            float innerContainerSize = constants.innerContainerSize.main(constants.dir);
            float newGap =
                    OptF32.unwrapOr(
                            style.gap()
                                    .main(constants.dir)
                                    .maybeResolveO(innerContainerSize, tree::calc),
                            0.0f);
            constants.gap = constants.gap.withMain(constants.dir, newGap);
        }

        // If only the container's main size has been requested then we are
        // done, as nothing below changes it. This avoids measuring the cross
        // size of every item.
        if (runMode == RunMode.COMPUTE_SIZE
                && constants.dir.mainAxisIsRequestedAxis(inputs.axis())) {
            SizeF32 size = SizeF32.ZERO.withMain(constants.dir, constants.containerSize.main(constants.dir));
            return LayoutOutput.fromOuterSize(size);
        }

        // 6. Resolve the flexible lengths of all the flex items to find their
        //    used main size.
        for (FlexLine line : flexLines) {
            resolveFlexibleLengths(line, constants);
        }

        // 9.4. Cross Size Determination

        // 7. Determine the hypothetical cross size of each item.
        for (FlexLine line : flexLines) {
            determineHypotheticalCrossSize(
                    tree, line, constants, knownDimensions, availableSpace);
        }

        // Calculate child baselines. This function is internally smart and
        // only computes child baselines if they are necessary.
        calculateChildrenBaseLines(
                tree, knownDimensions, availableSpace, flexLines, constants);

        // 8. Calculate the cross size of each flex line.
        calculateCrossSize(flexLines, knownDimensions, constants);

        // 9. Handle 'align-content: stretch'.
        handleAlignContentStretch(flexLines, knownDimensions, constants);

        // 10. Collapse visibility:collapse items. If any flex items have
        //     visibility: collapse, note the cross size of the line they’re
        //     in as the item’s strut size, and restart layout from the
        //     beginning.
        //
        //     In this second layout round, when collecting items into lines,
        //     treat the collapsed items as having zero main size. For the
        //     rest of the algorithm following that step, ignore the collapsed
        //     items entirely (as if they were display:none) except that after
        //     calculating the cross size of the lines, if any line’s cross
        //     size is less than the largest strut size among all the
        //     collapsed items in the line, set its cross size to that strut
        //     size.
        //
        //     Skip this step in the second layout round.

        // TODO implement once (if ever) we support visibility:collapse

        // 11. Determine the used cross size of each flex item.
        determineUsedCrossSize(tree, flexLines, constants);

        // 9.5. Main-Axis Alignment

        // 12. Distribute any remaining free space.
        distributeRemainingFreeSpace(flexLines, constants);

        // 9.6. Cross-Axis Alignment

        // 13. Resolve cross-axis auto margins (also includes 14).
        resolveCrossAxisAutoMargins(flexLines, constants);

        // 15. Determine the flex container’s used cross size.
        float totalLineCrossSize =
                determineContainerCrossSize(flexLines, knownDimensions, constants);

        // We have the container size.
        // If our caller does not care about performing layout we are done
        // now.
        if (runMode == RunMode.COMPUTE_SIZE) {
            return LayoutOutput.fromOuterSize(constants.containerSize);
        }

        // 16. Align all flex lines per align-content.
        alignFlexLinesPerAlignContent(flexLines, constants, totalLineCrossSize);

        // Do a final layout pass and gather the resulting layouts
        RectF32 inflowOverflowRect = finalLayoutPass(tree, flexLines, constants);

        // Collect out-of-flow candidates in document order (direct
        // out-of-flow children interleaved with candidates bubbled from
        // in-flow children's subtrees). These are laid out by the
        // out-of-flow positioning pass (`compute_oof_layout`), which runs
        // after this algorithm.
        OofCandidates candidates = OofCandidates.create();
        collectOofCandidates(tree, node, constants, flexLines, candidates);

        SizeF32 absolutePositionArea =
                constants.containerSize.minus(constants.paddingBoxInset.sumAxes());
        PointF32 absolutePositionOffset =
                new PointF32(
                        constants.paddingBoxInset.left(), constants.paddingBoxInset.top());

        int len = tree.childCount(node);
        for (int order = 0; order < len; order++) {
            long child = tree.getChildId(node, order);
            if (tree.getFlexboxChildStyle(child).boxGenerationMode()
                    == BoxGenerationMode.NONE) {
                tree.setUnroundedLayout(child, Layout.withOrder(order));
                tree.performChildLayout(
                        child,
                        SizeOptF32.NONE,
                        SizeOptF32.NONE,
                        StyleHelpers.SizeAvailableSpaceExt.maxContent(),
                        SizingMode.CONTENT_SIZE,
                        LineBool.FALSE);
            }
        }

        // 8.5. Flex Container Baselines: calculate the flex container's first
        // baseline
        // See https://www.w3.org/TR/css-flexbox-1/#flex-baselines
        // The baselines are generated from the startmost flex line, where
        // "startmost" refers to the line's visual position: for wrap-reverse
        // containers the cross axis is flipped, so the startmost line is the
        // last line in flex-line order rather than the first.
        FlexLine firstLine =
                flexLines.isEmpty()
                        ? null
                        : constants.isWrapReverse
                                ? flexLines.get(flexLines.size() - 1)
                                : flexLines.get(0);
        float firstVerticalBaseline = OptF32.NONE;
        if (firstLine != null) {
            if (constants.isColumn) {
                // For column containers the baseline is generated from the
                // startmost item in the line, which for reverse-direction
                // containers is the last item in flex order.
                if (!firstLine.items.isEmpty()) {
                    FlexItem item =
                            constants.dir.isReverse()
                                    ? firstLine.items.get(firstLine.items.size() - 1)
                                    : firstLine.items.get(0);
                    firstVerticalBaseline = item.baseline;
                }
            } else {
                FlexItem item = null;
                for (FlexItem it : firstLine.items) {
                    if (it.participatesInBaselineAlignment(constants.dir)) {
                        item = it;
                        break;
                    }
                }
                if (item == null && !firstLine.items.isEmpty()) {
                    item = firstLine.items.get(0);
                }
                if (item != null) {
                    firstVerticalBaseline = item.baseline;
                }
            }
        }

        LayoutOutput output =
                LayoutOutput.fromSizesAndBaselines(
                        constants.containerSize,
                        // `content_size` branch — unconditional
                        net.morphismmc.jaffy.compute.common.ScrollableOverflow
                                .finalizeScrollableOverflow(
                                        inflowOverflowRect,
                                        constants
                                                .containerSize
                                                .minus(constants.border.sumAxes())
                                                .minus(
                                                        new SizeF32(
                                                                constants.scrollbarGutter.x(),
                                                                constants.scrollbarGutter.y())),
                                        constants.scrollOriginReversed(),
                                        constants.isScrollContainer),
                        Baselines.fromFirst(firstVerticalBaseline));
        return new LayoutOutput(
                output.size(),
                output.scrollableOverflowRect(),
                output.baselines(),
                output.topMargin(),
                output.bottomMargin(),
                output.marginsCanCollapseThrough(),
                candidates,
                new OofPositioningArea(absolutePositionArea, absolutePositionOffset));
    }

    /// Compute the layout of a flex container with no children. Equivalent to
    /// running the remainder of {@link #computePreliminary} with zero flex
    /// items, without generating items or lines
    /// (`compute_empty_container_layout`).
    private static LayoutOutput computeEmptyContainerLayout(
            LayoutFlexboxContainer tree, LayoutInput inputs, AlgoConstants constants) {
        SizeOptF32 knownDimensions = inputs.knownDimensions();
        Size<AvailableSpace> outerAvailableSpace = inputs.availableSpace();
        RunMode runMode = inputs.runMode();
        FlexDirection dir = constants.dir;

        Size<AvailableSpace> availableSpace =
                determineAvailableSpace(knownDimensions, outerAvailableSpace, constants);

        if (OptF32.isSome(constants.nodeInnerSize.main(dir))) {
            float innerMainSize = constants.nodeInnerSize.main(dir);
            float outerMainSize = innerMainSize + constants.contentBoxInset.mainAxisSum(dir);
            constants.innerContainerSize =
                    constants.innerContainerSize.withMain(dir, innerMainSize);
            constants.containerSize = constants.containerSize.withMain(dir, outerMainSize);
        } else {
            determineContainerMainSize(
                    tree, availableSpace, java.util.Collections.emptyList(), constants);
        }

        if (runMode == RunMode.COMPUTE_SIZE && dir.mainAxisIsRequestedAxis(inputs.axis())) {
            SizeF32 size = SizeF32.ZERO.withMain(dir, constants.containerSize.main(dir));
            return LayoutOutput.fromOuterSize(size);
        }

        determineContainerCrossSize(
                java.util.Collections.emptyList(), knownDimensions, constants);

        // With no children there are no out-of-flow candidates, so the
        // positioning area's value is never read. It still has to be `Some`:
        // that is what makes `compute_oof_layout` clear a stale
        // hoisted-children list from a run in which the container still had
        // descendants.
        LayoutOutput output = LayoutOutput.fromOuterSize(constants.containerSize);
        // `content_size` branch — unconditional
        float rtlRight =
                constants.layoutDirection.isRtl()
                        ? F32.max(
                                constants.containerSize.width()
                                        - constants.border.horizontalAxisSum()
                                        - constants.scrollbarGutter.x(),
                                0.0f)
                        : 0.0f;
        net.morphismmc.jaffy.tree.layout.ScrollableOverflow scrollableOverflow =
                net.morphismmc.jaffy.compute.common.ScrollableOverflow
                        .finalizeScrollableOverflow(
                                new RectF32(0.0f, rtlRight, 0.0f, 0.0f),
                                constants
                                        .containerSize
                                        .minus(constants.border.sumAxes())
                                        .minus(
                                                new SizeF32(
                                                        constants.scrollbarGutter.x(),
                                                        constants.scrollbarGutter.y())),
                                constants.scrollOriginReversed(),
                                constants.isScrollContainer);
        return new LayoutOutput(
                output.size(),
                scrollableOverflow,
                output.baselines(),
                output.topMargin(),
                output.bottomMargin(),
                output.marginsCanCollapseThrough(),
                output.oofCandidates(),
                new OofPositioningArea(SizeF32.ZERO, PointF32.ZERO));
    }

}
