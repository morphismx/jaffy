// JAFFY: behavior checks for float.rs ports (FloatContext, ContentSlot, BfcSlot,
// PlacedFloatedBox, FloatIntrinsicWidthCalculator). Upstream has no inline unit
// tests; floats.rs hand-written cases require the L8 TaffyTree layer.
package net.morphismmc.jaffy.compute.float_;

import static org.junit.jupiter.api.Assertions.*;

import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.just.math.OptI32;
import org.junit.jupiter.api.Test;

class FloatContextTest {

    private static FloatContext context(float width) {
        FloatContext ctx = new FloatContext();
        ctx.setWidth(width);
        return ctx;
    }

    @Test
    void emptyContextHasNoFloats() {
        FloatContext ctx = context(100.0f);
        assertFalse(ctx.hasFloats());
        assertFalse(ctx.hasActiveFloats(0.0f));

        // No floats: the content slot is the full width at min_y
        ContentSlot slot = ctx.findContentSlot(5.0f, 10.0f, FloatInsets.ZERO, Clear.NONE, OptI32.NONE);
        assertEquals(OptI32.NONE, slot.segmentId());
        assertEquals(0.0f, slot.x());
        assertEquals(5.0f, slot.y());
        assertEquals(100.0f, slot.width());
        assertEquals(Float.POSITIVE_INFINITY, slot.height());
    }

    @Test
    void leftFloatOccupiesTopLeftCorner() {
        FloatContext ctx = context(100.0f);
        PointF32 pos = ctx.placeFloatedBox(1, new SizeF32(30.0f, 20.0f), 0.0f,
                FloatInsets.ZERO, FloatDirection.LEFT, Clear.NONE);
        assertEquals(0.0f, pos.x());
        assertEquals(0.0f, pos.y());
        assertTrue(ctx.hasFloats());
        assertTrue(ctx.hasActiveFloats(0.0f));
        assertFalse(ctx.hasActiveFloats(20.0f)); // ends exactly at min_y
        assertEquals(1, ctx.leftFloats().size());
        assertEquals(0, ctx.rightFloats().size());
    }

    @Test
    void contentSlotMovesBesideFloat() {
        FloatContext ctx = context(100.0f);
        ctx.placeFloatedBox(1, new SizeF32(30.0f, 20.0f), 0.0f, FloatInsets.ZERO,
                FloatDirection.LEFT, Clear.NONE);

        // Beside the float: narrowed by its inset
        ContentSlot beside = ctx.findContentSlot(0.0f, 10.0f, FloatInsets.ZERO, Clear.NONE, OptI32.NONE);
        assertEquals(30.0f, beside.x());
        assertEquals(0.0f, beside.y());
        assertEquals(70.0f, beside.width());

        // Below the float: full width again
        ContentSlot below = ctx.findContentSlot(25.0f, 10.0f, FloatInsets.ZERO, Clear.NONE, OptI32.NONE);
        assertEquals(OptI32.NONE, below.segmentId());
        assertEquals(0.0f, below.x());
        assertEquals(25.0f, below.y());
        assertEquals(100.0f, below.width());
    }

    @Test
    void sameSideFloatsStackThenWrap() {
        FloatContext ctx = context(100.0f);
        // Two 30-wide floats fit side by side
        assertEquals(new PointF32(0.0f, 0.0f), ctx.placeFloatedBox(1,
                new SizeF32(30.0f, 20.0f), 0.0f, FloatInsets.ZERO, FloatDirection.LEFT, Clear.NONE));
        assertEquals(new PointF32(30.0f, 0.0f), ctx.placeFloatedBox(2,
                new SizeF32(30.0f, 20.0f), 0.0f, FloatInsets.ZERO, FloatDirection.LEFT, Clear.NONE));
        // 50-wide float does not fit beside them (60 + 50 > 100) -> wraps below (y=20)
        assertEquals(new PointF32(0.0f, 20.0f), ctx.placeFloatedBox(3,
                new SizeF32(50.0f, 20.0f), 0.0f, FloatInsets.ZERO, FloatDirection.LEFT, Clear.NONE));
        assertEquals(3, ctx.leftFloats().size());
    }

    @Test
    void rightFloatIsPlacedFromRightEdge() {
        FloatContext ctx = context(100.0f);
        PointF32 pos = ctx.placeFloatedBox(1, new SizeF32(30.0f, 10.0f), 0.0f,
                FloatInsets.ZERO, FloatDirection.RIGHT, Clear.NONE);
        assertEquals(70.0f, pos.x()); // 100 - 0 inset - 30 width
        assertEquals(0.0f, pos.y());
        assertEquals(1, ctx.rightFloats().size());
    }

    @Test
    void clearPushesFloatBelow() {
        FloatContext ctx = context(100.0f);
        ctx.placeFloatedBox(1, new SizeF32(30.0f, 20.0f), 0.0f, FloatInsets.ZERO,
                FloatDirection.LEFT, Clear.NONE);
        // A left float that clears left goes below the previous left float
        // even though it would fit beside it horizontally
        PointF32 pos = ctx.placeFloatedBox(2, new SizeF32(30.0f, 10.0f), 0.0f,
                FloatInsets.ZERO, FloatDirection.LEFT, Clear.LEFT);
        assertEquals(0.0f, pos.x());
        assertEquals(20.0f, pos.y());
        // clear_bottoms tracks the max bottom over ALL floats on the side:
        // placing B at y=20 h=10 raises the left threshold to 30
        assertEquals(30.0f, ctx.clearedThreshold(Clear.LEFT));
        assertEquals(OptF32.NONE, ctx.clearedThreshold(Clear.RIGHT));
        assertEquals(30.0f, ctx.clearedThreshold(Clear.BOTH));
        assertEquals(OptF32.NONE, ctx.clearedThreshold(Clear.NONE));
    }

