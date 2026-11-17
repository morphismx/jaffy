// JAFFY-UPSTREAM: crate:slotmap:src/basic.rs
// JAFFY-NOTE: (the APIs taffy uses — GAP-015)
//
// Keys are `long` (NodeId, GAP-015): high 32 bits = version (always odd),
// low 32 bits = slot index. `KeyData::new` stores `version | 1`.
//
// `union SlotUnion { value, next_free }` -> two fields (Java has no unions).
// `Vec<Slot<V>>` -> ArrayList; `capacity()` tracks the requested hint since
// ArrayList does not expose capacity (GAP-015: only `>=` asserts use it).
//
// Ported API: with_capacity, insert, remove, get, get_mut, index/indexMut
// (Index/IndexMut), len, is_empty, capacity, clear (drain ordering),
// contains_key. Not ported: retain/detach/reattach/iterators/serde/clone —
// no upstream call sites.
package net.morphismmc.jaffy.infra.slotmap;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

public final class SlotMap<V> {

    /// `union SlotUnion` + `version`: value + nextFree share no storage, but
    /// only one is live at a time (version % 2 > 0 means occupied).
    private static final class Slot<V> {
        @Nullable
        V value;

        int nextFree;
        int version; // Even = vacant, odd = occupied.

        boolean occupied() {
            return version % 2 > 0;
        }
    }

    private final ArrayList<Slot<V>> slots;
    private int freeHead; // u32
    private int numElems; // u32
    private int capacityHint;

    /// `SlotMap::new()`.
    public SlotMap() {
        this(0);
    }

    /// `SlotMap::with_capacity_and_key(capacity)` — `with_capacity` for DefaultKey.
    private SlotMap(int capacity) {
        // Create slots with a sentinel at index 0.
        slots = new ArrayList<>(capacity + 1);
        Slot<V> sentinel = new Slot<>();
        sentinel.nextFree = 0;
        sentinel.version = 0;
        slots.add(sentinel);
        freeHead = 1;
        numElems = 0;
        capacityHint = capacity;
    }

    /// `SlotMap::with_capacity`.
    public static <V> SlotMap<V> withCapacity(int capacity) {
        return new SlotMap<>(capacity);
    }

    /// `KeyData::new(idx, version)` + `as_ffi()` — stores `version | 1`.
    private static long keyData(int idx, int version) {
        // JAFFY(GAP-014): u32 parts packed unsigned into the u64 key.
        return (Integer.toUnsignedLong(version | 1) << 32) | Integer.toUnsignedLong(idx);
    }

    /// `key.data().idx` — low 32 bits (u32 -> int).
    private static int keyIdx(long key) {
        return (int) key;
    }

    /// `key.data().version.get()` — high 32 bits.
    private static int keyVersion(long key) {
        return (int) (key >>> 32);
    }

    /// `slots.get(kd.idx as usize)` — unsigned idx bound check (GAP-014).
    private @Nullable Slot<V> slotAt(int idx) {
        return Integer.compareUnsigned(idx, slots.size()) < 0 ? slots.get(idx) : null;
    }

    /// `len`.
    public int len() {
        return numElems;
    }

    /// `is_empty`.
    public boolean isEmpty() {
        return numElems == 0;
    }

    /// `capacity` — one slot is reserved for the sentinel. ArrayList does not
    /// expose capacity; return the tracked hint or live slot count (GAP-015).
    public int capacity() {
        int alloc = slots.size() - 1;
        return capacityHint > alloc ? capacityHint : alloc;
    }

    /// `contains_key`.
    public boolean containsKey(long key) {
        Slot<V> slot = slotAt(keyIdx(key));
        return slot != null && slot.version == keyVersion(key);
    }

    /// `try_insert_with_key` with `f = |_| value` (the only shape taffy uses).
    /// Returns the u64 key (as_ffi). Panics ("SlotMap is full") when full.
    public long insert(V value) {
        int head = freeHead;
        Slot<V> slot = slotAt(head);
        if (slot != null) {
            int occupiedVersion = slot.version | 1;
            long kd = keyData(head, occupiedVersion);

            // Update.
            freeHead = slot.nextFree;
            slot.value = value;
            slot.version = occupiedVersion;
            numElems += 1;
            return kd;
        }

        if (slots.size() >= 0xFFFFFFFFL) { // u32::MAX — unreachable in practice
            throw new IllegalStateException("SlotMap is full"); // JAFFY(GAP-010)
        }

        int version = 1;
        int idx = slots.size();
        long kd = keyData(idx, version);

        Slot<V> created = new Slot<>();
        created.value = value;
        created.version = version;
        slots.add(created);

        freeHead = idx + 1;
        numElems += 1;
        return kd;
    }

    /// `remove_from_slot` — safe iff the slot is occupied.
    private @Nullable V removeFromSlot(int idx) {
        Slot<V> slot = slots.get(idx);
        V value = slot.value;
        slot.value = null;

        // Maintain freelist.
        slot.nextFree = freeHead;
        freeHead = idx;
        numElems -= 1;
        slot.version = slot.version + 1; // wrapping_add(1) — int wraps like u32

        return value;
    }

    /// `remove` — returns the value or null (None).
    public @Nullable V remove(long key) {
        if (containsKey(key)) {
            return removeFromSlot(keyIdx(key));
        }
        return null;
    }

    /// `drain` — removes all occupied slots in ascending index order.
    /// Rust yields (key, value) pairs; the only upstream use is `clear()`,
    /// which drains to completion.
    private void drain() {
        int cur = 1;
        int len = slots.size();
        while (cur < len) {
            Slot<V> slot = slots.get(cur);
            if (slot.occupied()) {
                removeFromSlot(cur);
            }
            cur += 1;
        }
    }

    /// `clear` = `drain` to completion.
    public void clear() {
        drain();
    }

    /// `get` — returns the value or null (None).
    public @Nullable V get(long key) {
        Slot<V> slot = slotAt(keyIdx(key));
        if (slot != null && slot.version == keyVersion(key)) {
            return slot.value;
        }
        return null;
    }

    /// `get_mut` — same object as `get`; callers mutate fields (GAP-018).
    public @Nullable V getMut(long key) {
        return get(key);
    }

    /// `Index<K>` — panics on invalid key.
    public V index(long key) {
        V v = get(key);
        if (v == null) {
            throw new IllegalStateException("invalid SlotMap key used"); // JAFFY(GAP-010)
        }
        return v;
    }

    /// `IndexMut<K>` — panics on invalid key; field-level mutation only (GAP-018).
    public V indexMut(long key) {
        V v = getMut(key);
        if (v == null) {
            throw new IllegalStateException("invalid SlotMap key used"); // JAFFY(GAP-010)
        }
        return v;
    }

    /// `IndexMut<K>` whole-slot write — `self[key] = value`. Needed for
    /// immutable value types (e.g. `Option<NodeId>` -> `Long`), where
    /// `indexMut` cannot express assignment. Panics on invalid key.
    public void set(long key, @Nullable V value) {
        Slot<V> slot = slotAt(keyIdx(key));
        if (slot == null || slot.version != keyVersion(key)) {
            throw new IllegalStateException("invalid SlotMap key used"); // JAFFY(GAP-010)
        }
        slot.value = value;
    }
}
