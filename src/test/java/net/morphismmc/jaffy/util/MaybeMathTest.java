// JAFFY: port of upstream src/util/math.rs `mod tests` — the three scalar
// modules map 1:1, plus coverage for the Size-level blanket impls and
// AvailableSpace impls that upstream leaves untested.
package net.morphismmc.jaffy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaybeMathTest {

    private static boolean optEq(@Opt float a, @Opt float b) {
        return OptF32.eq(a, b);
    }

    // ---- upstream: mod lhs_option_f32_rhs_option_f32 ----
    @Nested
    class LhsOptionF32RhsOptionF32 {

        @Test
        void testMaybeMin() {
            assertTrue(optEq(3.0f, OptF32.maybeMinO(3.0f, 5.0f)));
            assertTrue(optEq(3.0f, OptF32.maybeMinO(5.0f, 3.0f)));
            assertTrue(optEq(3.0f, OptF32.maybeMinO(3.0f, OptF32.NONE)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeMinO(OptF32.NONE, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeMinO(OptF32.NONE, OptF32.NONE)));
        }

        @Test
        void testMaybeMax() {
            assertTrue(optEq(5.0f, OptF32.maybeMaxO(3.0f, 5.0f)));
            assertTrue(optEq(5.0f, OptF32.maybeMaxO(5.0f, 3.0f)));
            assertTrue(optEq(3.0f, OptF32.maybeMaxO(3.0f, OptF32.NONE)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeMaxO(OptF32.NONE, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeMaxO(OptF32.NONE, OptF32.NONE)));
        }

        @Test
        void testMaybeAdd() {
            assertTrue(optEq(8.0f, OptF32.maybeAddO(3.0f, 5.0f)));
            assertTrue(optEq(8.0f, OptF32.maybeAddO(5.0f, 3.0f)));
            assertTrue(optEq(3.0f, OptF32.maybeAddO(3.0f, OptF32.NONE)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeAddO(OptF32.NONE, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeAddO(OptF32.NONE, OptF32.NONE)));
        }

        @Test
        void testMaybeSub() {
            assertTrue(optEq(-2.0f, OptF32.maybeSubO(3.0f, 5.0f)));
            assertTrue(optEq(2.0f, OptF32.maybeSubO(5.0f, 3.0f)));
            assertTrue(optEq(3.0f, OptF32.maybeSubO(3.0f, OptF32.NONE)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeSubO(OptF32.NONE, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeSubO(OptF32.NONE, OptF32.NONE)));
        }
    }

    // ---- upstream: mod lhs_option_f32_rhs_f32 ----
    @Nested
    class LhsOptionF32RhsF32 {

        @Test
        void testMaybeMin() {
            assertTrue(optEq(3.0f, OptF32.maybeMinF(3.0f, 5.0f)));
            assertTrue(optEq(3.0f, OptF32.maybeMinF(5.0f, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeMinF(OptF32.NONE, 3.0f)));
        }

        @Test
        void testMaybeMax() {
            assertTrue(optEq(5.0f, OptF32.maybeMaxF(3.0f, 5.0f)));
            assertTrue(optEq(5.0f, OptF32.maybeMaxF(5.0f, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeMaxF(OptF32.NONE, 3.0f)));
        }

        @Test
        void testMaybeAdd() {
            assertTrue(optEq(8.0f, OptF32.maybeAddF(3.0f, 5.0f)));
            assertTrue(optEq(8.0f, OptF32.maybeAddF(5.0f, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeAddF(OptF32.NONE, 3.0f)));
        }

        @Test
        void testMaybeSub() {
            assertTrue(optEq(-2.0f, OptF32.maybeSubF(3.0f, 5.0f)));
            assertTrue(optEq(2.0f, OptF32.maybeSubF(5.0f, 3.0f)));
            assertTrue(optEq(OptF32.NONE, OptF32.maybeSubF(OptF32.NONE, 3.0f)));
        }
    }

    // ---- upstream: mod lhs_f32_rhs_option_f32 ----
    @Nested
    class LhsF32RhsOptionF32 {

        @Test
        void testMaybeMin() {
            assertEquals(3.0f, F32.maybeMinO(3.0f, 5.0f));
            assertEquals(3.0f, F32.maybeMinO(5.0f, 3.0f));
            assertEquals(3.0f, F32.maybeMinO(3.0f, OptF32.NONE));
        }

        @Test
        void testMaybeMax() {
            assertEquals(5.0f, F32.maybeMaxO(3.0f, 5.0f));
            assertEquals(5.0f, F32.maybeMaxO(5.0f, 3.0f));
            assertEquals(3.0f, F32.maybeMaxO(3.0f, OptF32.NONE));
        }

        @Test
        void testMaybeAdd() {
            assertEquals(8.0f, F32.maybeAddO(3.0f, 5.0f));
            assertEquals(8.0f, F32.maybeAddO(5.0f, 3.0f));
            assertEquals(3.0f, F32.maybeAddO(3.0f, OptF32.NONE));
        }

        @Test
        void testMaybeSub() {
            assertEquals(-2.0f, F32.maybeSubO(3.0f, 5.0f));
            assertEquals(2.0f, F32.maybeSubO(5.0f, 3.0f));
            assertEquals(3.0f, F32.maybeSubO(3.0f, OptF32.NONE));
        }
    }

    // ---- jaffy: Size-level blanket impls (upstream untested) ----
    @Nested
    class SizeLevel {

        @Test
        void sizeOptF32RhsO() {
            SizeOptF32 a = new SizeOptF32(3.0f, OptF32.NONE);
            SizeOptF32 b = new SizeOptF32(5.0f, 2.0f);
            assertTrue(a.maybeMinO(b).rustEq(new SizeOptF32(3.0f, OptF32.NONE)));
            assertTrue(a.maybeMaxO(b).rustEq(new SizeOptF32(5.0f, OptF32.NONE)));
            assertTrue(a.maybeAddO(b).rustEq(new SizeOptF32(8.0f, OptF32.NONE)));
            assertTrue(a.maybeSubO(b).rustEq(new SizeOptF32(-2.0f, OptF32.NONE)));
            assertTrue(new SizeOptF32(3.0f, 9.0f)
                    .maybeClampO(new SizeOptF32(4.0f, OptF32.NONE), new SizeOptF32(8.0f, 6.0f))
                    .rustEq(new SizeOptF32(4.0f, 6.0f)));
        }

        @Test
        void sizeOptF32RhsF() {
            SizeOptF32 a = new SizeOptF32(3.0f, OptF32.NONE);
            SizeF32 b = new SizeF32(5.0f, 2.0f);
            assertTrue(a.maybeMinF(b).rustEq(new SizeOptF32(3.0f, OptF32.NONE)));
            assertTrue(a.maybeMaxF(b).rustEq(new SizeOptF32(5.0f, OptF32.NONE)));
            assertTrue(a.maybeAddF(b).rustEq(new SizeOptF32(8.0f, OptF32.NONE)));
            assertTrue(a.maybeSubF(b).rustEq(new SizeOptF32(-2.0f, OptF32.NONE)));
        }

        @Test
        void sizeF32RhsO() {
            SizeF32 a = new SizeF32(3.0f, 7.0f);
            assertTrue(a.maybeMinO(new SizeOptF32(5.0f, OptF32.NONE)).rustEq(new SizeF32(3.0f, 7.0f)));
            assertTrue(a.maybeMaxO(new SizeOptF32(5.0f, OptF32.NONE)).rustEq(new SizeF32(5.0f, 7.0f)));
            assertTrue(a.maybeAddO(new SizeOptF32(5.0f, OptF32.NONE)).rustEq(new SizeF32(8.0f, 7.0f)));
            assertTrue(a.maybeSubO(new SizeOptF32(5.0f, OptF32.NONE)).rustEq(new SizeF32(-2.0f, 7.0f)));
        }
    }

    // ---- jaffy: AvailableSpace impls (upstream untested) ----
    @Nested
    class AvailableSpaceLevel {

        @Test
        void rhsF32() {
            AvailableSpace d = new AvailableSpace.Definite(3.0f);
            assertTrue(d.maybeMinF(5.0f).rustEq(new AvailableSpace.Definite(3.0f)));
            assertTrue(d.maybeMaxF(5.0f).rustEq(new AvailableSpace.Definite(5.0f)));
            assertTrue(d.maybeAddF(5.0f).rustEq(new AvailableSpace.Definite(8.0f)));
            assertTrue(d.maybeSubF(5.0f).rustEq(new AvailableSpace.Definite(-2.0f)));
            assertTrue(d.maybeClampF(4.0f, 8.0f).rustEq(new AvailableSpace.Definite(4.0f)));
            // Min/MaxContent on maybe_min become Definite(rhs); on max/clamp/add/sub stay
            assertTrue(AvailableSpace.MIN_CONTENT.maybeMinF(5.0f).rustEq(new AvailableSpace.Definite(5.0f)));
            assertTrue(AvailableSpace.MAX_CONTENT.maybeMinF(5.0f).rustEq(new AvailableSpace.Definite(5.0f)));
            assertTrue(AvailableSpace.MIN_CONTENT.maybeMaxF(5.0f).rustEq(AvailableSpace.MIN_CONTENT));
            assertTrue(AvailableSpace.MAX_CONTENT.maybeClampF(1.0f, 2.0f).rustEq(AvailableSpace.MAX_CONTENT));
            assertTrue(AvailableSpace.MIN_CONTENT.maybeAddF(5.0f).rustEq(AvailableSpace.MIN_CONTENT));
        }

        @Test
        void rhsOptionF32() {
            AvailableSpace d = new AvailableSpace.Definite(3.0f);
            assertTrue(d.maybeMinO(5.0f).rustEq(new AvailableSpace.Definite(3.0f)));
            assertTrue(d.maybeMinO(OptF32.NONE).rustEq(d));
            assertTrue(d.maybeMaxO(5.0f).rustEq(new AvailableSpace.Definite(5.0f)));
            assertTrue(AvailableSpace.MIN_CONTENT.maybeMinO(5.0f).rustEq(new AvailableSpace.Definite(5.0f)));
            assertTrue(AvailableSpace.MIN_CONTENT.maybeMinO(OptF32.NONE).rustEq(AvailableSpace.MIN_CONTENT));
            assertTrue(AvailableSpace.MAX_CONTENT.maybeMaxO(5.0f).rustEq(AvailableSpace.MAX_CONTENT));
            assertTrue(d.maybeClampO(OptF32.NONE, 8.0f).rustEq(new AvailableSpace.Definite(3.0f)));
            assertTrue(d.maybeClampO(4.0f, OptF32.NONE).rustEq(new AvailableSpace.Definite(4.0f)));
            assertTrue(d.maybeAddO(OptF32.NONE).rustEq(d));
        }

        @Test
        void sizeLevel() {
            Size<AvailableSpace> s = new Size<>(new AvailableSpace.Definite(3.0f), AvailableSpace.MIN_CONTENT);
            Size<AvailableSpace> min = MaybeMath.maybeMinF(s, new SizeF32(5.0f, 4.0f));
            assertTrue(min.width().rustEq(new AvailableSpace.Definite(3.0f)));
            assertTrue(min.height().rustEq(new AvailableSpace.Definite(4.0f)));

            Size<AvailableSpace> max = MaybeMath.maybeMaxF(s, new SizeF32(5.0f, 4.0f));
            assertTrue(max.width().rustEq(new AvailableSpace.Definite(5.0f)));
            assertTrue(max.height().rustEq(AvailableSpace.MIN_CONTENT));

            Size<AvailableSpace> sub = MaybeMath.maybeSubO(s, new SizeOptF32(1.0f, 2.0f));
            assertTrue(sub.width().rustEq(new AvailableSpace.Definite(2.0f)));
            assertTrue(sub.height().rustEq(AvailableSpace.MIN_CONTENT));
        }
    }
}
