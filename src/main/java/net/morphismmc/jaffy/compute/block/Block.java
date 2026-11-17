// JAFFY: upstream=src/compute/block.rs
// JAFFY-NOTE: CSS Block layout
//
// `float_layout`/`content_size` cargo features are always on (GAP-007).
// `Option<f32>` -> `@Opt float` (GAP-006); reference Options -> `@Nullable`;
// `&mut f32` accumulators -> reassigned locals; `usize`/`u32`/`u64` ->
// `int`/`long` (GAP-014); Rust labeled blocks -> if/else chains; the
// `item_inputs` closure -> {@link #itemInputs}; the 5-tuple return of
// `perform_final_layout_on_in_flow_children` -> {@link FinalLayoutResult}.
// `item.margin` is `Rect<LengthPercentageAuto>`; `Float` in this file is
// `java.lang.Float` (item float style is reached via `item.floatValue`).
package net.morphismmc.jaffy.compute.block;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.jaffy.Features;
import net.morphismmc.jaffy.compute.common.Alignment;
import net.morphismmc.jaffy.compute.common.ScrollableOverflow;
import net.morphismmc.jaffy.compute.common.ScrollableOverflow.ScrollableOverflowContainer;
import net.morphismmc.jaffy.compute.common.SizingKeyword;
import net.morphismmc.jaffy.compute.common.SizingKeyword.SizingKeywordResolution;
import net.morphismmc.jaffy.compute.float_.BfcSlot;
import net.morphismmc.jaffy.compute.float_.FloatContext;
import net.morphismmc.jaffy.compute.float_.FloatIntrinsicWidthCalculator;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.Rect;
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
import net.morphismmc.jaffy.style.StyleHelpers.SizeAvailableSpaceExt;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.block.TextAlign;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.jaffy.tree.layout.AxisStaticPosition;
import net.morphismmc.jaffy.tree.layout.Baselines;
import net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.OofCandidate;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.layout.OofPositioningArea;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutBlockContainer;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.jaffy.util.DebugLogger;
import net.morphismmc.jaffy.util.MaybeResolve.RectLengthPercentageAutoExt;
import net.morphismmc.jaffy.util.MaybeResolve.RectLengthPercentageExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeDimensionExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeLengthPercentageAutoExt;
import net.morphismmc.jaffy.util.Sys;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongIterator;

/// Computes the layout of {@link LayoutPartialTree} according to the block
/// layout algorithm.
public final class Block {
    private Block() {}

    /// The 5-tuple returned by `perform_final_layout_on_in_flow_children`.
    private record FinalLayoutResult(
            RectF32 inflowOverflowRect,
            float contentHeight,
            CollapsibleMarginSet firstChildTopMarginSet,
            CollapsibleMarginSet lastChildBottomMarginSet,
            @Opt float firstBaseline) {}

    /// `compute_block_align_content_offset` — the offset applied to the whole
    /// in-flow content stack by a non-`normal` `align-content`.
    public static float computeBlockAlignContentOffset(AlignContent alignContent, float freeSpace) {
        AlignContentKeyword keyword = Alignment.applyAlignmentFallback(freeSpace, 1, alignContent);
        return Alignment.computeAlignmentOffset(freeSpace, 1, 0.0f, keyword, false, true);
    }

