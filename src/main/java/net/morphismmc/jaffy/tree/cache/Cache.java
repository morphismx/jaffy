// JAFFY: port of upstream src/tree/cache.rs — `Cache` + private helpers.
//
// u64/u32/u16/u8/usize -> long/int (GAP-014). `AtomicU16` -> `int` — atomics are
// unnecessary: `cache_get` is `&self` upstream only because Rust forbids
// `&mut`; in Java both read as plain instance methods on an exclusively-borrowed
// tree (GAP-017).
// Module-private `CacheKey`/`CacheMode`/`CacheEntry` -> package-private nested
// records (same-package tests reach them, mirroring `mod tests` in-cache.rs).
// `[Option<CacheEntry<Size<f32>>>; CACHE_SIZE]` -> `@Nullable CacheEntry<?>[]`.
//
// `cache_mode_change_evictions` is `#[cfg(all(debug_assertions, feature = "std"))]`
// upstream; Java has no debug/release split so the counter is always present
// (it is only written through `store` and read by tests).
package net.morphismmc.jaffy.tree.cache;

import java.util.Arrays;
import java.util.Objects;

import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

public final class Cache {

    /// The number of cache entries for each node in the tree
    static final int CACHE_SIZE = 9;

    /// `f32::INFINITY` as a u32
    private static final int INFINITY_BITS = Float.floatToRawIntBits(Float.POSITIVE_INFINITY);
    /// `f32::NEG_INFINITY` as a u32
    private static final int NEG_INFINITY_BITS = Float.floatToRawIntBits(Float.NEGATIVE_INFINITY);

    // The `CacheKey` encodes two f32s as a u64. We know that the f32s will always be
    // non-negative, so we pack two extra bits encoding the `RequestedAxis` into the
    // sign bits of the f32s. These constants help to encode and decode those bits.

    /// The sign bit of the first f32
    private static final long SIGN_BIT_1 = 1L << 63;
    /// The sign bit of the second f32
    private static final long SIGN_BIT_2 = 1L << 31;
    /// Mask of both sign bits (used to compute NON_SIGN_BITS_MASK)
    private static final long BOTH_SIGN_BITS_MASK = SIGN_BIT_1 | SIGN_BIT_2;
    /// Mask of excluding the sign bits (used when setting/getting the size excluding the packed bits)
    private static final long NON_SIGN_BITS_MASK = ~BOTH_SIGN_BITS_MASK;

    /// Mask which includes only the bits which encode the x-axis value that we can use to ignore the
    /// y-axis value when comparing a cache key.
    private static final long X_AXIS_VALUE_MASK = 0xFFFFFFFF00000000L;

    /// Pack `Option<f32>` into `u32`.
    /// `Some(value).to_bits()` -> `floatToRawIntBits` (NaN payloads preserved; a
    /// `Some(NaN)` must not be canonicalized — GAP-001).
    private static int optionCacheKey(@Opt float input) {
        return OptF32.isNone(input) ? INFINITY_BITS : Float.floatToRawIntBits(input);
    }

    /// Pack `Size<Option<f32>>` into `u64`
    private static long sizeOptionCacheKey(SizeOptF32 input) {
        return Integer.toUnsignedLong(optionCacheKey(input.width())) << 32
                | Integer.toUnsignedLong(optionCacheKey(input.height()));
    }

    /// Pack `AvailableSpace` into `u32`
    private static int availableSpaceCacheKey(AvailableSpace input) {
        return switch (input) {
            case AvailableSpace.Definite d -> Float.floatToRawIntBits(-d.value());
            case AvailableSpace.MinContent m -> NEG_INFINITY_BITS;
            case AvailableSpace.MaxContent m -> INFINITY_BITS;
        };
    }

    /// Pack `Size<AvailableSpace>` into `u64` (`#[allow(dead_code)]` upstream —
    /// no call sites, mirrored for parity)
    private static long sizeAvailableSpaceCacheKey(Size<AvailableSpace> input) {
        return Integer.toUnsignedLong(availableSpaceCacheKey(input.width())) << 32
                | Integer.toUnsignedLong(availableSpaceCacheKey(input.height()));
    }

    /// Encodes combination of a `known_dimension` (Option<f32>) and `AvailableSpace` in
    /// a single dimension into a cache key in a single dimension.
    private static int mixedCacheKey(@Opt float kd, AvailableSpace avs) {
        return OptF32.isNone(kd) ? availableSpaceCacheKey(avs) : Float.floatToRawIntBits(kd);
    }

