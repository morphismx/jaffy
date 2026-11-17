// JAFFY: upstream=src/compute/oof.rs
// JAFFY-NOTE: shared out-of-flow
// (`position: absolute` / `position: fixed`) positioning pass.
//
// Multiple `pub` items stay nested so the file keeps a single top-level class
// (per the file-name subpackage convention): `OofLayoutResult` is `pub` in
// Rust. `pub(crate)` items (`OofBoxLayout`, `layout_oof_box`,
// `perform_oof_layout`) are `public` here because callers in sibling files
// (`compute/mod.rs`) live in the same package but Java has no module-private
// visibility below package level; `pub(crate)` types used only internally stay
// private (`OofAlignment`, `OofAxis`).
//
// `&mut LayoutOutput` -> the updated `LayoutOutput` is returned to the caller
// (immutable record, GAP-018). `Option<NodeId>` -> `@Opt long` (0L = None:
// NodeId's `version | 1` FFI encoding guarantees 0 is never valid, GAP-015).
// `&[OofCandidate]` -> `List<OofCandidate>`. `Vec<NodeId>` -> `long[]` in
// `OofLayoutResult` and fastutil `LongList` while accumulating.
package net.morphismmc.jaffy.compute;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import net.morphismmc.jaffy.compute.common.ScrollableOverflow.ScrollableOverflowContainer;
import net.morphismmc.jaffy.compute.common.SizingKeyword;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.LineOptF32;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.RectOptF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.ContainingBlockClaims;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.layout.AxisStaticEdge;
import net.morphismmc.jaffy.tree.layout.AxisStaticPosition;
import net.morphismmc.jaffy.tree.layout.DetailedLayoutInfo;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.OofCandidate;
import net.morphismmc.jaffy.tree.layout.OofCandidates;
import net.morphismmc.jaffy.tree.layout.OofPositioningArea;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutContainingBlock;
import net.morphismmc.jaffy.util.MaybeResolve;
import net.morphismmc.jaffy.util.Sys;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

/// Shared positioning pass for out-of-flow (`position: absolute` / `position:
/// fixed`) boxes.
///
/// Layout algorithms do not lay out their out-of-flow children directly
/// (unless they are the child's containing block). Instead they emit
/// {@link OofCandidate} records which bubble up the tree via
/// {@link LayoutOutput#oofCandidates} until they reach the box's containing
/// block, which lays the box out using the routines in this class.
public final class Oof {
    private Oof() {}

    /// Resolve the final static offset of an out-of-flow box from its static
    /// position, given the box's final size and resolved margins.
    ///
    /// In each axis the box's margin box is aligned within the static-position
    /// area according to the recorded alignment keyword. When the alignment is
    /// `safe` and the margin box overflows the area, the fallback keyword is
    /// used instead (the `safe` overflow-position keyword from CSS Box
    /// Alignment). The returned offset locates the box's border box and is
    /// relative to whatever the static-position areas are relative to (the
    /// containing block's border box once a candidate has bubbled to its
    /// containing block).
    public static PointF32 resolveStaticOffset(
            Point<AxisStaticPosition> staticPosition, SizeF32 finalSize, RectF32 resolvedMargin) {
        return new PointF32(
                resolveStaticOffsetAxis(
                        staticPosition.x(),
                        finalSize.width(),
                        new LineF32(resolvedMargin.left(), resolvedMargin.right())),
                resolveStaticOffsetAxis(
                        staticPosition.y(),
                        finalSize.height(),
                        new LineF32(resolvedMargin.top(), resolvedMargin.bottom())));
    }

    /// Single-axis version of {@link #resolveStaticOffset}
    private static float resolveStaticOffsetAxis(AxisStaticPosition sp, float size, LineF32 margin) {
        float marginBoxSize = size + margin.start() + margin.end();
        boolean overflows = sp.align().safety() == AlignmentSafety.SAFE
                && marginBoxSize > sp.area().end() - sp.area().start();
        AxisStaticEdge keyword = overflows ? sp.align().fallback() : sp.align().keyword();
        return alignInLine(sp.area(), marginBoxSize, keyword) + margin.start();
    }

    /// Position a box of `size` within `container` so that it is aligned to
    /// the given physical edge
    private static float alignInLine(LineF32 container, float size, AxisStaticEdge edge) {
        return switch (edge) {
            case START -> container.start();
            case END -> container.end() - size;
            case CENTER -> (container.start() + container.end() - size) / 2.0f;
        };
    }

    /// The self-alignment of an out-of-flow box in one axis, resolved relative
    /// to the writing mode of its containing block (`Start` is the logical
    /// start edge of the containing block).
    private enum OofAlignment {
        /// `normal` (or `auto`): stretch-fit sizing for non-replaced,
        /// non-table boxes; start alignment
        NORMAL,
        /// `stretch`: stretch-fit sizing; start alignment
        STRETCH,
        /// Aligned to the logical start edge
        START,
        /// Centered
        CENTER,
        /// Aligned to the logical end edge
        END;

