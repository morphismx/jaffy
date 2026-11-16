// JAFFY: port of upstream src/style/alignment.rs `mod tests` (behavioral
// assertions; the `size_of` layout-budget test is Rust-specific — skipped).
package net.morphismmc.jaffy.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;

class AlignmentTest {

    @Test
    void alignItemsIsSafe() {
        assertTrue(AlignItems.SAFE_START.isSafe());
        assertTrue(AlignItems.SAFE_END.isSafe());
        assertTrue(AlignItems.SAFE_FLEX_START.isSafe());
        assertTrue(AlignItems.SAFE_FLEX_END.isSafe());
        assertTrue(AlignItems.SAFE_CENTER.isSafe());
        assertFalse(AlignItems.START.isSafe());
        assertFalse(AlignItems.END.isSafe());
        assertFalse(AlignItems.FLEX_START.isSafe());
        assertFalse(AlignItems.FLEX_END.isSafe());
        assertFalse(AlignItems.CENTER.isSafe());
        assertFalse(AlignItems.BASELINE.isSafe());
        assertFalse(AlignItems.STRETCH.isSafe());
        assertTrue(AlignItems.SAFE_SELF_START.isSafe());
        assertTrue(AlignItems.SAFE_SELF_END.isSafe());
        assertFalse(AlignItems.SELF_START.isSafe());
        assertFalse(AlignItems.SELF_END.isSafe());
        assertFalse(AlignItems.NORMAL.isSafe());
    }

    @Test
    void alignItemsKeywordStripsSafe() {
        assertEquals(AlignItemsKeyword.START, AlignItems.SAFE_START.keyword());
        assertEquals(AlignItemsKeyword.END, AlignItems.SAFE_END.keyword());
        assertEquals(AlignItemsKeyword.FLEX_START, AlignItems.SAFE_FLEX_START.keyword());
        assertEquals(AlignItemsKeyword.FLEX_END, AlignItems.SAFE_FLEX_END.keyword());
        assertEquals(AlignItemsKeyword.CENTER, AlignItems.SAFE_CENTER.keyword());
        assertEquals(AlignItemsKeyword.SELF_START, AlignItems.SAFE_SELF_START.keyword());
        assertEquals(AlignItemsKeyword.SELF_END, AlignItems.SAFE_SELF_END.keyword());
    }

    @Test
    void alignItemsKeywordPassthrough() {
        assertEquals(AlignItemsKeyword.START, AlignItems.START.keyword());
        assertEquals(AlignItemsKeyword.STRETCH, AlignItems.STRETCH.keyword());
        assertEquals(AlignItemsKeyword.BASELINE, AlignItems.BASELINE.keyword());
        assertEquals(AlignItemsKeyword.FLEX_START, AlignItems.FLEX_START.keyword());
        assertEquals(AlignItemsKeyword.NORMAL, AlignItems.NORMAL.keyword());
    }

    @Test
    void alignItemsDefaultIsNormal() {
        assertEquals(AlignItems.NORMAL, AlignItems.DEFAULT);
        assertEquals(AlignItemsKeyword.NORMAL, AlignItemsKeyword.DEFAULT);
        assertEquals(AlignmentSafety.DEFAULT, AlignmentSafety.DEFAULT);
    }

