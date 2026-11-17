// JAFFY: upstream=src/compute/flexbox.rs
// JAFFY-NOTE: the line-balancing phase

package net.morphismmc.jaffy.compute.flexbox;

import java.util.ArrayList;
import java.util.List;

final class Balance {
    private Balance() {}

    /// Score assigned to divisions that violate the line size constraint
    static final double INFEASIBLE = Double.POSITIVE_INFINITY;

    /// `usize`/`u32` min/max helpers (GAP-002 bans `Math.max`/`min`)
    static int iMin(int a, int b) {
        return a < b ? a : b;
    }

    static int iMax(int a, int b) {
        return a > b ? a : b;
    }

    /// Convert an item size in pixels to an `f64`, flooring negative
    /// sizes at zero. (An infinite size is clamped to the largest finite
    /// `f32` so that sums stay meaningful.)
    static double toSize(float value) {
        return Math.clamp((double) value, 0.0, (double) Float.MAX_VALUE);
    }

    /// One `sums` entry: the prefix sum through the item paired with
    /// whether the item's (floored) size is zero (`(f64, bool)` -> record)
    record SumEntry(double sum, boolean isZero) {}

    /// Line sizing shared by the scoring and readback phases
    static final class LineSizes {
        /// Per item, the prefix sum of the item sizes through it (each
        /// item also contributes one trailing gap, so the size of the
        /// line holding items `start..=end` is
        /// `sums[end].sum - sums[start - 1].sum - gapBetweenItems`),
        /// paired with whether the item's (floored) size is zero
        final List<SumEntry> sums;
        /// The size of the gap between adjacent items on a line
        final double gapBetweenItems;
        /// The container's inner main size, which lines may not exceed
        /// (unless they hold a single item)
        final double limit;

        LineSizes(List<SumEntry> sums, double gapBetweenItems, double limit) {
            this.sums = sums;
            this.gapBetweenItems = gapBetweenItems;
            this.limit = limit;
        }

        /// The total number of items
        int itemCount() {
            return sums.size();
        }

        /// Whether the item at `index` has a (floored) size of zero
        boolean isZeroItem(int index) {
            return sums.get(index).isZero();
        }

        /// The size of the line holding items `start..=end`
        double lineSize(int start, int end) {
            double startSum = start == 0 ? 0.0 : sums.get(start - 1).sum();
            return sums.get(end).sum() - startSum - gapBetweenItems;
        }

        /// The squared size of the line holding items `start..=end`, or
        /// {@link #INFEASIBLE} for a line of more than one item exceeding
        /// the limit
        double lineCost(int start, int end) {
            double size = lineSize(start, end);
            if (end > start && size > limit) {
                return INFEASIBLE;
            }
            return size * size;
        }

        /// For every suffix of the items, the number of lines that greedy
        /// line breaking produces for it: each line collects consecutive
        /// items until the next item no longer fits, and if even the
        /// first item of a line doesn't fit, that line takes just the one
        /// (overflowing) item. (The entry for the empty suffix is zero.)
        ///
        /// This is also the *fewest* lines each suffix can validly be
        /// divided into, and a suffix can validly be divided into any
        /// number of lines from that count up to its item count: dividing
        /// at greedy line ends never violates the zero-sized-item rule (a
        /// line that can hold no more of the following items can never
        /// glue them), and from any in-range line count the same holds
        /// with line ends capped to leave one item per remaining line.
        int[] suffixGreedyLineCounts() {
            int itemCount = itemCount();
            int[] counts = new int[itemCount + 1];
            // The (exclusive) end of the greedy first line of the suffix,
            // which only moves down as the suffix grows leftwards since
            // lines starting earlier are larger
            int lineEnd = itemCount;
            for (int start = itemCount - 1; start >= 0; start--) {
                while (lineEnd > start + 1 && lineSize(start, lineEnd - 1) > limit) {
                    lineEnd -= 1;
                }
                counts[start] = 1 + counts[lineEnd];
            }
            return counts;
        }

        /// For every `start`, the largest `end` such that the line
        /// holding items `start..=end` does not exceed the limit (or
        /// `start` itself if even that one item overflows)
        int[] fitEnds() {
            int itemCount = itemCount();
            int[] fitEnds = new int[itemCount];
            // Lines starting later are smaller, so the fit end only moves
            // up
            int fitEnd = 0;
            for (int start = 0; start < itemCount; start++) {
                if (fitEnd < start) {
                    fitEnd = start;
                }
                while (fitEnd + 1 < itemCount && lineSize(start, fitEnd + 1) <= limit) {
                    fitEnd += 1;
                }
                fitEnds[start] = fitEnd;
            }
            return fitEnds;
        }
    }