        /// Convert an `align-self`/`justify-self` value for an out-of-flow box
        /// into a logical alignment relative to the containing block's writing
        /// mode.
        ///
        /// `alignment` must already have had `self-start`/`self-end` resolved
        /// against the containing block's direction (see
        /// {@link AlignItems#resolveSelfRelative}). The upstream
        /// `debug_assert!` that SELF_START/SELF_END are resolved is omitted
        /// (debug-only; both keywords map to START/END by the same arms).
        static OofAlignment fromResolved(AlignItems alignment) {
            return switch (alignment.keyword()) {
                case NORMAL -> NORMAL;
                case STRETCH -> STRETCH;
                case CENTER -> CENTER;
                case END, FLEX_END, SELF_END -> END;
                // The fallback alignment of `baseline` is `start`
                case START, FLEX_START, SELF_START, BASELINE -> START;
            };
        }

        /// The physical edge of the containing block the box's margin box is
        /// aligned to once its size has been determined. `Normal` and
        /// `Stretch` behave as `Start`.
        AxisStaticEdge physicalEdge(boolean reversed) {
            if (this == CENTER) {
                return AxisStaticEdge.CENTER;
            }
            boolean end = (this == END) != reversed;
            return end ? AxisStaticEdge.END : AxisStaticEdge.START;
        }
    }