    @Test
    void laterFloatCannotBeHigherThanEarlier() {
        FloatContext ctx = context(100.0f);
        // CSS2 rule 5: even with a large negative min_y a new float may not be
        // placed above the top of an earlier float
        ctx.placeFloatedBox(1, new SizeF32(30.0f, 10.0f), 50.0f, FloatInsets.ZERO,
                FloatDirection.LEFT, Clear.NONE);
        PointF32 pos = ctx.placeFloatedBox(2, new SizeF32(30.0f, 10.0f), 0.0f,
                FloatInsets.ZERO, FloatDirection.LEFT, Clear.NONE);
        assertTrue(pos.y() >= 50.0f);
    }

    @Test
    void bfcSlotAvoidsFloatOnLeadSide() {
        FloatContext ctx = context(100.0f);
        ctx.placeFloatedBox(1, new SizeF32(30.0f, 20.0f), 0.0f, FloatInsets.ZERO,
                FloatDirection.LEFT, Clear.NONE);

        // Margins resolve against the containing block (not the float): the lead
        // margin is absorbed by the float edge which is further in
        FloatInsets margins = new FloatInsets(5.0f, 0.0f);
        BfcSlot slot = ctx.findBfcSlot(0.0f, 10.0f, FloatInsets.ZERO, margins,
                Direction.LTR, Clear.NONE, OptI32.NONE);
        assertEquals(30.0f, slot.x());
        assertEquals(0.0f, slot.y());
        assertEquals(70.0f, slot.borderWidth());
        assertEquals(70.0f, slot.stretchWidth());

        // No floats beside: margins apply as usual
        BfcSlot below = ctx.findBfcSlot(25.0f, 10.0f, FloatInsets.ZERO, margins,
                Direction.LTR, Clear.NONE, OptI32.NONE);
        assertEquals(OptI32.NONE, below.segmentId());
        assertEquals(5.0f, below.x());
        assertEquals(95.0f, below.borderWidth());
    }

    @Test
    void rtlUsesRightAsLeadSide() {
        FloatContext ctx = context(100.0f);
        ctx.placeFloatedBox(1, new SizeF32(30.0f, 20.0f), 0.0f, FloatInsets.ZERO,
                FloatDirection.RIGHT, Clear.NONE);
        BfcSlot slot = ctx.findBfcSlot(0.0f, 10.0f, FloatInsets.ZERO, FloatInsets.ZERO,
                Direction.RTL, Clear.NONE, OptI32.NONE);
        // Right float intrudes on the lead side in RTL
        assertEquals(0.0f, slot.x());
        assertEquals(70.0f, slot.borderWidth());
    }

    @Test
    void copyIsolatesSegments() {
        FloatContext ctx = context(100.0f);
        ctx.placeFloatedBox(1, new SizeF32(30.0f, 20.0f), 0.0f, FloatInsets.ZERO,
                FloatDirection.LEFT, Clear.NONE);
        FloatContext copy = ctx.copy();
        copy.placeFloatedBox(2, new SizeF32(10.0f, 10.0f), 0.0f, FloatInsets.ZERO,
                FloatDirection.LEFT, Clear.NONE);
        // The original still sees only the first float's slot layout
        ContentSlot slot = ctx.findContentSlot(0.0f, 10.0f, FloatInsets.ZERO, Clear.NONE, OptI32.NONE);
        assertEquals(30.0f, slot.x());
        assertEquals(1, ctx.leftFloats().size());
        assertEquals(2, copy.leftFloats().size());
    }

    @Test
    void intrinsicWidthAccumulatesAndClamps() {
        // Max-content: adjacent uncleared widths sum
        FloatIntrinsicWidthCalculator max = new FloatIntrinsicWidthCalculator(new AvailableSpace.MaxContent());
        max.addFloat(30.0f, FloatDirection.LEFT, Clear.NONE);
        max.addFloat(30.0f, FloatDirection.LEFT, Clear.NONE);
        max.addFloat(20.0f, FloatDirection.RIGHT, Clear.BOTH); // clears reset both sums
        assertEquals(60.0f, max.result()); // contribution unclamped

        // Min-content: only the widest float counts
        FloatIntrinsicWidthCalculator min = new FloatIntrinsicWidthCalculator(new AvailableSpace.MinContent());
        min.addFloat(30.0f, FloatDirection.LEFT, Clear.NONE);
        min.addFloat(20.0f, FloatDirection.LEFT, Clear.NONE);
        assertEquals(30.0f, min.result());

        // Definite (fit-content): clamped by the available width but never below widest
        FloatIntrinsicWidthCalculator fit = new FloatIntrinsicWidthCalculator(new AvailableSpace.Definite(50.0f));
        fit.addFloat(30.0f, FloatDirection.LEFT, Clear.NONE);
        fit.addFloat(30.0f, FloatDirection.LEFT, Clear.NONE);
        assertEquals(50.0f, fit.result()); // 60 clamped to 50, above widest 30

        FloatIntrinsicWidthCalculator fitWide = new FloatIntrinsicWidthCalculator(new AvailableSpace.Definite(50.0f));
        fitWide.addFloat(80.0f, FloatDirection.LEFT, Clear.NONE);
        assertEquals(80.0f, fitWide.result()); // widest wins over the clamp
    }
}