    /// Encodes combination of a `known_dimension` (Option<f32>) and `AvailableSpace` in
    /// two dimensions into a cache key in a single dimension.
    private static long sizeMixedCacheKey(SizeOptF32 kd, Size<AvailableSpace> avs) {
        return Integer.toUnsignedLong(mixedCacheKey(kd.width(), avs.width())) << 32
                | Integer.toUnsignedLong(mixedCacheKey(kd.height(), avs.height()));
    }

    /// Space-optimised cache key that packs bits into as small a size as possible
    /// (`struct CacheKey` — `u64` fields -> `long`)
    record CacheKey(
            /// The initial cached size of the node itself
            long kdAvailableSpace,
            /// The initial cached size of the parent's node
            long parentSize,
            /// Whether each known dimension is definite. Normalized such that an axis
            /// without a known dimension is always `true`.
            SizeBool knownDimensionsAreDefinite) {

        /// Return the parent size with the extra bits that encode the requested axis masked out
        long parentSizeMasked() {
            return parentSize & NON_SIGN_BITS_MASK;
        }

        /// Return the parent size with the extra bits that encode the requested axis masked out
        /// And the y-axis value masked out
        long xAxisParentSize() {
            return parentSize & (X_AXIS_VALUE_MASK & NON_SIGN_BITS_MASK);
        }

        /// Return the bits that encode the requested axis
        long requestedAxisBits() {
            return parentSize & BOTH_SIGN_BITS_MASK;
        }

        /// Whether a cached entry with this key contains a valid size for the axis requested by `other`.
        /// Sizes computed for a single axis may contain garbage values in the other axis, so an entry
        /// is only usable if it was computed for the same axis (or for both axes).
        boolean sizeIsValidFor(CacheKey other) {
            long entryAxis = requestedAxisBits();
            return entryAxis == BOTH_SIGN_BITS_MASK || entryAxis == other.requestedAxisBits();
        }

        /// `From<&LayoutInput> for CacheKey`
        static CacheKey from(LayoutInput input) {
            // Pack axis enum into spare bits in the known_dimensions and available_space values
            long extraBits = switch (input.axis()) {
                case HORIZONTAL -> SIGN_BIT_1;
                case VERTICAL -> SIGN_BIT_2;
                case BOTH -> SIGN_BIT_1 | SIGN_BIT_2;
            };

            SizeOptF32 kd = input.knownDimensions();
            SizeBool kdd = input.knownDimensionsAreDefinite();
            return new CacheKey(
                    sizeMixedCacheKey(kd, input.availableSpace()),
                    (sizeOptionCacheKey(input.parentSize()) & NON_SIGN_BITS_MASK) | extraBits,
                    // `kdd.zip_map(kd, |is_definite, kd| is_definite || kd.is_none())`
                    new SizeBool(kdd.width() || OptF32.isNone(kd.width()), kdd.height() || OptF32.isNone(kd.height())));
        }
    }

    /// The layout inputs that affect a node's layout but are not part of the {@link CacheKey}: the
    /// {@link SizingMode} and whether each of the node's vertical margins is collapsible, packed
    /// into a `u8` (`struct CacheMode(u8)` -> `int` bits, GAP-014).
    ///
    /// These are determined by the layout algorithm of the node's parent and by the node's own style,
    /// so they only change for a given node when one of those changes. Rather than keying every entry
    /// on them, a [`Cache`] records the single mode that all of its entries were computed with.
    record CacheMode(int bits) {

        /// Set if the sizing mode is `SizingMode::InherentSize`
        static final int INHERENT_SIZE_BIT = 0b001;
        /// Set if the node's top margin is collapsible
        static final int COLLAPSIBLE_START_MARGIN_BIT = 0b010;
        /// Set if the node's bottom margin is collapsible
        static final int COLLAPSIBLE_END_MARGIN_BIT = 0b100;

        /// The mode of a cache that has never been stored to. Which mode this is does not matter,
        /// as such a cache has no entries.
        static final CacheMode INITIAL = new CacheMode(0);

        /// `From<&LayoutInput> for CacheMode`
        static CacheMode from(LayoutInput input) {
            int inherentSize = input.sizingMode() == SizingMode.INHERENT_SIZE ? 1 : 0;
            LineBool collapsible = input.verticalMarginsAreCollapsible();
            return new CacheMode((inherentSize * INHERENT_SIZE_BIT)
                    | ((collapsible.start() ? 1 : 0) * COLLAPSIBLE_START_MARGIN_BIT)
                    | ((collapsible.end() ? 1 : 0) * COLLAPSIBLE_END_MARGIN_BIT));
        }
    }

