// JAFFY: upstream=none
// JAFFY-NOTE: primitive-long specialization of {@link SlotMap} for
// `SlotMap<Option<NodeId>>` (the `parents` map in TaffyTree). The value
// `0L` is the `Option<NodeId>::None` sentinel — a NodeId's `as_ffi` always
// has a nonzero version half, so 0 is never a valid stored NodeId
// (OptI64.NONE, GAP-001).
//
// `union SlotUnion { value, next_free }` -> parallel arrays `values` +
// `nextFree` + `versions` (Java has no unions). Keys are `long` as in
// {@link SlotMap}: high 32 bits = version (always odd), low 32 bits =
// slot index.
//
// Ported API: with_capacity, insert, get, set (IndexMut whole-slot write),
// contains_key, remove, clear (drain ordering), capacity. Not ported:
// len/is_empty/get_mut/index/iterators — no call sites (`get` returns the
// stored value or 0 for a miss; all callers gate on `containsKey` first,
// and a stored `None` is 0 anyway).
package net.morphismmc.jaffy.infra.slotmap;

import java.util.Arrays;

import net.morphismmc.just.math.Opt;

public final class LongSlotMap {

    /// Slot values; slot 0 is the sentinel. `0L` = `None` (`Option<NodeId>`).
    private long[] values;
    /// Freelist links (`next_free`); only live for vacant slots.
    private int[] nextFree;
    /// Per-slot version — even = vacant, odd = occupied.
    private int[] versions;
    /// Number of slots in use (including the sentinel at index 0).
    private int size;
    private int freeHead; // u32
    private int numElems; // u32
    private final int capacityHint;

    /// `LongSlotMap::new()`.
    public LongSlotMap() {
        this(0);
    }

    /// `SlotMap::with_capacity_and_key(capacity)` — `with_capacity` for DefaultKey.
    private LongSlotMap(int capacity) {
        // Create slots with a sentinel at index 0.
        values = new long[capacity + 1];
        nextFree = new int[capacity + 1];
        versions = new int[capacity + 1];
        // sentinel: nextFree = 0, version = 0 (array defaults)
        size = 1;
        freeHead = 1;
        numElems = 0;
        capacityHint = capacity;
    }

    /// `SlotMap::with_capacity`.
    public static LongSlotMap withCapacity(int capacity) {
        return new LongSlotMap(capacity);
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

    /// `capacity` — one slot is reserved for the sentinel.
    public int capacity() {
        int alloc = size - 1;
        return capacityHint > alloc ? capacityHint : alloc;
    }

    /// `contains_key`.
    public boolean containsKey(long key) {
        int idx = keyIdx(key);
        return Integer.compareUnsigned(idx, size) < 0 && versions[idx] == keyVersion(key);
    }

    /// Grow the parallel arrays so that `size + 1` fits.
    private void ensureSlotRoom() {
        if (size >= values.length) {
            int doubled = values.length * 2;
            int newLen = doubled > size + 1 ? doubled : size + 1;
            values = Arrays.copyOf(values, newLen);
            nextFree = Arrays.copyOf(nextFree, newLen);
            versions = Arrays.copyOf(versions, newLen);
        }
    }

    /// `try_insert_with_key` with `f = |_| value` (the only shape taffy uses).
    /// Returns the u64 key (as_ffi). Panics ("SlotMap is full") when full.
    public long insert(long value) {
        int head = freeHead;
        if (Integer.compareUnsigned(head, size) < 0) {
            int occupiedVersion = versions[head] | 1;
            long kd = keyData(head, occupiedVersion);

            // Update.
            freeHead = nextFree[head];
            values[head] = value;
            versions[head] = occupiedVersion;
            numElems += 1;
            return kd;
        }

        if (size >= 0xFFFFFFFFL) { // u32::MAX — unreachable in practice
            throw new IllegalStateException("SlotMap is full"); // JAFFY(GAP-010)
        }

        int version = 1;
        int idx = size;
        long kd = keyData(idx, version);

        ensureSlotRoom();
        values[idx] = value;
        versions[idx] = version;
        size += 1;

        freeHead = idx + 1;
        numElems += 1;
        return kd;
    }

    /// `remove_from_slot` — safe iff the slot is occupied.
    private void removeFromSlot(int idx) {
        values[idx] = 0L;

        // Maintain freelist.
        nextFree[idx] = freeHead;
        freeHead = idx;
        numElems -= 1;
        versions[idx] = versions[idx] + 1; // wrapping_add(1) — int wraps like u32
    }

    /// `remove` — clears the slot and returns whether it was occupied
    /// (`Option<V>::is_some()`; the removed value itself has no call site).
    public boolean remove(long key) {
        if (!containsKey(key)) {
            return false;
        }
        removeFromSlot(keyIdx(key));
        return true;
    }

    /// `clear` = `drain` to completion, ascending index order.
    public void clear() {
        for (int cur = 1; cur < size; cur++) {
            if (versions[cur] % 2 > 0) {
                removeFromSlot(cur);
            }
        }
    }

    /// `get` — returns the stored value, or `0L` (`None`) on a miss.
    /// A stored `Option<NodeId>::None` is also `0L`; callers that must
    /// distinguish a miss check `containsKey` first.
    public @Opt long get(long key) {
        int idx = keyIdx(key);
        if (Integer.compareUnsigned(idx, size) < 0 && versions[idx] == keyVersion(key)) {
            return values[idx];
        }
        return 0L;
    }

    /// `IndexMut<K>` whole-slot write — `self[key] = value`. Needed because
    /// `long` values cannot be mutated through a returned reference.
    /// Panics on invalid key.
    public void set(long key, long value) {
        int idx = keyIdx(key);
        if (Integer.compareUnsigned(idx, size) >= 0 || versions[idx] != keyVersion(key)) {
            throw new IllegalStateException("invalid SlotMap key used"); // JAFFY(GAP-010)
        }
        values[idx] = value;
    }
}
