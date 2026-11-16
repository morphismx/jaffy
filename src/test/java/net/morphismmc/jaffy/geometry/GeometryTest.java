// JAFFY: behavior checks for geometry.rs ports (axis enums, generic records,
// f32/OptF32/bool specializations, FloatInsets) and the Ext classes.
package net.morphismmc.jaffy.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.dimension.RectDimensionExt;
import net.morphismmc.jaffy.style.available_space.SizeAvailableSpaceExt;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void axes() {
        assertEquals(AbsoluteAxis.VERTICAL, AbsoluteAxis.HORIZONTAL.otherAxis());
        assertEquals(AbsoluteAxis.HORIZONTAL, AbsoluteAxis.VERTICAL.otherAxis());
        assertEquals(AbstractAxis.BLOCK, AbstractAxis.INLINE.other());
        assertEquals(AbstractAxis.INLINE, AbstractAxis.BLOCK.other());
        assertEquals(AbsoluteAxis.HORIZONTAL, AbstractAxis.INLINE.asAbsNaive());
        assertEquals(AbsoluteAxis.VERTICAL, AbstractAxis.BLOCK.asAbsNaive());
    }

    @Test
    void sizeBasics() {
        Size<String> s = new Size<>("w", "h");
        assertEquals("w", s.getAbs(AbsoluteAxis.HORIZONTAL));
        assertEquals("h", s.getAbs(AbsoluteAxis.VERTICAL));
        assertEquals("h", s.transpose().width());
        assertEquals("w", s.transpose().height());
        assertEquals("w!", s.map(v -> v + "!").width());
        assertEquals("w?", s.mapWidth(v -> v + "?").width());
        assertEquals("h?", s.mapHeight(v -> v + "?").height());
        assertEquals("w+H", s.zipMap(new Size<>("H", "W"), (a, b) -> a + "+" + b).width());

        // flexbox helpers
        assertEquals("w", s.main(FlexDirection.ROW));
        assertEquals("h", s.main(FlexDirection.COLUMN));
        assertEquals("h", s.cross(FlexDirection.ROW));
        assertEquals("w", s.cross(FlexDirection.COLUMN));
        assertEquals("x", s.withMain(FlexDirection.ROW, "x").width());
        assertEquals("h", s.withMain(FlexDirection.ROW, "x").height());
        assertEquals("x", s.withCross(FlexDirection.ROW, "x").height());
        assertEquals("w!", s.mapMain(FlexDirection.ROW, v -> v + "!").width());
        assertEquals("w!", s.mapCross(FlexDirection.COLUMN, v -> v + "!").width());

        // grid helpers
        assertEquals("w", s.get(AbstractAxis.INLINE));
        assertEquals("h", s.get(AbstractAxis.BLOCK));
        assertEquals("x", s.with(AbstractAxis.INLINE, "x").width());
        assertEquals("x", s.with(AbstractAxis.BLOCK, "x").height());
    }

    @Test
    void sizeOptionGeneric() {
        // Size<Option<T>> on object T = nullable fields (GAP-001)
        Size<String> some = new Size<>("a", "b");
        Size<String> partial = new Size<>("a", null);
        assertTrue(some.bothAxisDefined());
        assertFalse(partial.bothAxisDefined());
        assertEquals("d", partial.unwrapOr(new Size<>("c", "d")).height());
        assertEquals("d", partial.or(new Size<>("c", "d")).height());
        assertEquals("a", partial.or(new Size<>("c", "d")).width());
    }

    @Test
    void sizeF32() {
        assertTrue(SizeF32.ZERO.rustEq(new SizeF32(0.0f, 0.0f)));
        assertTrue(new SizeF32(1.0f, 2.0f).f32Max(new SizeF32(2.0f, 1.0f)).rustEq(new SizeF32(2.0f, 2.0f)));
        assertTrue(new SizeF32(1.0f, 2.0f).f32Min(new SizeF32(2.0f, 1.0f)).rustEq(new SizeF32(1.0f, 1.0f)));
        // GAP-002: NaN is dropped, not propagated (non-NaN operand wins)
        assertEquals(2.0f, new SizeF32(Float.NaN, 1.0f).f32Max(new SizeF32(2.0f, Float.NaN)).width());
        assertEquals(1.0f, new SizeF32(Float.NaN, 1.0f).f32Max(new SizeF32(2.0f, Float.NaN)).height());
        assertTrue(new SizeF32(1.0f, 0.5f).hasNonZeroArea());
        assertFalse(new SizeF32(1.0f, 0.0f).hasNonZeroArea());
        assertTrue(new SizeF32(1.0f, 2.0f).plus(new SizeF32(3.0f, 4.0f)).rustEq(new SizeF32(4.0f, 6.0f)));
        assertTrue(new SizeF32(4.0f, 6.0f).minus(new SizeF32(1.0f, 2.0f)).rustEq(new SizeF32(3.0f, 4.0f)));
        assertTrue(new SizeF32(1.0f, 2.0f).mapO(v -> v).rustEq(new SizeOptF32(1.0f, 2.0f)));
        // GAP-004: NaN is not equal to itself
        assertFalse(new SizeF32(Float.NaN, 1.0f).rustEq(new SizeF32(Float.NaN, 1.0f)));
    }

    @Test
    void sizeOptF32() {
        assertTrue(OptF32.isNone(SizeOptF32.NONE.width()));
        assertTrue(OptF32.isNone(SizeOptF32.NONE.height()));

        // from_cross: row -> height, column -> width
        SizeOptF32 row = SizeOptF32.fromCross(FlexDirection.ROW, 5.0f);
        assertTrue(OptF32.isNone(row.width()));
        assertEquals(5.0f, row.height());
        SizeOptF32 col = SizeOptF32.fromCross(FlexDirection.COLUMN, 5.0f);
        assertEquals(5.0f, col.width());
        assertTrue(OptF32.isNone(col.height()));

        // maybe_apply_aspect_ratio
        SizeOptF32 w = new SizeOptF32(10.0f, OptF32.NONE);
        assertTrue(w.maybeApplyAspectRatio(2.0f).rustEq(new SizeOptF32(10.0f, 5.0f)));
        SizeOptF32 h = new SizeOptF32(OptF32.NONE, 10.0f);
        assertTrue(h.maybeApplyAspectRatio(2.0f).rustEq(new SizeOptF32(20.0f, 10.0f)));
        assertTrue(w.maybeApplyAspectRatio(OptF32.NONE).rustEq(w));
        assertTrue(new SizeOptF32(1.0f, 2.0f).maybeApplyAspectRatio(2.0f).rustEq(new SizeOptF32(1.0f, 2.0f)));

        // Size<Option<f32>> impls
        assertTrue(new SizeOptF32(1.0f, OptF32.NONE).unwrapOr(new SizeF32(7.0f, 8.0f)).rustEq(new SizeF32(1.0f, 8.0f)));
        assertTrue(new SizeOptF32(1.0f, OptF32.NONE).or(new SizeOptF32(7.0f, 8.0f)).rustEq(new SizeOptF32(1.0f, 8.0f)));
        assertFalse(new SizeOptF32(1.0f, OptF32.NONE).bothAxisDefined());
        assertTrue(new SizeOptF32(1.0f, 2.0f).bothAxisDefined());
        assertTrue(new SizeOptF32(1.0f, OptF32.NONE).mapF(v -> OptF32.unwrapOr(v, 9.0f)).rustEq(new SizeF32(1.0f, 9.0f)));
        // GAP-004: None == None
        assertTrue(new SizeOptF32(1.0f, OptF32.NONE).rustEq(new SizeOptF32(1.0f, OptF32.NONE)));
    }

    @Test
    void rectBasics() {
        Rect<Integer> r = new Rect<>(1, 2, 3, 4);
        assertEquals(new Rect<>(3, 4, 1, 2), r.transpose());
        assertEquals(new Rect<>(2, 4, 6, 8), r.map(v -> v * 2));
        assertEquals(new Line<>(1, 2), r.horizontalComponents());
        assertEquals(new Line<>(3, 4), r.verticalComponents());
        assertEquals(new Rect<>(10, 20, 30, 40), r.zipMap(new Rect<>(9, 18, 27, 36), Integer::sum));
        assertEquals(3, (int) r.horizontalAxisSum(Integer::sum));
        assertEquals(7, (int) r.verticalAxisSum(Integer::sum));
        assertEquals(new Size<>(3, 7), r.sumAxes(Integer::sum));
        assertEquals(3, (int) r.gridAxisSum(AbsoluteAxis.HORIZONTAL, Integer::sum));
        assertEquals(7, (int) r.gridAxisSum(AbsoluteAxis.VERTICAL, Integer::sum));
        assertEquals(3, (int) r.mainAxisSum(FlexDirection.ROW, Integer::sum));
        assertEquals(7, (int) r.mainAxisSum(FlexDirection.COLUMN, Integer::sum));
        assertEquals(7, (int) r.crossAxisSum(FlexDirection.ROW, Integer::sum));
        assertEquals(1, r.mainStart(FlexDirection.ROW));
        assertEquals(2, r.mainEnd(FlexDirection.ROW));
        assertEquals(3, r.crossStart(FlexDirection.ROW));
        assertEquals(4, r.crossEnd(FlexDirection.ROW));
        assertEquals(3, r.mainStart(FlexDirection.COLUMN));
        assertEquals(1, r.crossStart(FlexDirection.COLUMN));
    }

    @Test
    void rectF32() {
        assertTrue(RectF32.ZERO.rustEq(new RectF32(0, 0, 0, 0)));
        // union: left/top min, right/bottom max
        RectF32 a = new RectF32(1.0f, 5.0f, 2.0f, 6.0f);
        RectF32 b = new RectF32(0.0f, 4.0f, 3.0f, 8.0f);
        assertTrue(a.union(b).rustEq(new RectF32(0.0f, 5.0f, 2.0f, 8.0f)));
        assertTrue(a.plus(new RectF32(1, 1, 1, 1)).rustEq(new RectF32(2.0f, 6.0f, 3.0f, 7.0f)));
        assertEquals(new SizeF32(6.0f, 8.0f), a.sumAxes());
        assertEquals(6.0f, a.horizontalAxisSum());
        assertEquals(6.0f, a.mainAxisSum(FlexDirection.ROW));
        assertEquals(8.0f, a.crossAxisSum(FlexDirection.ROW));
        assertEquals(new LineF32(1.0f, 5.0f), a.horizontalComponents());
        assertTrue(a.mapO(v -> v).rustEq(new RectOptF32(1.0f, 5.0f, 2.0f, 6.0f)));
    }

    @Test
    void rectOptF32() {
        assertTrue(OptF32.isNone(RectOptF32.NONE.left()));
        RectOptF32 r = new RectOptF32(1.0f, OptF32.NONE, 3.0f, 4.0f);
        assertEquals(1.0f, r.mainStart(FlexDirection.ROW));
        assertTrue(OptF32.isNone(r.mainEnd(FlexDirection.ROW)));
        assertEquals(3.0f, r.crossStart(FlexDirection.ROW));
        assertEquals(4.0f, r.crossEnd(FlexDirection.ROW));
        assertEquals(new LineOptF32(1.0f, OptF32.NONE), r.horizontalComponents());
        assertTrue(r.mapF(v -> OptF32.unwrapOr(v, 0.0f)).rustEq(new RectF32(1.0f, 0.0f, 3.0f, 4.0f)));
        // GAP-004: None == None
        assertTrue(r.rustEq(new RectOptF32(1.0f, OptF32.NONE, 3.0f, 4.0f)));
    }

    @Test
    void zipSize() {
        // Rect<Dimension>.zip_size(Size<f32>, maybe_resolve-like) -> Rect<Option<f32>>
        Rect<Dimension> inset = RectDimensionExt.fromPercent(0.5f, 0.25f, 0.1f, 0.2f);
        RectOptF32 resolved = inset.zipSizeO(new SizeF32(100.0f, 200.0f), (d, s) -> {
            // emulate LengthPercentageAuto::maybe_resolve for a percent value
            return new DimensionCalcShim(d).resolvePercent(s);
        });
        assertEquals(50.0f, resolved.left());
        assertEquals(25.0f, resolved.right());
        assertEquals(20.0f, resolved.top());
        assertEquals(40.0f, resolved.bottom());
    }

    /// Test-local percent resolver standing in for maybe_resolve (resolve.rs
    /// is not ported yet — the zip_size mechanics are what is under test).
    private record DimensionCalcShim(Dimension d) {
        float resolvePercent(float s) {
            return d.tag() == Dimension.percent(0.0f).tag() ? d.value() * s : d.value();
        }
    }

    @Test
    void pointsAndLines() {
        PointF32 p = new PointF32(1.0f, 2.0f);
        assertTrue(PointF32.ZERO.rustEq(new PointF32(0, 0)));
        assertTrue(p.transpose().rustEq(new PointF32(2.0f, 1.0f)));
        assertTrue(p.plus(new PointF32(3, 4)).rustEq(new PointF32(4.0f, 6.0f)));
        assertEquals(1.0f, p.get(AbstractAxis.INLINE));
        assertEquals(2.0f, p.get(AbstractAxis.BLOCK));
        assertTrue(p.with(AbstractAxis.INLINE, 9.0f).rustEq(new PointF32(9.0f, 2.0f)));
        assertEquals(1.0f, p.main(FlexDirection.ROW));
        assertEquals(2.0f, p.main(FlexDirection.COLUMN));
        assertTrue(p.toSize().rustEq(new SizeF32(1.0f, 2.0f)));

        assertTrue(PointOptF32.NONE.rustEq(new PointOptF32(OptF32.NONE, OptF32.NONE)));

        assertEquals(new Line<>(1, 2), new Line<>(1, 2).map(v -> v));
        assertEquals(3, (int) new Line<>(1, 2).sum(Integer::sum));
        assertEquals(3.0f, new LineF32(1.0f, 2.0f).sum());
        assertEquals(LineBool.TRUE, new LineBool(true, true));
        assertEquals(LineBool.FALSE, new LineBool(false, false));

        MinMax<Integer, String> mm = new MinMax<>(1, "x");
        assertEquals(1, mm.min());
        assertEquals("x", mm.max());
        assertTrue(mm.rustEq(new MinMax<>(1, "x"), Integer::equals, String::equals));
    }

    @Test
    void floatInsets() {
        assertTrue(FloatInsets.ZERO.rustEq(new FloatInsets(0, 0)));
        assertEquals(3.0f, new FloatInsets(1.0f, 2.0f).sum());
        assertTrue(new FloatInsets(1.0f, 5.0f).max(new FloatInsets(4.0f, 2.0f)).rustEq(new FloatInsets(4.0f, 5.0f)));
        assertTrue(new FloatInsets(1.0f, 2.0f).plus(new FloatInsets(3.0f, 4.0f)).rustEq(new FloatInsets(4.0f, 6.0f)));
    }

    @Test
    void inBothAbsAxis() {
        InBothAbsAxis<Integer> both = new InBothAbsAxis<>(1, 2);
        assertEquals(1, both.get(AbsoluteAxis.HORIZONTAL));
        assertEquals(2, both.get(AbsoluteAxis.VERTICAL));
        assertTrue(both.rustEq(new InBothAbsAxis<>(1, 2), Integer::equals));
    }

    @Test
    void requestedAxis() {
        assertEquals(RequestedAxis.VERTICAL, RequestedAxis.HORIZONTAL.transpose());
        assertEquals(RequestedAxis.HORIZONTAL, RequestedAxis.VERTICAL.transpose());
        assertEquals(RequestedAxis.BOTH, RequestedAxis.BOTH.transpose());
        assertEquals(RequestedAxis.HORIZONTAL, RequestedAxis.from(AbsoluteAxis.HORIZONTAL));
        assertEquals(RequestedAxis.VERTICAL, RequestedAxis.from(AbsoluteAxis.VERTICAL));
        assertEquals(AbsoluteAxis.HORIZONTAL, AbsoluteAxis.tryFrom(RequestedAxis.HORIZONTAL).ok());
        assertNull(AbsoluteAxis.tryFrom(RequestedAxis.BOTH).ok());
    }

    @Test
    void dimensionAndAvailableSpaceExts() {
        Size<Dimension> sl = SizeDimensionExt.fromLengths(10.0f, 20.0f);
        assertEquals(10.0f, sl.width().value());
        Size<Dimension> sp = SizeDimensionExt.fromPercent(0.5f, 0.25f);
        assertEquals(0.5f, sp.width().value());

        Rect<Dimension> rl = RectDimensionExt.fromLength(1.0f, 2.0f, 3.0f, 4.0f);
        assertEquals(2.0f, rl.right().value());
        Rect<Dimension> rp = RectDimensionExt.fromPercent(0.1f, 0.2f, 0.3f, 0.4f);
        assertEquals(0.4f, rp.bottom().value(), 1e-7f);

        Size<AvailableSpace> as = new Size<>(new AvailableSpace.Definite(10.0f), AvailableSpace.MIN_CONTENT);
        SizeOptF32 opts = SizeAvailableSpaceExt.intoOptions(as);
        assertEquals(10.0f, opts.width());
        assertTrue(OptF32.isNone(opts.height()));

        Size<AvailableSpace> set = SizeAvailableSpaceExt.maybeSet(as, new SizeOptF32(5.0f, 6.0f));
        assertTrue(set.width().rustEq(new AvailableSpace.Definite(5.0f)));
        assertTrue(set.height().rustEq(new AvailableSpace.Definite(6.0f)));
        Size<AvailableSpace> unset = SizeAvailableSpaceExt.maybeSet(as, new SizeOptF32(OptF32.NONE, 6.0f));
        assertTrue(unset.width().rustEq(new AvailableSpace.Definite(10.0f)));
    }
}
