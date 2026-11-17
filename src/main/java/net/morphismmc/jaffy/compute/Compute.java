// JAFFY-UPSTREAM: src/compute/mod.rs
// JAFFY-NOTE: the layout dispatcher
//
// `mod detailed_info` is a re-export-only module: nothing to port (JAFFY
// callers import the grid classes directly). `impl LayoutContainingBlock +
// CacheTree` -> `<Tree extends LayoutContainingBlock & CacheTree>` generic
// bound; `ComputeFunction: FnOnce` -> {@link ComputeFunction}; `&mut bool`
// flag -> `boolean[1]`; `(Layout, OofCandidates)` tuple -> {@link
// InFlowResult}; `content_size`/`block_layout`/`grid` cfg always on.
// `Layout`/`LayoutOutput`/`ScrollableOverflow` are immutable records — field
// writes rebuild the record.
package net.morphismmc.jaffy.compute;

import net.morphismmc.jaffy.Features;
import net.morphismmc.jaffy.compute.Oof.OofBoxLayout;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.ContainingBlockClaims;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.available_space.SizeAvailableSpaceExt;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.tree.layout.AxisStaticAlign;
import net.morphismmc.jaffy.tree.layout.AxisStaticEdge;
import net.morphismmc.jaffy.tree.layout.AxisStaticPosition;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.OofCandidate;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.CacheTree;
import net.morphismmc.jaffy.tree.traits.LayoutContainingBlock;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;
import net.morphismmc.jaffy.tree.traits.RoundTree;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.jaffy.util.DebugLogger;
import net.morphismmc.jaffy.util.MaybeResolve.RectLengthPercentageAutoExt;
import net.morphismmc.jaffy.util.MaybeResolve.RectLengthPercentageExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeDimensionExt;
import net.morphismmc.jaffy.util.MaybeResolve.SizeLengthPercentageAutoExt;
import net.morphismmc.jaffy.util.Sys;
import net.morphismmc.just.fmt.RustFmt;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;

/// Low-level access to the layout algorithms themselves.
public final class Compute {
    private Compute() {}

    /// `FnOnce(&mut Tree, NodeId, LayoutInput) -> LayoutOutput`
    @FunctionalInterface
    public interface ComputeFunction<Tree extends CacheTree> {
        LayoutOutput apply(Tree tree, long node, LayoutInput inputs);
    }

    /// `(Layout, OofCandidates)` returned by `compute_in_flow_root_layout`.
    private record InFlowResult(Layout layout, OofCandidates candidates) {}