    @Test
    void resolveSelfRelativeInlineAxis() {
        // Same direction: self-start == start, self-end == end
        assertEquals(AlignItems.START, AlignItems.SELF_START.resolveSelfRelative(Direction.LTR, Direction.LTR, true));
        assertEquals(AlignItems.END, AlignItems.SELF_END.resolveSelfRelative(Direction.LTR, Direction.LTR, true));
        assertEquals(AlignItems.START, AlignItems.SELF_START.resolveSelfRelative(Direction.RTL, Direction.RTL, true));
        assertEquals(AlignItems.END, AlignItems.SELF_END.resolveSelfRelative(Direction.RTL, Direction.RTL, true));
        // Opposite direction: self-start == end, self-end == start
        assertEquals(AlignItems.END, AlignItems.SELF_START.resolveSelfRelative(Direction.LTR, Direction.RTL, true));
        assertEquals(AlignItems.START, AlignItems.SELF_END.resolveSelfRelative(Direction.LTR, Direction.RTL, true));
        assertEquals(AlignItems.END, AlignItems.SELF_START.resolveSelfRelative(Direction.RTL, Direction.LTR, true));
        assertEquals(AlignItems.START, AlignItems.SELF_END.resolveSelfRelative(Direction.RTL, Direction.LTR, true));
        // Safety modifier is preserved
        assertEquals(AlignItems.SAFE_END, AlignItems.SAFE_SELF_START.resolveSelfRelative(Direction.LTR, Direction.RTL, true));
        // Other keywords are unchanged
        assertEquals(AlignItems.START, AlignItems.START.resolveSelfRelative(Direction.LTR, Direction.RTL, true));
        assertEquals(AlignItems.FLEX_END, AlignItems.FLEX_END.resolveSelfRelative(Direction.LTR, Direction.RTL, true));
        assertEquals(AlignItems.NORMAL, AlignItems.NORMAL.resolveSelfRelative(Direction.LTR, Direction.RTL, true));
    }

    @Test
    void resolveSelfRelativeBlockAxis() {
        // In the block axis (horizontal-tb only) direction never flips
        assertEquals(AlignItems.START, AlignItems.SELF_START.resolveSelfRelative(Direction.LTR, Direction.RTL, false));
        assertEquals(AlignItems.END, AlignItems.SELF_END.resolveSelfRelative(Direction.RTL, Direction.LTR, false));
    }

    @Test
    void alignContentDefaultIsNormal() {
        assertEquals(AlignContent.NORMAL, AlignContent.DEFAULT);
    }

    @Test
    void alignContentIsSafe() {
        assertTrue(AlignContent.SAFE_START.isSafe());
        assertTrue(AlignContent.SAFE_CENTER.isSafe());
        assertFalse(AlignContent.SPACE_BETWEEN.isSafe());
        assertFalse(AlignContent.STRETCH.isSafe());
        assertFalse(AlignContent.NORMAL.isSafe());
    }

    @Test
    void alignContentKeywordStripsSafe() {
        assertEquals(AlignContentKeyword.START, AlignContent.SAFE_START.keyword());
        assertEquals(AlignContentKeyword.FLEX_END, AlignContent.SAFE_FLEX_END.keyword());
        assertEquals(AlignContentKeyword.CENTER, AlignContent.SAFE_CENTER.keyword());
        assertEquals(AlignContentKeyword.SPACE_BETWEEN, AlignContent.SPACE_BETWEEN.keyword());
        assertEquals(AlignContentKeyword.NORMAL, AlignContent.NORMAL.keyword());
    }

    @Test
    void alignContentKeywordReversedSwapsStartEnd() {
        assertEquals(AlignContentKeyword.END, AlignContentKeyword.START.reversed());
        assertEquals(AlignContentKeyword.START, AlignContentKeyword.END.reversed());
        assertEquals(AlignContentKeyword.FLEX_END, AlignContentKeyword.FLEX_START.reversed());
        assertEquals(AlignContentKeyword.FLEX_START, AlignContentKeyword.FLEX_END.reversed());
        // Stretch reverses to End — preserves pre-refactor behaviour.
        assertEquals(AlignContentKeyword.END, AlignContentKeyword.STRETCH.reversed());
        assertEquals(AlignContentKeyword.NORMAL, AlignContentKeyword.NORMAL.reversed());
        assertEquals(AlignContentKeyword.CENTER, AlignContentKeyword.CENTER.reversed());
        assertEquals(AlignContentKeyword.SPACE_BETWEEN, AlignContentKeyword.SPACE_BETWEEN.reversed());
        assertEquals(AlignContentKeyword.SPACE_EVENLY, AlignContentKeyword.SPACE_EVENLY.reversed());
        assertEquals(AlignContentKeyword.SPACE_AROUND, AlignContentKeyword.SPACE_AROUND.reversed());
    }
}
