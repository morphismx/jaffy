// JAFFY: upstream/src/util/front_back_vec_builder.rs #[cfg(test)] mod tests
// -> nested class Tests (snake_case -> camelCase)
package net.morphismmc.jaffy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FrontBackVecBuilderTest {

    @Nested
    class Tests {

        @Test
        void frontItemsKeepTheirOrderAndBackItemsAreReversed() {
            var builder = FrontBackVecBuilder.<Integer>withCapacity(6);
            for (int value = 0; value < 6; value++) {
                if (value % 2 == 0) {
                    builder.pushFront(value);
                } else {
                    builder.pushBack(value);
                }
            }
            var result = builder.intoVec();
            assertEquals(List.of(0, 2, 4, 5, 3, 1), result.vec());
            assertEquals(3, result.frontLen());
        }

        @Test
        void allItemsPushedToOneEnd() {
            var builder = FrontBackVecBuilder.<Integer>withCapacity(3);
            for (int value = 0; value < 3; value++) {
                builder.pushFront(value);
            }
            var result = builder.intoVec();
            assertEquals(List.of(0, 1, 2), result.vec());
            assertEquals(3, result.frontLen());

            var builder2 = FrontBackVecBuilder.<Integer>withCapacity(3);
            for (int value = 0; value < 3; value++) {
                builder2.pushBack(value);
            }
            var result2 = builder2.intoVec();
            assertEquals(List.of(2, 1, 0), result2.vec());
            assertEquals(0, result2.frontLen());
        }

        @Test
        void zeroCapacity() {
            var result = FrontBackVecBuilder.<Integer>withCapacity(0).intoVec();
            assertTrue(result.vec().isEmpty());
            assertEquals(0, result.frontLen());
        }

        @Test
        void ownedItemsAreMovedIntoTheVec() {
            var builder = FrontBackVecBuilder.<List<Integer>>withCapacity(2);
            builder.pushBack(List.of(1, 2));
            builder.pushFront(List.of(3));
            var result = builder.intoVec();
            assertEquals(List.of(List.of(3), List.of(1, 2)), result.vec());
            assertEquals(1, result.frontLen());
        }

        @Test
        void pushingTooManyItemsToTheFrontPanicsInIntoVec() {
            var builder = FrontBackVecBuilder.<Integer>withCapacity(1);
            builder.pushBack(1);
            builder.pushFront(2);
            var e = assertThrows(AssertionError.class, builder::intoVec);
            assertTrue(e.getMessage().contains("must equal its capacity"));
        }

        @Test
        void pushingTooManyItemsToTheBackPanicsInIntoVec() {
            var builder = FrontBackVecBuilder.<Integer>withCapacity(1);
            builder.pushFront(1);
            builder.pushBack(2);
            var e = assertThrows(AssertionError.class, builder::intoVec);
            assertTrue(e.getMessage().contains("must equal its capacity"));
        }

        @Test
        void pushFrontOutOfBoundsPanics() {
            var builder = FrontBackVecBuilder.<Integer>withCapacity(0);
            assertThrows(IndexOutOfBoundsException.class, () -> builder.pushFront(1));
        }

        @Test
        void pushBackOutOfBoundsPanics() {
            var builder = FrontBackVecBuilder.<Integer>withCapacity(1);
            builder.pushBack(1);
            assertThrows(IndexOutOfBoundsException.class, () -> builder.pushBack(2));
        }

        @Test
        void intoVecBeforeFullPanics() {
            var builder = FrontBackVecBuilder.<Integer>withCapacity(2);
            builder.pushFront(1);
            var e = assertThrows(AssertionError.class, builder::intoVec);
            assertTrue(e.getMessage().contains("must equal its capacity"));
        }
    }
}
