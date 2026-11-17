// JAFFY-UPSTREAM: none
// JAFFY-NOTE: unit tests for LongSlotMap — primitive-long specialization
// SlotMap<Option<NodeId>>. Key semantics are identical to SlotMapTest's
// measured sequence (GAP-015); value 0L is the `None` sentinel (GAP-001).
package net.morphismmc.jaffy.infra.slotmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LongSlotMapTest {

    @Test
    void keyReuseSequence() {
        // Same rustc-1.96 + slotmap-1.1.1 measured table as SlotMapTest.
        LongSlotMap sm = LongSlotMap.withCapacity(0);
        long a = sm.insert(10);
        long b = sm.insert(20);
        long c = sm.insert(30);
        assertEquals(4294967297L, a);
        assertEquals(4294967298L, b);
        assertEquals(4294967299L, c);

        sm.remove(a);
        sm.remove(c);
        long d = sm.insert(40); // reuses idx 3 (LIFO), v3
        long e = sm.insert(50); // reuses idx 1, v3
        long f = sm.insert(60); // new idx 4, v1
        assertEquals(12884901891L, d);
        assertEquals(12884901889L, e);
        assertEquals(4294967300L, f);
        assertEquals(40, sm.get(d));
        assertEquals(50, sm.get(e));
        assertEquals(60, sm.get(f));

        sm.clear();
        long g = sm.insert(70); // reuses idx 4 (drain freed descending free list), v3
        long h = sm.insert(80); // reuses idx 3, v5
        assertEquals(12884901892L, g);
        assertEquals(21474836483L, h);
    }

    @Test
    void staleKeysAreRejected() {
        LongSlotMap sm = new LongSlotMap();
        long a = sm.insert(1);
        assertTrue(sm.containsKey(a));
        assertEquals(1, sm.get(a));

        sm.remove(a);
        assertFalse(sm.containsKey(a));
        assertEquals(0L, sm.get(a));
        assertFalse(sm.remove(a));

        long b = sm.insert(2); // reuses idx with bumped version
        assertFalse(sm.containsKey(a));
        assertTrue(sm.containsKey(b));
    }

    @Test
    void nullKeyIsNotContained() {
        // NodeId with idx == u32::MAX is the null key.
        LongSlotMap sm = new LongSlotMap();
        long nullKey = 0xFFFFFFFFL; // version 0 (even), idx u32::MAX
        assertFalse(sm.containsKey(nullKey));
        assertEquals(0L, sm.get(nullKey));
        assertFalse(sm.remove(nullKey));
    }

    @Test
    void noneValueAndMissShareZero() {
        // Storing OptI64.NONE (0L) reads back as 0, same as a miss —
        // `containsKey` is the only way to tell them apart (matches
        // `Option<Option<NodeId>>` collapsing to `Option<NodeId>` upstream:
        // the parents map never needs to distinguish them).
        LongSlotMap sm = new LongSlotMap();
        long k = sm.insert(0L);
        assertTrue(sm.containsKey(k));
        assertEquals(0L, sm.get(k));

        sm.remove(k);
        assertFalse(sm.containsKey(k));
        assertEquals(0L, sm.get(k));
    }

    @Test
    void setWritesWholeSlot() {
        LongSlotMap sm = new LongSlotMap();
        long k = sm.insert(0L);
        sm.set(k, 42);
        assertEquals(42, sm.get(k));

        sm.remove(k);
        // set on a stale/invalid key panics like `Index` (GAP-010)
        try {
            sm.set(k, 1);
            throw new AssertionError("set on invalid key must throw");
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
