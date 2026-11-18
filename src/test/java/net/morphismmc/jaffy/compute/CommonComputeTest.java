// JAFFY-UPSTREAM: none
// JAFFY-NOTE: these files carry no upstream `mod tests`; this locks the observable
// semantics of the ported helper functions (fallback order, packing, NaN-free
// fast paths, measure routing).
package net.morphismmc.jaffy.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.compute.common.Alignment;
import net.morphismmc.jaffy.compute.common.ScrollableOverflow.ScrollableOverflowContainer;
import net.morphismmc.jaffy.compute.common.SizingKeyword;
import net.morphismmc.jaffy.compute.common.SizingKeyword.SizingKeywordResolution;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class CommonComputeTest {

    // ---- alignment.rs ----

    @Test
    void childFirstBaselineClampsOnlyForScrollContainers() {
        assertEquals(4.0f, Alignment.childFirstBaseline(4.0f, 10.0f, Overflow.VISIBLE));
        assertTrue(OptF32.isNone(Alignment.childFirstBaseline(OptF32.NONE, 10.0f, Overflow.VISIBLE)));
        // Scroll container: missing baseline synthesizes from the border-box
        // bottom edge; reported baseline is clamped into [0, height].
        assertEquals(10.0f, Alignment.childFirstBaseline(OptF32.NONE, 10.0f, Overflow.HIDDEN));
        assertEquals(10.0f, Alignment.childFirstBaseline(14.0f, 10.0f, Overflow.SCROLL));
        assertEquals(0.0f, Alignment.childFirstBaseline(-3.0f, 10.0f, Overflow.HIDDEN));
        assertEquals(4.0f, Alignment.childFirstBaseline(4.0f, 10.0f, Overflow.HIDDEN));
    }

    @Test
    void selfAlignmentSafetyFallsBackToStartOnOverflow() {
        AlignItems safeEnd = new AlignItems(AlignItemsKeyword.END, AlignmentSafety.SAFE);
        AlignItems unsafeEnd = new AlignItems(AlignItemsKeyword.END, AlignmentSafety.UNSAFE);
        assertEquals(AlignItemsKeyword.START, Alignment.resolveSelfAlignmentSafety(safeEnd, true));
        assertEquals(AlignItemsKeyword.END, Alignment.resolveSelfAlignmentSafety(safeEnd, false));
        assertEquals(AlignItemsKeyword.END, Alignment.resolveSelfAlignmentSafety(unsafeEnd, true));
    }

    @Test
    void alignmentFallbackDistributionRules() {
        AlignContent between = new AlignContent(AlignContentKeyword.SPACE_BETWEEN, AlignmentSafety.DEFAULT);
        AlignContent stretch = new AlignContent(AlignContentKeyword.STRETCH, AlignmentSafety.DEFAULT);
        AlignContent safeCenter = new AlignContent(AlignContentKeyword.CENTER, AlignmentSafety.SAFE);
        // Multi-item with free space keeps the distributed keyword
        assertEquals(AlignContentKeyword.SPACE_BETWEEN, Alignment.applyAlignmentFallback(10.0f, 3, between));
        // Single item: space-between -> flex-start (+implicit safe, still no
        // overflow so stays flex-start)
        assertEquals(AlignContentKeyword.FLEX_START, Alignment.applyAlignmentFallback(10.0f, 1, between));
        // Single item + overflow: space-between -> safe flex-start -> start
        assertEquals(AlignContentKeyword.START, Alignment.applyAlignmentFallback(-1.0f, 1, between));
        // stretch -> flex-start when alone/overflowing (keeps unsafe flag)
        assertEquals(AlignContentKeyword.FLEX_START, Alignment.applyAlignmentFallback(-1.0f, 1, stretch));
        // safe center overflowing -> start
        assertEquals(AlignContentKeyword.START, Alignment.applyAlignmentFallback(-2.0f, 4, safeCenter));
        // non-overflowing safe center stays center
        assertEquals(AlignContentKeyword.CENTER, Alignment.applyAlignmentFallback(5.0f, 4, safeCenter));
    }

    @Test
    void alignmentOffsetMatrix() {
        // First item offsets
        assertEquals(0.0f, Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.START, false, true));
        assertEquals(100.0f, Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.END, false, true));
        // `end` is not flex-relative — reversal has no effect on it
        assertEquals(100.0f, Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.END, true, true));
        assertEquals(50.0f, Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.CENTER, false, true));
        assertEquals(
                25.0f,
                Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.SPACE_EVENLY, false, true));
        // flex-reversed: FLEX_START takes the free space
        assertEquals(
                100.0f, Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.FLEX_START, true, true));
        assertEquals(0.0f, Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.FLEX_END, true, true));
        // Subsequent items: gap + per-item share (free space clamped at 0)
        assertEquals(
                55.0f,
                Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.SPACE_BETWEEN, false, false));
        assertEquals(
                5.0f,
                Alignment.computeAlignmentOffset(-10.0f, 3, 5.0f, AlignContentKeyword.SPACE_BETWEEN, false, false));
        assertEquals(5.0f, Alignment.computeAlignmentOffset(100.0f, 3, 5.0f, AlignContentKeyword.END, false, false));
    }

    // ---- scrollable_overflow.rs ----

    private static ScrollableOverflowContainer container(
            boolean scroll, float w, float h, boolean rx, boolean ry, float pad) {
        return new ScrollableOverflowContainer(
                scroll, new SizeF32(w, h), new PointBool(rx, ry), new RectF32(pad, pad, pad, pad));
    }

    @Test
    void contributionOfBorderBoxWhenNotPropagating() {
        // Child is a scroll container => only its border box contributes
        var overflow = new Point<>(Overflow.HIDDEN, Overflow.VISIBLE);
        RectF32 r = net.morphismmc.jaffy.compute.common.ScrollableOverflow.computeScrollableOverflowContribution(
                new PointF32(5.0f, 5.0f),
                new SizeF32(10.0f, 10.0f),
                new RectF32(-4.0f, 20.0f, -4.0f, 20.0f), // propagated child overflow (clipped)
                RectF32.ZERO,
                overflow,
                net.morphismmc.jaffy.style.Contain.NONE,
                container(false, 100.0f, 100.0f, false, false, 0.0f),
                RectF32.ZERO);
        assertEquals(new RectF32(5.0f, 15.0f, 5.0f, 15.0f), r);
    }

    @Test
    void contributionPropagatesVisibleOverflow() {
        var overflow = new Point<>(Overflow.VISIBLE, Overflow.VISIBLE);
        RectF32 r = net.morphismmc.jaffy.compute.common.ScrollableOverflow.computeScrollableOverflowContribution(
                new PointF32(5.0f, 5.0f),
                new SizeF32(10.0f, 10.0f),
                new RectF32(-4.0f, 20.0f, -4.0f, 20.0f),
                RectF32.ZERO,
                overflow,
                net.morphismmc.jaffy.style.Contain.NONE,
                container(false, 100.0f, 100.0f, false, false, 0.0f),
                RectF32.ZERO);
        // contribution = location + (start_extent, end_extent)
        assertEquals(new RectF32(1.0f, 25.0f, 1.0f, 25.0f), r);
    }

    @Test
    void unreachableOverflowClippedOnlyForScrollContainerParent() {
        var overflow = new Point<>(Overflow.VISIBLE, Overflow.VISIBLE);
        // Child entirely before the scroll origin (right edge <= 0)
        RectF32 r = net.morphismmc.jaffy.compute.common.ScrollableOverflow.computeScrollableOverflowContribution(
                new PointF32(-30.0f, 5.0f),
                new SizeF32(10.0f, 10.0f),
                RectF32.ZERO,
                RectF32.ZERO,
                overflow,
                net.morphismmc.jaffy.style.Contain.NONE,
                container(true, 100.0f, 100.0f, false, false, 0.0f),
                RectF32.ZERO);
        assertEquals(RectF32.ZERO, r);
        // Same geometry under a non-scroll parent contributes normally
        r = net.morphismmc.jaffy.compute.common.ScrollableOverflow.computeScrollableOverflowContribution(
                new PointF32(-30.0f, 5.0f),
                new SizeF32(10.0f, 10.0f),
                RectF32.ZERO,
                RectF32.ZERO,
                overflow,
                net.morphismmc.jaffy.style.Contain.NONE,
                container(false, 100.0f, 100.0f, false, false, 0.0f),
                RectF32.ZERO);
        assertEquals(new RectF32(-30.0f, -20.0f, 5.0f, 15.0f), r);
    }

    @Test
    void finalizeClipsStartOverflowForScrollContainer() {
        var packed = net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                new RectF32(-20.0f, 40.0f, -30.0f, 50.0f),
                new SizeF32(30.0f, 30.0f),
                new PointBool(false, false),
                true);
        // Non-reversed scroll container: start overflow clipped to 0
        assertEquals(new RectF32(0.0f, 40.0f, 0.0f, 50.0f), packed.rect());
        assertEquals(new PointBool(false, false), packed.isReversed());
        // Reversed y scroll container: end overflow clipped to size
        packed = net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                new RectF32(-20.0f, 40.0f, -30.0f, 50.0f), new SizeF32(30.0f, 30.0f), new PointBool(false, true), true);
        assertEquals(new RectF32(0.0f, 40.0f, -30.0f, 30.0f), packed.rect());
        assertEquals(new PointBool(false, true), packed.isReversed());
        // Non-scroll container: no clipping, no reversal
        packed = net.morphismmc.jaffy.compute.common.ScrollableOverflow.finalizeScrollableOverflow(
                new RectF32(-20.0f, 40.0f, -30.0f, 50.0f), new SizeF32(30.0f, 30.0f), new PointBool(true, true), false);
        assertEquals(new RectF32(-20.0f, 40.0f, -30.0f, 50.0f), packed.rect());
        assertEquals(PointBool.FALSE, packed.isReversed());
    }

    // ---- sizing_keyword.rs ----

    private static SizingKeywordResolution resolve(
            Dimension style, @net.morphismmc.just.math.Opt float stretch, @net.morphismmc.just.math.Opt float basis) {
        return SizingKeyword.resolveSizingKeyword(style, stretch, basis, (v, b) -> b);
    }

    @Test
    void sizingKeywordTagResolution() {
        assertEquals(
                new SizingKeywordResolution.Measure(AvailableSpace.MIN_CONTENT),
                resolve(Dimension.minContent(), OptF32.NONE, OptF32.NONE));
        assertEquals(
                new SizingKeywordResolution.Measure(AvailableSpace.MAX_CONTENT),
                resolve(Dimension.maxContent(), OptF32.NONE, OptF32.NONE));
        assertEquals(
                new SizingKeywordResolution.Measure(new AvailableSpace.Definite(42.0f)),
                resolve(Dimension.fitContentPx(42.0f), OptF32.NONE, OptF32.NONE));
        // fit-content(50%) needs a percent basis
        assertNull(resolve(Dimension.fitContentPercent(0.5f), OptF32.NONE, OptF32.NONE));
        assertEquals(
                new SizingKeywordResolution.Measure(new AvailableSpace.Definite(50.0f)),
                resolve(Dimension.fitContentPercent(0.5f), OptF32.NONE, 100.0f));
        // fit-content keyword needs a stretch size
        assertNull(resolve(Dimension.fitContent(), OptF32.NONE, OptF32.NONE));
        assertEquals(
                new SizingKeywordResolution.Measure(new AvailableSpace.Definite(30.0f)),
                resolve(Dimension.fitContent(), 30.0f, OptF32.NONE));
        // stretch keyword -> Exact
        assertEquals(new SizingKeywordResolution.Exact(30.0f), resolve(Dimension.stretch(), 30.0f, OptF32.NONE));
        // plain length / auto are not sizing keywords
        assertNull(resolve(Dimension.length(10.0f), 30.0f, 100.0f));
        assertNull(resolve(Dimension.AUTO, 30.0f, 100.0f));
    }

    // ---- leaf.rs ----

    private static LayoutInput leafInput(
            SizeOptF32 known, Size<net.morphismmc.jaffy.style.available_space.AvailableSpace> space) {
        return new LayoutInput(
                RunMode.COMPUTE_SIZE,
                SizingMode.INHERENT_SIZE,
                net.morphismmc.jaffy.tree.layout.RequestedAxis.BOTH,
                known,
                net.morphismmc.jaffy.geometry.SizeBool.TRUE,
                SizeOptF32.NONE,
                space,
                LineBool.FALSE);
    }

    @Test
    void leafLayoutEarlyReturnWithKnownSize() {
        var out = net.morphismmc.jaffy.compute.Leaf.computeLeafLayout(
                leafInput(
                        new SizeOptF32(30.0f, 40.0f),
                        new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT)),
                Style.DEFAULT,
                (v, b) -> 0.0f,
                (kd, sp) -> SizeF32.ZERO);
        assertEquals(new SizeF32(30.0f, 40.0f), out.size());
    }

    @Test
    void leafLayoutMeasuresUnderAvailableSpace() {
        // No known size -> measure callback produces the content size
        var out = net.morphismmc.jaffy.compute.Leaf.computeLeafLayout(
                leafInput(SizeOptF32.NONE, new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT)),
                Style.DEFAULT,
                (v, b) -> 0.0f,
                (kd, sp) -> new SizeF32(50.0f, 20.0f));
        assertEquals(new SizeF32(50.0f, 20.0f), out.size());
        // Non-scroll leaf: overflow rect = content box (0, 50, 0, 20), no reversal
        assertEquals(
                net.morphismmc.jaffy.tree.layout.ScrollableOverflow.of(
                        new RectF32(0.0f, 50.0f, 0.0f, 20.0f), PointBool.FALSE),
                out.scrollableOverflowRect());
    }
}
