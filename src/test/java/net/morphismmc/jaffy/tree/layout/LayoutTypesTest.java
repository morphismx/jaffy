// JAFFY: upstream=src/tree/layout.rs
// JAFFY-NOTE: tests for  — the upstream `scrollable_overflow_tests`
// module is ported verbatim; the rest covers CollapsibleMarginSet, LayoutInput,
// Baselines, AxisStaticPosition, OofCandidates, LayoutOutput and Layout.
package net.morphismmc.jaffy.tree.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class LayoutTypesTest {

    // ---- upstream `side_accessors_match_restored_rectangle` ----

    @Test
    void sideAccessorsMatchRestoredRectangle() {
        RectF32[] rects = {
            RectF32.ZERO, new RectF32(-0.0f, 0.0f, -0.0f, 0.0f), new RectF32(-10.0f, 30.0f, -20.0f, 40.0f),
        };
        for (RectF32 rect : rects) {
            for (boolean x : new boolean[] {false, true}) {
                for (boolean y : new boolean[] {false, true}) {
                    ScrollableOverflow overflow = ScrollableOverflow.of(rect, new PointBool(x, y));
                    RectF32 restored = overflow.rect();
                    assertEquals(Float.floatToRawIntBits(overflow.left()), Float.floatToRawIntBits(restored.left()));
                    assertEquals(Float.floatToRawIntBits(overflow.right()), Float.floatToRawIntBits(restored.right()));
                    assertEquals(Float.floatToRawIntBits(overflow.top()), Float.floatToRawIntBits(restored.top()));
                    assertEquals(
                            Float.floatToRawIntBits(overflow.bottom()), Float.floatToRawIntBits(restored.bottom()));
                }
            }
        }
    }

    // ---- upstream `packing_preserves_flags_rectangles_and_signed_zeroes` ----

    @Test
    void packingPreservesFlagsRectanglesAndSignedZeroes() {
        for (float right : new float[] {0.0f, 10.0f}) {
            for (float bottom : new float[] {0.0f, 20.0f}) {
                for (boolean x : new boolean[] {false, true}) {
                    for (boolean y : new boolean[] {false, true}) {
                        RectF32 rect = new RectF32(-0.0f, right, -0.0f, bottom);
                        PointBool reversed = new PointBool(x, y);
                        ScrollableOverflow overflow = ScrollableOverflow.of(rect, reversed);
                        assertEquals(Float.floatToRawIntBits(overflow.left()), Float.floatToRawIntBits(-0.0f));
                        assertEquals(Float.floatToRawIntBits(overflow.top()), Float.floatToRawIntBits(-0.0f));
                        assertEquals(Float.floatToRawIntBits(overflow.right()), Float.floatToRawIntBits(right));
                        assertEquals(Float.floatToRawIntBits(overflow.bottom()), Float.floatToRawIntBits(bottom));
                        assertEquals(overflow.isReversed(), reversed);
                        assertEquals(overflow.transpose().isReversed(), new PointBool(y, x));
                        assertEquals(overflow.transpose().transpose(), overflow);
                    }
                }
            }
        }
        assertEquals(ScrollableOverflow.ZERO, ScrollableOverflow.of(RectF32.ZERO, PointBool.FALSE));
        assertNotEquals(ScrollableOverflow.ZERO, ScrollableOverflow.of(RectF32.ZERO, new PointBool(true, false)));
        assertNotEquals(
                ScrollableOverflow.ZERO, ScrollableOverflow.of(new RectF32(-0.0f, 0.0f, 0.0f, 0.0f), PointBool.FALSE));
        assertNotEquals(
                ScrollableOverflow.ZERO, ScrollableOverflow.of(new RectF32(0.0f, 0.0f, -0.0f, 0.0f), PointBool.FALSE));
    }

    // ---- CollapsibleMarginSet ----

    @Test
    void collapsibleMarginSetCollapseAndResolve() {
        assertEquals(new CollapsibleMarginSet(4.0f, 0.0f), CollapsibleMarginSet.fromMargin(4.0f));
        assertEquals(new CollapsibleMarginSet(0.0f, -4.0f), CollapsibleMarginSet.fromMargin(-4.0f));
        assertEquals(CollapsibleMarginSet.ZERO, CollapsibleMarginSet.fromMargin(0.0f));

        CollapsibleMarginSet set = CollapsibleMarginSet.fromMargin(10.0f);
        assertEquals(set, set.collapseWithMargin(5.0f));
        assertEquals(new CollapsibleMarginSet(12.0f, 0.0f), set.collapseWithMargin(12.0f));
        assertEquals(new CollapsibleMarginSet(10.0f, -3.0f), set.collapseWithMargin(-3.0f));

        CollapsibleMarginSet a = new CollapsibleMarginSet(10.0f, -4.0f);
        CollapsibleMarginSet b = new CollapsibleMarginSet(8.0f, -6.0f);
        assertEquals(new CollapsibleMarginSet(10.0f, -6.0f), a.collapseWithSet(b));
        assertEquals(6.0f, a.resolve());
        assertEquals(2.0f, b.resolve());
    }

    // ---- LayoutInput ----

    @Test
    void layoutInputHiddenMatchesUpstreamConstant() {
        LayoutInput hidden = LayoutInput.HIDDEN;
        assertEquals(RunMode.PERFORM_HIDDEN_LAYOUT, hidden.runMode());
        assertEquals(SizingMode.INHERENT_SIZE, hidden.sizingMode());
        assertEquals(RequestedAxis.BOTH, hidden.axis());
        assertEquals(SizeOptF32.NONE, hidden.knownDimensions());
        assertTrue(hidden.knownDimensionsAreDefinite().width());
        assertTrue(hidden.knownDimensionsAreDefinite().height());
        assertEquals(SizeOptF32.NONE, hidden.parentSize());
        assertTrue(
                hidden.availableSpace().width()
                        instanceof net.morphismmc.jaffy.style.available_space.AvailableSpace.MaxContent);
        assertEquals(LineBool.FALSE, hidden.verticalMarginsAreCollapsible());
    }

    @Test
    void layoutInputTransposeSwapsAxesAndResetsCollapsibleMargins() {
        LayoutInput input = new LayoutInput(
                RunMode.COMPUTE_SIZE,
                SizingMode.CONTENT_SIZE,
                RequestedAxis.HORIZONTAL,
                new SizeOptF32(10.0f, OptF32.NONE),
                new net.morphismmc.jaffy.geometry.SizeBool(true, false),
                new SizeOptF32(100.0f, 200.0f),
                net.morphismmc.jaffy.style.StyleHelpers.SizeAvailableSpaceExt.maxContent(),
                LineBool.TRUE);
        LayoutInput t = input.transpose();
        assertEquals(RequestedAxis.VERTICAL, t.axis());
        assertEquals(new SizeOptF32(OptF32.NONE, 10.0f), t.knownDimensions());
        assertEquals(new net.morphismmc.jaffy.geometry.SizeBool(false, true), t.knownDimensionsAreDefinite());
        assertEquals(new SizeOptF32(200.0f, 100.0f), t.parentSize());
        assertEquals(LineBool.FALSE, t.verticalMarginsAreCollapsible());
        // Both stays Both
        assertEquals(RequestedAxis.BOTH, LayoutInput.HIDDEN.transpose().axis());
    }

    // ---- Baselines ----

    @Test
    void baselinesNoneAndFromFirst() {
        assertTrue(OptF32.isNone(Baselines.NONE.first()));
        assertTrue(OptF32.isNone(Baselines.NONE.last()));
        Baselines b = Baselines.fromFirst(12.5f);
        assertEquals(12.5f, b.first());
        assertTrue(OptF32.isNone(b.last()));
    }

    // ---- AxisStaticPosition ----

    @Test
    void axisStaticPositionFromEdgeAndFromAlignment() {
        AxisStaticPosition pos = AxisStaticPosition.fromEdge(5.0f, AxisStaticEdge.END);
        assertEquals(new LineF32(5.0f, 5.0f), pos.area());
        assertEquals(AxisStaticEdge.END, pos.align().keyword());
        assertEquals(AlignmentSafety.DEFAULT, pos.align().safety());
        assertEquals(AxisStaticEdge.END, pos.align().fallback());

        LineF32 area = new LineF32(0.0f, 100.0f);
        // Normal/Stretch -> Start
        assertEquals(
                AxisStaticEdge.START,
                AxisStaticPosition.fromAlignment(AlignItems.NORMAL, area, false)
                        .align()
                        .keyword());
        assertEquals(
                AxisStaticEdge.START,
                AxisStaticPosition.fromAlignment(AlignItems.STRETCH, area, false)
                        .align()
                        .keyword());
        // Center -> Center (unaffected by rtl)
        assertEquals(
                AxisStaticEdge.CENTER,
                AxisStaticPosition.fromAlignment(AlignItems.CENTER, area, false)
                        .align()
                        .keyword());
        assertEquals(
                AxisStaticEdge.CENTER,
                AxisStaticPosition.fromAlignment(AlignItems.CENTER, area, true)
                        .align()
                        .keyword());
        // End/FlexEnd flip with rtl; Start also flips with rtl
        assertEquals(
                AxisStaticEdge.END,
                AxisStaticPosition.fromAlignment(AlignItems.END, area, false)
                        .align()
                        .keyword());
        assertEquals(
                AxisStaticEdge.START,
                AxisStaticPosition.fromAlignment(AlignItems.END, area, true)
                        .align()
                        .keyword());
        assertEquals(
                AxisStaticEdge.END,
                AxisStaticPosition.fromAlignment(AlignItems.FLEX_END, area, false)
                        .align()
                        .keyword());
        assertEquals(
                AxisStaticEdge.END,
                AxisStaticPosition.fromAlignment(AlignItems.START, area, true)
                        .align()
                        .keyword());
        // Safe alignment: keyword kept, fallback forced to Start's edge
        AxisStaticPosition safe = AxisStaticPosition.fromAlignment(AlignItems.SAFE_END, area, false);
        assertEquals(AxisStaticEdge.END, safe.align().keyword());
        assertEquals(AlignmentSafety.SAFE, safe.align().safety());
        assertEquals(AxisStaticEdge.START, safe.align().fallback());
        // Unresolved self-start/self-end -> IllegalStateException (GAP-010)
        assertThrows(
                IllegalStateException.class,
                () -> AxisStaticPosition.fromAlignment(AlignItems.SELF_START, area, false));
    }

    // ---- OofCandidates ----

    @Test
    void oofCandidatesLazyListSemantics() {
        OofCandidates c = OofCandidates.none();
        assertTrue(c.isEmpty());
        assertEquals(0, c.asSlice().size());

        OofCandidate cand = new OofCandidate(
                4294967297L,
                3,
                Position.ABSOLUTE,
                new Point<>(
                        AxisStaticPosition.fromEdge(1.0f, AxisStaticEdge.START),
                        AxisStaticPosition.fromEdge(2.0f, AxisStaticEdge.END)));
        c.push(cand);
        assertFalse(c.isEmpty());
        assertEquals(cand, c.asSlice().get(0));

        // append moves candidates and leaves `other` empty
        OofCandidates dest = OofCandidates.create();
        dest.append(c);
        assertTrue(c.isEmpty());
        assertEquals(1, dest.asSlice().size());

        // append into non-empty
        OofCandidates more = OofCandidates.create();
        more.push(cand);
        dest.append(more);
        assertEquals(2, dest.asSlice().size());

        // translate shifts static-position areas on both axes
        dest.translate(new PointF32(10.0f, 20.0f));
        OofCandidate moved = dest.asSlice().get(0);
        assertEquals(new LineF32(11.0f, 11.0f), moved.staticPosition().x().area());
        assertEquals(new LineF32(22.0f, 22.0f), moved.staticPosition().y().area());

        // take drains the list; clear empties it
        OofCandidates taken = dest.take();
        assertTrue(dest.isEmpty());
        assertEquals(2, taken.asSlice().size());
        taken.clear();
        assertTrue(taken.isEmpty());

        // iterable + copy
        c.push(cand);
        for (OofCandidate it : c) {
            assertEquals(cand, it);
        }
        OofCandidates clone = c.copy();
        assertEquals(c, clone);
        clone.push(cand);
        assertEquals(1, c.asSlice().size());
        assertEquals(2, clone.asSlice().size());
    }

    // ---- LayoutOutput ----

    @Test
    void layoutOutputConstructorsAndTranspose() {
        LayoutOutput hidden = LayoutOutput.hidden();
        assertEquals(SizeF32.ZERO, hidden.size());
        assertEquals(ScrollableOverflow.ZERO, hidden.scrollableOverflowRect());
        assertEquals(Baselines.NONE, hidden.baselines());
        assertEquals(CollapsibleMarginSet.ZERO, hidden.topMargin());
        assertEquals(CollapsibleMarginSet.ZERO, hidden.bottomMargin());
        assertFalse(hidden.marginsCanCollapseThrough());
        assertTrue(hidden.oofCandidates().isEmpty());
        assertEquals(null, hidden.oofPositioningArea());
        assertEquals(hidden, LayoutOutput.defaultOutput());

        LayoutOutput fromSizes = LayoutOutput.fromSizesAndBaselines(
                new SizeF32(10.0f, 20.0f), ScrollableOverflow.ZERO, Baselines.fromFirst(5.0f));
        assertEquals(new SizeF32(10.0f, 20.0f), fromSizes.size());
        assertEquals(5.0f, fromSizes.baselines().first());
        assertEquals(
                LayoutOutput.fromOuterSize(new SizeF32(10.0f, 20.0f)),
                LayoutOutput.fromSizes(new SizeF32(10.0f, 20.0f), ScrollableOverflow.ZERO));

        // transpose: size flips, baselines/margins/through-flag drop, candidates & area carry over
        LayoutOutput out = new LayoutOutput(
                new SizeF32(10.0f, 20.0f),
                ScrollableOverflow.of(new RectF32(-1.0f, 2.0f, -3.0f, 4.0f), new PointBool(true, false)),
                Baselines.fromFirst(5.0f),
                CollapsibleMarginSet.fromMargin(3.0f),
                CollapsibleMarginSet.fromMargin(-2.0f),
                true,
                OofCandidates.none(),
                new OofPositioningArea(new SizeF32(8.0f, 9.0f), new PointF32(1.0f, 2.0f)));
        LayoutOutput t = out.transpose();
        assertEquals(new SizeF32(20.0f, 10.0f), t.size());
        assertEquals(new PointBool(false, true), t.scrollableOverflowRect().isReversed());
        assertEquals(Baselines.NONE, t.baselines());
        assertEquals(CollapsibleMarginSet.ZERO, t.topMargin());
        assertEquals(CollapsibleMarginSet.ZERO, t.bottomMargin());
        assertFalse(t.marginsCanCollapseThrough());
        assertEquals(new SizeF32(9.0f, 8.0f), t.oofPositioningArea().size());
        assertEquals(new PointF32(2.0f, 1.0f), t.oofPositioningArea().offset());
    }

    // ---- Layout ----

    @Test
    void layoutContentBoxAndScrollRange() {
        assertEquals(Layout.DEFAULT, Layout.of());
        assertEquals(3, Layout.withOrder(3).order());

        Layout layout = new Layout(
                0,
                new PointF32(5.0f, 6.0f),
                new SizeF32(100.0f, 50.0f),
                ScrollableOverflow.ZERO,
                SizeF32.ZERO,
                new RectF32(1.0f, 2.0f, 3.0f, 4.0f),
                new RectF32(10.0f, 20.0f, 30.0f, 40.0f),
                RectF32.ZERO);
        assertEquals(100.0f - 10.0f - 20.0f - 1.0f - 2.0f, layout.contentBoxWidth());
        assertEquals(50.0f - 30.0f - 40.0f - 3.0f - 4.0f, layout.contentBoxHeight());
        assertEquals(new SizeF32(layout.contentBoxWidth(), layout.contentBoxHeight()), layout.contentBoxSize());
        assertEquals(5.0f + 1.0f + 10.0f, layout.contentBoxX());
        assertEquals(6.0f + 3.0f + 30.0f, layout.contentBoxY());

        // scrollWidth/scrollHeight on a normal (non-reversed) axis
        Layout scrolled = new Layout(
                0,
                PointF32.ZERO,
                new SizeF32(100.0f, 100.0f),
                ScrollableOverflow.of(new RectF32(-10.0f, 250.0f, 0.0f, 150.0f), PointBool.FALSE),
                new SizeF32(10.0f, 0.0f),
                new RectF32(1.0f, 2.0f, 0.0f, 0.0f),
                RectF32.ZERO,
                RectF32.ZERO);
        // right(250) + min(scrollbar_w=10, size_w=100)=10 - 100 + 1 + 2 = 163
        assertEquals(163.0f, scrolled.scrollWidth());
        // bottom(150) + min(0,100)=0 - 100 + 0 + 0 = 50
        assertEquals(50.0f, scrolled.scrollHeight());
        // reversed axis: -left()
        Layout reversed = new Layout(
                0,
                PointF32.ZERO,
                new SizeF32(100.0f, 100.0f),
                ScrollableOverflow.of(new RectF32(-60.0f, 0.0f, 0.0f, 0.0f), new PointBool(true, false)),
                SizeF32.ZERO,
                RectF32.ZERO,
                RectF32.ZERO,
                RectF32.ZERO);
        assertEquals(60.0f, reversed.scrollWidth());
        assertEquals(0.0f, reversed.scrollHeight());
    }
}