    /// The inputs to the absolute positioning layout model
    /// (<https://www.w3.org/TR/css-position-3/#abspos-layout>) in a single
    /// axis. All coordinates are physical (left-to-right / top-to-bottom) and
    /// relative to the start of the containing block's inset-resolution area.
    private record OofAxis(
            /// The size of the containing block in this axis
            float cbSize,
            /// The box's resolved inset properties (`None` = `auto`)
            LineOptF32 inset,
            /// The box's resolved margins (`None` = `auto`)
            LineOptF32 margin,
            /// The box's static position (relative to the containing block's
            /// inset-resolution area)
            AxisStaticPosition staticPosition,
            /// The box's self-alignment in this axis
            OofAlignment alignment,
            /// The overflow-position modifier of the box's self-alignment in
            /// this axis
            AlignmentSafety safety,
            /// Whether this is the inline axis of the containing block
            boolean isInline,
            /// Whether the logical start edge of the containing block is the
            /// physical end edge of this axis (the inline axis of an RTL
            /// containing block)
            boolean reversed,
            /// Whether the containing block is a scroll container which
            /// scrolls in this axis
            boolean isScrollAxis) {

        /// The physical edge corresponding to the logical start edge of the
        /// containing block
        AxisStaticEdge startEdge() {
            return reversed ? AxisStaticEdge.END : AxisStaticEdge.START;
        }

        /// The physical edge corresponding to the logical end edge of the
        /// containing block
        AxisStaticEdge endEdge() {
            return reversed ? AxisStaticEdge.START : AxisStaticEdge.END;
        }

        /// The inset-modified containing block (IMCB) in this axis
        /// <https://www.w3.org/TR/css-position-3/#resolving-insets>
        LineF32 insetModifiedContainingBlock() {
            LineF32 imcb;
            AxisStaticEdge weakerEdge;
            float start = inset.start();
            float end = inset.end();
            if (OptF32.isSome(start) && OptF32.isSome(end)) {
                imcb = new LineF32(start, cbSize - end);
                weakerEdge = endEdge();
            } else if (OptF32.isSome(start)) {
                // A lone auto inset resolves to zero, and is the weaker inset
                imcb = new LineF32(start, cbSize);
                weakerEdge = AxisStaticEdge.END;
            } else if (OptF32.isSome(end)) {
                imcb = new LineF32(0.0f, cbSize - end);
                weakerEdge = AxisStaticEdge.START;
            } else {
                // Both insets are auto: resolve them from the static position
                // rectangle and the alignment within it
                AxisStaticPosition sp = staticPosition;
                imcb = switch (sp.align().keyword()) {
                    case START -> new LineF32(sp.area().start(), cbSize);
                    case END -> new LineF32(0.0f, sp.area().end());
                    case CENTER -> {
                        // Center the IMCB on the static position
                        // rectangle's center, bounded by the nearer
                        // edge of the containing block
                        float center = (sp.area().start() + sp.area().end()) / 2.0f;
                        float startDistance = center;
                        float endDistance = cbSize - center;
                        yield Math.abs(startDistance) <= Math.abs(endDistance)
                                ? new LineF32(0.0f, 2.0f * startDistance)
                                : new LineF32(cbSize - 2.0f * endDistance, cbSize);
                    }
                };
                weakerEdge = endEdge();
            }

            // Overconstrained insets: the weaker inset is reduced to bring the
            // IMCB size up to zero
            // <https://www.w3.org/TR/css-position-3/#resolving-insets>
            if (imcb.end() < imcb.start()) {
                imcb = weakerEdge == AxisStaticEdge.START
                        ? new LineF32(imcb.end(), imcb.end())
                        : new LineF32(imcb.start(), imcb.start());
            }
            return imcb;
        }

        /// The size of the IMCB in this axis: the space available to the box's
        /// margin box
        float insetModifiedContainingBlockSize() {
            LineF32 imcb = insetModifiedContainingBlock();
            return imcb.end() - imcb.start();
        }

        /// The stretch-fit size of the box's border box: the IMCB minus the
        /// non-auto margins (auto margins are treated as zero)
        /// <https://www.w3.org/TR/css-position-3/#abspos-auto-size>
        float stretchFitSize() {
            float nonAutoMarginSum = OptF32.unwrapOr(margin.start(), 0.0f) + OptF32.unwrapOr(margin.end(), 0.0f);
            return Sys.f32Max(insetModifiedContainingBlockSize() - nonAutoMarginSum, 0.0f);
        }

        /// Whether an `auto` size in this axis is the stretch-fit size (rather
        /// than fit-content)
        /// <https://www.w3.org/TR/css-position-3/#abspos-auto-size>
        boolean isStretchSized(boolean isReplaced, boolean isTable) {
            boolean noAutoInset = OptF32.isSome(inset.start()) && OptF32.isSome(inset.end());
            return noAutoInset
                    && switch (alignment) {
                        case STRETCH -> true;
                        case NORMAL -> !isReplaced && !isTable;
                        default -> false;
                    };
        }

        /// Resolve the box's auto margins given its final size
        /// <https://www.w3.org/TR/css-position-3/#abspos-margins>
        LineF32 resolveMargins(float size) {
            boolean bothInsets = OptF32.isSome(inset.start()) && OptF32.isSome(inset.end());
            LineF32 imcb = insetModifiedContainingBlock();
            float freeSpace = imcb.end() - imcb.start() - size;
            float start = margin.start();
            float end = margin.end();
            if (OptF32.isSome(start) && OptF32.isSome(end)) {
                return new LineF32(start, end);
            }
            // If either inset is auto then auto margins resolve to zero
            if (!bothInsets) {
                return new LineF32(OptF32.unwrapOr(start, 0.0f), OptF32.unwrapOr(end, 0.0f));
            }
            if (OptF32.isSome(start)) {
                return new LineF32(start, freeSpace - start);
            }
            if (OptF32.isSome(end)) {
                return new LineF32(freeSpace - end, end);
            }
            // In the inline axis, negative free space is absorbed entirely by
            // the end margin
            if (freeSpace < 0.0f && isInline) {
                return reversed ? new LineF32(freeSpace, 0.0f) : new LineF32(0.0f, freeSpace);
            }
            return new LineF32(freeSpace / 2.0f, freeSpace / 2.0f);
        }

        /// Compute the physical start of the box's border box given its final
        /// size and margins
        /// <https://www.w3.org/TR/css-position-3/#abspos-alignment>
        float position(float size, LineF32 margin) {
            LineF32 imcb = insetModifiedContainingBlock();
            float insetStart = inset.start();
            float insetEnd = inset.end();
            if (OptF32.isNone(insetStart) && OptF32.isNone(insetEnd)) {
                // Both insets are auto: static alignment, with safe overflow
                // relative to the IMCB
                AxisStaticPosition sp = staticPosition;
                float marginBoxSize = size + margin.start() + margin.end();
                float offset = sp.align().safety() == AlignmentSafety.SAFE && marginBoxSize > imcb.end() - imcb.start()
                        ? alignInLine(imcb, marginBoxSize, startEdge())
                        : alignInLine(sp.area(), marginBoxSize, sp.align().keyword());
                return offset + margin.start();
            }
            // One auto inset: the margin box is aligned to the edge of the
            // stronger inset
            if (OptF32.isSome(insetStart) && OptF32.isNone(insetEnd)) {
                return imcb.start() + margin.start();
            }
            if (OptF32.isNone(insetStart)) {
                return imcb.end() - size - margin.end();
            }
            // Both insets set: auto margins have already absorbed all of the
            // free space
            if (OptF32.isNone(this.margin.start()) || OptF32.isNone(this.margin.end())) {
                return imcb.start() + margin.start();
            }

            float marginBoxSize = size + margin.start() + margin.end();
            float imcbSize = imcb.end() - imcb.start();
            AxisStaticEdge edge = alignment.physicalEdge(reversed);
            float marginBoxStart;
            switch (safety) {
                case UNSAFE -> marginBoxStart = alignInLine(imcb, marginBoxSize, edge);
                case SAFE -> {
                    AxisStaticEdge resolvedEdge = marginBoxSize > imcbSize ? startEdge() : edge;
                    marginBoxStart = alignInLine(imcb, marginBoxSize, resolvedEdge);
                }
                // Default overflow alignment for absolutely positioned boxes:
                // <https://www.w3.org/TR/css-align-3/#auto-safety-position>
                case DEFAULT -> {
                    if (marginBoxSize <= imcbSize || alignment == OofAlignment.NORMAL) {
                        // 1. Fits within the IMCB: align as specified
                        marginBoxStart = alignInLine(imcb, marginBoxSize, edge);
                    } else {
                        // The overflow limit rect is the bounding rect of the
                        // IMCB and the containing block, extended to infinity
                        // in the scrollable direction of a scroll container
                        float limitStart = Sys.f32Min(imcb.start(), 0.0f);
                        float limitEnd = Sys.f32Max(imcb.end(), cbSize);
                        if (isScrollAxis) {
                            if (reversed) {
                                limitStart = Float.NEGATIVE_INFINITY;
                            } else {
                                limitEnd = Float.POSITIVE_INFINITY;
                            }
                        }
                        if (marginBoxSize <= limitEnd - limitStart) {
                            // 2. Fits within the overflow limit rect: cover the
                            // IMCB and align as specified as far as possible
                            // without overflowing the limit
                            float start = alignInLine(imcb, marginBoxSize, edge);
                            marginBoxStart = Sys.f32Max(Sys.f32Min(start, limitEnd - marginBoxSize), limitStart);
                        } else {
                            // 3. Start-align within the overflow limit rect
                            marginBoxStart = alignInLine(new LineF32(limitStart, limitEnd), marginBoxSize, startEdge());
                        }
                    }
                }
                default -> throw new IllegalStateException("unreachable AlignmentSafety variant");
            }
            return marginBoxStart + margin.start();
        }
    }