    /// Compute layout for the root node in the tree (`compute_root_layout`).
    ///
    /// The root's containing block is the initial containing block (ICB),
    /// which has the dimensions of the viewport (`availableSpace`) and is
    /// anchored at the canvas origin. The root's `position` and `inset` styles
    /// are resolved against it:
    ///
    /// - A `position: absolute`/`fixed` root is laid out by the out-of-flow
    ///   positioning routine used for every other out-of-flow box (insets,
    ///   shrink-to-fit sizing, auto margins, ...), with its static position at
    ///   the ICB origin. This requires a definite `availableSpace` in both
    ///   axes: otherwise there is no ICB to resolve against and the root is
    ///   laid out as if it were in flow.
    /// - A `position: relative` root is laid out in flow and then offset by
    ///   its insets.
    public static <Tree extends LayoutContainingBlock & CacheTree> void computeRootLayout(
            Tree tree, long root, Size<AvailableSpace> availableSpace) {
        Style style = tree.getCoreContainerStyle(root);
        Position position = style.position();
        Direction direction = style.direction();

        SizeOptF32 icbSize = SizeAvailableSpaceExt.intoOptions(availableSpace);
        @Nullable
        SizeF32 oofRootArea =
                position.isOutOfFlow() && OptF32.isSome(icbSize.width()) && OptF32.isSome(icbSize.height())
                        ? new SizeF32(icbSize.width(), icbSize.height())
                        : null;

        // When the root's layout is served from the cache its layout
        // algorithm does not run, so the hoisted children it recorded on a
        // previous run (including those added by the root positioning pass
        // below) are still in place and must not be re-added.
        boolean[] rootIsCached = {false};

        Layout layout;
        OofCandidates candidates;
        if (oofRootArea != null) {
            // The static position of the root places its margin box at the
            // origin of the ICB
            float start = direction.isRtl() ? oofRootArea.width() : 0.0f;
            float end = direction.isRtl() ? oofRootArea.width() : 0.0f;
            OofCandidate candidate = new OofCandidate(
                    root,
                    0,
                    position,
                    new Point<>(
                            new AxisStaticPosition(
                                    new LineF32(start, end),
                                    AxisStaticAlign.fromKeyword(
                                            direction.isRtl() ? AxisStaticEdge.END : AxisStaticEdge.START)),
                            AxisStaticPosition.fromEdge(0.0f, AxisStaticEdge.START)));
            OofBoxLayout box = Oof.layoutOofBox(
                    tree,
                    candidate,
                    oofRootArea,
                    PointF32.ZERO,
                    direction,
                    // The initial containing block is not a scroll container
                    new Point<>(Overflow.VISIBLE, Overflow.VISIBLE),
                    0L,
                    (t, inputs) -> rootIsCached[0] = tree.cacheGet(root, inputs) != null);
            layout = box.layout();
            candidates = box.surfaced();
        } else {
            InFlowResult result = computeInFlowRootLayout(tree, root, availableSpace, rootIsCached);
            layout = result.layout();
            candidates = result.candidates();
        }

        // Final positioning pass for out-of-flow boxes with no nearer
        // containing block: the initial containing block is the containing
        // block for `position: fixed` boxes and for `position: absolute`
        // boxes with no positioned ancestor.
        if (!candidates.isEmpty()) {
            Point<Overflow> overflow = tree.getCoreContainerStyle(root).overflow();

            // The initial containing block has the dimensions of the viewport
            // (the available space) and is anchored at the canvas origin. In
            // an axis where the available space is indefinite, fall back to
            // the root's padding box.
            RectF32 areaInset = layout.border()
                    .plus(new RectF32(
                            0.0f,
                            layout.scrollbarSize().width(),
                            0.0f,
                            layout.scrollbarSize().height()));
            SizeF32 rootPaddingBoxSize =
                    layout.size().minus(new SizeF32(areaInset.horizontalAxisSum(), areaInset.verticalAxisSum()));
            float areaWidth;
            float areaX;
            if (OptF32.isSome(icbSize.width())) {
                areaWidth = Sys.f32Max(icbSize.width() - layout.scrollbarSize().width(), 0.0f);
                areaX = -layout.location().x();
            } else {
                areaWidth = rootPaddingBoxSize.width();
                areaX = areaInset.left();
            }
            float areaHeight;
            float areaY;
            if (OptF32.isSome(icbSize.height())) {
                areaHeight =
                        Sys.f32Max(icbSize.height() - layout.scrollbarSize().height(), 0.0f);
                areaY = -layout.location().y();
            } else {
                areaHeight = rootPaddingBoxSize.height();
                areaY = areaInset.top();
            }
            SizeF32 areaSize = new SizeF32(areaWidth, areaHeight);
            PointF32 areaOffset = new PointF32(areaX, areaY);

            LongArrayList hoisted = new LongArrayList();
            OofCandidates unclaimed = OofCandidates.create();
            Oof.performOofLayout(
                    tree,
                    root,
                    candidates.asSlice(),
                    areaSize,
                    areaOffset,
                    direction,
                    // The root is the initial containing block and claims all
                    // remaining candidates
                    ContainingBlockClaims.ALL,
                    overflow,
                    new PointBool(direction.isRtl(), false),
                    hoisted,
                    unclaimed);
            // debug_assert!(unclaimed.is_empty()) — the root positioning pass
            // must claim all remaining candidates (unconditional `claims`).
            if (!rootIsCached[0]) {
                tree.addHoistedChildren(root, hoisted.toLongArray());
            }
        }
    }