    /// `#[cfg(all(debug_assertions, feature = "std"))]` thread_local Cell — always
    /// present in Java (see file header). `usize` -> `int` (GAP-014).
    private static final ThreadLocal<int[]> MODE_CHANGE_EVICTIONS = ThreadLocal.withInitial(() -> new int[1]);

    /// The number of times that the current thread has stored a result into a non-empty {@link Cache}
    /// whose entries were computed with a different `sizing_mode` or `vertical_margins_are_collapsible`,
    /// dropping those entries.
    ///
    /// This is expected when the layout algorithm of a node's parent has changed since the node was
    /// last laid out. It must not happen while laying out a tree whose caches started out empty: that
    /// would mean that Taffy lays a node out with more than one mode in a single pass, with each
    /// change of mode throwing away the node's cache. Taffy's test suite uses this function to check
    /// that. Only available in debug builds upstream.
    public static int cacheModeChangeEvictions() {
        return MODE_CHANGE_EVICTIONS.get()[0];
    }

    /// Cached intermediate layout results (`struct CacheEntry<T>`)
    record CacheEntry<T>(
            /// The key for the cache entry
            CacheKey key,
            /// The cached size and baselines of the item
            T content) {}

    /// The cache entry for the node's final layout
    @Nullable
    CacheEntry<LayoutOutput> finalLayoutEntry;
    /// The cache entries for the node's preliminary size measurements.
    /// Elements are `CacheEntry<SizeF32>`; `CacheEntry<?>` sidesteps Java's ban
    /// on generic array creation (content is cast at the single read site).
    final @Nullable CacheEntry<?>[] measureEntries;
    /// Tracks which measure entries have been used since the eviction cursor last passed them.
    /// Atomic so that {@link Cache#get} can take `&self`. Each flag is only ever written with a
    /// plain (non read-modify-write) `Relaxed` store, so a racing write can at worst lose a hint.
    int recentlyUsedEntries; // `AtomicU16` -> `int` (GAP-014; non-atomic — GAP-017)
    /// The next measure entry to consider replacing
    private int nextMeasureEntry; // u8
    /// Tracks if all cache entries are empty
    private boolean isEmpty;
    /// The `sizing_mode` and `vertical_margins_are_collapsible` inputs that all of the cache's
    /// entries were computed with. Results are only retrieved for inputs that have the same mode,
    /// and storing a result that was computed with a different mode drops the existing entries.
    private CacheMode mode;

    /// Create a new empty cache (`Cache::new` + `Default::default`)
    public Cache() {
        finalLayoutEntry = null;
        measureEntries = new CacheEntry<?>[CACHE_SIZE];
        recentlyUsedEntries = 0;
        nextMeasureEntry = 0;
        isEmpty = true;
        mode = CacheMode.INITIAL;
    }

    /// `Clone` — GAP-005 `.copy()`. `finalLayoutEntry`'s `LayoutOutput` is
    /// deep-copied (its `OofCandidates` is mutable); measure entries are
    /// immutable records.
    public Cache copy() {
        Cache c = new Cache();
        CacheEntry<LayoutOutput> fle = finalLayoutEntry;
        c.finalLayoutEntry =
                fle == null ? null : new CacheEntry<>(fle.key(), fle.content().copy());
        System.arraycopy(measureEntries, 0, c.measureEntries, 0, CACHE_SIZE);
        c.recentlyUsedEntries = recentlyUsedEntries;
        c.nextMeasureEntry = nextMeasureEntry;
        c.isEmpty = isEmpty;
        c.mode = mode;
        return c;
    }

