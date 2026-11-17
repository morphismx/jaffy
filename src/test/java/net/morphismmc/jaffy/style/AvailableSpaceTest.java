// JAFFY: upstream=none
// JAFFY-NOTE: behavior checks for available_space.rs ports
// Float, FloatDirection, Clear).
package net.morphismmc.jaffy.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.float_.Float;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class AvailableSpaceTest {

    @Test
    void definiteBasics() {
        AvailableSpace d = new AvailableSpace.Definite(50.0f);
        assertTrue(d.isDefinite());
        assertEquals(50.0f, d.intoOption());
        assertEquals(50.0f, d.unwrap());
        assertEquals(50.0f, d.unwrapOr(1.0f));
        assertEquals(50.0f, d.unwrapOrElse(() -> 1.0f));
        assertTrue(d.or(new AvailableSpace.MinContent()).rustEq(d));
        assertEquals(30.0f, d.computeFreeSpace(20.0f));
    }

    @Test
    void constraintBasics() {
        assertFalse(AvailableSpace.MIN_CONTENT.isDefinite());
        assertTrue(OptF32.isNone(AvailableSpace.MIN_CONTENT.intoOption()));
        assertThrows(IllegalStateException.class, AvailableSpace.MAX_CONTENT::unwrap);
        assertEquals(7.0f, AvailableSpace.MAX_CONTENT.unwrapOr(7.0f));
        assertEquals(9.0f, AvailableSpace.MIN_CONTENT.unwrapOrElse(() -> 9.0f));
        assertEquals(java.lang.Float.POSITIVE_INFINITY, AvailableSpace.MAX_CONTENT.computeFreeSpace(20.0f));
        assertEquals(0.0f, AvailableSpace.MIN_CONTENT.computeFreeSpace(20.0f));
        assertTrue(AvailableSpace.MIN_CONTENT.or(new AvailableSpace.Definite(3.0f)) instanceof AvailableSpace.Definite);
    }

    @Test
    void maybeSetAndMap() {
        AvailableSpace m = AvailableSpace.MIN_CONTENT;
        assertTrue(m.maybeSet(OptF32.NONE).rustEq(m));
        assertTrue(m.maybeSet(4.0f).rustEq(new AvailableSpace.Definite(4.0f)));
        assertTrue(m.mapDefiniteValue(v -> v * 2).rustEq(m));
        assertTrue(new AvailableSpace.Definite(4.0f)
                .mapDefiniteValue(v -> v * 2)
                .rustEq(new AvailableSpace.Definite(8.0f)));
    }

    @Test
    void roughlyEqual() {
        assertTrue(new AvailableSpace.Definite(1.0f).isRoughlyEqual(new AvailableSpace.Definite(1.0f + 1e-8f)));
        assertFalse(new AvailableSpace.Definite(1.0f).isRoughlyEqual(new AvailableSpace.Definite(2.0f)));
        assertTrue(AvailableSpace.MIN_CONTENT.isRoughlyEqual(AvailableSpace.MIN_CONTENT));
        assertTrue(AvailableSpace.MAX_CONTENT.isRoughlyEqual(AvailableSpace.MAX_CONTENT));
        assertFalse(AvailableSpace.MIN_CONTENT.isRoughlyEqual(AvailableSpace.MAX_CONTENT));
    }

    @Test
    void fromImpls() {
        assertTrue(AvailableSpace.fromF(3.0f).rustEq(new AvailableSpace.Definite(3.0f)));
        assertTrue(AvailableSpace.fromO(3.0f).rustEq(new AvailableSpace.Definite(3.0f)));
        assertTrue(AvailableSpace.fromO(OptF32.NONE) instanceof AvailableSpace.MaxContent);
        assertTrue(AvailableSpace.fromLength(4.0).rustEq(new AvailableSpace.Definite(4.0f)));
    }

    @Test
    void floatEnums() {
        assertTrue(Float.LEFT.isFloated());
        assertTrue(Float.RIGHT.isFloated());
        assertFalse(Float.NONE.isFloated());
        assertEquals(FloatDirection.LEFT, Float.LEFT.floatDirection());
        assertEquals(FloatDirection.RIGHT, Float.RIGHT.floatDirection());
        assertEquals(null, Float.NONE.floatDirection());
        assertEquals(FloatDirection.RIGHT, FloatDirection.LEFT.opposite());
        assertEquals(FloatDirection.LEFT, FloatDirection.RIGHT.opposite());
    }
}
