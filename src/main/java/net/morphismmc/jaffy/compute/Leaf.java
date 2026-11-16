// JAFFY: port of upstream src/compute/leaf.rs — `compute_leaf_layout`.
//
// `MeasureFunction: FnOnce(Size<Option<f32>>, Size<AvailableSpace>) ->
// Size<f32>` -> `MeasureFn` (GAP-019: FnMut-style captured state is the
// caller's own mutable container); `impl Fn(*const (), f32) -> f32` ->
// `CalcResolver`; `Option<f32>` -> @Opt float; `unreachable!` ->
// IllegalStateException (GAP-010).
package net.morphismmc.jaffy.compute;

import net.morphismmc.jaffy.Features;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.just.fmt.RustFmt;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.CoreStyle;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.tree.layout.Baselines;
import net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.ScrollableOverflow;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.jaffy.util.DebugLogger;
import net.morphismmc.just.math.F32;
import net.morphismmc.jaffy.util.MaybeResolve.RectLengthPercentageExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeDimensionExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeLengthPercentageAutoExt;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.jaffy.util.MaybeResolve;

public final class Leaf {
    private Leaf() {}

    /// `MeasureFunction: FnOnce(Size<Option<f32>>, Size<AvailableSpace>) ->
    /// Size<f32>` — the caller-provided intrinsic-size measurement.
    @FunctionalInterface
    public interface MeasureFn {
        /// `measure_function(known_dimensions, available_space)`
        SizeF32 measure(SizeOptF32 knownDimensions, Size<AvailableSpace> availableSpace);
    }