    /// Lay out an in-flow (or `position: relative`) root node against the
    /// available space, store its layout and return it along with the
    /// out-of-flow candidates bubbled up from its subtree
    /// (`compute_in_flow_root_layout`).
    private static <Tree extends LayoutContainingBlock & CacheTree> InFlowResult computeInFlowRootLayout(
            Tree tree, long root, Size<AvailableSpace> availableSpace, boolean[] rootIsCached) {
        CalcResolver calc = tree::calc;
        SizeOptF32 knownDimensions = SizeOptF32.NONE;

        // The available space passed to the root excludes the root's margins
        Size<AvailableSpace> rootAvailableSpace;
        {
            Style style = tree.getCoreContainerStyle(root);
            RectF32 margin = RectLengthPercentageAutoExt.resolveOrZeroO(
                    style.margin(), availableSpace.width().intoOption(), calc);
            rootAvailableSpace = SizeAvailableSpaceExt.maybeMax(
                    SizeAvailableSpaceExt.maybeSub(availableSpace, margin.sumAxes()), SizeF32.ZERO);
        }

        {
            SizeOptF32 parentSize = SizeAvailableSpaceExt.intoOptions(availableSpace);
            Style style = tree.getCoreContainerStyle(root);

            if (style.isBlock()) {
                // Pull these out earlier to avoid borrowing issues
                @Opt float aspectRatio = style.aspectRatio();
                RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(style.padding(), parentSize.width(), calc);
                RectF32 border = RectLengthPercentageExt.resolveOrZeroO(style.border(), parentSize.width(), calc);
                SizeF32 paddingBorderSize = padding.plus(border).sumAxes();
                SizeF32 boxSizingAdjustment =
                        style.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSize : SizeF32.ZERO;

                SizeOptF32 minSize = SizeLengthPercentageAutoExt.maybeResolveO(style.minSize(), parentSize, calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
                SizeOptF32 maxSize = SizeLengthPercentageAutoExt.maybeResolveO(style.maxSize(), parentSize, calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment);
                SizeOptF32 clampedStyleSize = SizeDimensionExt.maybeResolveO(style.size(), parentSize, calc)
                        .maybeApplyAspectRatio(aspectRatio)
                        .maybeAddF(boxSizingAdjustment)
                        .maybeClampO(minSize, maxSize);

                // If both min and max in a given axis are set and max <= min
                // then this determines the size in that axis
                SizeOptF32 minMaxDefiniteSize = minSize.zipMap(
                        maxSize,
                        (min, max) -> OptF32.isSome(min) && OptF32.isSome(max) && max <= min ? min : OptF32.NONE);

                // Block nodes automatically stretch fit their width to fit
                // available space if available space is definite
                SizeOptF32 availableSpaceBasedSize =
                        new SizeOptF32(rootAvailableSpace.width().intoOption(), OptF32.NONE);

                knownDimensions = knownDimensions
                        .or(minMaxDefiniteSize)
                        .or(clampedStyleSize)
                        .or(availableSpaceBasedSize)
                        .maybeMaxF(paddingBorderSize);
            }
        }

        LayoutInput inputs = new LayoutInput(
                RunMode.PERFORM_LAYOUT,
                SizingMode.INHERENT_SIZE,
                RequestedAxis.BOTH,
                knownDimensions,
                SizeBool.TRUE,
                SizeAvailableSpaceExt.intoOptions(availableSpace),
                rootAvailableSpace,
                LineBool.FALSE);
        rootIsCached[0] = tree.cacheGet(root, inputs) != null;

        // Recursively compute node layout
        LayoutOutput output = tree.computeChildLayout(root, inputs);
        Style style = tree.getCoreContainerStyle(root);
        RectF32 padding = RectLengthPercentageExt.resolveOrZeroO(
                style.padding(), availableSpace.width().intoOption(), calc);
        RectF32 border = RectLengthPercentageExt.resolveOrZeroO(
                style.border(), availableSpace.width().intoOption(), calc);
        RectF32 margin = RectLengthPercentageAutoExt.resolveOrZeroO(
                style.margin(), availableSpace.width().intoOption(), calc);
        SizeF32 scrollbarSize = new SizeF32(
                style.overflow().y() == Overflow.SCROLL ? style.scrollbarWidth() : 0.0f,
                style.overflow().x() == Overflow.SCROLL ? style.scrollbarWidth() : 0.0f);
        boolean isRtl = style.direction().isRtl();

        // The root's margin box is placed at the origin of the initial
        // containing block
        PointF32 location = new PointF32(
                isRtl && OptF32.isSome(availableSpace.width().intoOption())
                        ? availableSpace.width().intoOption() - output.size().width() - margin.right()
                        : margin.left(),
                margin.top());

        // A relatively positioned root is offset by its insets (resolved
        // against the initial containing block)
        if (style.position() == Position.RELATIVE) {
            SizeOptF32 icb = SizeAvailableSpaceExt.intoOptions(availableSpace);
            Rect<LengthPercentageAuto> inset = style.inset();
            @Opt float left = inset.left().maybeResolveO(icb.width(), calc);
            @Opt float right = inset.right().maybeResolveO(icb.width(), calc);
            @Opt float top = inset.top().maybeResolveO(icb.height(), calc);
            @Opt float bottom = inset.bottom().maybeResolveO(icb.height(), calc);
            float x = location.x()
                    + OptF32.unwrapOr(
                            isRtl
                                    ? OptF32.or(OptF32.map(right, v -> -v), left)
                                    : OptF32.or(left, OptF32.map(right, v -> -v)),
                            0.0f);
            float y = location.y() + OptF32.unwrapOr(OptF32.or(top, OptF32.map(bottom, v -> -v)), 0.0f);
            location = new PointF32(x, y);
        }

        Layout layout = new Layout(
                0,
                location,
                output.size(),
                output.scrollableOverflowRect(),
                scrollbarSize,
                border,
                padding,
                // TODO: support auto margins for root node?
                margin);
        tree.setUnroundedLayout(root, layout);

        return new InFlowResult(layout, output.oofCandidates().take());
    }

    /// Attempts to find a cached layout for the specified node and layout
    /// inputs.
    ///
    /// Uses the provided function to compute the layout (and then stores the
    /// result in the cache) if no cached layout is found
    /// (`compute_cached_layout`).
    public static <Tree extends CacheTree> LayoutOutput computeCachedLayout(
            Tree tree, long node, LayoutInput inputs, ComputeFunction<Tree> computeUncached) {
        DebugLogger.NODE_LOGGER.debugPushNode(node);

        // First we check if we have a cached result for the given input
        LayoutOutput cached = tree.cacheGet(node, inputs);
        if (cached != null) {
            if (Features.DEBUG) {
                debugLogNode(inputs);
                DebugLogger.NODE_LOGGER.labelledDebugLog("RESULT (CACHED)", debugSizeF32(cached.size()));
            }
            DebugLogger.NODE_LOGGER.debugPopNode();
            return cached;
        }

        if (Features.DEBUG) {
            debugLogNode(inputs);
        }

        LayoutOutput computed = computeUncached.apply(tree, node, inputs);

        // Cache result (`clone` — the record is immutable but `OofCandidates`
        // is a mutable container, so it is snapshot-copied)
        tree.cacheStore(
                node,
                inputs,
                new LayoutOutput(
                        computed.size(),
                        computed.scrollableOverflowRect(),
                        computed.baselines(),
                        computed.topMargin(),
                        computed.bottomMargin(),
                        computed.marginsCanCollapseThrough(),
                        computed.oofCandidates().copy(),
                        computed.oofPositioningArea()));

        if (Features.DEBUG) {
            DebugLogger.NODE_LOGGER.labelledDebugLog("RESULT", debugSizeF32(computed.size()));
        }
        DebugLogger.NODE_LOGGER.debugPopNode();

        return computed;
    }

    /// Rounds the calculated layout to exact pixel values (`round_layout`).
    ///
    /// In order to ensure that no gaps in the layout are introduced we:
    ///   - Always round based on the cumulative x/y coordinates (relative to
    ///     the viewport) rather than parent-relative coordinates
    ///   - Compute width/height by first rounding the top/bottom/left/right
    ///     and then computing the difference rather than rounding the
    ///     width/height directly
    ///
    /// See <https://github.com/facebook/yoga/commit/aa5b296ac78f7a22e1aeaf4891243c6bb76488e2>
    /// for more context
    ///
    /// In order to prevent inaccuracies caused by rounding already-rounded
    /// values, we read from `unroundedLayout` and write to `finalLayout`.
    public static void roundLayout(RoundTree tree, long nodeId) {
        roundLayoutInner(tree, nodeId, 0.0f, 0.0f);
    }

    /// Recursive function to apply rounding to all descendants
    /// (`round_layout_inner`).
    private static void roundLayoutInner(RoundTree tree, long nodeId, float cumulativeX, float cumulativeY) {
        Layout unrounded = tree.getUnroundedLayout(nodeId);

        float parentX = cumulativeX;
        float parentY = cumulativeY;
        float cx = cumulativeX + unrounded.location().x();
        float cy = cumulativeY + unrounded.location().y();

        Layout layout = new Layout(
                unrounded.order(),
                new PointF32(Sys.round(cx) - Sys.round(parentX), Sys.round(cy) - Sys.round(parentY)),
                new SizeF32(
                        Sys.round(cx + unrounded.size().width()) - Sys.round(cx),
                        Sys.round(cy + unrounded.size().height()) - Sys.round(cy)),
                roundScrollableOverflowRect(unrounded.scrollableOverflowRect(), cx, cy),
                new SizeF32(
                        Sys.round(unrounded.scrollbarSize().width()),
                        Sys.round(unrounded.scrollbarSize().height())),
                new RectF32(
                        Sys.round(cx + unrounded.border().left()) - Sys.round(cx),
                        Sys.round(cx + unrounded.size().width())
                                - Sys.round(cx
                                        + unrounded.size().width()
                                        - unrounded.border().right()),
                        Sys.round(cy + unrounded.border().top()) - Sys.round(cy),
                        Sys.round(cy + unrounded.size().height())
                                - Sys.round(cy
                                        + unrounded.size().height()
                                        - unrounded.border().bottom())),
                new RectF32(
                        Sys.round(cx + unrounded.padding().left()) - Sys.round(cx),
                        Sys.round(cx + unrounded.size().width())
                                - Sys.round(cx
                                        + unrounded.size().width()
                                        - unrounded.padding().right()),
                        Sys.round(cy + unrounded.padding().top()) - Sys.round(cy),
                        Sys.round(cy + unrounded.size().height())
                                - Sys.round(cy
                                        + unrounded.size().height()
                                        - unrounded.padding().bottom())),
                unrounded.margin());

        tree.setFinalLayout(nodeId, layout);

        // Recurse into in-flow children. Out-of-flow (absolute/fixed)
        // children are skipped here: they are instead visited via their
        // containing block's hoisted child list below, which ensures each
        // node is visited exactly once and that its cumulative offset is
        // accumulated relative to its containing block (which its `location`
        // is relative to).
        int childCount = tree.childCount(nodeId);
        for (int index = 0; index < childCount; index++) {
            long child = tree.getChildId(nodeId, index);
            if (!tree.isOutOfFlow(child)) {
                roundLayoutInner(tree, child, cx, cy);
            }
        }

        // Recurse into out-of-flow boxes for which this node is the
        // containing block
        int hoistedCount = tree.hoistedChildCount(nodeId);
        for (int index = 0; index < hoistedCount; index++) {
            long child = tree.getHoistedChildId(nodeId, index);
            roundLayoutInner(tree, child, cx, cy);
        }
    }

    /// Round the scrollable overflow rect. This is split into a separate
    /// function to make it easier to feature flag
    /// (`round_scrollable_overflow_rect`; `content_size` always on).
    private static net.morphismmc.jaffy.tree.layout.ScrollableOverflow roundScrollableOverflowRect(
            net.morphismmc.jaffy.tree.layout.ScrollableOverflow unroundedOverflow,
            float cumulativeX,
            float cumulativeY) {
        RectF32 unroundedRect = unroundedOverflow.rect();
        return net.morphismmc.jaffy.tree.layout.ScrollableOverflow.of(
                new RectF32(
                        roundEdge(cumulativeX, unroundedRect.left()),
                        roundEdge(cumulativeX, unroundedRect.right()),
                        roundEdge(cumulativeY, unroundedRect.top()),
                        roundEdge(cumulativeY, unroundedRect.bottom())),
                unroundedOverflow.isReversed());
    }

    /// The `round_edge` closure of `round_scrollable_overflow_rect`. Keeps
    /// left/top -0.0; `ScrollableOverflow` only re-packs right/bottom signs
    /// from reversed.
    private static float roundEdge(float offset, float edge) {
        float rounded = Sys.round(offset + edge) - Sys.round(offset);
        return rounded == 0.0f ? Math.copySign(rounded, edge) : rounded;
    }

    /// Creates a layout for this node and its children, recursively. Each
    /// hidden node has zero size and is placed at the origin
    /// (`compute_hidden_layout`).
    public static <Tree extends LayoutPartialTree & CacheTree> LayoutOutput computeHiddenLayout(Tree tree, long node) {
        // Clear cache and set zeroed-out layout for the node
        tree.cacheClear(node);
        tree.setUnroundedLayout(node, Layout.withOrder(0));

        // Perform hidden layout on all children
        int childCount = tree.childCount(node);
        for (int index = 0; index < childCount; index++) {
            long childId = tree.getChildId(node, index);
            tree.computeChildLayout(childId, LayoutInput.HIDDEN);
        }

        return LayoutOutput.hidden();
    }

    /// `debug_log_node!(inputs)` — the five `debug_log!` calls of the upstream
    /// macro (Rust `{:?}` formatting via {@link RustFmt}). Public so that
    /// `taffy_tree.TaffyView`'s `compute_child_layout` (the other upstream
    /// call site of the macro) can share it.
    public static void debugLogNode(LayoutInput inputs) {
        if (!Features.DEBUG) {
            return;
        }
        DebugLogger.NODE_LOGGER.debugLog(debugRunMode(inputs.runMode()));
        DebugLogger.NODE_LOGGER.labelledDebugLog("sizing_mode", debugSizingMode(inputs.sizingMode()));
        DebugLogger.NODE_LOGGER.labelledDebugLog("known_dimensions", debugSizeOptF32(inputs.knownDimensions()));
        DebugLogger.NODE_LOGGER.labelledDebugLog("parent_size", debugSizeOptF32(inputs.parentSize()));
        DebugLogger.NODE_LOGGER.labelledDebugLog("available_space", debugSizeAvailableSpace(inputs.availableSpace()));
    }

    /// Rust `{:?}` for `RunMode`
    private static String debugRunMode(RunMode runMode) {
        return switch (runMode) {
            case PERFORM_LAYOUT -> "PerformLayout";
            case COMPUTE_SIZE -> "ComputeSize";
            case PERFORM_HIDDEN_LAYOUT -> "PerformHiddenLayout";
        };
    }

    /// Rust `{:?}` for `SizingMode`
    private static String debugSizingMode(SizingMode sizingMode) {
        return switch (sizingMode) {
            case CONTENT_SIZE -> "ContentSize";
            case INHERENT_SIZE -> "InherentSize";
        };
    }

    /// Rust `{:?}` for `Size<Option<f32>>`
    private static String debugSizeOptF32(SizeOptF32 size) {
        return "Size { width: " + debugOptF32(size.width()) + ", height: " + debugOptF32(size.height()) + " }";
    }

    /// Rust `{:?}` for `Size<f32>`
    private static String debugSizeF32(SizeF32 size) {
        return "Size { width: " + RustFmt.debug(size.width()) + ", height: " + RustFmt.debug(size.height()) + " }";
    }

    /// Rust `{:?}` for `Size<AvailableSpace>`
    private static String debugSizeAvailableSpace(Size<AvailableSpace> size) {
        return "Size { width: " + debugAvailableSpace(size.width()) + ", height: " + debugAvailableSpace(size.height())
                + " }";
    }

    /// Rust `{:?}` for `AvailableSpace`
    private static String debugAvailableSpace(AvailableSpace space) {
        if (space instanceof AvailableSpace.Definite d) {
            return "Definite(" + RustFmt.debug(d.value()) + ")";
        }
        if (space instanceof AvailableSpace.MinContent) {
            return "MinContent";
        }
        return "MaxContent";
    }

    /// Rust `{:?}` for `Option<f32>` — `Some(v)` / `None`
    private static String debugOptF32(@Opt float v) {
        return OptF32.isSome(v) ? "Some(" + RustFmt.debug(v) + ")" : "None";
    }
}
