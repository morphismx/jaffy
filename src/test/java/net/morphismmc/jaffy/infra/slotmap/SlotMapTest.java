// JAFFY: upstream=none
// JAFFY-NOTE: unit tests for SlotMap/SparseSecondaryMap (GAP-015). Expected key
// values are the rustc-1.96 + slotmap-1.1.1 measured sequence from GAPS.md.
package net.morphismmc.jaffy.infra.slotmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlotMapTest {

    @Test
    void keyReuseSequence() {
        // Measured table from GAP-015 (rustc 1.96, slotmap 1.1.1).
        SlotMap<Integer> sm = SlotMap.withCapacity(0);
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
        assertEquals(4, sm.len());

        sm.clear();
        assertEquals(0, sm.len());
        assertTrue(sm.isEmpty());
        long g = sm.insert(70); // reuses idx 4 (drain freed descending free list), v3
        long h = sm.insert(80); // reuses idx 3, v5
        assertEquals(12884901892L, g);
        assertEquals(21474836483L, h);
    }

    @Test
    void staleKeysAreRejected() {
        SlotMap<Integer> sm = new SlotMap<>();
        long a = sm.insert(1);
        assertTrue(sm.containsKey(a));
        assertEquals(1, sm.get(a));
        assertEquals(1, sm.index(a));

        sm.remove(a);
        assertFalse(sm.containsKey(a));
        assertNull(sm.get(a));
        assertNull(sm.remove(a));
        assertThrows(IllegalStateException.class, () -> sm.index(a));

        long b = sm.insert(2); // reuses idx with bumped version
        assertFalse(sm.containsKey(a));
        assertTrue(sm.containsKey(b));
    }

    @Test
    void nullKeyIsNotContained() {
        // NodeId with idx == u32::MAX is the null key.
        SlotMap<Integer> sm = new SlotMap<>();
        long nullKey = 0xFFFFFFFFL; // version 0 (even), idx u32::MAX
        assertFalse(sm.containsKey(nullKey));
        assertNull(sm.get(nullKey));
        assertNull(sm.remove(nullKey));
    }

    @Test
    void sparseInsertVersionRules() {
        SparseSecondaryMap<Integer> sec = new SparseSecondaryMap<>();
        long k1v1 = (1L << 32) | 1; // idx 1, v1
        long k1v3 = (3L << 32) | 1; // idx 1, v3 (newer)
        long k1v2 = (2L << 32) | 1; // idx 1, v2 — even, invalid key data

        // null key silently fails
        assertNull(sec.insert(0xFFFFFFFFL, 9));
        assertEquals(0, sec.len());

        assertNull(sec.insert(k1v1, 10));
        assertEquals(10, sec.insert(k1v1, 11)); // same version replaces
        assertEquals(11, sec.get(k1v1));

        // newer version overwrites and returns null
        assertNull(sec.insert(k1v3, 30));
        assertNull(sec.get(k1v1));
        assertEquals(30, sec.get(k1v3));

        // older version is rejected silently
        assertNull(sec.insert(k1v1, 99));
        assertEquals(30, sec.get(k1v3));

        // even versions never match (keys are always odd)
        assertNull(sec.get(k1v2));

        assertEquals(30, sec.remove(k1v3));
        assertNull(sec.remove(k1v3));
    }

    @Test
    void sparseGetDisjointMut() {
        SparseSecondaryMap<String> sec = new SparseSecondaryMap<>();
        long ka = (1L << 32) | 1; // v1, idx 1
        long kb = (1L << 32) | 2; // v1, idx 2
        long kc = (1L << 32) | 3; // v1, idx 3
        sec.insert(ka, "a");
        sec.insert(kb, "b");
        sec.insert(kc, "c");

        // invalid key fails the whole call
        assertNull(sec.getDisjointMut(String[]::new, ka, kb, 0xDEADBEEFL));
        // duplicate key is not disjoint
        assertNull(sec.getDisjointMut(String[]::new, ka, ka));
        // versions restored even after failure
        String[] out = sec.getDisjointMut(String[]::new, ka, kc);
        assertEquals("a", out[0]);
        assertEquals("c", out[1]);
        assertEquals("a", sec.get(ka));
        assertEquals("c", sec.get(kc));
    }
}
