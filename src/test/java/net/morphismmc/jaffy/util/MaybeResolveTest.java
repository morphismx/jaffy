// JAFFY: port of upstream src/util/resolve.rs `mod tests`.
// The `|_, _| 42.42` calc callback maps to a CalcResolver constant.
package net.morphismmc.jaffy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.dimension.RectDimensionExt;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaybeResolveTest {

    private static final CalcResolver CALC = (calc, ctx) -> 42.42f;

    private static boolean optEq(@Opt float a, @Opt float b) {
        return OptF32.eq(a, b);
    }

    // ---- upstream: mod maybe_resolve_dimension ----
    @Nested
    class MaybeResolveDimension {

        @Test
        void resolveAuto() {
            assertTrue(optEq(OptF32.NONE, Dimension.AUTO.maybeResolveO(OptF32.NONE, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.AUTO.maybeResolveO(5.0f, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.AUTO.maybeResolveO(-5.0f, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.AUTO.maybeResolveO(0.0f, CALC)));
        }

        @Test
        void resolveLength() {
            Dimension l = Dimension.fromLength(1.0);
            assertTrue(optEq(1.0f, l.maybeResolveO(OptF32.NONE, CALC)));
            assertTrue(optEq(1.0f, l.maybeResolveO(5.0f, CALC)));
            assertTrue(optEq(1.0f, l.maybeResolveO(-5.0f, CALC)));
            assertTrue(optEq(1.0f, l.maybeResolveO(0.0f, CALC)));
        }

        @Test
        void resolvePercent() {
            Dimension p = Dimension.fromPercent(1.0);
            assertTrue(optEq(OptF32.NONE, p.maybeResolveO(OptF32.NONE, CALC)));
            assertTrue(optEq(5.0f, p.maybeResolveO(5.0f, CALC)));
            assertTrue(optEq(-5.0f, p.maybeResolveO(-5.0f, CALC)));
            assertTrue(optEq(50.0f, p.maybeResolveO(50.0f, CALC)));
        }
    }

    // ---- upstream: mod maybe_resolve_size_dimension ----
    @Nested
    class MaybeResolveSizeDimension {

        private Size<Dimension> auto() {
            return new Size<>(Dimension.AUTO, Dimension.AUTO);
        }

        @Test
        void maybeResolveAuto() {
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(auto(), SizeOptF32.NONE, CALC)
                    .rustEq(SizeOptF32.NONE));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(auto(), new SizeOptF32(5.0f, 5.0f), CALC)
                    .rustEq(SizeOptF32.NONE));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(auto(), new SizeOptF32(-5.0f, -5.0f), CALC)
                    .rustEq(SizeOptF32.NONE));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(auto(), new SizeOptF32(0.0f, 0.0f), CALC)
                    .rustEq(SizeOptF32.NONE));
        }

        @Test
        void maybeResolveLength() {
            Size<Dimension> s = net.morphismmc.jaffy.geometry.SizeDimensionExt.fromLengths(5.0f, 5.0f);
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, SizeOptF32.NONE, CALC)
                    .rustEq(new SizeOptF32(5.0f, 5.0f)));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, new SizeOptF32(5.0f, 5.0f), CALC)
                    .rustEq(new SizeOptF32(5.0f, 5.0f)));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, new SizeOptF32(-5.0f, -5.0f), CALC)
                    .rustEq(new SizeOptF32(5.0f, 5.0f)));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, new SizeOptF32(0.0f, 0.0f), CALC)
                    .rustEq(new SizeOptF32(5.0f, 5.0f)));
        }

        @Test
        void maybeResolvePercent() {
            Size<Dimension> s = net.morphismmc.jaffy.geometry.SizeDimensionExt.fromPercent(5.0f, 5.0f);
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, SizeOptF32.NONE, CALC)
                    .rustEq(SizeOptF32.NONE));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, new SizeOptF32(5.0f, 5.0f), CALC)
                    .rustEq(new SizeOptF32(25.0f, 25.0f)));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, new SizeOptF32(-5.0f, -5.0f), CALC)
                    .rustEq(new SizeOptF32(-25.0f, -25.0f)));
            assertTrue(MaybeResolve.SizeDimensionExt.maybeResolveO(s, new SizeOptF32(0.0f, 0.0f), CALC)
                    .rustEq(new SizeOptF32(0.0f, 0.0f)));
        }
    }

    // ---- upstream: mod resolve_or_zero_dimension_to_option_f32 ----
    @Nested
    class ResolveOrZeroDimensionToOptionF32 {

        @Test
        void resolveOrZeroAuto() {
            assertEquals(0.0f, Dimension.AUTO.resolveOrZero(OptF32.NONE, CALC));
            assertEquals(0.0f, Dimension.AUTO.resolveOrZero(5.0f, CALC));
            assertEquals(0.0f, Dimension.AUTO.resolveOrZero(-5.0f, CALC));
            assertEquals(0.0f, Dimension.AUTO.resolveOrZero(0.0f, CALC));
        }

        @Test
        void resolveOrZeroLength() {
            Dimension l = Dimension.fromLength(5.0);
            assertEquals(5.0f, l.resolveOrZero(OptF32.NONE, CALC));
            assertEquals(5.0f, l.resolveOrZero(5.0f, CALC));
            assertEquals(5.0f, l.resolveOrZero(-5.0f, CALC));
            assertEquals(5.0f, l.resolveOrZero(0.0f, CALC));
        }

        @Test
        void resolveOrZeroPercent() {
            Dimension p = Dimension.fromPercent(5.0);
            assertEquals(0.0f, p.resolveOrZero(OptF32.NONE, CALC));
            assertEquals(25.0f, p.resolveOrZero(5.0f, CALC));
            assertEquals(-25.0f, p.resolveOrZero(-5.0f, CALC));
            assertEquals(0.0f, p.resolveOrZero(0.0f, CALC));
        }
    }

    // ---- upstream: mod resolve_or_zero_rect_dimension_to_rect ----
    @Nested
    class ResolveOrZeroRectDimensionToRect {

        private Rect<Dimension> auto() {
            return new Rect<>(Dimension.AUTO, Dimension.AUTO, Dimension.AUTO, Dimension.AUTO);
        }

        @Test
        void resolveOrZeroAuto() {
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), SizeOptF32.NONE, CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), new SizeOptF32(5.0f, 5.0f), CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), new SizeOptF32(-5.0f, -5.0f), CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), new SizeOptF32(0.0f, 0.0f), CALC)
                    .rustEq(RectF32.ZERO));
        }

        @Test
        void resolveOrZeroLength() {
            Rect<Dimension> r = RectDimensionExt.fromLength(5.0f, 5.0f, 5.0f, 5.0f);
            RectF32 five = new RectF32(5.0f, 5.0f, 5.0f, 5.0f);
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, SizeOptF32.NONE, CALC)
                    .rustEq(five));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, new SizeOptF32(5.0f, 5.0f), CALC)
                    .rustEq(five));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, new SizeOptF32(-5.0f, -5.0f), CALC)
                    .rustEq(five));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, new SizeOptF32(0.0f, 0.0f), CALC)
                    .rustEq(five));
        }

        @Test
        void resolveOrZeroPercent() {
            Rect<Dimension> r = RectDimensionExt.fromPercent(5.0f, 5.0f, 5.0f, 5.0f);
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, SizeOptF32.NONE, CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, new SizeOptF32(5.0f, 5.0f), CALC)
                    .rustEq(new RectF32(25.0f, 25.0f, 25.0f, 25.0f)));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, new SizeOptF32(-5.0f, -5.0f), CALC)
                    .rustEq(new RectF32(-25.0f, -25.0f, -25.0f, -25.0f)));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, new SizeOptF32(0.0f, 0.0f), CALC)
                    .rustEq(RectF32.ZERO));
        }
    }

    // ---- upstream: mod resolve_or_zero_rect_dimension_to_rect_f32_via_option ----
    @Nested
    class ResolveOrZeroRectDimensionToRectF32ViaOption {

        private Rect<Dimension> auto() {
            return new Rect<>(Dimension.AUTO, Dimension.AUTO, Dimension.AUTO, Dimension.AUTO);
        }

        @Test
        void resolveOrZeroAuto() {
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), OptF32.NONE, CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), 5.0f, CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), -5.0f, CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(auto(), 0.0f, CALC)
                    .rustEq(RectF32.ZERO));
        }

        @Test
        void resolveOrZeroLength() {
            Rect<Dimension> r = RectDimensionExt.fromLength(5.0f, 5.0f, 5.0f, 5.0f);
            RectF32 five = new RectF32(5.0f, 5.0f, 5.0f, 5.0f);
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, OptF32.NONE, CALC)
                    .rustEq(five));
            assertTrue(
                    MaybeResolve.RectDimensionExt.resolveOrZeroO(r, 5.0f, CALC).rustEq(five));
            assertTrue(
                    MaybeResolve.RectDimensionExt.resolveOrZeroO(r, -5.0f, CALC).rustEq(five));
            assertTrue(
                    MaybeResolve.RectDimensionExt.resolveOrZeroO(r, 0.0f, CALC).rustEq(five));
        }

        @Test
        void resolveOrZeroPercent() {
            Rect<Dimension> r = RectDimensionExt.fromPercent(5.0f, 5.0f, 5.0f, 5.0f);
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, OptF32.NONE, CALC)
                    .rustEq(RectF32.ZERO));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, 5.0f, CALC)
                    .rustEq(new RectF32(25.0f, 25.0f, 25.0f, 25.0f)));
            assertTrue(MaybeResolve.RectDimensionExt.resolveOrZeroO(r, -5.0f, CALC)
                    .rustEq(new RectF32(-25.0f, -25.0f, -25.0f, -25.0f)));
            assertTrue(
                    MaybeResolve.RectDimensionExt.resolveOrZeroO(r, 0.0f, CALC).rustEq(RectF32.ZERO));
        }
    }

    // ---- jaffy: LengthPercentage/LengthPercentageAuto scalar impls ----
    @Nested
    class ScalarImpls {

        @Test
        void lengthPercentage() {
            LengthPercentage l = LengthPercentage.length(7.0f);
            assertTrue(optEq(7.0f, l.maybeResolveO(OptF32.NONE, CALC)));
            LengthPercentage p = LengthPercentage.percent(0.5f);
            assertTrue(optEq(OptF32.NONE, p.maybeResolveO(OptF32.NONE, CALC)));
            assertTrue(optEq(10.0f, p.maybeResolveO(20.0f, CALC)));
            assertTrue(optEq(10.0f, p.maybeResolveF(20.0f, CALC)));
            assertEquals(10.0f, p.resolveOrZero(20.0f, CALC));
            assertEquals(0.0f, p.resolveOrZero(OptF32.NONE, CALC));
        }

        @Test
        void lengthPercentageAuto() {
            assertTrue(optEq(OptF32.NONE, LengthPercentageAuto.AUTO.maybeResolveO(5.0f, CALC)));
            LengthPercentageAuto p = LengthPercentageAuto.percent(0.5f);
            assertTrue(optEq(10.0f, p.maybeResolveO(20.0f, CALC)));
            assertEquals(0.0f, LengthPercentageAuto.AUTO.resolveOrZero(20.0f, CALC));
        }

        @Test
        void dimensionKeywords() {
            // intrinsic sizing keywords and content resolve to None
            assertTrue(optEq(OptF32.NONE, Dimension.minContent().maybeResolveO(100.0f, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.maxContent().maybeResolveO(100.0f, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.fitContent().maybeResolveO(100.0f, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.stretch().maybeResolveO(100.0f, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.content().maybeResolveO(100.0f, CALC)));
            assertTrue(optEq(OptF32.NONE, Dimension.fitContentPx(30.0f).maybeResolveO(100.0f, CALC)));
        }
    }
}
