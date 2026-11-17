// JAFFY: port of slotmap 1.1.1 src/sparse_secondary.rs (the APIs taffy uses — GAP-015).
//
// `HashMap<u32, Slot<V>>` -> fastutil Int2ObjectOpenHashMap keyed by the u32
// slot index (GAP-014/-015). taffy never iterates it, so hash order is
// unobservable.
//
// Ported API: with_capacity, insert, remove, get, get_mut, index/indexMut,
// len, is_empty, capacity, clear, contains_key, get_disjoint_mut.
// Not ported: retain/entry/iterators/serde/clone — no upstream call sites.
package net.morphismmc.jaffy.infra.slotmap;

import java.util.function.IntFunction;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public final class SparseSecondaryMap<V> {

    /// `struct Slot { version: u32, value: T }`.
    private static final class Slot<V> {
        int version;
        V value;

        Slot(int version, V value) {
            this.version = version;
            this.value = value;
        }
    }

    private final Int2ObjectOpenHashMap<Slot<V>> slots;
    private int capacityHint;

    /// `SparseSecondaryMap::new()`.
    public SparseSecondaryMap() {
        this(0);
    }

    private SparseSecondaryMap(int capacity) {
        slots = new Int2ObjectOpenHashMap<>(capacity);
        capacityHint = capacity;
    }

    /// `SparseSecondaryMap::with_capacity`.
    public static <V> SparseSecondaryMap<V> withCapacity(int capacity) {
        return new SparseSecondaryMap<>(capacity);
    }

    /// `key.data().idx` — low 32 bits (u32 -> int).
    private static int keyIdx(long key) {
        return (int) key;
    }

    /// `key.data().version.get()` — high 32 bits.
    private static int keyVersion(long key) {
        return (int) (key >>> 32);
    }

    /// `key.is_null()` — `idx == u32::MAX`.
    private static boolean keyIsNull(long key) {
        return keyIdx(key) == -1;
    }

    /// `is_older_version(a, b)` = `a.wrapping_sub(b) >= (1 << 31)` (u32)
    /// ⇔ signed `(a - b) < 0` (GAP-014/-015).
    private static boolean isOlderVersion(int a, int b) {
        return (a - b) < 0;
    }

    /// `len`.
    public int len() {
        return slots.size();
    }

    /// `is_empty`.
    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /// `capacity` — Int2ObjectOpenHashMap does not expose table capacity;
    /// return the tracked hint or live size (GAP-015: only `>=` asserts use it).
    public int capacity() {
        int size = slots.size();
        return capacityHint > size ? capacityHint : size;
    }

    /// `contains_key`.
    public boolean containsKey(long key) {
        Slot<V> slot = slots.get(keyIdx(key));
        return slot != null && slot.version == keyVersion(key);
    }

    /// `insert` — can silently fail (return null) if the key was removed from
    /// the originating slot map. Returns the old value if the key was present.
    public @Nullable V insert(long key, V value) {
        if (keyIsNull(key)) {
            return null;
        }

        int idx = keyIdx(key);
        int version = keyVersion(key);

        Slot<V> slot = slots.get(idx);
        if (slot != null) {
            if (slot.version == version) {
                V old = slot.value;
                slot.value = value;
                return old;
            }

            // Don't replace existing newer values.
            if (isOlderVersion(version, slot.version)) {
                return null;
            }

            slot.version = version;
            slot.value = value;
            return null;
        }

        slots.put(idx, new Slot<>(version, value));
        return null;
    }

    /// `remove` — returns the value or null (None).
    public @Nullable V remove(long key) {
        int idx = keyIdx(key);
        Slot<V> slot = slots.get(idx);
        if (slot != null && slot.version == keyVersion(key)) {
            slots.remove(idx);
            return slot.value;
        }
        return null;
    }

    /// `clear`.
    public void clear() {
        slots.clear();
    }

    /// `get` — returns the value or null (None).
    public @Nullable V get(long key) {
        Slot<V> slot = slots.get(keyIdx(key));
        return slot != null && slot.version == keyVersion(key) ? slot.value : null;
    }

    /// `get_mut` — same object as `get`; callers mutate fields (GAP-018).
    public @Nullable V getMut(long key) {
        return get(key);
    }

    /// `Index<K>` — panics on invalid key.
    public V index(long key) {
        V v = get(key);
        if (v == null) {
            throw new IllegalStateException("invalid SparseSecondaryMap key used"); // JAFFY(GAP-010)
        }
        return v;
    }

    /// `IndexMut<K>` — panics on invalid key; field-level mutation only (GAP-018).
    public V indexMut(long key) {
        V v = getMut(key);
        if (v == null) {
            throw new IllegalStateException("invalid SparseSecondaryMap key used"); // JAFFY(GAP-010)
        }
        return v;
    }

    /// `get_disjoint_mut` — returns the value objects for all keys if all are
    /// valid and disjoint, else null (None). Duplicate detection: temporarily
    /// flip each matched slot's version to even so a repeated key fails the
    /// `version == key.version` check (key versions are always odd).
    ///
    /// `generator` creates the result array (generic arrays cannot be
    /// instantiated in Java); it is invoked with `keys.length`, mirroring the
    /// const-generic `[&mut V; N]` of the Rust signature.
    public @Nullable V[] getDisjointMut(IntFunction<V[]> generator, long... keys) {
        int n = keys.length;
        V[] out = generator.apply(n);

        int i = 0;
        while (i < n) {
            int idx = keyIdx(keys[i]);
            Slot<V> slot = slots.get(idx);
            if (slot == null || slot.version != keyVersion(keys[i])) {
                break;
            }
            // Temporarily make the version even so duplicate keys show up as invalid.
            out[i] = slot.value;
            slot.version ^= 1;
            i += 1;
        }

        // Undo temporary even versions.
        for (int j = 0; j < i; j++) {
            slots.get(keyIdx(keys[j])).version ^= 1;
        }

        return i == n ? out : null; // All were valid and disjoint.
    }
}
