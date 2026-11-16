// JAFFY: tests for the upstream src/style/mod.rs port (Style + traits).
// Upstream has no non-parse test module for this file; these assert the
// documented DEFAULT field values and the overridden trait methods.
package net.morphismmc.jaffy.style;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.just.math.OptF32;
import org.junit.jupiter.api.Test;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.block.BlockItemStyle;
import net.morphismmc.jaffy.style.block.TextAlign;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.Float;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateArea;
import net.morphismmc.jaffy.style.grid.GridTemplateAreas;
import net.morphismmc.jaffy.style.grid.GridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.LineGridPlacementExt;

class StyleTest {

    @Test
    void styleDefaultsMatchUpstream() {
        Style s = Style.DEFAULT;
        assertSame(s, Style.defaultStyle());
        assertEquals(Display.FLEX, s.display());
        assertFalse(s.itemIsTable());
        assertFalse(s.itemIsReplaced());
        assertFalse(s.itemIsCompressibleReplaced());
        assertEquals(BoxSizing.BORDER_BOX, s.boxSizing());
        assertEquals(Direction.LTR, s.direction());
        assertEquals(Overflow.VISIBLE, s.overflow().x());
        assertEquals(Overflow.VISIBLE, s.overflow().y());
        assertEquals(0.0f, s.scrollbarWidth());
        assertEquals(Contain.NONE, s.contain());
        assertEquals(Float.NONE, s.floatValue());
        assertEquals(Clear.NONE, s.clear());
        assertEquals(Position.STATIC, s.position());
        assertEquals(OptF32.NONE, s.aspectRatio());
        assertEquals(AlignItems.NORMAL, s.alignItems());
        assertNull(s.alignSelf());
        assertEquals(AlignItems.NORMAL, s.justifyItems());
        assertNull(s.justifySelf());
        assertEquals(AlignContent.NORMAL, s.alignContent());
        assertEquals(AlignContent.NORMAL, s.justifyContent());
        assertEquals(TextAlign.AUTO, s.textAlign());
        assertEquals(FlexDirection.ROW, s.flexDirection());
        assertEquals(FlexWrap.NO_WRAP, s.flexWrap());
        assertEquals(1, s.flexLineCount());
        assertTrue(s.flexBasis().isAuto());
        assertEquals(0.0f, s.flexGrow());
        assertEquals(1.0f, s.flexShrink());
        assertTrue(s.gridTemplateRows().isEmpty());
        assertTrue(s.gridTemplateColumns().isEmpty());
        assertTrue(s.gridAutoRows().isEmpty());
        assertTrue(s.gridAutoColumns().isEmpty());
        assertEquals(GridAutoFlow.ROW, s.gridAutoFlow());
        assertNull(s.gridTemplateAreas());
        assertTrue(s.gridTemplateColumnNames().isEmpty());
        assertTrue(s.gridTemplateRowNames().isEmpty());
        assertInstanceOf(GridPlacement.Auto.class, s.gridRow().start());
    }