    /// Computes the layout of {@link LayoutPartialTree} according to the block
    /// layout algorithm (`compute_block_layout`).
    public static LayoutOutput computeBlockLayout(
            LayoutBlockContainer tree, long nodeId, LayoutInput inputs, @Nullable BlockContext blockCtx) {
        SizeOptF32 knownDimensions = inputs.knownDimensions();
        SizeOptF32 parentSize = inputs.parentSize();
        Style style = tree.getBlockContainerStyle(nodeId);

        // Pull these out earlier to avoid borrowing issues
        Point<Overflow> overflow = style.overflow();
        boolean isScrollContainer =
                overflow.x().isScrollContainer() || overflow.y().isScrollContainer();
        Contain contain = style.contain();
        // css-align-3 §5.1.1: a non-`normal` `align-content` makes a block
        // container establish an independent formatting context.
        // <https://drafts.csswg.org/css-align-3/#distribution-block>
        // Layout and paint containment also establish an independent
        // formatting context. <https://drafts.csswg.org/css-contain-2/#containment-layout>
        boolean establishesNewBfc = isScrollContainer
                || style.alignContent().keyword() != AlignContentKeyword.NORMAL
                || contain.establishesIndependentFormattingContext();
        @Opt float aspectRatio = style.aspectRatio();
        CalcResolver calc = tree::calc;
        RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(style.padding(), parentSize.width(), calc);
        RectF32 border = RectLengthPercentageExt.resolveOrZeroO(style.border(), parentSize.width(), calc);
        SizeF32 paddingBorderSize = padding.plus(border).sumAxes();
        SizeF32 boxSizingAdjustment = style.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSize : SizeF32.ZERO;

        SizeOptF32 minSize = SizeLengthPercentageAutoExt.maybeResolveO(style.minSize(), parentSize, calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 maxSize = SizeLengthPercentageAutoExt.maybeResolveO(style.maxSize(), parentSize, calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 clampedStyleSize = inputs.sizingMode() == SizingMode.INHERENT_SIZE
                ? SizeDimensionExt.maybeResolveO(style.size(), parentSize, calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment)
                        .maybeClampO(minSize, maxSize)
                : SizeOptF32.NONE;

        // If both min and max in a given axis are set and max <= min then this
        // determines the size in that axis
        SizeOptF32 minMaxDefiniteSize = minSize.zipMap(
                maxSize, (min, max) -> OptF32.isSome(min) && OptF32.isSome(max) && max <= min ? min : OptF32.NONE);

        SizeOptF32 styledBasedKnownDimensions =
                knownDimensions.or(minMaxDefiniteSize).or(clampedStyleSize).maybeMaxF(paddingBorderSize);

        // Short-circuit layout if the container's size is fully determined by
        // the container's size and the run mode is ComputeSize (and thus the
        // container's size is all that we're interested in)
        if (inputs.runMode() == RunMode.COMPUTE_SIZE) {
            if (OptF32.isSome(styledBasedKnownDimensions.width())
                    && OptF32.isSome(styledBasedKnownDimensions.height())) {
                return LayoutOutput.fromOuterSize(
                        new SizeF32(styledBasedKnownDimensions.width(), styledBasedKnownDimensions.height()));
            }

            // We can also short-circuit if the width is known and only the
            // width has been requested.
            if (inputs.axis() == RequestedAxis.HORIZONTAL && OptF32.isSome(styledBasedKnownDimensions.width())) {
                return LayoutOutput.fromOuterSize(new SizeF32(styledBasedKnownDimensions.width(), 0.0f));
            }
        }

        // Unwrap the block formatting context if one was passed, or else
        // create a new one
        if (Features.DEBUG) {
            DebugLogger.NODE_LOGGER.log("BLOCK");
        }
        LayoutOutput output;
        if (blockCtx != null && !establishesNewBfc) {
            output = computeInner(
                    tree,
                    nodeId,
                    new LayoutInput(
                            inputs.runMode(),
                            inputs.sizingMode(),
                            inputs.axis(),
                            styledBasedKnownDimensions,
                            inputs.knownDimensionsAreDefinite(),
                            inputs.parentSize(),
                            inputs.availableSpace(),
                            inputs.verticalMarginsAreCollapsible()),
                    blockCtx);
        } else {
            BlockFormattingContext rootBfc = BlockFormattingContext.create();
            BlockContext rootCtx = rootBfc.rootBlockContext();
            output = computeInner(
                    tree,
                    nodeId,
                    new LayoutInput(
                            inputs.runMode(),
                            inputs.sizingMode(),
                            inputs.axis(),
                            styledBasedKnownDimensions,
                            inputs.knownDimensionsAreDefinite(),
                            inputs.parentSize(),
                            inputs.availableSpace(),
                            inputs.verticalMarginsAreCollapsible()),
                    rootCtx);
        }

        // Layout containment suppresses the box's baseline for
        // baseline-alignment purposes
        if (contain.suppressesBaseline()) {
            output = new LayoutOutput(
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

    /// Computes the layout of {@link LayoutBlockContainer} according to the
    /// block layout algorithm (`compute_inner`).
    private static LayoutOutput computeInner(
            LayoutBlockContainer tree, long nodeId, LayoutInput inputs, BlockContext blockCtx) {
        SizeOptF32 inputKnownDimensions = inputs.knownDimensions();
        SizeOptF32 parentSize = inputs.parentSize();
        Size<AvailableSpace> availableSpace = inputs.availableSpace();
        RunMode runMode = inputs.runMode();
        LineBool verticalMarginsAreCollapsible = inputs.verticalMarginsAreCollapsible();

        Style style = tree.getBlockContainerStyle(nodeId);
        Rect<LengthPercentage> rawPadding = style.padding();
        Rect<LengthPercentage> rawBorder = style.border();
        Rect<LengthPercentageAuto> rawMargin = style.margin();
        @Opt float aspectRatio = style.aspectRatio();
        CalcResolver calc = tree::calc;
        RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(rawPadding, parentSize.width(), calc);
        RectF32 border = RectLengthPercentageExt.resolveOrZeroO(rawBorder, parentSize.width(), calc);
        Direction direction = style.direction();

        // Scrollbar gutters are reserved when the `overflow` property is set
        // to `Overflow::Scroll`. However, the axis are switched (transposed)
        // because a node that scrolls vertically needs *horizontal* space to
        // be reserved for a scrollbar
        RectF32 scrollbarGutter;
        {
            Point<Overflow> offsets = style.overflow().transpose();
            float offX = offsets.x() == Overflow.SCROLL ? style.scrollbarWidth() : 0.0f;
            float offY = offsets.y() == Overflow.SCROLL ? style.scrollbarWidth() : 0.0f;
            scrollbarGutter = direction == Direction.LTR
                    ? new RectF32(0.0f, offX, 0.0f, offY)
                    : new RectF32(offX, 0.0f, 0.0f, offY);
        }
        RectF32 paddingBorder = padding.plus(border);
        SizeF32 paddingBorderSize = paddingBorder.sumAxes();
        RectF32 contentBoxInset = paddingBorder.plus(scrollbarGutter);

        // Apply content box inset
        blockCtx.applyContentBoxInset(new FloatInsets(contentBoxInset.left(), contentBoxInset.right()));

        SizeF32 boxSizingAdjustment = style.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSize : SizeF32.ZERO;
        SizeOptF32 size = SizeDimensionExt.maybeResolveO(style.size(), parentSize, calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 minSize = SizeLengthPercentageAutoExt.maybeResolveO(style.minSize(), parentSize, calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 maxSize = SizeLengthPercentageAutoExt.maybeResolveO(style.maxSize(), parentSize, calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);

        // css-sizing-4: a definite size in one axis transfers through
        // `aspect-ratio` to make the other definite. Deriving it from
        // `known_dimensions` self-gates the transfer — a block parent fills an
        // axis only when it's a real constraint (e.g. the stretched width at
        // final layout) and leaves it None while probing intrinsic sizes, so
        // measure passes stay content-based. Only a newly-filled axis is
        // adopted (and clamped); an incoming known size is left as the parent
        // resolved it (re-clamping would undo padding/border overrides).
        SizeOptF32 knownDimensions;
        {
            SizeOptF32 derived =
                    inputKnownDimensions.maybeApplyAspectRatio(aspectRatio).maybeClampO(minSize, maxSize);
            knownDimensions = new SizeOptF32(
                    OptF32.or(inputKnownDimensions.width(), derived.width()),
                    OptF32.or(inputKnownDimensions.height(), derived.height()));
        }
        SizeOptF32 percentageBasisDimensions = new SizeOptF32(
                knownDimensions.width(),
                OptF32.filter(
                        knownDimensions.height(),
                        v -> inputs.knownDimensionsAreDefinite().height()));
        SizeOptF32 containerContentBoxSize = percentageBasisDimensions.maybeSubF(contentBoxInset.sumAxes());

        Point<Overflow> overflow = style.overflow();
        boolean isScrollContainer =
                overflow.x().isScrollContainer() || overflow.y().isScrollContainer();
        boolean establishesNewBfc = isScrollContainer
                || style.alignContent().keyword() != AlignContentKeyword.NORMAL
                || style.contain().establishesIndependentFormattingContext();

        // Determine margin collapsing behaviour
        //
        // Whether a child's margins could collapse through each of this box's
        // block-axis edges: the box does not establish a new BFC and has
        // neither border nor padding on that side.
        boolean outOfFlow = style.position().isOutOfFlow();
        LineBool childrenMarginsAdjoinOwnEdges = new LineBool(
                verticalMarginsAreCollapsible.start()
                        && !establishesNewBfc
                        && !outOfFlow
                        && padding.top() == 0.0f
                        && border.top() == 0.0f,
                verticalMarginsAreCollapsible.end()
                        && !establishesNewBfc
                        && !outOfFlow
                        && padding.bottom() == 0.0f
                        && border.bottom() == 0.0f);
        LineBool ownMarginsCollapseWithChildren = new LineBool(
                childrenMarginsAdjoinOwnEdges.start(),
                childrenMarginsAdjoinOwnEdges.end() && OptF32.isNone(size.height()));
        boolean hasStylesPreventingBeingCollapsedThrough = !style.isBlock()
                || blockCtx.isBfcRoot()
                || establishesNewBfc
                || outOfFlow
                || padding.top() > 0.0f
                || padding.bottom() > 0.0f
                || border.top() > 0.0f
                || border.bottom() > 0.0f;

        TextAlign textAlign = style.textAlign();
        AlignContent alignContent = style.alignContent();
        AlignItems justifyItems = style.justifyItems();

        // 1. Generate items
        List<BlockItem> items = generateItemList(tree, nodeId, containerContentBoxSize);

        // 2. Compute container width
        float containerOuterWidth;
        if (OptF32.isSome(knownDimensions.width())) {
            containerOuterWidth = knownDimensions.width();
        } else {
            AvailableSpace availableWidth = availableSpace.width().maybeSubF(contentBoxInset.horizontalAxisSum());
            float intrinsicWidth = determineContentBasedContainerWidth(tree, items, availableWidth)
                    + contentBoxInset.horizontalAxisSum();
            containerOuterWidth = F32.maybeMaxO(
                    F32.maybeClampO(intrinsicWidth, minSize.width(), maxSize.width()), paddingBorderSize.width());
        }

        // Short-circuit if computing size and both dimensions known
        if (runMode == RunMode.COMPUTE_SIZE && OptF32.isSome(knownDimensions.height())) {
            return LayoutOutput.fromOuterSize(new SizeF32(containerOuterWidth, knownDimensions.height()));
        }

        // We can also short-circuit if the width is known and only the width
        // has been requested.
        if (runMode == RunMode.COMPUTE_SIZE && inputs.axis() == RequestedAxis.HORIZONTAL) {
            return LayoutOutput.fromOuterSize(new SizeF32(containerOuterWidth, 0.0f));
        }

        // Under `SizingMode::ContentSize` the container's own
        // `height`/`min-height` are ignored, so they cannot serve as the
        // percentage basis for children either
        // (https://drafts.csswg.org/css-flexbox/#min-size-auto: the content
        // size suggestion must not be influenced by the item's specified
        // height).
        @Opt
        float containerPercentageResolutionHeight = OptF32.or(
                percentageBasisDimensions.height(),
                inputs.sizingMode() == SizingMode.INHERENT_SIZE
                        ? OptF32.maybeMaxO(size.height(), minSize.height())
                        : OptF32.NONE);

        // 3. Perform final item layout and return content height
        //
        // Percentage padding and borders resolve against the *containing
        // block's* width (`parent_size`), not the box's own width. These only
        // differ when the box has a non-stretch width. Fall back to the box's
        // own width when the parent size is unknown (e.g. at the root of the
        // layout tree).
        float percentageResolutionWidth = OptF32.unwrapOr(parentSize.width(), containerOuterWidth);
        RectF32 resolvedPadding = RectLengthPercentageExt.resolveOrZeroO(rawPadding, percentageResolutionWidth, calc);
        RectF32 resolvedBorder = RectLengthPercentageExt.resolveOrZeroO(rawBorder, percentageResolutionWidth, calc);
        RectF32 resolvedContentBoxInset = resolvedPadding.plus(resolvedBorder).plus(scrollbarGutter);
        FinalLayoutResult result = performFinalLayoutOnInFlowChildren(
                tree,
                runMode,
                items,
                containerOuterWidth,
                containerPercentageResolutionHeight,
                contentBoxInset,
                resolvedContentBoxInset,
                resolvedBorder,
                resolvedPadding,
                textAlign,
                justifyItems,
                direction,
                ownMarginsCollapseWithChildren,
                childrenMarginsAdjoinOwnEdges,
                isScrollContainer,
                blockCtx);

        RectF32 inflowOverflowRect = result.inflowOverflowRect();
        float intrinsicOuterHeight = result.contentHeight();
        @Opt float firstBaseline = result.firstBaseline();

        // Root BFCs contain floats: the content height extends to the bottom
        // margin edge of any floated descendant, and the container's bottom
        // padding and border sit below that
        if (blockCtx.isBfcRoot() || establishesNewBfc) {
            intrinsicOuterHeight = Sys.f32Max(
                    intrinsicOuterHeight,
                    blockCtx.floatedContentHeightContribution() + resolvedContentBoxInset.bottom());
        }

        float containerOuterHeight = F32.maybeMaxO(
                OptF32.unwrapOr(
                        knownDimensions.height(),
                        F32.maybeClampO(intrinsicOuterHeight, minSize.height(), maxSize.height())),
                paddingBorderSize.height());
        SizeF32 finalOuterSize = new SizeF32(containerOuterWidth, containerOuterHeight);

        // CSS2 §8.3.1: the bottom margin of a block with `height: auto`
        // collapses with its last in-flow child's bottom margin only if the
        // box's `min-height` is less than the box's used height. When
        // `min-height` determines the used height, the last child's bottom
        // margin no longer adjoins the box's bottom edge, so it stays inside
        // the box instead of collapsing with the box's own bottom margin.
        // (`max-height` has no such effect.)
        boolean heightConstrainedByMinHeight =
                OptF32.isSome(minSize.height()) && minSize.height() > 0.0f && minSize.height() >= containerOuterHeight;
        boolean ownBottomMarginCollapsesWithChildren =
                ownMarginsCollapseWithChildren.end() && !heightConstrainedByMinHeight;

        // Apply `align-content` to in-flow non-floated items if requested. The
        // per-item layouts were held back in `item.final_layout` so that this
        // step can shift `location.y` before tree commit.
        //
        // For block layout the entire stack of in-flow children is treated as
        // a single alignment subject. That means distribution keywords
        // (`space-between`, `space-around`, `space-evenly`, `stretch`) must
        // invoke the single-subject fallback unconditionally — which is what
        // passing `num_items = 1` to `apply_alignment_fallback` does. The
        // whole group then shifts by one offset, with zero inter-item gap.
        if (alignContent.keyword() != AlignContentKeyword.NORMAL) {
            float containerInnerHeight = containerOuterHeight - resolvedContentBoxInset.verticalAxisSum();
            float inflowContentHeight = intrinsicOuterHeight - resolvedContentBoxInset.verticalAxisSum();
            float freeSpace = containerInnerHeight - inflowContentHeight;
            float groupOffset = computeBlockAlignContentOffset(alignContent, freeSpace);
            firstBaseline = OptF32.map(firstBaseline, baseline -> baseline + groupOffset);
            for (BlockItem item : items) {
                if (item.finalLayout != null) {
                    Layout layout = item.finalLayout;
                    item.finalLayout = new Layout(
                            layout.order(),
                            new PointF32(
                                    layout.location().x(), layout.location().y() + groupOffset),
                            layout.size(),
                            layout.scrollableOverflowRect(),
                            layout.scrollbarSize(),
                            layout.border(),
                            layout.padding(),
                            layout.margin());
                }
                // The static positions of out-of-flow children move with the
                // in-flow content
                if (item.position.isOutOfFlow()) {
                    item.staticPosition = new PointF32(item.staticPosition.x(), item.staticPosition.y() + groupOffset);
                }
            }
            boolean anyInFlow = items.stream().anyMatch(item -> item.finalLayout != null);
            if (anyInFlow) {
                inflowOverflowRect = RectF32.ZERO;
                ScrollableOverflowContainer overflowContainer = new ScrollableOverflowContainer(
                        isScrollContainer,
                        finalOuterSize.minus(resolvedBorder.sumAxes()).minus(scrollbarGutter.sumAxes()),
                        new PointBool(direction.isRtl(), false),
                        resolvedPadding);
                for (BlockItem item : items) {
                    Layout layout = item.finalLayout;
                    if (layout == null) {
                        continue;
                    }
                    PointF32 contributionLocation = new PointF32(
                            layout.location().x() - resolvedBorder.left() - scrollbarGutter.left(),
                            layout.location().y() - resolvedBorder.top());
                    inflowOverflowRect =
                            inflowOverflowRect.union(ScrollableOverflow.computeScrollableOverflowContribution(
                                    contributionLocation,
                                    layout.size(),
                                    layout.scrollableOverflowRect().rect(),
                                    layout.border(),
                                    item.overflow,
                                    item.contain,
                                    overflowContainer,
                                    layout.margin().map(value -> F32.min(value, 0.0f))));
                }
            }
        }

        // Determine whether this node can be collapsed through
        boolean allInFlowChildrenCanBeCollapsedThrough = items.stream().allMatch(item -> {
            if (item.floatValue.isFloated()) {
                return true;
            }
            return item.position.isOutOfFlow() || item.canBeCollapsedThrough;
        });
        // CSS2 §8.3.1: a box's top and bottom margins collapse through it only
        // if its *used* height is zero, so `height`/`min-height`/`max-height`
        // are judged by the height they resolve to (e.g. `height: 1px;
        // max-height: 0` collapses, `min-height: 1%` under an auto-height
        // parent collapses) rather than by their computed values.
        boolean canBeCollapsedThrough = !hasStylesPreventingBeingCollapsedThrough
                && containerOuterHeight == 0.0f
                && allInFlowChildrenCanBeCollapsedThrough;

        CollapsibleMarginSet topMargin;
        if (ownMarginsCollapseWithChildren.start()) {
            topMargin = result.firstChildTopMarginSet();
        } else {
            float marginTop = rawMargin.top().resolveOrZero(parentSize.width(), calc);
            topMargin = CollapsibleMarginSet.fromMargin(marginTop);
        }
        CollapsibleMarginSet bottomMargin;
        if (ownBottomMarginCollapsesWithChildren) {
            bottomMargin = result.lastChildBottomMarginSet();
        } else {
            float marginBottom = rawMargin.bottom().resolveOrZero(parentSize.width(), calc);
            bottomMargin = CollapsibleMarginSet.fromMargin(marginBottom);
        }

        LayoutOutput output = new LayoutOutput(
                finalOuterSize,
                net.morphismmc.jaffy.tree.layout.ScrollableOverflow.ZERO,
                Baselines.fromFirst(firstBaseline),
                topMargin,
                bottomMargin,
                canBeCollapsedThrough,
                OofCandidates.none(),
                null);

        // Short-circuit if computing size.
        //
        // Note: it is important that we return the margin-collapsing related
        // outputs here as Parent block containers rely on the
        // `top_margin`/`bottom_margin` of their children to compute their own
        // intrinsic height.
        if (runMode == RunMode.COMPUTE_SIZE) {
            return output;
        }

        // Commit deferred child layouts to the tree.
        for (BlockItem item : items) {
            if (item.finalLayout != null) {
                tree.setUnroundedLayout(item.nodeId, item.finalLayout);
            }
        }

        // 4. Collect out-of-flow candidates in document order (direct
        // out-of-flow children plus candidates bubbled from in-flow children's
        // subtrees). These are laid out by the out-of-flow positioning pass
        // (`compute_oof_layout`), which runs after this algorithm.
        OofCandidates candidates = OofCandidates.create();
        for (BlockItem item : items) {
            if (item.position.isOutOfFlow()) {
                // The static-position rectangle of an out-of-flow child of a
                // block container spans the container's content box in the
                // inline axis, and is a zero-height line at the child's
                // hypothetical position in the block axis. The child's
                // self-alignment properties determine how it is aligned within
                // that rectangle.
                // <https://www.w3.org/TR/css-position-3/#staticpos-rect>
                LineF32 inlineArea = new LineF32(
                        resolvedContentBoxInset.left(), containerOuterWidth - resolvedContentBoxInset.right());
                LineF32 blockArea = new LineF32(item.staticPosition.y(), item.staticPosition.y());
                // A `justify_self` of `None` takes the container's
                // `justify-items`. Block containers have no `align-items`, so
                // an `align_self` of `None` behaves as `normal`.
                AlignItems justifySelf = (item.justifySelf != null ? item.justifySelf : justifyItems)
                        .resolveSelfRelative(item.direction, direction, true);
                AlignItems alignSelf = (item.alignSelf != null ? item.alignSelf : AlignItems.NORMAL)
                        .resolveSelfRelative(item.direction, direction, false);
                candidates.push(new OofCandidate(
                        item.nodeId,
                        item.order,
                        item.position,
                        new Point<>(
                                AxisStaticPosition.fromAlignment(justifySelf, inlineArea, direction.isRtl()),
                                AxisStaticPosition.fromAlignment(alignSelf, blockArea, false))));
            } else if (!item.oofCandidates.isEmpty()) {
                // Translate anchors from item-relative to container-relative
                // coordinates
                if (item.finalLayout != null) {
                    item.oofCandidates.translate(item.finalLayout.location());
                }
                candidates.append(item.oofCandidates);
            }
        }

        RectF32 absolutePositionInset = resolvedBorder.plus(scrollbarGutter);
        SizeF32 absolutePositionArea = finalOuterSize.minus(absolutePositionInset.sumAxes());
        PointF32 absolutePositionOffset = new PointF32(absolutePositionInset.left(), absolutePositionInset.top());
        output = new LayoutOutput(
                output.size(),
                output.scrollableOverflowRect(),
                output.baselines(),
                output.topMargin(),
                output.bottomMargin(),
                output.marginsCanCollapseThrough(),
                candidates,
                new OofPositioningArea(absolutePositionArea, absolutePositionOffset));

        {
            float clientWidth = F32.max(
                    finalOuterSize.width() - resolvedBorder.horizontalAxisSum() - scrollbarGutter.horizontalAxisSum(),
                    0.0f);
            if (direction.isRtl()) {
                inflowOverflowRect = inflowOverflowRect.union(new RectF32(0.0f, clientWidth, 0.0f, 0.0f));
            }
            if (isScrollContainer) {
                inflowOverflowRect = inflowOverflowRect.union(new RectF32(
                        direction.isRtl() ? F32.min(clientWidth - resolvedPadding.left(), 0.0f) : 0.0f,
                        direction.isRtl() ? clientWidth : resolvedPadding.right(),
                        0.0f,
                        resolvedPadding.bottom()));
            }
            net.morphismmc.jaffy.tree.layout.ScrollableOverflow sof = ScrollableOverflow.finalizeScrollableOverflow(
                    inflowOverflowRect,
                    finalOuterSize.minus(resolvedBorder.sumAxes()).minus(scrollbarGutter.sumAxes()),
                    new PointBool(direction.isRtl(), false),
                    isScrollContainer);
            output = new LayoutOutput(
                    output.size(),
                    sof,
                    output.baselines(),
                    output.topMargin(),
                    output.bottomMargin(),
                    output.marginsCanCollapseThrough(),
                    output.oofCandidates(),
                    output.oofPositioningArea());
        }

        // 5. Perform hidden layout on hidden children
        int len = tree.childCount(nodeId);
        for (int order = 0; order < len; order++) {
            long child = tree.getChildId(nodeId, order);
            Style childStyle = tree.getBlockChildStyle(child);
            if (childStyle.boxGenerationMode() == BoxGenerationMode.NONE) {
                tree.setUnroundedLayout(child, Layout.withOrder(order));
                tree.performChildLayout(
                        child,
                        SizeOptF32.NONE,
                        SizeOptF32.NONE,
                        SizeAvailableSpaceExt.maxContent(),
                        SizingMode.INHERENT_SIZE,
                        LineBool.FALSE);
            }
        }

        return output;
    }

    /// Create a `List` of {@link BlockItem} structs where each item in the
    /// list represents a child of the current node (`generate_item_list`).
    private static List<BlockItem> generateItemList(LayoutBlockContainer tree, long node, SizeOptF32 nodeInnerSize) {
        CalcResolver calc = tree::calc;
        List<BlockItem> items = new ArrayList<>();
        LongIterator childIds = tree.childIds(node);
        int order = 0;
        while (childIds.hasNext()) {
            long childNodeId = childIds.nextLong();
            Style childStyle = tree.getBlockChildStyle(childNodeId);
            if (childStyle.boxGenerationMode() == BoxGenerationMode.NONE) {
                continue;
            }
            @Opt float aspectRatio = childStyle.aspectRatio();
            RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(childStyle.padding(), nodeInnerSize.width(), calc);
            RectF32 border = RectLengthPercentageExt.resolveOrZeroO(childStyle.border(), nodeInnerSize.width(), calc);
            SizeF32 pbSum = padding.plus(border).sumAxes();
            SizeF32 boxSizingAdjustment = childStyle.boxSizing() == BoxSizing.CONTENT_BOX ? pbSum : SizeF32.ZERO;

            Position position = childStyle.position();
            Point<Overflow> overflow = childStyle.overflow();

            net.morphismmc.jaffy.style.float_.Float floatValue = childStyle.floatValue();
            boolean isNotFloated = floatValue == net.morphismmc.jaffy.style.float_.Float.NONE;

            boolean isBlock = childStyle.isBlock();
            boolean isTable = childStyle.isTable();
            boolean isCompressibleReplaced = childStyle.isCompressibleReplaced();
            @Opt float minContentWidthCap = OptF32.NONE;
            if (isCompressibleReplaced) {
                @Opt float width = childStyle.size().width().maybeResolveO(0.0f, calc);
                @Opt float maxWidth = childStyle.maxSize().width().maybeResolveO(0.0f, calc);
                minContentWidthCap = OptF32.isSome(width) ? OptF32.maybeMinO(width, maxWidth) : maxWidth;
                minContentWidthCap = OptF32.maybeAddF(minContentWidthCap, boxSizingAdjustment.width());
            }
            boolean isScrollContainer =
                    overflow.x().isScrollContainer() || overflow.y().isScrollContainer();
            Contain contain = childStyle.contain();

            boolean isInSameBfc = isBlock
                    && !isTable
                    && !position.isOutOfFlow()
                    && isNotFloated
                    && !isScrollContainer
                    && !contain.establishesIndependentFormattingContext()
                    && childStyle.alignContent().keyword() == AlignContentKeyword.NORMAL;

            items.add(new BlockItem(
                    childNodeId,
                    order++,
                    isTable,
                    isCompressibleReplaced,
                    minContentWidthCap,
                    isInSameBfc,
                    floatValue,
                    childStyle.clear(),
                    childStyle.size(),
                    SizeDimensionExt.maybeResolveO(childStyle.size(), nodeInnerSize, calc)
                            .maybeApplyAspectRatio(aspectRatio)
                            .maybeAddF(boxSizingAdjustment),
                    SizeLengthPercentageAutoExt.maybeResolveO(childStyle.minSize(), nodeInnerSize, calc)
                            .maybeApplyAspectRatio(aspectRatio)
                            .maybeAddF(boxSizingAdjustment),
                    SizeLengthPercentageAutoExt.maybeResolveO(childStyle.maxSize(), nodeInnerSize, calc)
                            .maybeApplyAspectRatio(aspectRatio)
                            .maybeAddF(boxSizingAdjustment),
                    overflow,
                    contain,
                    childStyle.scrollbarWidth(),
                    position,
                    childStyle.direction(),
                    childStyle.alignSelf(),
                    childStyle.justifySelf(),
                    childStyle.inset(),
                    childStyle.margin(),
                    padding,
                    border,
                    pbSum));
        }
        return items;
    }

    /// Resolve the `stretch` sizing keyword for an item's height style. In the
    /// block axis the intrinsic sizing keywords (`min-content`, `max-content`,
    /// `fit-content`, `fit-content(...)`) are all equal to the content size,
    /// which is what an auto height already resolves to, so only `stretch`
    /// requires explicit resolution.
    ///
    /// Each of the item's block-axis margins is subtracted from the stretch
    /// size unless it is flagged in `marginsTreatedAsZero`: when stretching a
    /// block-level box, a margin that could collapse through the
    /// corresponding edge of the containing block (which has neither border
    /// nor padding on that side and does not establish a new BFC) is treated
    /// as zero.
    /// <https://github.com/w3c/csswg-drafts/issues/11044#issuecomment-2599101601>
    /// <https://github.com/w3c/csswg-drafts/issues/13260>
    private static @Opt float resolveStretchHeight(
            Dimension heightStyle, @Opt float containerInnerHeight, RectF32 itemMargin, LineBool marginsTreatedAsZero) {
        if (!heightStyle.isStretch()) {
            return OptF32.NONE;
        }
        if (OptF32.isNone(containerInnerHeight)) {
            return OptF32.NONE;
        }
        float height = containerInnerHeight;
        if (!marginsTreatedAsZero.start()) {
            height -= itemMargin.top();
        }
        if (!marginsTreatedAsZero.end()) {
            height -= itemMargin.bottom();
        }
        return height;
    }

    /// Compute the offset applied to a box by its `inset` if it is `position:
    /// relative` (CSS2.2 9.4.3).
    ///
    /// Percentages resolve against the containing block's content box
    /// (`parentSize`); a percentage that cannot be resolved behaves as `auto`.
    /// If both insets in an axis are non-auto, `top` wins over `bottom`, and
    /// `left` wins over `right` in LTR (`right` over `left` in RTL).
    private static PointF32 resolveRelativeOffset(
            BlockItem item, SizeOptF32 parentSize, Direction direction, LayoutPartialTree tree) {
        if (item.position != Position.RELATIVE) {
            return PointF32.ZERO;
        }
        CalcResolver calc = tree::calc;
        RectOptF32 inset = new RectOptF32(
                item.inset.left().maybeResolveO(parentSize.width(), calc),
                item.inset.right().maybeResolveO(parentSize.width(), calc),
                item.inset.top().maybeResolveO(parentSize.height(), calc),
                item.inset.bottom().maybeResolveO(parentSize.height(), calc));
        @Opt
        float x = direction.isRtl()
                ? OptF32.or(OptF32.map(inset.right(), v -> -v), inset.left())
                : OptF32.or(inset.left(), OptF32.map(inset.right(), v -> -v));
        @Opt float y = OptF32.or(inset.top(), OptF32.map(inset.bottom(), v -> -v));
        return new PointF32(OptF32.unwrapOr(x, 0.0f), OptF32.unwrapOr(y, 0.0f));
    }

    /// Compute the content-based width in the case that the width of the
    /// container is not known (`determine_content_based_container_width`).
    private static float determineContentBasedContainerWidth(
            LayoutPartialTree tree, List<BlockItem> items, AvailableSpace availableWidth) {
        Size<AvailableSpace> availableSpace = new Size<>(availableWidth, AvailableSpace.MIN_CONTENT);
        CalcResolver calc = tree::calc;

        float maxChildWidth = 0.0f;
        FloatIntrinsicWidthCalculator floatContribution = new FloatIntrinsicWidthCalculator(availableWidth);
        for (BlockItem item : items) {
            if (item.position.isOutOfFlow()) {
                continue;
            }
            SizeOptF32 knownDimensions = item.size.maybeClampO(item.minSize, item.maxSize);

            float itemXMarginSum = RectLengthPercentageAutoExt.resolveOrZeroO(
                            item.margin, availableSpace.width().intoOption(), calc)
                    .horizontalAxisSum();
            float width;
            if (OptF32.isSome(knownDimensions.width())) {
                width = knownDimensions.width();
            } else {
                AvailableSpace itemAvailableWidth;
                SizingKeywordResolution resolution =
                        SizingKeyword.resolveSizingKeyword(item.sizeStyle.width(), OptF32.NONE, OptF32.NONE, calc);
                if (resolution instanceof SizingKeywordResolution.Measure m) {
                    itemAvailableWidth = m.availableSpace();
                } else if (resolution instanceof SizingKeywordResolution.Exact e) {
                    itemAvailableWidth = new AvailableSpace.Definite(e.value());
                } else {
                    itemAvailableWidth = availableSpace.width().maybeSubF(itemXMarginSum);
                }
                float measured = tree.measureChildSize(
                        item.nodeId,
                        knownDimensions,
                        SizeOptF32.NONE,
                        new Size<>(itemAvailableWidth, availableSpace.height()),
                        SizingMode.INHERENT_SIZE,
                        AbsoluteAxis.HORIZONTAL,
                        // Must match the value passed when laying the item out
                        // (see `Cache`)
                        item.isInSameBfc ? LineBool.TRUE : LineBool.FALSE);
                // The cap is the item's min-content contribution, so its
                // fit-content contribution is
                // min(max-content, max(cap, available))
                if (OptF32.isSome(item.minContentWidthCap) && itemAvailableWidth instanceof AvailableSpace.MinContent) {
                    width = F32.maybeMaxO(F32.min(measured, item.minContentWidthCap), item.minSize.width());
                } else if (OptF32.isSome(item.minContentWidthCap)
                        && itemAvailableWidth instanceof AvailableSpace.Definite d) {
                    width = F32.maybeMaxO(
                            F32.min(measured, Sys.f32Max(item.minContentWidthCap, d.value())), item.minSize.width());
                } else {
                    width = measured;
                }
            }

            width = Sys.f32Max(width, item.paddingBorderSum.width()) + itemXMarginSum;

            FloatDirection floatDirection = item.floatValue.floatDirection();
            if (floatDirection != null) {
                floatContribution.addFloat(width, floatDirection, item.clear);
                continue;
            }

            maxChildWidth = Sys.f32Max(maxChildWidth, width);
        }

        return Sys.f32Max(maxChildWidth, floatContribution.result());
    }

    /// Resolve the known dimensions an in-flow block-level item is laid out
    /// with (`block_item_known_dimensions`).
    private static SizeOptF32 blockItemKnownDimensions(
            LayoutBlockContainer tree,
            BlockItem item,
            float stretchWidth,
            float containerInnerWidth,
            @Opt float containerPercentageResolutionHeight,
            RectF32 itemNonAutoMargin,
            LineBool childrenMarginsAdjoinOwnEdges,
            SizeOptF32 parentSize,
            boolean isStretch,
            boolean isFitContent) {
        // Unless stretched, tables and compressible replaced elements resolve
        // their own width
        // <https://www.w3.org/TR/CSS22/visudet.html#block-replaced-width>
        if ((item.isTable || item.isCompressibleReplaced) && !isStretch) {
            return SizeOptF32.NONE;
        }

        // The automatic width of a block-level box whose `justify-self` is
        // neither `normal` nor `stretch` is equivalent to `fit-content` rather
        // than `stretch`
        Dimension widthStyle =
                isFitContent && item.sizeStyle.width().isAuto() ? Dimension.fitContent() : item.sizeStyle.width();

        // Items with a sizing keyword width (min-content, max-content,
        // fit-content, fit-content(...), stretch) resolve their width either
        // directly or by measuring the item under the corresponding available
        // space constraint
        CalcResolver calc = tree::calc;
        @Opt float keywordWidth = OptF32.NONE;
        SizingKeywordResolution widthResolution =
                SizingKeyword.resolveSizingKeyword(widthStyle, stretchWidth, containerInnerWidth, calc);
        if (widthResolution instanceof SizingKeywordResolution.Exact e) {
            keywordWidth = e.value();
        } else if (widthResolution instanceof SizingKeywordResolution.Measure m) {
            keywordWidth = tree.measureChildSize(
                    item.nodeId,
                    SizeOptF32.NONE,
                    parentSize,
                    new Size<>(m.availableSpace(), AvailableSpace.MAX_CONTENT),
                    SizingMode.INHERENT_SIZE,
                    AbsoluteAxis.HORIZONTAL,
                    // Must match the value passed when laying the item out
                    // (see `Cache`)
                    item.isInSameBfc ? LineBool.TRUE : LineBool.FALSE);
        }

        @Opt
        float keywordHeight = resolveStretchHeight(
                item.sizeStyle.height(),
                containerPercentageResolutionHeight,
                itemNonAutoMargin,
                childrenMarginsAdjoinOwnEdges);

        final float keywordWidthFinal = keywordWidth;
        return item.size
                .mapWidth(width -> F32.maybeClampO(
                        OptF32.unwrapOr(OptF32.or(width, keywordWidthFinal), stretchWidth),
                        item.minSize.width(),
                        item.maxSize.width()))
                .mapHeight(height -> OptF32.or(height, keywordHeight))
                .maybeClampO(item.minSize, item.maxSize);
    }

    /// The `item_inputs` closure of
    /// `perform_final_layout_on_in_flow_children`.
    private static LayoutInput itemInputs(
            LayoutBlockContainer tree,
            RunMode runMode,
            BlockItem item,
            float stretchWidth,
            float containerInnerWidth,
            @Opt float containerPercentageResolutionHeight,
            RectF32 itemNonAutoMargin,
            LineBool childrenMarginsAdjoinOwnEdges,
            SizeOptF32 parentSize,
            Size<AvailableSpace> availableSpace,
            boolean isStretch,
            boolean isFitContent) {
        return new LayoutInput(
                runMode,
                SizingMode.INHERENT_SIZE,
                RequestedAxis.BOTH,
                blockItemKnownDimensions(
                        tree,
                        item,
                        stretchWidth,
                        containerInnerWidth,
                        containerPercentageResolutionHeight,
                        itemNonAutoMargin,
                        childrenMarginsAdjoinOwnEdges,
                        parentSize,
                        isStretch,
                        isFitContent),
                SizeBool.TRUE,
                parentSize,
                new Size<>(new AvailableSpace.Definite(stretchWidth), availableSpace.height()),
                item.isInSameBfc ? LineBool.TRUE : LineBool.FALSE);
    }

    /// Compute each child's final size and position
    /// (`perform_final_layout_on_in_flow_children`).
    private static FinalLayoutResult performFinalLayoutOnInFlowChildren(
            LayoutBlockContainer tree,
            RunMode runMode,
            List<BlockItem> items,
            float containerOuterWidth,
            @Opt float containerPercentageResolutionHeight,
            RectF32 contentBoxInset,
            RectF32 resolvedContentBoxInset,
            RectF32 resolvedBorder,
            RectF32 resolvedPadding,
            TextAlign textAlign,
            AlignItems justifyItems,
            Direction direction,
            LineBool ownMarginsCollapseWithChildren,
            LineBool childrenMarginsAdjoinOwnEdges,
            boolean isScrollContainer,
            BlockContext blockCtx) {
        CalcResolver calc = tree::calc;
        // Resolve container_inner_width for sizing child nodes using initial
        // content_box_inset
        float containerInnerWidth = F32.max(containerOuterWidth - resolvedContentBoxInset.horizontalAxisSum(), 0.0f);
        ScrollableOverflowContainer overflowContainer = new ScrollableOverflowContainer(
                isScrollContainer,
                new SizeF32(containerInnerWidth + resolvedPadding.horizontalAxisSum(), 0.0f),
                new PointBool(direction.isRtl(), false),
                resolvedPadding);
        @Opt
        float containerPctResHeight =
                OptF32.maybeSubF(containerPercentageResolutionHeight, resolvedContentBoxInset.verticalAxisSum());
        SizeOptF32 parentSize = new SizeOptF32(containerInnerWidth, containerPctResHeight);
        // Vertical available space in block flow is indefinite, NOT a
        // min-content constraint: MaxContent is taffy's representation of
        // "indefinite". Passing MinContent here made every descendant grid
        // believe it was being sized under a min-content constraint, in which
        // the maximize-tracks step has zero free space — so auto rows
        // containing only scroll-container items (overflow != visible,
        // automatic minimum size = 0) collapsed to zero height. Browsers size
        // such rows to the item's content.
        Size<AvailableSpace> availableSpace =
                new Size<>(new AvailableSpace.Definite(containerInnerWidth), AvailableSpace.MAX_CONTENT);

        // TODO: handle nested blocks with different widths
        if (blockCtx.isBfcRoot()) {
            blockCtx.setWidth(containerOuterWidth);
            blockCtx.applyContentBoxInset(
                    new FloatInsets(resolvedContentBoxInset.left(), resolvedContentBoxInset.right()));
        }

        // If this block's top margin does not collapse with its children's
        // then the position of its top margin strut is resolved relative to
        // it, and floats adjoining ancestor struts do not adjoin this block's
        // strut.
        if (!ownMarginsCollapseWithChildren.start()) {
            blockCtx.commitStrut();
        }

        RectF32 inflowOverflowRect = RectF32.ZERO;
        float committedYOffset = resolvedContentBoxInset.top();
        float yOffsetForAbsolute = resolvedContentBoxInset.top();
        CollapsibleMarginSet firstChildTopMarginSet = CollapsibleMarginSet.ZERO;
        CollapsibleMarginSet activeCollapsibleMarginSet = CollapsibleMarginSet.ZERO;
        boolean isCollapsingWithFirstMarginSet = true;
        @Opt float firstBaseline = OptF32.NONE;
        // Whether the active margin set contains the margins of a
        // self-collapsing element with clearance. Such margins collapse with
        // the margins of following siblings but the resulting margin does not
        // collapse with the bottom margin of the parent block.
        boolean activeMarginSetHasClearance = false;

        boolean hasActiveFloats = blockCtx.hasActiveFloats(committedYOffset);

        for (BlockItem item : items) {
            if (item.position.isOutOfFlow()) {
                float x = direction == Direction.LTR
                        ? resolvedContentBoxInset.left()
                        : containerOuterWidth - resolvedContentBoxInset.right();
                item.staticPosition = new PointF32(x, yOffsetForAbsolute);
            } else {
                RectOptF32 itemMargin = item.margin.mapO(m -> m.resolveToOption(containerInnerWidth, calc));
                RectF32 itemNonAutoMargin = itemMargin.mapF(m -> OptF32.unwrapOr(m, 0.0f));
                float itemNonAutoXMarginSum = itemNonAutoMargin.horizontalAxisSum();

                SizeF32 scrollbarSize = new SizeF32(
                        item.overflow.y() == Overflow.SCROLL ? item.scrollbarWidth : 0.0f,
                        item.overflow.x() == Overflow.SCROLL ? item.scrollbarWidth : 0.0f);

                // Handle floated boxes
                FloatDirection floatDirection = item.floatValue.floatDirection();
                if (floatDirection != null) {
                    hasActiveFloats = true;

                    // A float with `width: auto` is shrink-to-fit
                    // (fit-content) sized: the available space clamped between
                    // its min-content and max-content sizes.
                    float availableWidth = F32.max(containerInnerWidth - itemNonAutoXMarginSum, 0.0f);
                    @Opt float itemKnownWidth;
                    AvailableSpace itemAvailableWidth;
                    SizingKeywordResolution resolution = SizingKeyword.resolveSizingKeyword(
                            item.sizeStyle.width(), availableWidth, containerInnerWidth, calc);
                    if (resolution instanceof SizingKeywordResolution.Measure m) {
                        itemKnownWidth = OptF32.NONE;
                        itemAvailableWidth = m.availableSpace();
                    } else if (resolution instanceof SizingKeywordResolution.Exact e) {
                        itemKnownWidth = e.value();
                        itemAvailableWidth = new AvailableSpace.Definite(e.value());
                    } else {
                        itemKnownWidth = OptF32.NONE;
                        itemAvailableWidth = new AvailableSpace.Definite(availableWidth);
                    }
                    @Opt
                    float itemKnownHeight = resolveStretchHeight(
                            item.sizeStyle.height(),
                            containerPctResHeight,
                            itemNonAutoMargin,
                            // The margins of a float never collapse
                            LineBool.FALSE);
                    LayoutOutput itemLayout = tree.performChildLayout(
                            item.nodeId,
                            new SizeOptF32(itemKnownWidth, itemKnownHeight),
                            parentSize,
                            new Size<>(itemAvailableWidth, AvailableSpace.MAX_CONTENT),
                            SizingMode.INHERENT_SIZE,
                            // A float establishes a new block formatting
                            // context: its margins do not collapse with the
                            // margins of its children
                            LineBool.FALSE);
                    item.oofCandidates = itemLayout.oofCandidates().take();
                    SizeF32 marginBox = itemLayout.size().plus(itemNonAutoMargin.sumAxes());

                    // Floats that occur between collapsing margins are
                    // positioned as if they had an otherwise empty anonymous
                    // block parent taking part in the flow, so the pending
                    // collapsible margins contribute to the float's minimum y
                    // position (unless those margins collapse with the
                    // container's own top margin, in which case they are
                    // applied outside the container).
                    //
                    // In the latter case the position of the float is not
                    // fully resolved: margins contributed by later siblings
                    // can still collapse into the strut and move the container
                    // (and float). Such floats force clearance on cleared
                    // elements whose margins adjoin the same strut.
                    boolean adjoinsUnresolvedStrut =
                            isCollapsingWithFirstMarginSet && ownMarginsCollapseWithChildren.start();
                    float yOffsetForFloat = adjoinsUnresolvedStrut
                            ? committedYOffset
                            : committedYOffset + activeCollapsibleMarginSet.resolve();

                    PointF32 location = blockCtx.placeFloatedBox(
                            item.nodeId,
                            marginBox,
                            yOffsetForFloat,
                            floatDirection,
                            item.clear,
                            adjoinsUnresolvedStrut);

                    // Convert the margin-box location returned by float
                    // placement into a border-box location for the output
                    // Layout
                    location = new PointF32(
                            location.x() + itemNonAutoMargin.left(), location.y() + itemNonAutoMargin.top());

                    // A relatively positioned float is placed as a float
                    // first, then offset by its inset. The offset does not
                    // affect the placement of other content (CSS 2.2 9.4.3).
                    PointF32 insetOffset = resolveRelativeOffset(item, parentSize, direction, tree);
                    location = new PointF32(location.x() + insetOffset.x(), location.y() + insetOffset.y());

                    // Deferred to the post-loop pass in `computeInner` (like
                    // in-flow items) so that `align-content` can shift
                    // `location.y` before the layout is committed.
                    item.finalLayout = new Layout(
                            item.order,
                            location,
                            itemLayout.size(),
                            itemLayout.scrollableOverflowRect(),
                            scrollbarSize,
                            item.border,
                            item.padding,
                            itemNonAutoMargin);

                    // TODO: Should the overflow of floated boxes count as
                    // "inflow_overflow_rect" or should it be counted
                    // separately?
                    PointF32 contributionLocation = new PointF32(
                            location.x() - resolvedContentBoxInset.left() + resolvedPadding.left(),
                            location.y() - resolvedBorder.top());
                    inflowOverflowRect =
                            inflowOverflowRect.union(ScrollableOverflow.computeScrollableOverflowContribution(
                                    contributionLocation,
                                    itemLayout.size(),
                                    itemLayout.scrollableOverflowRect().rect(),
                                    item.border,
                                    item.overflow,
                                    item.contain,
                                    overflowContainer,
                                    itemNonAutoMargin.map(value -> F32.min(value, 0.0f))));

                    continue;
                }

                // Handle non-floated boxes

                // `justify-self` on an in-flow block-level box (css-align-3
                // §6.1.1). `None` takes the container's `justify-items`, and
                // `normal` lays the box out according to the default block
                // layout rules. `stretch` does too: the default rules already
                // stretch an auto-width box, so it only differs from `normal`
                // for tables and replaced boxes, which would otherwise resolve
                // their own width.
                AlignItems justifySelf = (item.justifySelf != null ? item.justifySelf : justifyItems)
                        .resolveSelfRelative(item.direction, direction, true);
                boolean isStretch = justifySelf.keyword() == AlignItemsKeyword.STRETCH;
                // The (resolved) `justify-self` if it is neither `normal` nor
                // `stretch`
                @Nullable
                AlignItems nonStretchJustifySelf = justifySelf.keyword() == AlignItemsKeyword.NORMAL
                                || justifySelf.keyword() == AlignItemsKeyword.STRETCH
                        ? null
                        : justifySelf;

                float yMarginOffset = 0.0f;
                boolean itemAvoidsFloats = false;
                boolean itemPushedBelowFloat = false;

                float stretchWidth;
                PointF32 floatAvoidingPosition;
                float floatAvoidingWidth;
                if (item.isInSameBfc) {
                    stretchWidth = F32.max(containerInnerWidth - itemNonAutoXMarginSum, 0.0f);
                    floatAvoidingPosition = PointF32.ZERO;
                    floatAvoidingWidth = 0.0f;
                } else {
                    // Set yMarginOffset (different bfc child)
                    if (!isCollapsingWithFirstMarginSet || !ownMarginsCollapseWithChildren.start()) {
                        yMarginOffset = activeCollapsibleMarginSet
                                .collapseWithMargin(itemNonAutoMargin.top())
                                .resolve();
                    }
                    float minY = committedYOffset + yMarginOffset;

                    // In addition to the running flag, check the float
                    // context directly: floats placed by the subtree of a
                    // preceding in-flow sibling (in the same BFC) are not
                    // reflected in the flag
                    if (hasActiveFloats || blockCtx.hasActiveFloats(minY)) {
                        FloatInsets xMargins = new FloatInsets(itemNonAutoMargin.left(), itemNonAutoMargin.right());
                        // An auto width resolves to at least the negation of
                        // the margin sum (so that the margin box width is
                        // non-negative, per CSS2 §10.3.3)
                        float minAutoWidth = -itemNonAutoXMarginSum;

                        // Find the highest slot (at or below `minY`) with
                        // enough horizontal space for the item's border box,
                        // which must not overlap any float. The item's height
                        // isn't known until it is laid out, so only its styled
                        // (minimum) height is checked against floats below its
                        // top edge
                        float minHeight = Sys.f32Max(
                                OptF32.unwrapOr(
                                        OptF32.or(
                                                OptF32.maybeClampO(
                                                        item.size.height(),
                                                        item.minSize.height(),
                                                        item.maxSize.height()),
                                                item.minSize.height()),
                                        0.0f),
                                item.paddingBorderSum.height());
                        @Opt
                        @Unsigned
                        int slotSegment = OptI32.NONE;
                        BfcSlot slot;
                        while (true) {
                            slot = blockCtx.findBfcSlot(minY, minHeight, xMargins, direction, item.clear, slotSegment);
                            if (slot.segmentId() == OptI32.NONE) {
                                break;
                            }
                            float width = F32.maybeClampO(
                                    OptF32.unwrapOr(
                                            item.size.width(),
                                            Sys.f32Max(Sys.f32Max(slot.stretchWidth(), minAutoWidth), 0.0f)),
                                    item.minSize.width(),
                                    item.maxSize.width());
                            if (width <= slot.borderWidth() + FloatContext.FIT_TOLERANCE) {
                                break;
                            }
                            slotSegment = slot.segmentId();
                        }

                        // If the item had to move down to avoid floats then it
                        // "separates from the float": similarly to clearance,
                        // its top margin no longer collapses with the parent's
                        // margins.
                        if (slot.y() > minY) {
                            itemPushedBelowFloat = true;
                        }

                        hasActiveFloats = slot.segmentId() != OptI32.NONE;
                        itemAvoidsFloats = true;
                        stretchWidth = Sys.f32Max(Sys.f32Max(slot.stretchWidth(), minAutoWidth), 0.0f);
                        floatAvoidingPosition = new PointF32(slot.x(), slot.y());
                        floatAvoidingWidth = slot.borderWidth();
                    } else if (!hasActiveFloats) {
                        stretchWidth = F32.max(containerInnerWidth - itemNonAutoXMarginSum, 0.0f);
                        floatAvoidingPosition = new PointF32(resolvedContentBoxInset.left(), minY);
                        floatAvoidingWidth = containerInnerWidth;
                    } else {
                        throw new IllegalStateException("One of the above cases will always be hit");
                    }
                }

                LayoutInput childInputs = itemInputs(
                        tree,
                        runMode,
                        item,
                        stretchWidth,
                        containerInnerWidth,
                        containerPctResHeight,
                        itemNonAutoMargin,
                        childrenMarginsAdjoinOwnEdges,
                        parentSize,
                        availableSpace,
                        isStretch,
                        nonStretchJustifySelf != null);

                @Opt float clearThreshold = blockCtx.clearedThreshold(item.clear);
                float clearPos = OptF32.unwrapOr(clearThreshold, Float.NEGATIVE_INFINITY);

                // The height consumed by floats within a same-BFC child,
                // relative to the child's border-top. Added to the parent's
                // contribution once the child's position is known.
                float childFloatContribution = Float.NEGATIVE_INFINITY;

                LayoutOutput itemLayout;
                if (item.isInSameBfc) {
                    // Replaced elements may not have a known width (they are
                    // sized by their measure function rather than
                    // stretch-sized)
                    float width = F32.max(
                            OptF32.unwrapOr(childInputs.knownDimensions().width(), stretchWidth),
                            item.paddingBorderSum.width());

                    // TODO: account for auto margins
                    float insetLeft = itemNonAutoMargin.left() + contentBoxInset.left();
                    float insetRight = containerOuterWidth - width - insetLeft;
                    FloatInsets insets = new FloatInsets(insetLeft, insetRight);

                    // Compute child layout. The child's position is estimated
                    // from its top margin collapsed with the preceding margins
                    // (the child's own collapsed-through margins are not yet
                    // known).
                    float estimatedY = committedYOffset
                            + activeCollapsibleMarginSet
                                    .collapseWithMargin(itemNonAutoMargin.top())
                                    .resolve();
                    BlockContext childBlockCtx = blockCtx.subContext(Sys.f32Max(estimatedY, clearPos), insets);
                    LayoutOutput output = tree.computeBlockChildLayout(item.nodeId, childInputs, childBlockCtx);

                    // Extract float contribution from child block context
                    childFloatContribution = childBlockCtx.floatedContentHeightContribution();
                    boolean[] childTopAdjoiningFloats = childBlockCtx.topAdjoiningFloats();
                    // Floats placed while the position of the child's top
                    // margin strut was unresolved also adjoin this block's
                    // current strut
                    blockCtx.mergeAdjoiningFloats(childTopAdjoiningFloats);

                    itemLayout = output;
                } else {
                    itemLayout = tree.computeChildLayout(item.nodeId, childInputs);
                }
                item.oofCandidates = itemLayout.oofCandidates().take();
                SizeF32 finalSize = itemLayout.size();

                CollapsibleMarginSet topMarginSet =
                        itemLayout.topMargin().collapseWithMargin(OptF32.unwrapOr(itemMargin.top(), 0.0f));
                CollapsibleMarginSet bottomMarginSet =
                        itemLayout.bottomMargin().collapseWithMargin(OptF32.unwrapOr(itemMargin.bottom(), 0.0f));

                // Expand auto margins to fill available space
                // Note: Vertical auto-margins for relatively positioned block
                // items simply resolve to 0. See:
                // https://www.w3.org/TR/CSS21/visudet.html#abs-non-replaced-width
                float freeXSpace = Sys.f32Max(0.0f, stretchWidth - finalSize.width());
                int autoMarginCount =
                        (OptF32.isNone(itemMargin.left()) ? 1 : 0) + (OptF32.isNone(itemMargin.right()) ? 1 : 0);
                float xAxisAutoMarginSize = autoMarginCount > 0 ? freeXSpace / autoMarginCount : 0.0f;
                RectF32 resolvedMargin = new RectF32(
                        OptF32.unwrapOr(itemMargin.left(), xAxisAutoMarginSize),
                        OptF32.unwrapOr(itemMargin.right(), xAxisAutoMarginSize),
                        OptF32.unwrapOr(itemMargin.top(), 0.0f),
                        OptF32.unwrapOr(itemMargin.bottom(), 0.0f));

                // Resolve item inset
                PointF32 insetOffset = resolveRelativeOffset(item, parentSize, direction, tree);

                // Set yMarginOffset (same bfc child)
                if (item.isInSameBfc && (!isCollapsingWithFirstMarginSet || !ownMarginsCollapseWithChildren.start())) {
                    yMarginOffset = activeCollapsibleMarginSet
                            .collapseWithSet(topMarginSet)
                            .resolve();
                }

                // Compute clearance (CSS2.2 9.5.2). Clearance is introduced if
                // the hypothetical position of the item's top border edge (the
                // position it would have with normal margin collapsing) is not
                // past the bottom of the relevant floats. When clearance is
                // introduced the item's border edge is placed at
                // `max(float bottom, hypothetical position)`.
                boolean hasClearance = false;
                if (item.isInSameBfc && OptF32.isSome(clearThreshold)) {
                    // The hypothetical position always includes the item's
                    // collapsed top margin set, even when those margins
                    // collapse with the container's own top margin (and are
                    // thus applied outside the container): in that case they
                    // still move the container (and hence the item) relative
                    // to the floats.
                    float hypotheticalY = committedYOffset
                            + activeCollapsibleMarginSet
                                    .collapseWithSet(topMarginSet)
                                    .resolve();
                    // Clearance is forced (regardless of the hypothetical
                    // position) if a relevant float is adjoining the
                    // margin-collapse strut that the item's top margin would
                    // collapse into: if the margins were allowed to collapse
                    // they would pull the float down with the item, so
                    // clearance is inserted to separate the two, placing the
                    // item just below the float.
                    boolean forcedClearance = blockCtx.hasAdjoiningFloat(item.clear);
                    if (forcedClearance || hypotheticalY < clearThreshold) {
                        hasClearance = true;
                        // Clearance stops the item's top margin collapsing
                        // with preceding margins. If those preceding margins
                        // collapse with the container's own top margin they
                        // are applied outside the container (moving it down),
                        // so the item's cleared position within the container
                        // must be reduced by that amount to keep its absolute
                        // position correct.
                        float escapedMargin = isCollapsingWithFirstMarginSet && ownMarginsCollapseWithChildren.start()
                                ? activeCollapsibleMarginSet.resolve()
                                : 0.0f;
                        yMarginOffset = clearThreshold - committedYOffset - escapedMargin;
                    }
                }

                item.computedSize = itemLayout.size();
                item.canBeCollapsedThrough = itemLayout.marginsCanCollapseThrough() && !hasClearance;
                if (item.isInSameBfc) {
                    float unclearedY = committedYOffset + activeCollapsibleMarginSet.resolve();
                    item.staticPosition = new PointF32(
                            direction == Direction.LTR
                                    ? resolvedContentBoxInset.left()
                                    : containerOuterWidth - resolvedContentBoxInset.right() - finalSize.width(),
                            Sys.f32Max(unclearedY, clearPos));
                } else {
                    // TODO: handle inset and margins
                    item.staticPosition = new PointF32(
                            direction == Direction.LTR
                                    ? floatAvoidingPosition.x()
                                    : floatAvoidingPosition.x() + floatAvoidingWidth - finalSize.width(),
                            floatAvoidingPosition.y());
                }

                PointF32 location;
                if (item.isInSameBfc) {
                    location = new PointF32(
                            direction == Direction.LTR
                                    ? resolvedContentBoxInset.left() + insetOffset.x() + resolvedMargin.left()
                                    : containerOuterWidth
                                            - resolvedContentBoxInset.right()
                                            - finalSize.width()
                                            - resolvedMargin.right()
                                            + insetOffset.x(),
                            committedYOffset + yMarginOffset + insetOffset.y());
                } else {
                    // When the item avoids floats, its non-auto margins are
                    // already accounted for in the slot's border-box
                    // position/width (margins may overlap floats), so only the
                    // auto portion of the resolved margin is added here.
                    float extraMarginLeft =
                            itemAvoidsFloats ? resolvedMargin.left() - itemNonAutoMargin.left() : resolvedMargin.left();
                    float extraMarginRight = itemAvoidsFloats
                            ? resolvedMargin.right() - itemNonAutoMargin.right()
                            : resolvedMargin.right();

                    // TODO: handle inset and margins
                    location = new PointF32(
                            direction == Direction.LTR
                                    ? floatAvoidingPosition.x() + extraMarginLeft + insetOffset.x()
                                    : floatAvoidingPosition.x()
                                            + floatAvoidingWidth
                                            - finalSize.width()
                                            - extraMarginRight
                                            + insetOffset.x(),
                            floatAvoidingPosition.y() + insetOffset.y());
                }

                // Floats within the child are positioned relative to the
                // child's border-top, so the child's (margin-collapsed)
                // position, rather than its relative offset, is added.
                if (item.isInSameBfc) {
                    blockCtx.addChildFloatedContentHeightContribution(
                            location.y() - insetOffset.y() + childFloatContribution);
                }

                // Apply alignment. `justify-self` aligns the item's margin box
                // within its alignment container and takes precedence over
                // legacy `text-align`, but auto margins (which have already
                // absorbed the free space) take precedence over
                // `justify-self`.
                boolean hasAutoXMargin = OptF32.isNone(itemMargin.left()) || OptF32.isNone(itemMargin.right());
                float itemOuterWidth = itemLayout.size().width() + resolvedMargin.horizontalAxisSum();
                if (nonStretchJustifySelf != null && !hasAutoXMargin) {
                    // The alignment container is the containing block, except
                    // that a BFC-establishing box placed next to a float
                    // aligns within the space left over by the float (in which
                    // case the slot is already inset by the item's margins).
                    float containerStart;
                    float alignFreeXSpace;
                    if (itemAvoidsFloats) {
                        containerStart = floatAvoidingPosition.x() - resolvedMargin.left();
                        alignFreeXSpace = floatAvoidingWidth - itemLayout.size().width();
                    } else {
                        containerStart = resolvedContentBoxInset.left();
                        alignFreeXSpace = containerInnerWidth - itemOuterWidth;
                    }

                    AlignItemsKeyword keyword =
                            nonStretchJustifySelf.safety() == AlignmentSafety.SAFE && alignFreeXSpace < 0.0f
                                    ? AlignItemsKeyword.START
                                    : nonStretchJustifySelf.keyword();
                    float alignOffset;
                    if (keyword == AlignItemsKeyword.CENTER) {
                        alignOffset = alignFreeXSpace / 2.0f;
                    } else {
                        boolean end = keyword == AlignItemsKeyword.END
                                || keyword == AlignItemsKeyword.FLEX_END
                                || keyword == AlignItemsKeyword.SELF_END;
                        alignOffset = end != direction.isRtl() ? alignFreeXSpace : 0.0f;
                    }
                    location = new PointF32(
                            containerStart + resolvedMargin.left() + insetOffset.x() + alignOffset, location.y());
                } else if (nonStretchJustifySelf == null && itemOuterWidth < containerInnerWidth) {
                    float alignFreeXSpace = containerInnerWidth - itemOuterWidth;
                    float x = location.x();
                    if (textAlign == TextAlign.LEGACY_LEFT && direction == Direction.RTL) {
                        x -= alignFreeXSpace;
                    } else if (textAlign == TextAlign.LEGACY_RIGHT && direction == Direction.LTR) {
                        x += alignFreeXSpace;
                    } else if (textAlign == TextAlign.LEGACY_CENTER) {
                        x += (direction == Direction.LTR ? alignFreeXSpace : -alignFreeXSpace) / 2.0f;
                    }
                    // AUTO always, LEGACY_LEFT in LTR, LEGACY_RIGHT in RTL:
                    // aligned at the start edge by default.
                    location = new PointF32(x, location.y());
                }

                // A block container's first baseline is the first baseline of
                // its first in-flow child that has one. The child's relative
                // offset does not affect it.
                if (OptF32.isNone(firstBaseline)) {
                    @Opt
                    float childBaseline = Alignment.childFirstBaseline(
                            itemLayout.baselines().first(), itemLayout.size().height(), item.overflow.y());
                    final float baselineLocY = location.y();
                    firstBaseline = OptF32.map(childBaseline, baseline -> baselineLocY - insetOffset.y() + baseline);
                }

                // Used auto margins include float-avoidance offsets, unlike
                // the alignment margins above.
                RectF32 usedMargin = resolvedMargin;
                if (itemAvoidsFloats) {
                    float marginLeft = usedMargin.left();
                    float marginRight = usedMargin.right();
                    if (OptF32.isNone(itemMargin.left())) {
                        marginLeft += floatAvoidingPosition.x() - resolvedContentBoxInset.left();
                    }
                    if (OptF32.isNone(itemMargin.right())) {
                        marginRight += containerOuterWidth
                                - resolvedContentBoxInset.right()
                                - floatAvoidingPosition.x()
                                - floatAvoidingWidth;
                    }
                    usedMargin = new RectF32(marginLeft, marginRight, usedMargin.top(), usedMargin.bottom());
                }

                // Defer `setUnroundedLayout` to the post-loop pass in
                // `computeInner` so that `align-content` can shift
                // `location.y` before the layout is committed to the tree.
                item.finalLayout = new Layout(
                        item.order,
                        location,
                        itemLayout.size(),
                        itemLayout.scrollableOverflowRect(),
                        scrollbarSize,
                        item.border,
                        item.padding,
                        usedMargin);

                {
                    PointF32 contributionLocation = new PointF32(
                            location.x() - resolvedContentBoxInset.left() + resolvedPadding.left(),
                            location.y() - resolvedBorder.top());
                    inflowOverflowRect =
                            inflowOverflowRect.union(ScrollableOverflow.computeScrollableOverflowContribution(
                                    contributionLocation,
                                    finalSize,
                                    itemLayout.scrollableOverflowRect().rect(),
                                    item.border,
                                    item.overflow,
                                    item.contain,
                                    overflowContainer,
                                    usedMargin.map(value -> F32.min(value, 0.0f))));
                }

                // Update first_child_top_margin_set
                //
                // The top margin of an item with clearance does not collapse
                // with the container's top margin, so clearance terminates
                // collapsing without contributing the item's own margins.
                if (isCollapsingWithFirstMarginSet && itemPushedBelowFloat) {
                    // The item's top margin "separated from the float" and
                    // must not propagate to the parent
                    isCollapsingWithFirstMarginSet = false;
                }
                if (isCollapsingWithFirstMarginSet && hasClearance) {
                    isCollapsingWithFirstMarginSet = false;
                } else if (isCollapsingWithFirstMarginSet) {
                    if (item.canBeCollapsedThrough) {
                        firstChildTopMarginSet = firstChildTopMarginSet
                                .collapseWithSet(topMarginSet)
                                .collapseWithSet(bottomMarginSet);
                    } else {
                        firstChildTopMarginSet = firstChildTopMarginSet.collapseWithSet(topMarginSet);
                        isCollapsingWithFirstMarginSet = false;
                    }
                }

                // Update active_collapsible_margin_set
                if (item.canBeCollapsedThrough) {
                    activeCollapsibleMarginSet = activeCollapsibleMarginSet
                            .collapseWithSet(topMarginSet)
                            .collapseWithSet(bottomMarginSet);
                    yOffsetForAbsolute = committedYOffset + itemLayout.size().height() + yMarginOffset;
                } else {
                    committedYOffset =
                            location.y() - insetOffset.y() + itemLayout.size().height();
                    // A self-collapsing item with clearance is not collapsed
                    // through (its margins do not collapse with margins of
                    // preceding siblings), but its top and bottom margins
                    // still collapse with each other and with the margins of
                    // following siblings.
                    if (hasClearance && itemLayout.marginsCanCollapseThrough()) {
                        // The element's border edge stays at the cleared
                        // position, but its collapsed margin extends below it:
                        // the border edge sits `top margin` inside the
                        // collapsed margin, so following content is offset by
                        // `collapsed margin - top margin` from the border edge.
                        committedYOffset -= topMarginSet.resolve();
                        activeCollapsibleMarginSet = topMarginSet.collapseWithSet(bottomMarginSet);
                        activeMarginSetHasClearance = true;
                    } else {
                        activeCollapsibleMarginSet = bottomMarginSet;
                        activeMarginSetHasClearance = false;
                    }
                    yOffsetForAbsolute = committedYOffset + activeCollapsibleMarginSet.resolve();
                    // Committing in-flow content resolves the position of the
                    // current margin-collapse strut, so floats placed before
                    // this point no longer force clearance
                    blockCtx.commitStrut();
                }
            }
        }

        // The margins of a self-collapsing element with clearance do not
        // collapse with the bottom margin of the parent block: they extend the
        // parent's content height instead of escaping it
        CollapsibleMarginSet lastChildBottomMarginSet =
                activeMarginSetHasClearance ? CollapsibleMarginSet.ZERO : activeCollapsibleMarginSet;
        float bottomYMarginOffset;
        if (activeMarginSetHasClearance) {
            bottomYMarginOffset = activeCollapsibleMarginSet.resolve();
        } else if (ownMarginsCollapseWithChildren.end()
                || (isCollapsingWithFirstMarginSet && ownMarginsCollapseWithChildren.start())) {
            bottomYMarginOffset = 0.0f;
        } else {
            bottomYMarginOffset = lastChildBottomMarginSet.resolve();
        }

        committedYOffset += resolvedContentBoxInset.bottom() + bottomYMarginOffset;
        float contentHeight = Sys.f32Max(0.0f, committedYOffset);
        return new FinalLayoutResult(
                inflowOverflowRect, contentHeight, firstChildTopMarginSet, lastChildBottomMarginSet, firstBaseline);
    }
}