    /// `PartialEq` — compares the atomic counter's current value (Relaxed).
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Cache other
                && Objects.equals(finalLayoutEntry, other.finalLayoutEntry)
                && Arrays.equals(measureEntries, other.measureEntries)
                && recentlyUsedEntries == other.recentlyUsedEntries
                && nextMeasureEntry == other.nextMeasureEntry
                && isEmpty == other.isEmpty
                && mode.equals(other.mode);
    }

    /// Try to retrieve a cached result from the cache (`Option<LayoutOutput>` -> nullable)
    public @Nullable LayoutOutput get(LayoutInput input) {
        if (!mode.equals(CacheMode.from(input))) {
            return null;
        }
        CacheKey key = CacheKey.from(input);
        switch (input.runMode()) {
            case PERFORM_LAYOUT -> {
                CacheEntry<LayoutOutput> entry = finalLayoutEntry;
                if (entry != null && entry.key().equals(key)) {
                    return entry.content().copy();
                }
                return null;
            }
            case COMPUTE_SIZE -> {
                for (int index = 0; index < CACHE_SIZE; index++) {
                    CacheEntry<?> entry = measureEntries[index];
                    if (entry == null) {
                        continue;
                    }
                    if (entry.key().kdAvailableSpace == key.kdAvailableSpace
                            && entry.key().knownDimensionsAreDefinite.equals(key.knownDimensionsAreDefinite)
                            && entry.key().xAxisParentSize() == key.xAxisParentSize()
                            && entry.key().sizeIsValidFor(key)) {
                        recentlyUsedEntries |= (1 << index);
                        return LayoutOutput.fromOuterSize((SizeF32) entry.content());
                    }
                }
                return null;
            }
            case PERFORM_HIDDEN_LAYOUT -> {
                return null;
            }
            default -> throw new IllegalStateException("unreachable"); // JAFFY(GAP-010)
        }
    }

    /// Store a computed size in the cache
    public void store(LayoutInput input, LayoutOutput layoutOutput) {
        if (input.runMode() == RunMode.PERFORM_HIDDEN_LAYOUT) {
            return;
        }
        CacheMode newMode = CacheMode.from(input);
        if (!mode.equals(newMode)) {
            // `#[cfg(all(debug_assertions, feature = "std"))]` upstream — always
            // counted here (see file header).
            if (!isEmpty) {
                MODE_CHANGE_EVICTIONS.get()[0] += 1;
            }
            clear();
            mode = newMode;
        }
        CacheKey key = CacheKey.from(input);
        switch (input.runMode()) {
            case PERFORM_LAYOUT -> {
                isEmpty = false;
                finalLayoutEntry = new CacheEntry<>(key, layoutOutput);
            }
            case COMPUTE_SIZE -> {
                // Measure entries only store the size, and cache hits are reconstructed with
                // `LayoutOutput::from_outer_size`, which resets the margin-collapse metadata
                // (`top_margin`, `bottom_margin`, `margins_can_collapse_through`). Results that
                // carry such metadata cannot be reconstructed from their size, so don't cache them.
                //
                // Upstream compares `!= CollapsibleMarginSet::ZERO` with derived (IEEE) float
                // equality; record `equals` is Float.compare-based (-0.0f != 0.0f there), so the
                // components are compared with plain `!=`/`==` here — JAFFY(GAP-004).
                CollapsibleMarginSet topMargin = layoutOutput.topMargin();
                CollapsibleMarginSet bottomMargin = layoutOutput.bottomMargin();
                if (layoutOutput.marginsCanCollapseThrough()
                        || topMargin.positive() != 0.0f
                        || topMargin.negative() != 0.0f
                        || bottomMargin.positive() != 0.0f
                        || bottomMargin.negative() != 0.0f) {
                    return;
                }
                isEmpty = false;
                for (int index = 0; index < CACHE_SIZE; index++) {
                    CacheEntry<?> entry = measureEntries[index];
                    if (entry != null && entry.key().equals(key)) {
                        // `entry.as_mut().unwrap().content = layout_output.size` — records are
                        // immutable, so the entry is replaced (GAP-005).
                        measureEntries[index] = new CacheEntry<>(key, layoutOutput.size());
                        recentlyUsedEntries |= (1 << index);
                        return;
                    }
                }
                while ((recentlyUsedEntries & (1 << nextMeasureEntry)) != 0) {
                    recentlyUsedEntries &= ~(1 << nextMeasureEntry);
                    nextMeasureEntry += 1;
                    if (nextMeasureEntry == CACHE_SIZE) {
                        nextMeasureEntry = 0;
                    }
                }
                int entryIndex = nextMeasureEntry;
                measureEntries[entryIndex] = new CacheEntry<>(key, layoutOutput.size());
                recentlyUsedEntries |= (1 << entryIndex);
                nextMeasureEntry += 1;
                if (nextMeasureEntry == CACHE_SIZE) {
                    nextMeasureEntry = 0;
                }
            }
            case PERFORM_HIDDEN_LAYOUT -> {}
        }
    }

    /// Clear all cache entries and reports clear operation outcome ({@link ClearState})
    public ClearState clear() {
        if (isEmpty) {
            return ClearState.ALREADY_EMPTY;
        }
        isEmpty = true;
        finalLayoutEntry = null;
        Arrays.fill(measureEntries, null);
        recentlyUsedEntries = 0;
        nextMeasureEntry = 0;
        return ClearState.CLEARED;
    }

    /// Returns true if all cache entries are None, else false
    public boolean isEmpty() {
        if (finalLayoutEntry != null) {
            return false;
        }
        for (CacheEntry<?> entry : measureEntries) {
            if (entry != null) {
                return false;
            }
        }
        return true;
    }
}
