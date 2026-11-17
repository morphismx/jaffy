// JAFFY: upstream=src/tree/cache.rs
// JAFFY-NOTE: tests for  — upstream `mod tests` ported verbatim
// `mode_change_evictions_are_counted_for_non_empty_caches_only` is
// `cfg(debug_assertions && std)` upstream; the counter always exists in Java.
package net.morphismmc.jaffy.tree.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class CacheTest {

    private static final int CACHE_SIZE = Cache.CACHE_SIZE;

    private static LayoutInput input(float width) {
        return new LayoutInput(
                RunMode.COMPUTE_SIZE,
                SizingMode.INHERENT_SIZE,
                RequestedAxis.BOTH,
                new SizeOptF32(width, OptF32.NONE),
                SizeBool.TRUE,
                SizeOptF32.NONE,
                new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT),
                LineBool.FALSE);
    }

    private static LayoutOutput output(float width) {
        return LayoutOutput.fromOuterSize(new SizeF32(width, width));
    }

    /// `LayoutInput { sizing_mode: mode, ..input }` style helper — record
    /// component-wise rebuild per GAP-005.
    private static LayoutInput withMode(LayoutInput base, SizingMode sizingMode, LineBool collapsible) {
        return new LayoutInput(
                base.runMode(),
                sizingMode,
                base.axis(),
                base.knownDimensions(),
                base.knownDimensionsAreDefinite(),
                base.parentSize(),
                base.availableSpace(),
                collapsible);
    }

    private static LayoutInput performLayoutInput(float width, float height) {
        // `LayoutInput { run_mode: PerformLayout, known_dimensions: Size {
        //   width: Some(w), height: Some(h) }, parent_size: same, ..input(w) }`
        LayoutInput base = input(width);
        return new LayoutInput(
                RunMode.PERFORM_LAYOUT,
                base.sizingMode(),
                base.axis(),
                new SizeOptF32(width, height),
                base.knownDimensionsAreDefinite(),
                new SizeOptF32(width, height),
                base.availableSpace(),
                base.verticalMarginsAreCollapsible());
    }

    /// Inputs that differ from `input`/`perform_layout_input` only in their mode
    private static LayoutInput[] otherModes(LayoutInput base) {
        return new LayoutInput[] {
            withMode(base, SizingMode.CONTENT_SIZE, base.verticalMarginsAreCollapsible()),
            withMode(base, base.sizingMode(), LineBool.TRUE),
            withMode(base, base.sizingMode(), new LineBool(true, false)),
            withMode(base, base.sizingMode(), new LineBool(false, true)),
        };
    }

    private static int measureEntryCount(Cache cache) {
        int n = 0;
        for (var entry : cache.measureEntries) {
            if (entry != null) {
                n++;
            }
        }
        return n;
    }

    // ---- upstream `recently_used_measure_entries_get_a_second_chance` ----

    @Test
    void recentlyUsedMeasureEntriesGetASecondChance() {
        Cache cache = new Cache();
        for (int width = 0; width < CACHE_SIZE; width++) {
            cache.store(input(width), output(width));
        }
        cache.store(input(CACHE_SIZE), output(CACHE_SIZE));

        assertEquals(output(1.0f), cache.get(input(1.0f)));
        cache.store(input(CACHE_SIZE + 1), output(CACHE_SIZE + 1));

        assertEquals(output(1.0f), cache.get(input(1.0f)));
        assertNull(cache.get(input(2.0f)));
    }

    // ---- upstream `storing_an_existing_measurement_updates_it_in_place` ----

    @Test
    void storingAnExistingMeasurementUpdatesItInPlace() {
        Cache cache = new Cache();
        cache.store(input(1.0f), output(1.0f));
        cache.store(input(2.0f), output(2.0f));
        cache.store(input(1.0f), output(3.0f));

        assertEquals(2, measureEntryCount(cache));
        assertEquals(output(3.0f), cache.get(input(1.0f)));
    }

    // ---- upstream `measurements_with_margin_collapse_metadata_are_not_cached` ----

    @Test
    void measurementsWithMarginCollapseMetadataAreNotCached() {
        Cache cache = new Cache();

        LayoutOutput base = output(1.0f);
        LayoutOutput collapseThrough = new LayoutOutput(
                base.size(),
                base.scrollableOverflowRect(),
                base.baselines(),
                base.topMargin(),
                base.bottomMargin(),
                true,
                base.oofCandidates(),
                base.oofPositioningArea());
        cache.store(input(1.0f), collapseThrough);
        assertNull(cache.get(input(1.0f)));

        LayoutOutput out2 = output(2.0f);
        LayoutOutput carried = new LayoutOutput(
                out2.size(),
                out2.scrollableOverflowRect(),
                out2.baselines(),
                CollapsibleMarginSet.fromMargin(10.0f),
                out2.bottomMargin(),
                false,
                out2.oofCandidates(),
                out2.oofPositioningArea());
        cache.store(input(2.0f), carried);
        assertNull(cache.get(input(2.0f)));
    }

    // ---- upstream `retrieving_a_measurement_only_marks_its_slot_as_used` ----

    @Test
    void retrievingAMeasurementOnlyMarksItsSlotAsUsed() {
        Cache cache = new Cache();
        cache.store(input(1.0f), output(1.0f));
        cache.store(input(2.0f), output(2.0f));
        cache.recentlyUsedEntries = 0;
        Cache.CacheEntry<?>[] entries = cache.measureEntries.clone();

        assertEquals(output(1.0f), cache.get(input(1.0f)));
        assertArrayEquals(entries, cache.measureEntries);
        assertNotEquals(0, cache.recentlyUsedEntries);
    }

    // ---- upstream `cache_mode_distinguishes_every_combination_of_inputs` ----

    @Test
    void cacheModeDistinguishesEveryCombinationOfInputs() {
        Cache.CacheMode[] modes = new Cache.CacheMode[8];
        for (int index = 0; index < modes.length; index++) {
            modes[index] = Cache.CacheMode.from(withMode(
                    input(1.0f),
                    index % 2 == 0 ? SizingMode.CONTENT_SIZE : SizingMode.INHERENT_SIZE,
                    new LineBool((index & 2) != 0, (index & 4) != 0)));
        }
        for (int index = 0; index < modes.length; index++) {
            for (int prior = 0; prior < index; prior++) {
                assertNotEquals(modes[prior], modes[index]);
            }
        }
    }

    // ---- upstream `retrieving_with_a_different_mode_misses_and_keeps_the_entries` ----

    @Test
    void retrievingWithADifferentModeMissesAndKeepsTheEntries() {
        Cache cache = new Cache();
        cache.store(performLayoutInput(100.0f, 50.0f), output(1.0f));
        cache.store(input(1.0f), output(2.0f));

        for (LayoutInput other : otherModes(performLayoutInput(100.0f, 50.0f))) {
            assertNull(cache.get(other));
        }
        for (LayoutInput other : otherModes(input(1.0f))) {
            assertNull(cache.get(other));
        }

        assertEquals(output(1.0f), cache.get(performLayoutInput(100.0f, 50.0f)));
        assertEquals(output(2.0f), cache.get(input(1.0f)));
    }

    // ---- upstream `storing_with_a_different_mode_drops_the_existing_entries` ----

    @Test
    void storingWithADifferentModeDropsTheExistingEntries() {
        for (LayoutInput other : otherModes(input(3.0f))) {
            Cache cache = new Cache();
            cache.store(performLayoutInput(100.0f, 50.0f), output(1.0f));
            cache.store(input(1.0f), output(2.0f));

            cache.store(other, output(3.0f));
            assertEquals(output(3.0f), cache.get(other));
            assertNull(cache.finalLayoutEntry);
            assertEquals(1, measureEntryCount(cache));

            // The old entries are gone, even for inputs that have the mode that the cache had before
            cache.store(input(4.0f), output(4.0f));
            assertNull(cache.get(performLayoutInput(100.0f, 50.0f)));
            assertNull(cache.get(input(1.0f)));
            assertNull(cache.get(other));
            assertEquals(output(4.0f), cache.get(input(4.0f)));
        }

        for (LayoutInput other : otherModes(performLayoutInput(100.0f, 50.0f))) {
            Cache cache = new Cache();
            cache.store(performLayoutInput(100.0f, 50.0f), output(1.0f));
            cache.store(input(1.0f), output(2.0f));

            cache.store(other, output(3.0f));
            assertEquals(output(3.0f), cache.get(other));
            assertEquals(0, measureEntryCount(cache));
            assertNull(cache.get(performLayoutInput(100.0f, 50.0f)));
        }
    }

    // ---- upstream `hidden_layouts_do_not_affect_the_cache` ----

    @Test
    void hiddenLayoutsDoNotAffectTheCache() {
        Cache cache = new Cache();
        cache.store(input(1.0f), output(1.0f));
        cache.store(LayoutInput.HIDDEN, output(2.0f));
        assertEquals(output(1.0f), cache.get(input(1.0f)));
    }

    // ---- upstream `mode_change_evictions_are_counted_for_non_empty_caches_only` ----

    @Test
    void modeChangeEvictionsAreCountedForNonEmptyCachesOnly() {
        LayoutInput contentSize = withMode(input(1.0f), SizingMode.CONTENT_SIZE, LineBool.FALSE);
        int evictions = Cache.cacheModeChangeEvictions();

        Cache cache = new Cache();
        cache.store(contentSize, output(1.0f));
        cache.store(withMode(input(2.0f), SizingMode.CONTENT_SIZE, LineBool.FALSE), output(2.0f));
        assertEquals(evictions, Cache.cacheModeChangeEvictions());

        cache.store(input(1.0f), output(1.0f));
        assertEquals(evictions + 1, Cache.cacheModeChangeEvictions());

        cache.clear();
        cache.store(contentSize, output(1.0f));
        assertEquals(evictions + 1, Cache.cacheModeChangeEvictions());
    }
}
