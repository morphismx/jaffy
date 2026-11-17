// JAFFY: port of upstream src/compute/oof.rs `mod tests` — the 7
// `resolve_static_offset` tests, verbatim.
package net.morphismmc.jaffy.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.jaffy.tree.layout.AxisStaticAlign;
import net.morphismmc.jaffy.tree.layout.AxisStaticEdge;
import net.morphismmc.jaffy.tree.layout.AxisStaticPosition;

import org.junit.jupiter.api.Test;

class OofTest {

    private static AxisStaticPosition sp(
            LineF32 area, AxisStaticEdge keyword, AlignmentSafety safety, AxisStaticEdge fallback) {
        return new AxisStaticPosition(area, new AxisStaticAlign(keyword, safety, fallback));
    }

    private static float resolveX(AxisStaticPosition spX, float width, RectF32 margin) {
        Point<AxisStaticPosition> position = new Point<>(spX, AxisStaticPosition.fromEdge(0.0f, AxisStaticEdge.START));
        return Oof.resolveStaticOffset(position, new SizeF32(width, 10.0f), margin)
                .x();
    }

    private static final LineF32 AREA = new LineF32(10.0f, 110.0f);
    private static final RectF32 NO_MARGIN = new RectF32(0.0f, 0.0f, 0.0f, 0.0f);
    private static final RectF32 MARGIN = new RectF32(5.0f, 15.0f, 0.0f, 0.0f);

    @Test
    void startAlignment() {
        AxisStaticPosition spX = sp(AREA, AxisStaticEdge.START, AlignmentSafety.UNSAFE, AxisStaticEdge.START);
        assertEquals(10.0f, resolveX(spX, 20.0f, NO_MARGIN));
        assertEquals(15.0f, resolveX(spX, 20.0f, MARGIN));
    }

    @Test
    void endAlignment() {
        AxisStaticPosition spX = sp(AREA, AxisStaticEdge.END, AlignmentSafety.UNSAFE, AxisStaticEdge.END);
        assertEquals(90.0f, resolveX(spX, 20.0f, NO_MARGIN));
        assertEquals(75.0f, resolveX(spX, 20.0f, MARGIN));
    }

    @Test
    void centerAlignment() {
        AxisStaticPosition spX = sp(AREA, AxisStaticEdge.CENTER, AlignmentSafety.UNSAFE, AxisStaticEdge.CENTER);
        assertEquals(50.0f, resolveX(spX, 20.0f, NO_MARGIN));
        // Center offsets by half the margin difference
        assertEquals(45.0f, resolveX(spX, 20.0f, MARGIN));
    }

    @Test
    void safeAlignmentFallsBackOnlyOnOverflow() {
        AxisStaticPosition spX = sp(AREA, AxisStaticEdge.END, AlignmentSafety.SAFE, AxisStaticEdge.START);
        // Fits: aligned to the end
        assertEquals(90.0f, resolveX(spX, 20.0f, NO_MARGIN));
        // Margin box exactly fills the area: no fallback
        assertEquals(10.0f, resolveX(spX, 100.0f, NO_MARGIN));
        // Margin box overflows the area: falls back to start
        assertEquals(10.0f, resolveX(spX, 120.0f, NO_MARGIN));
        assertEquals(15.0f, resolveX(spX, 90.0f, MARGIN));
    }

    @Test
    void unsafeAlignmentNeverFallsBack() {
        AxisStaticPosition spX = sp(AREA, AxisStaticEdge.END, AlignmentSafety.UNSAFE, AxisStaticEdge.START);
        assertEquals(-10.0f, resolveX(spX, 120.0f, NO_MARGIN));
    }

    @Test
    void degenerateArea() {
        // A zero-extent area (static positions emitted by block layout)
        // behaves as an anchor point
        AxisStaticPosition anchor = AxisStaticPosition.fromEdge(40.0f, AxisStaticEdge.START);
        assertEquals(40.0f, resolveX(anchor, 20.0f, NO_MARGIN));
        AxisStaticPosition anchorEnd = AxisStaticPosition.fromEdge(40.0f, AxisStaticEdge.END);
        assertEquals(20.0f, resolveX(anchorEnd, 20.0f, NO_MARGIN));
    }
}