    /// One row of the balancing dynamic program: divisions of item
    /// suffixes into exactly `lines` lines
    private record Row(
            /// The line sizing for the items being divided
            LineSizes sizes,
            /// See {@link LineSizes#fitEnds}
            int[] fitEnds,
            /// The previous row: `prev[start]` is the minimum score of any
            /// valid division of items `start..` into exactly
            /// `lines - 1` lines ({@link #INFEASIBLE} if there is none)
            double[] prev,
            /// The line ends that precede a non-zero item, in ascending
            /// order and capped to `maxEnd`. Line ends preceding a *zero*
            /// item are constrained by the zero-sized-item rule and
            /// handled separately: for a first line starting at `start`,
            /// the only end preceding a zero item that can be part of a
            /// valid division is `min(fitEnds[start], maxEnd)` — the line
            /// extended as far as the limit and the remaining lines'
            /// one-item-each budget allow. Ending earlier is forbidden
            /// by the rule (the zero item could be glued: the extended
            /// line fits and, per
            /// {@link LineSizes#suffixGreedyLineCounts}, what remains
            /// after even the longest glued line divides validly into the
            /// remaining lines whenever what remains after the shorter
            /// line does, which it must for the division to be feasible
            /// at all), and ending later exceeds the limit on a line of
            /// more than one item.
            int[] nonzeroEnds,
            /// The largest possible end of the first line: the remaining
            /// `lines - 1` lines need one item each
            int maxEnd) {}