    @Test
    void coreStyleOverrides() {
        assertEquals(BoxGenerationMode.NORMAL, Style.DEFAULT.boxGenerationMode());
        assertFalse(Style.DEFAULT.isBlock());
        // display: none -> BoxGenerationMode::None
        var none = new Style(
                Display.NONE, false, false, false, BoxSizing.BORDER_BOX, Direction.LTR,
                Style.DEFAULT.overflow(), 0.0f, Contain.NONE, Float.NONE, Clear.NONE, Position.STATIC, Style.DEFAULT.inset(),
                Style.DEFAULT.size(), Style.DEFAULT.minSize(), Style.DEFAULT.maxSize(), OptF32.NONE,
                Style.DEFAULT.margin(), Style.DEFAULT.padding(), Style.DEFAULT.border(),
                AlignItems.NORMAL, null, AlignItems.NORMAL, null, AlignContent.NORMAL,
                AlignContent.NORMAL, Style.DEFAULT.gap(), TextAlign.AUTO, FlexDirection.ROW,
                FlexWrap.NO_WRAP, 1, Dimension.AUTO, 0.0f, 1.0f, List.of(), List.of(), List.of(),
                List.of(), GridAutoFlow.ROW, null, List.of(), List.of(), LineGridPlacementExt.DEFAULT,
                LineGridPlacementExt.DEFAULT);
        assertEquals(BoxGenerationMode.NONE, none.boxGenerationMode());

        // is_block only for Display::Block (not FlowRoot)
        var block = none.withDisplay(Display.BLOCK);
        assertTrue(block.isBlock());
        assertFalse(none.withDisplay(Display.FLOW_ROOT).isBlock());

        // is_containing_block default: absolute iff positioned, never fixed
        assertFalse(Style.DEFAULT.isContainingBlock().absolute());
        var positioned = block.withPosition(Position.ABSOLUTE);
        assertTrue(positioned.isContainingBlock().absolute());
        assertFalse(positioned.isContainingBlock().fixed());
        assertTrue(positioned.isContainingBlock().forPosition(Position.ABSOLUTE));
        assertFalse(positioned.isContainingBlock().forPosition(Position.FIXED));
    }

    @Test
    void gridContainerStyleOverrides() {
        // Stored counts win over the derived-max default
        var areas = new GridTemplateAreas(
                List.of(new GridTemplateArea("a", 0, 2, 0, 3)),
                7,
                9);
        var s = Style.DEFAULT.withGridTemplateAreas(areas);
        assertSame(areas, s.gridTemplateAreas());
        assertEquals(7, s.gridTemplateAreaRowCount());
        assertEquals(9, s.gridTemplateAreaColumnCount());

        // grid_template_tracks axis dispatch
        var s2 = Style.DEFAULT.withGridTemplateColumns(
                        List.of(GridTemplateComponent.fromLength(10.0)))
                .withGridTemplateRows(List.of(GridTemplateComponent.fromLength(20.0)));
        assertEquals(1, s2.gridTemplateTracks(AbsoluteAxis.HORIZONTAL).size());
        var row = (GenericGridTemplateComponent.Single<GridTemplateRepetition>)
                s2.gridTemplateTracks(AbsoluteAxis.VERTICAL).get(0);
        assertEquals(20.0f, row.size().min().definiteValue(OptF32.NONE, null));
        assertEquals(20.0f, row.size().max().definiteValue(OptF32.NONE, null));

        // grid_align_content: normal -> stretch, other keywords pass through
        assertEquals(AlignContent.STRETCH, s2.gridAlignContent(AbstractAxis.BLOCK));
        var centered = s2.withAlignContent(AlignContent.CENTER);
        assertEquals(AlignContent.CENTER, centered.gridAlignContent(AbstractAxis.BLOCK));
    }

    @Test
    void gridItemStyleDefaults() {
        var s = Style.DEFAULT.withGridColumn(
                LineGridPlacementExt.fromLineIndex(2));
        assertSame(s.gridRow(), LineGridPlacementExt.DEFAULT);
        assertEquals(2, ((GridPlacement.Line) s.gridPlacement(AbsoluteAxis.HORIZONTAL).start()).line().asI16());
    }

    @Test
    void oofAndBlockItemStyle() {
        var s = Style.DEFAULT;
        assertFalse(s.isTable());
        var table = s.withItemIsTable(true);
        assertTrue(table.isTable());
        // OofItemStyle::is_table and BlockItemStyle::is_table share one impl
        OofItemStyle oof = table;
        BlockItemStyle item = table;
        assertTrue(oof.isTable());
        assertTrue(item.isTable());
        // is_compressible_replaced includes item_is_replaced
        assertTrue(s.withItemIsReplaced(true).isCompressibleReplaced());
        assertTrue(s.withItemIsCompressibleReplaced(true).isCompressibleReplaced());
    }
}
