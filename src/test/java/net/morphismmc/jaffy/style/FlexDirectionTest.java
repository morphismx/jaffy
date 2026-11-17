// JAFFY: port of upstream src/style/flex.rs `mod tests::test_flex_direction`.
package net.morphismmc.jaffy.style;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;

import org.junit.jupiter.api.Test;

class FlexDirectionTest {

    @Test
    void flexDirectionIsRow() {
        assertTrue(FlexDirection.ROW.isRow());
        assertTrue(FlexDirection.ROW_REVERSE.isRow());
        assertFalse(FlexDirection.COLUMN.isRow());
        assertFalse(FlexDirection.COLUMN_REVERSE.isRow());
    }

    @Test
    void flexDirectionIsColumn() {
        assertFalse(FlexDirection.ROW.isColumn());
        assertFalse(FlexDirection.ROW_REVERSE.isColumn());
        assertTrue(FlexDirection.COLUMN.isColumn());
        assertTrue(FlexDirection.COLUMN_REVERSE.isColumn());
    }

    @Test
    void flexDirectionIsReverse() {
        assertFalse(FlexDirection.ROW.isReverse());
        assertTrue(FlexDirection.ROW_REVERSE.isReverse());
        assertFalse(FlexDirection.COLUMN.isReverse());
        assertTrue(FlexDirection.COLUMN_REVERSE.isReverse());
    }

    @Test
    void mainAxisIsRequestedAxis() {
        assertTrue(FlexDirection.ROW.mainAxisIsRequestedAxis(RequestedAxis.HORIZONTAL));
        assertFalse(FlexDirection.ROW.mainAxisIsRequestedAxis(RequestedAxis.VERTICAL));
        assertFalse(FlexDirection.ROW.mainAxisIsRequestedAxis(RequestedAxis.BOTH));
        assertTrue(FlexDirection.COLUMN.mainAxisIsRequestedAxis(RequestedAxis.VERTICAL));
        assertFalse(FlexDirection.COLUMN.mainAxisIsRequestedAxis(RequestedAxis.HORIZONTAL));
    }

    @Test
    void absoluteAxes() {
        assertTrue(FlexDirection.ROW.mainAxis() == AbsoluteAxis.HORIZONTAL);
        assertTrue(FlexDirection.ROW_REVERSE.mainAxis() == AbsoluteAxis.HORIZONTAL);
        assertTrue(FlexDirection.COLUMN.mainAxis() == AbsoluteAxis.VERTICAL);
        assertTrue(FlexDirection.COLUMN_REVERSE.mainAxis() == AbsoluteAxis.VERTICAL);
        assertTrue(FlexDirection.ROW.crossAxis() == AbsoluteAxis.VERTICAL);
        assertTrue(FlexDirection.COLUMN.crossAxis() == AbsoluteAxis.HORIZONTAL);
    }

    @Test
    void flexWrap() {
        assertFalse(FlexWrap.NO_WRAP.isMultiLine());
        assertTrue(FlexWrap.WRAP.isMultiLine());
        assertTrue(FlexWrap.WRAP_REVERSE.isMultiLine());
        assertFalse(FlexWrap.NO_WRAP.isReverse());
        assertFalse(FlexWrap.WRAP.isReverse());
        assertTrue(FlexWrap.WRAP_REVERSE.isReverse());
        assertTrue(FlexWrap.BALANCE.isBalance());
        assertTrue(FlexWrap.BALANCE_REVERSE.isBalance());
        assertTrue(FlexWrap.BALANCE_REVERSE.isReverse());
        assertFalse(FlexWrap.WRAP.isBalance());
    }
}
