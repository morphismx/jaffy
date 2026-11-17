// JAFFY-UPSTREAM: none
// JAFFY-NOTE: the `mod tests` block in upstream compact_length.rs and the
// `expand_tests` block in dimension.rs (rustc 1.96 semantics as oracle).
package net.morphismmc.jaffy.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.ExpandedDimension;
import net.morphismmc.jaffy.style.dimension.ExpandedLengthPercentage;
import net.morphismmc.jaffy.style.dimension.ExpandedLengthPercentageAuto;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class CompactLengthTest {

    private static final int[] NON_PERCENTAGE_TAGS = {
        CompactLength.LENGTH_TAG,
        CompactLength.AUTO_TAG,
        CompactLength.FR_TAG,
        CompactLength.MIN_CONTENT_TAG,
        CompactLength.MAX_CONTENT_TAG,
        CompactLength.FIT_CONTENT_PX_TAG,
        CompactLength.FIT_CONTENT_KEYWORD_TAG,
        CompactLength.STRETCH_TAG,
        CompactLength.CONTENT_TAG,
    };

    private static final int[] PERCENTAGE_TAGS = {CompactLength.PERCENT_TAG, CompactLength.FIT_CONTENT_PERCENT_TAG};

    private static CompactLength fromRawTag(int tag) {
        // `Self(CompactLengthInner::from_tag(tag))` — tagged_ptr = tag.
        return new CompactLength.Primitive(tag);
    }

    @Test
    void tagsAreUniqueAndFitInAByte() {
        List<Integer> all = new ArrayList<>();
        for (int t : NON_PERCENTAGE_TAGS) all.add(t);
        for (int t : PERCENTAGE_TAGS) all.add(t);
        all.add(CompactLength.CALC_TAG);
        all.add(CompactLength.FIT_CONTENT_CALC_TAG);
        for (int t : all) {
            assertTrue(t <= 0xFF);
        }
        assertEquals(all.size(), new TreeSet<>(all).size());
    }

    @Test
    void nonPointerTagsHaveNonPointerBitSet() {
        for (int tag : NON_PERCENTAGE_TAGS) {
            assertNotEquals(0, tag & CompactLength.NON_POINTER_BIT);
        }
        for (int tag : PERCENTAGE_TAGS) {
            assertNotEquals(0, tag & CompactLength.NON_POINTER_BIT);
        }
        assertEquals(0, CompactLength.CALC_TAG & CompactLength.NON_POINTER_BIT);
    }

    @Test
    void keywordBitsMatchPredicates() {
        for (int tag : NON_PERCENTAGE_TAGS) {
            CompactLength value = fromRawTag(tag);
            boolean isPlain = tag == CompactLength.LENGTH_TAG || tag == CompactLength.PERCENT_TAG;
            assertEquals((tag & CompactLength.KEYWORD_BIT) == 0, isPlain, "tag " + tag);
            assertEquals(value.isLengthOrPercentage(), isPlain, "tag " + tag);
            boolean isSizingKeyword = tag == CompactLength.MIN_CONTENT_TAG
                    || tag == CompactLength.MAX_CONTENT_TAG
                    || tag == CompactLength.FIT_CONTENT_KEYWORD_TAG
                    || tag == CompactLength.FIT_CONTENT_PX_TAG
                    || tag == CompactLength.FIT_CONTENT_PERCENT_TAG
                    || tag == CompactLength.STRETCH_TAG;
            assertEquals(value.isSizingKeyword(), isSizingKeyword, "tag " + tag);
        }
        for (int tag : PERCENTAGE_TAGS) {
            CompactLength value = fromRawTag(tag);
            boolean isPlain = tag == CompactLength.LENGTH_TAG || tag == CompactLength.PERCENT_TAG;
            assertEquals((tag & CompactLength.KEYWORD_BIT) == 0, isPlain, "tag " + tag);
            assertEquals(value.isLengthOrPercentage(), isPlain, "tag " + tag);
            boolean isSizingKeyword = tag == CompactLength.FIT_CONTENT_PERCENT_TAG;
            assertEquals(value.isSizingKeyword(), isSizingKeyword, "tag " + tag);
        }
        assertEquals(0, CompactLength.CALC_TAG & CompactLength.KEYWORD_BIT);
        assertNotEquals(0, CompactLength.FIT_CONTENT_CALC_TAG & CompactLength.KEYWORD_BIT);
        assertEquals(0, CompactLength.FIT_CONTENT_CALC_TAG & CompactLength.NON_POINTER_BIT);
        assertNotEquals(0, CompactLength.FIT_CONTENT_CALC_TAG & CompactLength.PERCENTAGE_BIT);
    }

    @Test
    void percentageBitMatchesUsesPercentage() {
        for (int tag : NON_PERCENTAGE_TAGS) {
            assertEquals(0, tag & CompactLength.PERCENTAGE_BIT);
        }
        for (int tag : PERCENTAGE_TAGS) {
            assertNotEquals(0, tag & CompactLength.PERCENTAGE_BIT);
        }
        assertNotEquals(0, CompactLength.CALC_TAG & CompactLength.PERCENTAGE_BIT);

        assertFalse(CompactLength.length(10.0f).usesPercentage());
        assertFalse(CompactLength.auto().usesPercentage());
        assertFalse(CompactLength.fr(1.0f).usesPercentage());
        assertFalse(CompactLength.minContent().usesPercentage());
        assertFalse(CompactLength.maxContent().usesPercentage());
        assertFalse(CompactLength.fitContentPx(10.0f).usesPercentage());
        assertFalse(CompactLength.fitContentKeyword().usesPercentage());
        assertFalse(CompactLength.stretch().usesPercentage());
        assertFalse(CompactLength.content().usesPercentage());
        assertTrue(CompactLength.percent(0.5f).usesPercentage());
        assertTrue(CompactLength.fitContentPercent(0.5f).usesPercentage());
    }

    @Test
    void fitContentCalcRoundTripsHandle() {
        Object handle = new Object();
        CompactLength value = CompactLength.fitContentCalc(handle);
        assertTrue(value.isFitContentCalc());
        assertTrue(value.hasCalc());
        assertFalse(value.isPlainCalc());
        assertTrue(value.isFitContent());
        assertTrue(value.isSizingKeyword());
        assertTrue(value.isIntrinsic());
        assertTrue(value.isMaxOrFitContent());
        assertTrue(value.isMaxContentAlike());
        assertTrue(value.usesPercentage());
        assertFalse(value.isLengthOrPercentage());
        assertSame(handle, value.calcValue());

        CompactLength calc = CompactLength.calc(handle);
        assertTrue(calc.isPlainCalc());
        assertTrue(calc.hasCalc());
        assertFalse(calc.isFitContentCalc());
        assertFalse(calc.isFitContent());
        assertFalse(calc.isSizingKeyword());
    }

    @Test
    void calcRoundTripsHandle() {
        Object handle = new Object();
        CompactLength value = CompactLength.calc(handle);
        assertTrue(value.isPlainCalc());
        assertTrue(value.usesPercentage());
        assertFalse(value.isLengthOrPercentage());
        assertFalse(value.isAuto());
        assertFalse(value.isIntrinsic());
        assertSame(handle, value.calcValue());
    }

    @Test
    void isZeroComparesBits() {
        assertTrue(CompactLength.length(0.0f).isZero());
        assertTrue(CompactLength.ZERO.isZero());
        assertFalse(CompactLength.auto().isZero());
        // -0.0 has different bits than +0.0 (Rust compares raw tagged_ptr)
        assertFalse(CompactLength.length(-0.0f).isZero());
    }

    // ---- dimension.rs expand_tests ----

    @Test
    void lengthPercentageRoundTrips() {
        LengthPercentage[] cases = {
            LengthPercentage.length(12.0f), LengthPercentage.percent(0.5f), LengthPercentage.ZERO
        };
        for (LengthPercentage value : cases) {
            assertTrue(LengthPercentage.from(value.expand()).rustEq(value));
            assertTrue(ExpandedLengthPercentage.from(value).rustEq(value.expand()));
        }
        assertTrue(LengthPercentage.length(3.0f).expand().rustEq(new ExpandedLengthPercentage.Length(3.0f)));
        assertTrue(LengthPercentage.percent(0.25f).expand().rustEq(new ExpandedLengthPercentage.Percent(0.25f)));
    }

    @Test
    void lengthPercentageAutoRoundTrips() {
        LengthPercentageAuto[] cases = {
            LengthPercentageAuto.length(12.0f), LengthPercentageAuto.percent(0.5f), LengthPercentageAuto.auto()
        };
        for (LengthPercentageAuto value : cases) {
            assertTrue(LengthPercentageAuto.from(value.expand()).rustEq(value));
        }
        assertTrue(LengthPercentageAuto.auto().expand().rustEq(new ExpandedLengthPercentageAuto.Auto()));
    }

    @Test
    void lengthPercentageAutoResolveToOption() {
        assertEquals(12.0f, LengthPercentageAuto.length(12.0f).resolveToOption(100.0f, (c, ctx) -> 0.0f));
        assertEquals(50.0f, LengthPercentageAuto.percent(0.5f).resolveToOption(100.0f, (c, ctx) -> 0.0f));
        assertTrue(OptF32.isNone(LengthPercentageAuto.auto().resolveToOption(100.0f, (c, ctx) -> 0.0f)));
        Object handle = new Object();
        assertEquals(42.0f, LengthPercentageAuto.calc(handle).resolveToOption(100.0f, (c, ctx) -> {
            assertSame(handle, c);
            assertEquals(100.0f, ctx);
            return 42.0f;
        }));
    }

    @Test
    void dimensionRoundTripsAllKeywords() {
        Dimension[] cases = {
            Dimension.length(12.0f),
            Dimension.percent(0.5f),
            Dimension.auto(),
            Dimension.minContent(),
            Dimension.maxContent(),
            Dimension.fitContentPx(30.0f),
            Dimension.fitContentPercent(0.75f),
            Dimension.fitContent(),
            Dimension.stretch(),
            Dimension.content(),
        };
        for (Dimension value : cases) {
            assertTrue(Dimension.from(value.expand()).rustEq(value));
        }
        assertTrue(Dimension.fitContentPx(30.0f).expand().rustEq(new ExpandedDimension.FitContentPx(30.0f)));
        assertTrue(Dimension.content().expand().rustEq(new ExpandedDimension.Content()));
    }

    @Test
    void calcRoundTrips() {
        Object handle = new Object();
        assertTrue(LengthPercentage.calc(handle).expand().rustEq(new ExpandedLengthPercentage.Calc(handle)));
        assertTrue(
                LengthPercentage.from(new ExpandedLengthPercentage.Calc(handle)).rustEq(LengthPercentage.calc(handle)));
        assertTrue(Dimension.calc(handle).expand().rustEq(new ExpandedDimension.Calc(handle)));
        assertTrue(Dimension.fitContentCalc(handle).expand().rustEq(new ExpandedDimension.FitContentCalc(handle)));
        assertTrue(
                Dimension.from(new ExpandedDimension.FitContentCalc(handle)).rustEq(Dimension.fitContentCalc(handle)));
        assertTrue(LengthPercentageAuto.calc(handle).expand().rustEq(new ExpandedLengthPercentageAuto.Calc(handle)));
    }

    @Test
    void conversionsFromLengthPercentage() {
        Dimension d = Dimension.from(LengthPercentage.percent(0.5f));
        assertEquals(CompactLength.PERCENT_TAG, d.tag());
        assertEquals(0.5f, d.value());
        LengthPercentageAuto lpa = LengthPercentageAuto.from(LengthPercentage.length(7.0f));
        assertEquals(CompactLength.LENGTH_TAG, lpa.tag());
        assertEquals(7.0f, lpa.value());
    }

    @Test
    void resolvedPercentageSize() {
        assertEquals(50.0f, CompactLength.percent(0.5f).resolvedPercentageSize(100.0f, (c, ctx) -> 0.0f));
        assertTrue(OptF32.isNone(CompactLength.length(5.0f).resolvedPercentageSize(100.0f, (c, ctx) -> 0.0f)));
        Object handle = new Object();
        assertEquals(9.0f, CompactLength.calc(handle).resolvedPercentageSize(100.0f, (c, ctx) -> 9.0f));
    }
}