    /// Compute the size of a leaf node (node with no children)
    ///
    /// A definite `inputs.availableSpace` is treated as the space available
    /// to the node's border box: the caller is expected to have already
    /// subtracted the node's margins from it.
    public static LayoutOutput computeLeafLayout(
            LayoutInput inputs, CoreStyle style, CalcResolver resolveCalcValue, MeasureFn measureFunction) {
        SizeOptF32 knownDimensions = inputs.knownDimensions();
        SizeOptF32 parentSize = inputs.parentSize();
        Size<AvailableSpace> availableSpace = inputs.availableSpace();
        SizingMode sizingMode = inputs.sizingMode();
        RunMode runMode = inputs.runMode();

        // Note: both horizontal and vertical percentage padding/borders are
        // resolved against the container's inline size (i.e. width). This is
        // not a bug, but is how CSS is specified.
        // (see: https://developer.mozilla.org/en-US/docs/Web/CSS/padding#values)
        RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(style.padding(), parentSize.width(), resolveCalcValue);
        RectF32 border = RectLengthPercentageExt.resolveOrZeroO(style.border(), parentSize.width(), resolveCalcValue);
        RectF32 paddingBorder = padding.plus(border);
        SizeF32 pbSum = paddingBorder.sumAxes();
        SizeF32 boxSizingAdjustment = style.boxSizing() == BoxSizing.CONTENT_BOX ? pbSum : SizeF32.ZERO;

        // Resolve node's preferred/min/max sizes (width/heights) against the
        // available space (percentages resolve to pixel values)
        // For ContentSize mode, we pretend that the node has no size styles
        // as these should be ignored.
        SizeOptF32 nodeSize;
        SizeOptF32 nodeMinSize;
        SizeOptF32 nodeMaxSize;
        @Opt float aspectRatio;
        switch (sizingMode) {
            case CONTENT_SIZE -> {
                nodeSize = knownDimensions;
                nodeMinSize = SizeOptF32.NONE;
                nodeMaxSize = SizeOptF32.NONE;
                aspectRatio = OptF32.NONE;
            }
            case INHERENT_SIZE -> {
                aspectRatio = style.aspectRatio();
                SizeOptF32 styleSize = SizeDimensionExt.maybeResolveO(style.size(), parentSize, resolveCalcValue)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
                nodeMinSize = SizeLengthPercentageAutoExt.maybeResolveO(
                                style.minSize(), parentSize, resolveCalcValue)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
                nodeMaxSize = SizeLengthPercentageAutoExt.maybeResolveO(
                                style.maxSize(), parentSize, resolveCalcValue)
                        .maybeAddF(boxSizingAdjustment);

                nodeSize = knownDimensions.or(styleSize);
            }
            default -> throw new IllegalStateException("unreachable"); // JAFFY(GAP-010): exhaustive enum
        }

        // Scrollbar gutters are reserved when the `overflow` property is set
        // to `Overflow::Scroll`. However, the axes are switched (transposed)
        // because a node that scrolls vertically needs *horizontal* space to
        // be reserved for a scrollbar
        float scrollbarWidth = style.scrollbarWidth();
        PointF32 scrollbarGutter = new PointF32(
                style.overflow().y() == Overflow.SCROLL ? scrollbarWidth : 0.0f,
                style.overflow().x() == Overflow.SCROLL ? scrollbarWidth : 0.0f);
        // TODO: make side configurable based on the `direction` property
        RectF32 contentBoxInset = new RectF32(
                paddingBorder.left(),
                paddingBorder.right() + scrollbarGutter.x(),
                paddingBorder.top(),
                paddingBorder.bottom() + scrollbarGutter.y());

        boolean hasStylesPreventingBeingCollapsedThrough = !style.isBlock()
                || style.overflow().x().isScrollContainer()
                || style.overflow().y().isScrollContainer()
                || style.position().isOutOfFlow()
                || style.contain().establishesIndependentFormattingContext()
                || padding.top() > 0.0f
                || padding.bottom() > 0.0f
                || border.top() > 0.0f
                || border.bottom() > 0.0f
                || (OptF32.isSome(nodeSize.height()) && nodeSize.height() > 0.0f)
                || (OptF32.isSome(nodeMinSize.height()) && nodeMinSize.height() > 0.0f);

        if (Features.DEBUG) {
            DebugLogger.NODE_LOGGER.log("LEAF");
            DebugLogger.NODE_LOGGER.labelledDebugLog("node_size", debugSizeOptF32(nodeSize));
            DebugLogger.NODE_LOGGER.labelledDebugLog("min_size ", debugSizeOptF32(nodeMinSize));
            DebugLogger.NODE_LOGGER.labelledDebugLog("max_size ", debugSizeOptF32(nodeMaxSize));
        }

        // Return early if both width and height are known
        if (runMode == RunMode.COMPUTE_SIZE && hasStylesPreventingBeingCollapsedThrough) {
            if (OptF32.isSome(nodeSize.width()) && OptF32.isSome(nodeSize.height())) {
                SizeF32 size = new SizeF32(nodeSize.width(), nodeSize.height())
                        .maybeClampO(nodeMinSize, nodeMaxSize)
                        .maybeMaxO(pbSum.mapO(v -> v));
                return new LayoutOutput(
                        size,
                        ScrollableOverflow.ZERO,
                        Baselines.NONE,
                        CollapsibleMarginSet.ZERO,
                        CollapsibleMarginSet.ZERO,
                        false,
                        OofCandidates.none(),
                        null);
            }
        }

        // Compute available space
        Size<AvailableSpace> computedAvailableSpace = new Size<>(
                (OptF32.isSome(knownDimensions.width())
                                ? new AvailableSpace.Definite(knownDimensions.width())
                                : availableSpace.width())
                        .maybeSet(knownDimensions.width())
                        .maybeSet(nodeSize.width())
                        .mapDefiniteValue(s -> F32.maybeClampO(s, nodeMinSize.width(), nodeMaxSize.width())
                                - contentBoxInset.horizontalAxisSum()),
                (OptF32.isSome(knownDimensions.height())
                                ? new AvailableSpace.Definite(knownDimensions.height())
                                : availableSpace.height())
                        .maybeSet(knownDimensions.height())
                        .maybeSet(nodeSize.height())
                        .mapDefiniteValue(s -> F32.maybeClampO(s, nodeMinSize.height(), nodeMaxSize.height())
                                - contentBoxInset.verticalAxisSum()));

        // Measure node
        SizeF32 measuredSize =
                measureFunction.measure(
                        switch (runMode) {
                            case COMPUTE_SIZE -> knownDimensions;
                            case PERFORM_LAYOUT -> SizeOptF32.NONE;
                            case PERFORM_HIDDEN_LAYOUT -> throw new IllegalStateException(
                                    "unreachable"); // JAFFY(GAP-010)
                        },
                        computedAvailableSpace);
        SizeF32 clampedSize = knownDimensions
                .or(nodeSize)
                .unwrapOr(measuredSize.plus(contentBoxInset.sumAxes()))
                .maybeClampO(nodeMinSize, nodeMaxSize);
        SizeF32 size = new SizeF32(
                clampedSize.width(),
                F32.max(
                        clampedSize.height(),
                        OptF32.unwrapOr(
                                OptF32.map(aspectRatio, ratio -> clampedSize.width() / ratio), 0.0f)));
        size = size.maybeMaxO(pbSum.mapO(v -> v));

        // A scroll container's own padding at the end of the content is part
        // of its scrollable overflow region, so it is included in the
        // overflow rect. Boxes that are not scroll containers do not extend
        // their overflow region by their own padding.
        boolean isScrollContainer =
                style.overflow().x().isScrollContainer() || style.overflow().y().isScrollContainer();
        boolean isRtl = style.direction().isRtl();
        float startPadding = isRtl ? padding.right() : padding.left();
        float endPadding = isRtl ? padding.left() : padding.right();
        float clientWidth = F32.max(size.width() - border.horizontalAxisSum() - scrollbarGutter.x(), 0.0f);
        float contentEnd = startPadding + measuredSize.width() + (isScrollContainer ? endPadding : 0.0f);
        RectF32 rect = new RectF32(
                isRtl ? F32.min(clientWidth - contentEnd, 0.0f) : 0.0f,
                isRtl ? clientWidth : contentEnd,
                0.0f,
                padding.top() + measuredSize.height() + (isScrollContainer ? padding.bottom() : 0.0f));
        ScrollableOverflow scrollableOverflowRect =
                net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                        rect,
                        size.minus(border.sumAxes())
                                .minus(new SizeF32(scrollbarGutter.x(), scrollbarGutter.y())),
                        new PointBool(isRtl, false),
                        isScrollContainer);

        return new LayoutOutput(
                size,
                scrollableOverflowRect,
                Baselines.NONE,
                CollapsibleMarginSet.ZERO,
                CollapsibleMarginSet.ZERO,
                !hasStylesPreventingBeingCollapsedThrough && size.height() == 0.0f && measuredSize.height() == 0.0f,
                OofCandidates.none(),
                null);
    }

    /// Rust derived-`Debug` text for `Size<Option<f32>>` (`debug_log!` dbg:
    /// formatting — GAP-016)
    private static String debugSizeOptF32(SizeOptF32 size) {
        return "Size { width: " + debugOptF32(size.width()) + ", height: " + debugOptF32(size.height()) + " }";
    }

    /// Rust `{:?}` for `Option<f32>` — `Some(v)` / `None`
    private static String debugOptF32(@Opt float v) {
        return OptF32.isSome(v) ? "Some(" + RustFmt.debug(v) + ")" : "None";
    }
}