    /// The result of laying out out-of-flow candidates against an explicit
    /// positioning area (`OofLayoutResult`).
    ///
    /// @param hoisted candidates claimed and laid out by this pass, in
    ///        document order
    /// @param unclaimed candidates not claimed by this pass, which must
    ///        continue bubbling
    /// @param scrollableOverflowRect scrollable overflow contributed by the
    ///        claimed candidates, relative to the positioning area's inner
    ///        top-left edge, in physical coordinates (RTL is not mirrored)
    public record OofLayoutResult(long[] hoisted, OofCandidates unclaimed, RectF32 scrollableOverflowRect) {}

    /// Run the out-of-flow positioning pass for `nodeId` after its layout
    /// algorithm has produced `output`: lay out the out-of-flow candidates in
    /// `output.oofCandidates` for which the node is the containing block,
    /// record them as the node's hoisted children, and replace
    /// `output.oofCandidates` with the unclaimed remainder (which bubble
    /// further up the tree). The scrollable overflow contributed by the
    /// claimed boxes is merged into `output.scrollableOverflowRect`.
    ///
    /// This should be called once per `RunMode::PerformLayout` container
    /// layout, after the layout algorithm (block/flexbox/grid) has run, and
    /// *inside* any layout caching wrapper (such as `compute_cached_layout`)
    /// so that cache hits do not re-run the pass. It is a no-op when
    /// `output.oofPositioningArea` is `None` (leaf and size-only outputs).
    ///
    /// Which candidates the node claims is determined by the node's style via
    /// {@link net.morphismmc.jaffy.style.CoreStyle#isContainingBlock}.
    /// `position: fixed` boxes unclaimed by every ancestor are claimed by the
    /// final root positioning pass in `compute_root_layout`.
    ///
    /// Returns the updated `LayoutOutput` (`&mut` receiver -> return,
    /// GAP-018). The input's `oofCandidates` list is drained by `take`.
    public static LayoutOutput computeOofLayout(LayoutContainingBlock tree, long nodeId, LayoutOutput output) {
        OofPositioningArea area = output.oofPositioningArea();
        if (area == null) {
            return output;
        }
        if (output.oofCandidates().isEmpty()) {
            tree.clearHoistedChildren(nodeId);
            return output;
        }

        Style style = tree.getCoreContainerStyle(nodeId);
        Direction direction = style.direction();
        ContainingBlockClaims claims = style.isContainingBlock();

        OofCandidates candidates = output.oofCandidates().take();
        PointBool reversed = output.scrollableOverflowRect().isReversed();
        OofLayoutResult result = computeOofLayoutForAreaWithOrigin(
                tree, nodeId, candidates.asSlice(), area, direction, claims, reversed);
        // Always record the hoisted child list (even when empty) so that lists
        // recorded by previous layout runs do not persist
        tree.clearHoistedChildren(nodeId);
        tree.addHoistedChildren(nodeId, result.hoisted());
        Point<Overflow> overflow = tree.getCoreContainerStyle(nodeId).overflow();
        net.morphismmc.jaffy.tree.layout.ScrollableOverflow scrollableOverflowRect =
                net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                        output.scrollableOverflowRect().rect().union(result.scrollableOverflowRect()),
                        area.size(),
                        reversed,
                        overflow.x().isScrollContainer() || overflow.y().isScrollContainer());
        return new LayoutOutput(
                output.size(),
                scrollableOverflowRect,
                output.baselines(),
                output.topMargin(),
                output.bottomMargin(),
                output.marginsCanCollapseThrough(),
                result.unclaimed(),
                area);
    }

    /// Lay out out-of-flow candidates against an explicit positioning area
    /// (`compute_oof_layout_for_area`).
    ///
    /// Unlike {@link #computeOofLayout}, this function does not update the
    /// geometry owner's hoisted children or merge overflow into a
    /// {@link LayoutOutput}. This is useful when the CSS containing block does
    /// not have its own layout node and another node owns the resulting
    /// geometry.
    ///
    /// `geometryOwner` supplies the layout tree operations, detailed layout
    /// information, and scroll-container state used by the positioning pass.
    /// `direction` and `claims` describe the actual CSS containing block.
    public static OofLayoutResult computeOofLayoutForArea(
            LayoutContainingBlock tree,
            long geometryOwner,
            List<OofCandidate> candidates,
            OofPositioningArea area,
            Direction direction,
            ContainingBlockClaims claims) {
        return computeOofLayoutForAreaWithOrigin(
                tree, geometryOwner, candidates, area, direction, claims, new PointBool(direction.isRtl(), false));
    }

    /// Run the positioning pass using the containing block's
    /// algorithm-specific physical origins
    /// (`compute_oof_layout_for_area_with_origin`).
    private static OofLayoutResult computeOofLayoutForAreaWithOrigin(
            LayoutContainingBlock tree,
            long geometryOwner,
            List<OofCandidate> candidates,
            OofPositioningArea area,
            Direction direction,
            ContainingBlockClaims claims,
            PointBool reversed) {
        Point<Overflow> overflow = tree.getCoreContainerStyle(geometryOwner).overflow();

        LongArrayList hoisted = new LongArrayList();
        OofCandidates unclaimed = OofCandidates.create();
        RectF32 scrollableOverflowRect = performOofLayout(
                tree,
                geometryOwner,
                candidates,
                area.size(),
                area.offset(),
                direction,
                claims,
                overflow,
                reversed,
                hoisted,
                unclaimed);

        scrollableOverflowRect = net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                        scrollableOverflowRect,
                        area.size(),
                        reversed,
                        overflow.x().isScrollContainer() || overflow.y().isScrollContainer())
                .rect();
        return new OofLayoutResult(hoisted.toLongArray(), unclaimed, scrollableOverflowRect);
    }

    /// The result of laying out a single out-of-flow box with
    /// {@link #layoutOofBox} (`OofBoxLayout`, `pub(crate)` -> package-scoped
    /// nested record).
    ///
    /// @param layout the box's final layout (its `location` is relative to the
    ///        containing block's border box)
    /// @param surfaced out-of-flow candidates surfaced from within the box's
    ///        subtree, with anchors relative to the box's own border box
    /// @param overflow the box's `overflow` style (`content_size`-gated in
    ///        Rust; unconditional here)
    /// @param contain the box's `contain` style (`content_size`-gated in Rust;
    ///        unconditional here)
    record OofBoxLayout(Layout layout, OofCandidates surfaced, Point<Overflow> overflow, Contain contain) {}

    /// Size and position a single out-of-flow box against its containing
    /// block, and store its layout (`layout_oof_box`).
    ///
    /// - `areaSize`/`areaOffset` describe the inset-resolution area of the
    ///   containing block (border box minus borders and scrollbar gutters),
    ///   relative to the containing block's border box.
    /// - `containerOverflow` is the `overflow` style of the containing block,
    ///   which determines whether overflowing boxes may be aligned into its
    ///   scrollable overflow area.
    /// - `gridOwner` is the node whose detailed layout info is consulted for
    ///   grid placement: if it is a grid container, the box is positioned
    ///   relative to the grid area determined by its grid-placement properties
    ///   rather than the passed area.
    /// - `beforeLayout` is called with the inputs of the final layout pass
    ///   just before it runs, which allows the caller to inspect the layout
    ///   cache for the box.
    public static OofBoxLayout layoutOofBox(
            LayoutContainingBlock tree,
            OofCandidate candidate,
            SizeF32 areaSize,
            PointF32 areaOffset,
            Direction direction,
            Point<Overflow> containerOverflow,
            @Opt long gridOwner,
            BiConsumer<LayoutContainingBlock, LayoutInput> beforeLayout) {
        Style childStyle = tree.getOofItemStyle(candidate.node());

        // If the current node is a grid container then the box is positioned
        // relative to the grid area determined by its grid-placement
        // properties (which falls back to the passed area for `auto`
        // placement)
        if (gridOwner != 0L && tree.getDetailedLayoutInfo(gridOwner) instanceof DetailedLayoutInfo.Grid grid) {
            RectF32 areaRect = new RectF32(
                    areaOffset.x(),
                    areaOffset.x() + areaSize.width(),
                    areaOffset.y(),
                    areaOffset.y() + areaSize.height());
            RectF32 gridArea = grid.gridInfo()
                    .resolveAbsoluteGridArea(childStyle.gridRow(), childStyle.gridColumn(), direction, areaRect);
            areaSize = new SizeF32(gridArea.right() - gridArea.left(), gridArea.bottom() - gridArea.top());
            areaOffset = new PointF32(gridArea.left(), gridArea.top());
        }
        float areaWidth = areaSize.width();
        float areaHeight = areaSize.height();

        float aspectRatio = childStyle.aspectRatio();
        RectOptF32 margin = childStyle.margin().mapO(m -> m.resolveToOption(areaWidth, tree::calc));
        RectF32 padding =
                MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(childStyle.padding(), areaWidth, tree::calc);
        RectF32 border =
                MaybeResolve.RectLengthPercentageExt.resolveOrZeroO(childStyle.border(), areaWidth, tree::calc);
        SizeF32 paddingBorderSum = padding.plus(border).sumAxes();
        SizeF32 boxSizingAdjustment = childStyle.boxSizing() == BoxSizing.CONTENT_BOX ? paddingBorderSum : SizeF32.ZERO;

        // Resolve inset
        float left = childStyle.inset().left().maybeResolveF(areaWidth, tree::calc);
        float right = childStyle.inset().right().maybeResolveF(areaWidth, tree::calc);
        float top = childStyle.inset().top().maybeResolveF(areaHeight, tree::calc);
        float bottom = childStyle.inset().bottom().maybeResolveF(areaHeight, tree::calc);

        // Compute known dimensions from min/max/inherent size styles
        var sizeStyle = childStyle.size();
        SizeOptF32 styleSize = MaybeResolve.SizeDimensionExt.maybeResolveF(sizeStyle, areaSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 minSize = MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveF(
                        childStyle.minSize(), areaSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment)
                .or(new SizeOptF32(paddingBorderSum.width(), paddingBorderSum.height()))
                .maybeMaxF(paddingBorderSum);
        SizeOptF32 maxSize = MaybeResolve.SizeLengthPercentageAutoExt.maybeResolveF(
                        childStyle.maxSize(), areaSize, tree::calc)
                .maybeApplyAspectRatio(aspectRatio)
                .maybeAddF(boxSizingAdjustment);
        SizeOptF32 knownDimensions = styleSize.maybeClampO(minSize, maxSize);

        boolean isReplaced = childStyle.isReplaced();
        boolean isTable = childStyle.isTable();
        Direction itemDirection = childStyle.direction();
        // Taffy only supports the `horizontal-tb` writing mode, so only the
        // inline axis can be reversed. `auto` (`None`) behaves as `normal` for
        // absolutely positioned boxes: it does not defer to the `*-items`
        // properties of the containing block.
        AlignItems justifySelf = (childStyle.justifySelf() != null ? childStyle.justifySelf() : AlignItems.NORMAL)
                .resolveSelfRelative(itemDirection, direction, true);
        AlignItems alignSelf = (childStyle.alignSelf() != null ? childStyle.alignSelf() : AlignItems.NORMAL)
                .resolveSelfRelative(itemDirection, direction, false);
        Point<Overflow> overflow = childStyle.overflow();
        float scrollbarWidth = childStyle.scrollbarWidth();
        Contain contain = childStyle.contain();

        // Static positions are relative to the containing block's border box:
        // make them relative to the inset-resolution area
        float areaOffsetX = areaOffset.x();
        float areaOffsetY = areaOffset.y();
        Point<AxisStaticPosition> staticPosition = new Point<>(
                new AxisStaticPosition(
                        candidate.staticPosition().x().area().map(v -> v - areaOffsetX),
                        candidate.staticPosition().x().align()),
                new AxisStaticPosition(
                        candidate.staticPosition().y().area().map(v -> v - areaOffsetY),
                        candidate.staticPosition().y().align()));

        OofAxis horizontal = new OofAxis(
                areaWidth,
                new LineOptF32(left, right),
                new LineOptF32(margin.left(), margin.right()),
                staticPosition.x(),
                OofAlignment.fromResolved(justifySelf),
                justifySelf.safety(),
                true,
                direction.isRtl(),
                containerOverflow.x().isScrollContainer());
        OofAxis vertical = new OofAxis(
                areaHeight,
                new LineOptF32(top, bottom),
                new LineOptF32(margin.top(), margin.bottom()),
                staticPosition.y(),
                OofAlignment.fromResolved(alignSelf),
                alignSelf.safety(),
                false,
                false,
                containerOverflow.y().isScrollContainer());
        SizeF32 stretchFitSize = new SizeF32(horizontal.stretchFitSize(), vertical.stretchFitSize());
        // The space available to the box's border box is its stretch-fit size
        // (the IMCB minus its non-auto margins). The available space for a
        // table never exceeds that of the containing block (minus the margins)
        // <https://www.w3.org/TR/css-tables-3/#abspos>
        SizeF32 availableSpaceSize = stretchFitSize;
        if (isTable) {
            RectF32 nonAutoMargin = margin.mapF(m -> OptF32.unwrapOr(m, 0.0f));
            availableSpaceSize = availableSpaceSize.f32Min(new SizeF32(
                    Sys.f32Max(areaWidth - nonAutoMargin.horizontalAxisSum(), 0.0f),
                    Sys.f32Max(areaHeight - nonAutoMargin.verticalAxisSum(), 0.0f)));
        }
        SizeF32 clampedAvailable = availableSpaceSize.maybeClampO(minSize, maxSize);
        Size<AvailableSpace> availableSpace = new Size<>(
                new AvailableSpace.Definite(clampedAvailable.width()),
                new AvailableSpace.Definite(clampedAvailable.height()));

        // Resolve any sizing keywords (min-content, max-content, fit-content,
        // fit-content(...), stretch) in the size styles. An explicitly sized
        // axis takes precedence over the automatic size below.
        if (sizeStyle.width().isSizingKeyword() || sizeStyle.height().isSizingKeyword()) {
            knownDimensions = SizingKeyword.resolveAbsoluteSizingKeywords(
                    tree,
                    candidate.node(),
                    knownDimensions,
                    sizeStyle,
                    areaSize,
                    new RectOptF32(left, right, top, bottom),
                    margin,
                    SizingMode.CONTENT_SIZE);
            knownDimensions = knownDimensions.maybeApplyAspectRatio(aspectRatio).maybeClampO(minSize, maxSize);
        }

        // Resolve automatic sizes
        // <https://www.w3.org/TR/css-position-3/#abspos-auto-size>
        //
        // An auto size is the stretch-fit size (the IMCB minus margins) if the
        // self-alignment in that axis is `stretch` (or `normal` for a
        // non-replaced, non-table box) and neither inset is auto. Otherwise it
        // is the fit-content size, which is measured below. Stretch-fit sizes
        // are resolved first so that a size in the other axis can be
        // transferred through the aspect ratio. The block axis is the
        // ratio-dependent axis, so a stretched inline size is transferred
        // through the aspect ratio rather than also stretching the block size.
        // An explicit `stretch` in both axes stretches both and ignores the
        // aspect ratio.
        boolean stretchWidth = OptF32.isNone(knownDimensions.width()) && horizontal.isStretchSized(isReplaced, isTable);
        boolean stretchHeight = OptF32.isNone(knownDimensions.height()) && vertical.isStretchSized(isReplaced, isTable);
        boolean bothExplicitStretch =
                horizontal.alignment() == OofAlignment.STRETCH && vertical.alignment() == OofAlignment.STRETCH;
        float stretchAspectRatio = stretchWidth && stretchHeight && bothExplicitStretch ? OptF32.NONE : aspectRatio;
        if (stretchWidth) {
            if (isTable) {
                // A table cannot be stretched smaller than its content:
                // stretch-fit is a minimum
                minSize = minSize.with(
                        AbstractAxis.INLINE,
                        Sys.f32Max(OptF32.unwrapOr(minSize.width(), 0.0f), stretchFitSize.width()));
            } else {
                knownDimensions = knownDimensions.with(AbstractAxis.INLINE, stretchFitSize.width());
                knownDimensions = knownDimensions
                        .maybeApplyAspectRatio(stretchAspectRatio)
                        .maybeClampO(minSize, maxSize);
            }
        }
        if (OptF32.isNone(knownDimensions.height()) && stretchHeight) {
            if (isTable) {
                minSize = minSize.with(
                        AbstractAxis.BLOCK,
                        Sys.f32Max(OptF32.unwrapOr(minSize.height(), 0.0f), stretchFitSize.height()));
            } else {
                knownDimensions = knownDimensions.with(AbstractAxis.BLOCK, stretchFitSize.height());
                knownDimensions = knownDimensions
                        .maybeApplyAspectRatio(stretchAspectRatio)
                        .maybeClampO(minSize, maxSize);
            }
        }

        SizeF32 finalSize;
        if (OptF32.isSome(knownDimensions.width()) && OptF32.isSome(knownDimensions.height())) {
            finalSize = new SizeF32(knownDimensions.width(), knownDimensions.height());
        } else {
            SizeF32 measuredSize = tree.measureChildSizeBoth(
                    candidate.node(),
                    knownDimensions,
                    new SizeOptF32(areaSize.width(), areaSize.height()),
                    availableSpace,
                    SizingMode.CONTENT_SIZE,
                    LineBool.FALSE);
            finalSize = knownDimensions.unwrapOr(measuredSize);
        }
        finalSize = finalSize.maybeClampO(minSize, maxSize);

        LayoutInput layoutInput = new LayoutInput(
                RunMode.PERFORM_LAYOUT,
                SizingMode.CONTENT_SIZE,
                RequestedAxis.BOTH,
                new SizeOptF32(finalSize.width(), finalSize.height()),
                SizeBool.TRUE,
                new SizeOptF32(areaSize.width(), areaSize.height()),
                availableSpace,
                LineBool.FALSE);
        beforeLayout.accept(tree, layoutInput);
        LayoutOutput layoutOutput = tree.computeChildLayout(candidate.node(), layoutInput);

        // Resolve auto margins <https://www.w3.org/TR/css-position-3/#abspos-margins>
        LineF32 horizontalMargin = horizontal.resolveMargins(finalSize.width());
        LineF32 verticalMargin = vertical.resolveMargins(finalSize.height());
        RectF32 resolvedMargin = new RectF32(
                horizontalMargin.start(), horizontalMargin.end(), verticalMargin.start(), verticalMargin.end());

        // Align the margin box within the IMCB
        // <https://www.w3.org/TR/css-position-3/#abspos-alignment>
        PointF32 location = new PointF32(
                horizontal.position(finalSize.width(), horizontalMargin) + areaOffset.x(),
                vertical.position(finalSize.height(), verticalMargin) + areaOffset.y());

        // Note: axis intentionally switched here as scrollbars take up space
        // in the opposite axis to the axis in which scrolling is enabled.
        SizeF32 scrollbarSize = new SizeF32(
                overflow.y() == Overflow.SCROLL ? scrollbarWidth : 0.0f,
                overflow.x() == Overflow.SCROLL ? scrollbarWidth : 0.0f);

        Layout layout = new Layout(
                candidate.order(),
                location,
                finalSize,
                layoutOutput.scrollableOverflowRect(),
                scrollbarSize,
                border,
                padding,
                resolvedMargin);
        tree.setUnroundedLayout(candidate.node(), layout);

        return new OofBoxLayout(layout, layoutOutput.oofCandidates().take(), overflow, contain);
    }

    /// Perform final layout on the out-of-flow candidates for which the
    /// current node is the containing block, and collect the remainder into
    /// `unclaimed` for further bubbling (`perform_oof_layout`).
    ///
    /// - `nodeId` is the current node. If its detailed layout info (as
    ///   returned by {@link LayoutContainingBlock#getDetailedLayoutInfo})
    ///   indicates that it is a grid container, each claimed box is positioned
    ///   relative to the grid area determined by its grid-placement properties
    ///   rather than the passed inset-resolution area.
    /// - `candidates` is the merged, document-ordered list of candidates held
    ///   by the current node (direct out-of-flow children plus candidates
    ///   bubbled from in-flow children). Anchors must be relative to the
    ///   current node's border box.
    /// - `areaSize`/`areaOffset` describe the inset-resolution area of the
    ///   current node (border box minus borders and scrollbar gutters).
    /// - `claims` describes which out-of-flow positions the current node acts
    ///   as a containing block for (see
    ///   {@link net.morphismmc.jaffy.style.CoreStyle#isContainingBlock}). The
    ///   final root positioning pass claims all.
    /// - `containerOverflow` is the `overflow` style of the containing block.
    ///
    /// Laying out a claimed box may surface further candidates from within its
    /// subtree (e.g. a `position: fixed` descendant of a `position: absolute`
    /// box). These are re-swept: claimed ones are appended to the work list,
    /// unclaimed ones are added to `unclaimed`.
    ///
    /// The ids of claimed boxes are appended to `hoisted` in order. Returns
    /// the scrollable overflow contribution of the claimed boxes.
    public static RectF32 performOofLayout(
            LayoutContainingBlock tree,
            long nodeId,
            List<OofCandidate> candidates,
            SizeF32 areaSize,
            PointF32 areaOffset,
            Direction direction,
            ContainingBlockClaims claims,
            Point<Overflow> containerOverflow,
            PointBool reversed,
            LongList hoisted,
            OofCandidates unclaimed) {
        RectF32 absoluteOverflowRect = RectF32.ZERO;
        if (direction.isRtl()) {
            absoluteOverflowRect =
                    absoluteOverflowRect.union(new RectF32(0.0f, Sys.f32Max(areaSize.width(), 0.0f), 0.0f, 0.0f));
        }

        if (candidates.isEmpty()) {
            return absoluteOverflowRect;
        }

        boolean isScrollContainer = containerOverflow.x().isScrollContainer()
                || containerOverflow.y().isScrollContainer();
        ScrollableOverflowContainer overflowContainer =
                new ScrollableOverflowContainer(isScrollContainer, areaSize, reversed, RectF32.ZERO);

        // Split the candidate list into an initial work list of claimed
        // candidates and the unclaimed remainder. Further claimed candidates
        // surfaced while laying out a claimed box are appended to the work
        // list (Blink-style re-sweep).
        List<OofCandidate> worklist = new ArrayList<>();
        for (OofCandidate candidate : candidates) {
            if (claims.forPosition(candidate.position())) {
                worklist.add(candidate);
            } else {
                unclaimed.push(candidate);
            }
        }

        int index = 0;
        while (index < worklist.size()) {
            OofCandidate candidate = worklist.get(index);
            index += 1;
            hoisted.add(candidate.node());

            OofBoxLayout result = layoutOofBox(
                    tree, candidate, areaSize, areaOffset, direction, containerOverflow, nodeId, (t, in) -> {});
            PointF32 location = result.layout().location();
            SizeF32 finalSize = result.layout().size();

            // Re-sweep any candidates surfaced from within the box's subtree,
            // translating their anchors so they are relative to this node's
            // border box
            OofCandidates surfaced = result.surfaced();
            if (!surfaced.isEmpty()) {
                surfaced.translate(location);
                for (OofCandidate surfacedCandidate : surfaced) {
                    if (claims.forPosition(surfacedCandidate.position())) {
                        worklist.add(surfacedCandidate);
                    } else {
                        unclaimed.push(surfacedCandidate);
                    }
                }
            }

            PointF32 relativeLocation = new PointF32(location.x() - areaOffset.x(), location.y() - areaOffset.y());
            absoluteOverflowRect = absoluteOverflowRect.union(
                    net.morphismmc.jaffy.compute.common.ScrollableOverflow.computeScrollableOverflowContribution(
                            relativeLocation,
                            finalSize,
                            result.layout().scrollableOverflowRect().rect(),
                            result.layout().border(),
                            result.overflow(),
                            result.contain(),
                            overflowContainer,
                            RectF32.ZERO));
        }

        return absoluteOverflowRect;
    }
}
