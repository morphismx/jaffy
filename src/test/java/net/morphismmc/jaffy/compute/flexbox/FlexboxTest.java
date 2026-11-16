// JAFFY: port of upstream src/compute/flexbox.rs `mod balance`'s
// `mod tests` — `zero_sized_item_rule` and `matches_naive_dp`, including
// the `naive_line_item_counts` O(n²) oracle and its helpers.
package net.morphismmc.jaffy.compute.flexbox;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class FlexboxTest {

    ///  min/max helpers (GAP-002 bans /)
    private static int iMin(int a, int b) {
        return a < b ? a : b;
    }

    private static int iMax(int a, int b) {
        return a > b ? a : b;
    }

    /// The number of lines that greedy line breaking produces: each line
    /// collects consecutive items until the next item no longer fits, and if
    /// even the first item of a line doesn't fit, that line takes just the
    /// one (overflowing) item
    private static int greedyLineCount(Balance.LineSizes sizes) {
        int itemCount = sizes.itemCount();
        int lineCount = 1;
        int index = 0;
        while (index < itemCount) {
            int next = index;
            while (next < itemCount && sizes.lineSize(index, next) <= sizes.limit) {
                next += 1;
            }
            if (next == index) {
                next = index + 1;
            }
            index = next;
            if (index < itemCount) {
                lineCount += 1;
            }
        }
        return lineCount;
    }

    /// The zero-sized-item rule, brute force: a line `start..=end` (of a
    /// division of `start..` into `lines` lines) may only end before a
    /// zero-sized item if no division that extends the line through the item
    /// exists — every extension either exceeds the limit or leaves the
    /// remaining items without a valid division into the remaining lines.
    /// (`minErrors` rows below `lines` must already be computed.)
    private static boolean zeroBoundaryValid(
            Balance.LineSizes sizes, double[] minErrors, int lines, int start, int end) {
        int itemCount = sizes.itemCount();
        if (!sizes.isZeroItem(end + 1)) {
            return true;
        }
        for (int gluedEnd = end + 1; gluedEnd <= itemCount - lines; gluedEnd++) {
            if (sizes.lineSize(start, gluedEnd) > sizes.limit) {
                break;
            }
            if (Double.isFinite(minErrors[(lines - 2) * itemCount + gluedEnd + 1])) {
                return false;
            }
        }
        return true;
    }

    private static Balance.LineSizes makeLineSizes(
            float[] itemSizes, float lineLimit, float gapBetweenItems) {
        double gap = Balance.toSize(gapBetweenItems);
        double limit = lineLimit;
        List<Balance.SumEntry> sums = new ArrayList<>(itemSizes.length);
        double sum = 0.0;
        for (float size : itemSizes) {
            double s = Balance.toSize(size);
            sum += s + gap;
            sums.add(new Balance.SumEntry(sum, s == 0.0));
        }
        return new Balance.LineSizes(sums, gap, limit);
    }

    /// The naive `O(line_count * item_count²)` dynamic program from the spec,
    /// used as a test oracle for the divide-and-conquer optimized
    /// implementation
    private static int[] naiveLineItemCounts(
            float[] itemSizes, float lineLimit, float gapBetweenItems, int minLineCount) {
        int itemCount = itemSizes.length;
        Balance.LineSizes sizes = makeLineSizes(itemSizes, lineLimit, gapBetweenItems);

        int lineCount =
                iMax(greedyLineCount(sizes), iMin(iMax(minLineCount, 1), itemCount));

        // `min_errors[(lines - 1) * item_count + start]` is the minimum total
        // score of any valid division of items `start..` into exactly `lines`
        // lines
        double[] minErrors = new double[lineCount * itemCount];
        int len = 0;
        for (int start = 0; start < itemCount; start++) {
            minErrors[len++] = sizes.lineCost(start, itemCount - 1);
        }
        for (int lines = 2; lines <= lineCount; lines++) {
            int row = (lines - 1) * itemCount;
            for (int start = 0; start < itemCount; start++) {
                double minError = Balance.INFEASIBLE;
                int end = start;
                while (end + lines <= itemCount) {
                    double lineCost = sizes.lineCost(start, end);
                    if (lineCost == Balance.INFEASIBLE) {
                        break;
                    }
                    if (zeroBoundaryValid(sizes, minErrors, lines, start, end)) {
                        double error = lineCost + minErrors[row - itemCount + end + 1];
                        if (error < minError) {
                            minError = error;
                        }
                    }
                    end += 1;
                }
                minErrors[len++] = minError;
            }
        }

        // For each line, the *largest* end whose error plus the minimum
        // remaining error adds up to the (feasible) minimum is chosen
        assertTrue(Double.isFinite(minErrors[(lineCount - 1) * itemCount]));
        int[] itemCounts = new int[lineCount];
        int itemCountsLen = 0;
        int start = 0;
        for (int linesAfter = lineCount - 1; linesAfter >= 0; linesAfter--) {
            double target = minErrors[linesAfter * itemCount + start];
            int end = itemCount - 1 - linesAfter;
            while (true) {
                if (sizes.lineCost(start, end) != Balance.INFEASIBLE
                        && (linesAfter == 0
                                || zeroBoundaryValid(
                                        sizes, minErrors, linesAfter + 1, start, end))) {
                    double remainingError =
                            linesAfter == 0
                                    ? 0.0
                                    : minErrors[(linesAfter - 1) * itemCount + end + 1];
                    if (sizes.lineCost(start, end) + remainingError == target) {
                        break;
                    }
                }
                assertTrue(end > start);
                end -= 1;
            }
            itemCounts[itemCountsLen++] = end - start + 1;
            start = end + 1;
        }
        assertTrue(start == itemCount);
        return itemCounts;
    }

    @Test
    void zeroSizedItemRule() {
        // The zero item joins the first line even though the gap it brings
        // makes the score worse: [70, 0] / [20] has line sizes 80 / 20 (score
        // 6800), while [70] / [0, 20] would have line sizes 70 / 30 (score
        // 5800)
        int[] counts =
                Balance.balancedLineItemCounts(new float[] {70.0f, 0.0f, 20.0f}, 100.0f, 10.0f, 1);
        assertArrayEquals(new int[] {2, 1}, counts);
        // The zero item is not glued to a single overflowing item's line
        counts =
                Balance.balancedLineItemCounts(
                        new float[] {150.0f, 0.0f, 30.0f}, 100.0f, 0.0f, 1);
        assertArrayEquals(new int[] {1, 2}, counts);
        // Zero items are glued as far as the requested line count allows
        counts =
                Balance.balancedLineItemCounts(
                        new float[] {70.0f, 0.0f, 0.0f}, 100.0f, 0.0f, 2);
        assertArrayEquals(new int[] {2, 1}, counts);
    }

    @Test
    void matchesNaiveDp() {
        // A simple xorshift PRNG so the test is deterministic and
        // dependency-free
        long[] state = {0x243F6A8885A308D3L};
        java.util.function.IntUnaryOperator rand =
                bound -> {
                    state[0] ^= state[0] << 13;
                    state[0] ^= state[0] >>> 7;
                    state[0] ^= state[0] << 17;
                    return (int) (((state[0] >>> 32) & 0xFFFFFFFFL) % bound);
                };

        for (int testCase = 0; testCase < 5000; testCase++) {
            // Mostly small item counts (quantized sizes maximize ties,
            // exercising the tie-break rules), with some larger ones
            // exercising the recursion
            int itemCount =
                    testCase % 20 == 0 ? rand.applyAsInt(60) + 1 : rand.applyAsInt(15) + 1;
            float[] itemSizes = new float[itemCount];
            for (int i = 0; i < itemCount; i++) {
                int r = rand.applyAsInt(8);
                // Frequent zero items exercise the zero-sized-item rule
                itemSizes[i] =
                        r <= 1 ? 0.0f : r == 2 ? -10.0f : rand.applyAsInt(8) * 25.0f;
            }
            float lineLimit =
                    rand.applyAsInt(5) == 0
                            ? Float.POSITIVE_INFINITY
                            : rand.applyAsInt(10) * 30.0f;
            float gapBetweenItems = rand.applyAsInt(4) * 5.0f;
            int minLineCount = rand.applyAsInt(itemCount + 2);

            int[] expected =
                    naiveLineItemCounts(itemSizes, lineLimit, gapBetweenItems, minLineCount);
            int[] actual =
                    Balance.balancedLineItemCounts(
                            itemSizes, lineLimit, gapBetweenItems, minLineCount);
            final int tc = testCase;
            assertArrayEquals(
                    expected,
                    actual,
                    () ->
                            "case "
                                    + tc
                                    + ": item_sizes="
                                    + Arrays.toString(itemSizes)
                                    + " line_limit="
                                    + lineLimit
                                    + " gap_between_items="
                                    + gapBetweenItems
                                    + " min_line_count="
                                    + minLineCount);
        }
    }
}