    /// `partition_point` on an ascending `int[]` range (count of elements
    /// in `[lo, hi)` satisfying `pred`)
    private static int partitionPoint(int[] arr, int lo, int hi, java.util.function.IntPredicate pred) {
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (pred.test(arr[mid])) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /// Compute `cur[start]` and `opts[start]` for `start` in
    /// `startLo..=startHi`, where `cur[start]` is the minimum over valid
    /// ends `end` of `row.sizes.lineCost(start, end) + row.prev[end + 1]`
    /// and `opts[start]` is the largest `end` achieving that minimum.
    /// Ends preceding a non-zero item are minimized over
    /// `row.nonzeroEnds[colLo..colHi]`; the (at most one) end preceding a
    /// zero item allowed by the zero-sized-item rule is merged in
    /// afterwards.
    ///
    /// Uses the divide-and-conquer dynamic programming optimization: over
    /// the ends preceding non-zero items, the cost function satisfies the
    /// concave quadrangle inequality (it is the square of a quantity that
    /// increases in `end` and decreases in `start`, plus a term depending
    /// on `end` alone, with infeasibility monotone in line length), so
    /// the largest minimizing end is non-decreasing in `start`. Solving
    /// the middle `start` therefore splits the end range in two, and each
    /// recursion level scans each candidate end a bounded number of
    /// times. (The ends constrained by the zero-sized-item rule are *not*
    /// monotone this way, which is why they are excluded from the scan
    /// and merged in separately.)
    private static void fillRow(
            Row row, double[] cur, int[] opts, int optsOffset, int startLo, int startHi, int colLo, int colHi) {
        if (startLo > startHi) {
            return;
        }
        int start = startLo + (startHi - startLo) / 2;

        // Minimize over the in-range ends preceding a non-zero item,
        // starting from `start` (the line must hold at least one item)
        int firstCol = partitionPoint(row.nonzeroEnds, colLo, colHi, end -> end < start);
        double minCost = INFEASIBLE;
        int minCol = -1;
        for (int col = firstCol; col < colHi; col++) {
            int end = row.nonzeroEnds[col];
            double lineCost = row.sizes.lineCost(start, end);
            if (lineCost == INFEASIBLE) {
                // Every longer line also exceeds the limit
                break;
            }
            double cost = lineCost + row.prev[end + 1];
            // `<=` keeps the *largest* minimizing end, assigning the most
            // items to the earliest lines as the tie-break requires
            if (cost <= minCost) {
                minCost = cost;
                minCol = col;
            }
        }

        // Merge the single end preceding a zero item that the
        // zero-sized-item rule allows
        int minEnd = minCol == -1 ? -1 : row.nonzeroEnds[minCol];
        int zeroEnd = iMin(row.fitEnds[start], row.maxEnd);
        if (row.sizes.isZeroItem(zeroEnd + 1)) {
            double cost = row.sizes.lineCost(start, zeroEnd) + row.prev[zeroEnd + 1];
            if (cost < minCost || (cost == minCost && (minEnd == -1 || minEnd < zeroEnd))) {
                minCost = cost;
                minEnd = zeroEnd;
            }
        }

        // Infeasible states are excluded from the range by the caller,
        // and every feasible state has a valid transition (see
        // [`LineSizes#suffixGreedyLineCounts`]) — upstream debug_assert!
        cur[start] = minCost;
        opts[optsOffset + start] = minEnd == -1 ? start : minEnd;

        // The zero-rule end is excluded from the narrowing: only the
        // non-zero ends' minima are monotone
        if (start > startLo) {
            int hi = minCol == -1 ? colHi : minCol + 1;
            fillRow(row, cur, opts, optsOffset, startLo, start - 1, colLo, hi);
        }
        if (start < startHi) {
            int lo = minCol == -1 ? firstCol : minCol;
            fillRow(row, cur, opts, optsOffset, start + 1, startHi, lo, colHi);
        }
    }

    /// Determine the number of items on each line that balances items
    /// across lines, such that the largest line is as small as possible,
    /// with a minimum of `minLineCount` lines (or one line per item if
    /// there are fewer items).
    ///
    /// `itemSizes` must be non-empty. The returned line item counts are
    /// all non-zero and sum to the number of items.
    ///
    /// Runs in `O(line_count * item_count * log(item_count))` time using
    /// `O(line_count * item_count)` transient memory.
    ///
    /// `impl ExactSizeIterator<Item = f32>` -> `float[]`.
    static int[] balancedLineItemCounts(float[] itemSizes, float lineLimit, float gapBetweenItems, int minLineCount) {
        int itemCount = itemSizes.length;
        double gap = toSize(gapBetweenItems);
        double limit = lineLimit;

        List<SumEntry> sums = new ArrayList<>(itemCount);
        double sum = 0.0;
        for (float size : itemSizes) {
            double s = toSize(size);
            sum += s + gap;
            sums.add(new SumEntry(sum, s == 0.0));
        }
        LineSizes sizes = new LineSizes(sums, gap, limit);

        // `suffixGreedy[start]` is the number of lines greedy line
        // breaking produces for items `start..`, which is also the
        // *fewest* lines the suffix can validly be divided into
        int[] suffixGreedy = sizes.suffixGreedyLineCounts();
        int lineCount = iMax(suffixGreedy[0], iMin(iMax(minLineCount, 1), itemCount));

        if (lineCount == itemCount) {
            // One item per line is the only division (this covers
            // `flex-line-count` of at least the item count as well as
            // every item overflowing a line of its own)
            int[] counts = new int[itemCount];
            java.util.Arrays.fill(counts, 1);
            return counts;
        }

        int[] fitEnds = sizes.fitEnds();
        int[] nonzeroEnds = new int[itemCount - 1];
        int nonzeroLen = 0;
        for (int end = 0; end < itemCount - 1; end++) {
            if (!sizes.isZeroItem(end + 1)) {
                nonzeroEnds[nonzeroLen++] = end;
            }
        }
        // Trim to logical length (upstream Vec::push semantics)
        nonzeroEnds = java.util.Arrays.copyOf(nonzeroEnds, nonzeroLen);

        // `prev[start]` is the minimum total score of any valid division
        // of items `start..` into exactly `lines - 1` lines (`INFEASIBLE`
        // if there is none), and `cur` is the row being computed for
        // `lines` lines: a division into `lines` lines is a first line
        // `start..=end` plus a division of `end + 1..` into `lines - 1`
        // lines. `opts[(lines - 2) * item_count + start]` records the
        // largest `end` achieving `cur[start]`, from which the chosen
        // division is read back.
        double[] prev = new double[itemCount];
        for (int start = 0; start < itemCount; start++) {
            prev[start] = sizes.lineCost(start, itemCount - 1);
        }
        double[] cur = new double[itemCount];
        java.util.Arrays.fill(cur, INFEASIBLE);
        int[] opts = new int[(lineCount - 1) * itemCount];
        for (int lines = 2; lines <= lineCount; lines++) {
            // The remaining `lines - 1` lines need one item each,
            // bounding this line's end
            int maxEnd = itemCount - lines;
            // Starts whose suffix doesn't fit in `lines` lines even with
            // greedy breaking are infeasible; they form a prefix (a
            // longer suffix never needs fewer lines), and excluding them
            // keeps every state `fillRow` solves feasible
            int firstStart = 0;
            while (firstStart < maxEnd && suffixGreedy[firstStart] > lines) {
                cur[firstStart] = INFEASIBLE;
                firstStart += 1;
            }
            int colHi = partitionPoint(nonzeroEnds, 0, nonzeroEnds.length, e -> e <= maxEnd);
            Row row = new Row(sizes, fitEnds, prev, nonzeroEnds, maxEnd);
            int optsOffset = (lines - 2) * itemCount;
            fillRow(row, cur, opts, optsOffset, firstStart, maxEnd, 0, colHi);
            double[] tmp = prev;
            prev = cur;
            cur = tmp;
        }

        // Read the division back out front to back (upstream
        // debug_assert: prev[0] is finite)
        int[] itemCounts = new int[lineCount];
        int start = 0;
        int idx = 0;
        for (int lines = lineCount; lines >= 2; lines--) {
            int end = opts[(lines - 2) * itemCount + start];
            itemCounts[idx++] = end - start + 1;
            start = end + 1;
        }
        itemCounts[idx] = itemCount - start;
        return itemCounts;
    }
}
