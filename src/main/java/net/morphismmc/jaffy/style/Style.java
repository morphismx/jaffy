// JAFFY: port of upstream src/style/mod.rs — `Style<S>` struct and its
// CoreStyle/OofItemStyle/Block{Container,Item}Style/Flexbox{Container,Item}
// Style/Grid{Container,Item}Style impls.
//
// `S: CheapCloneStr` -> `String` (GAP-017); the `dummy: PhantomData<S>` field
// has no Java counterpart. `float`/`clear` fields are `float_layout`-gated
// upstream; `float_layout` is in the default feature set (Features.FLOAT_LAYOUT)
// so they are always present. `float` is a Java keyword -> `floatValue`
// (GAP-017). `Vec` -> `List`; `Option<T>` on reference fields is a nullable
// reference (GAP-001). serde/parse impls are deferred with the features.
//
// Field accessors double as the trait getters (same upstream names); the few
// computed overrides are explicit methods below.
package net.morphismmc.jaffy.style;

import java.util.List;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import org.jspecify.annotations.Nullable;
import net.morphismmc.jaffy.geometry.SizeDimensionExt;
import net.morphismmc.jaffy.style.float_.Float;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.block.BlockContainerStyle;
import net.morphismmc.jaffy.style.block.BlockItemStyle;
import net.morphismmc.jaffy.style.block.TextAlign;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.style.flex.FlexboxContainerStyle;
import net.morphismmc.jaffy.style.flex.FlexboxItemStyle;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.GridContainerStyle;
import net.morphismmc.jaffy.style.grid.GridItemStyle;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateAreas;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.LineGridPlacementExt;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;

/// A typed representation of the CSS style information for a single node.
///
/// The most important idea in flexbox is the notion of a "main" and "cross" axis, which are always perpendicular to each other.
/// The orientation of these axes are controlled via the `flexDirection` field of this struct.
///
/// This struct follows the [CSS equivalent](https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Flexible_Box_Layout/Basic_Concepts_of_Flexbox) directly;
/// information about the behavior on the web should transfer directly.
public record Style(
        /// What layout strategy should be used?
        Display display,
        /// Whether a child is display:table or not. This affects children of block layouts.
        /// This should really be part of `Display`, but it is currently seperate because table layout isn't implemented
        boolean itemIsTable,
        /// Is it a replaced element like an image or video? Implies `itemIsCompressibleReplaced`.
        /// <https://html.spec.whatwg.org/multipage/rendering.html#replaced-elements>
        boolean itemIsReplaced,
        /// Is it a compressible replaced element that is not a replaced element (a form control with
        /// an intrinsic size)? <https://drafts.csswg.org/css-sizing-3/#compressible>
        boolean itemIsCompressibleReplaced,
        /// Should size styles apply to the content box or the border box of the node
        BoxSizing boxSizing,
        /// Sets the direction of text, table and grid columns, and horizontal overflow.
        Direction direction,

        // Overflow properties
        /// How children overflowing their container should affect layout
        Point<Overflow> overflow,
        /// How much space (in points) should be reserved for the scrollbars of `SCROLL` nodes.
        float scrollbarWidth,
        /// The layout-affecting parts of the CSS `contain` property
        Contain contain,

        /// Should the box be floated (`float` is a Java keyword -> `floatValue`)
        Float floatValue,
        /// Should the box clear floats
        Clear clear,

        // Position properties
        /// What should the `position` value of this struct use as a base offset?
        Position position,
        /// How should the position of this element be tweaked relative to the layout defined?
        Rect<LengthPercentageAuto> inset,

        // Size properties
        /// Sets the initial size of the item
        Size<Dimension> size,
        /// Controls the minimum size of the item
        Size<LengthPercentageAuto> minSize,
        /// Controls the maximum size of the item
        Size<LengthPercentageAuto> maxSize,
        /// Sets the preferred aspect ratio for the item
        /// The ratio is calculated as width divided by height.
        @Opt float aspectRatio,

        // Spacing Properties
        /// How large should the margin be on each side?
        Rect<LengthPercentageAuto> margin,
        /// How large should the padding be on each side?
        Rect<LengthPercentage> padding,
        /// How large should the border be on each side?
        Rect<LengthPercentage> border,

        // Alignment properties
        /// How this node's children aligned in the cross/block axis?
        AlignItems alignItems,
        /// How this node should be aligned in the cross/block axis
        /// Falls back to the parents `alignItems` if not set (`Option<AlignSelf>` -> nullable)
        @Nullable AlignItems alignSelf,
        /// How this node's children should be aligned in the inline axis
        AlignItems justifyItems,
        /// How this node should be aligned in the inline axis
        /// Falls back to the parents `justifyItems` if not set (`Option<AlignSelf>` -> nullable)
        @Nullable AlignItems justifySelf,
        /// How should content contained within this item be aligned in the cross/block axis
        AlignContent alignContent,
        /// How should content contained within this item be aligned in the main/inline axis
        AlignContent justifyContent,
        /// How large should the gaps between items in a grid or flex container be?
        Size<LengthPercentage> gap,

        // Block container properties
        /// How items elements should aligned in the inline axis
        TextAlign textAlign,

        // Flexbox container properties
        /// Which direction does the main axis flow in?
        FlexDirection flexDirection,
        /// Should elements wrap, or stay in a single line?
        FlexWrap flexWrap,
        /// The minimum number of flex lines requested for a multi-line container. (`u16` -> `int`)
        int flexLineCount,

        // Flexbox item properties
        /// Sets the initial main axis size of the item
        Dimension flexBasis,
        /// The relative rate at which this item grows when it is expanding to fill space
        float flexGrow,
        /// The relative rate at which this item shrinks when it is contracting to fit into space
        float flexShrink,

        // Grid container properies
        /// Defines the track sizing functions (heights) of the grid rows
        List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateRows,
        /// Defines the track sizing functions (widths) of the grid columns
        List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateColumns,
        /// Defines the size of implicitly created rows
        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoRows,
        /// Defined the size of implicitly created columns
        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoColumns,
        /// Controls how items get placed into the grid for auto-placed items
        GridAutoFlow gridAutoFlow,

        // Grid container named properties
        /// Defines the rectangular grid areas (nullable: `Option<GridTemplateAreas>`)
        @Nullable GridTemplateAreas gridTemplateAreas,
        /// The named lines between the columns
        List<List<String>> gridTemplateColumnNames,
        /// The named lines between the rows
        List<List<String>> gridTemplateRowNames,

        // Grid child properties
        /// Defines which row in the grid the item should start and end at
        Line<GridPlacement> gridRow,
        /// Defines which column in the grid the item should start and end at
        Line<GridPlacement> gridColumn)
        implements CoreStyle,
                OofItemStyle,
                BlockContainerStyle,
                BlockItemStyle,
                FlexboxContainerStyle,
                FlexboxItemStyle,
                GridContainerStyle,
                GridItemStyle {

    /// The `Default` layout, in a form that can be used in const functions
    /// (`Style::DEFAULT` = `Default::default()`).
    public static final Style DEFAULT = new Style(
            Display.DEFAULT, // display
            false, // itemIsTable
            false, // itemIsReplaced
            false, // itemIsCompressibleReplaced
            BoxSizing.BORDER_BOX, // boxSizing
            Direction.LTR, // direction
            new Point<>(Overflow.VISIBLE, Overflow.VISIBLE), // overflow
            0.0f, // scrollbarWidth
            Contain.NONE, // contain
            Float.NONE, // floatValue
            Clear.NONE, // clear
            Position.STATIC, // position
            StyleHelpers.RectLengthPercentageAutoExt.auto(), // inset
            StyleHelpers.SizeDimensionExt.auto(), // size
            StyleHelpers.SizeLengthPercentageAutoExt.auto(), // minSize
            StyleHelpers.SizeLengthPercentageAutoExt.auto(), // maxSize
            OptF32.NONE, // aspectRatio
            StyleHelpers.RectLengthPercentageAutoExt.zero(), // margin
            StyleHelpers.RectLengthPercentageExt.zero(), // padding
            StyleHelpers.RectLengthPercentageExt.zero(), // border
            AlignItems.NORMAL, // alignItems
            null, // alignSelf
            AlignItems.NORMAL, // justifyItems
            null, // justifySelf
            AlignContent.NORMAL, // alignContent
            AlignContent.NORMAL, // justifyContent
            StyleHelpers.SizeLengthPercentageExt.zero(), // gap
            TextAlign.AUTO, // textAlign
            FlexDirection.ROW, // flexDirection
            FlexWrap.NO_WRAP, // flexWrap
            1, // flexLineCount
            Dimension.AUTO, // flexBasis
            0.0f, // flexGrow
            1.0f, // flexShrink
            List.of(), // gridTemplateRows
            List.of(), // gridTemplateColumns
            List.of(), // gridAutoRows
            List.of(), // gridAutoColumns
            GridAutoFlow.ROW, // gridAutoFlow
            null, // gridTemplateAreas
            List.of(), // gridTemplateColumnNames
            List.of(), // gridTemplateRowNames
            LineGridPlacementExt.DEFAULT, // gridRow
            LineGridPlacementExt.DEFAULT); // gridColumn

    /// `Default::default()` — `Style::DEFAULT`
    public static Style defaultStyle() {
        return DEFAULT;
    }

    // ---- CoreStyle ----

    @Override
    public BoxGenerationMode boxGenerationMode() {
        return switch (display) {
            case NONE -> BoxGenerationMode.NONE;
            default -> BoxGenerationMode.NORMAL;
        };
    }

    @Override
    public boolean isBlock() {
        return display == Display.BLOCK;
    }

    @Override
    public boolean isCompressibleReplaced() {
        return itemIsReplaced || itemIsCompressibleReplaced;
    }

    @Override
    public boolean isReplaced() {
        return itemIsReplaced;
    }

    // ---- OofItemStyle / BlockItemStyle ----

    @Override
    public boolean isTable() {
        return itemIsTable;
    }

    // ---- GridContainerStyle ----

    // `grid_template_area_{row,column}_count` are overridden on `Style`
    // upstream: they return the stored template counts, NOT the default
    // derived max.
    @Override
    public int gridTemplateAreaRowCount() {
        return gridTemplateAreas != null ? gridTemplateAreas.rowCount() : 0;
    }

    @Override
    public int gridTemplateAreaColumnCount() {
        return gridTemplateAreas != null ? gridTemplateAreas.columnCount() : 0;
    }
    // ---- with-copiers (GAP-005: struct-update syntax on the record) ----

    /// Copies this Style, replacing only the 'display' field.
    public Style withDisplay(Display display) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'itemIsTable' field.
    public Style withItemIsTable(boolean itemIsTable) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'itemIsReplaced' field.
    public Style withItemIsReplaced(boolean itemIsReplaced) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'itemIsCompressibleReplaced' field.
    public Style withItemIsCompressibleReplaced(boolean itemIsCompressibleReplaced) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'boxSizing' field.
    public Style withBoxSizing(BoxSizing boxSizing) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'direction' field.
    public Style withDirection(Direction direction) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'overflow' field.
    public Style withOverflow(Point<Overflow> overflow) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'scrollbarWidth' field.
    public Style withScrollbarWidth(float scrollbarWidth) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'contain' field.
    public Style withContain(Contain contain) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'position' field.
    public Style withPosition(Position position) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'inset' field.
    public Style withInset(Rect<LengthPercentageAuto> inset) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'size' field.
    public Style withSize(Size<Dimension> size) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'minSize' field.
    public Style withMinSize(Size<LengthPercentageAuto> minSize) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'maxSize' field.
    public Style withMaxSize(Size<LengthPercentageAuto> maxSize) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'aspectRatio' field.
    public Style withAspectRatio(@Opt float aspectRatio) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'margin' field.
    public Style withMargin(Rect<LengthPercentageAuto> margin) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'padding' field.
    public Style withPadding(Rect<LengthPercentage> padding) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'border' field.
    public Style withBorder(Rect<LengthPercentage> border) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'alignItems' field.
    public Style withAlignItems(AlignItems alignItems) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'alignSelf' field.
    public Style withAlignSelf(@Nullable AlignItems alignSelf) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'justifyItems' field.
    public Style withJustifyItems(AlignItems justifyItems) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'justifySelf' field.
    public Style withJustifySelf(@Nullable AlignItems justifySelf) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'alignContent' field.
    public Style withAlignContent(AlignContent alignContent) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'justifyContent' field.
    public Style withJustifyContent(AlignContent justifyContent) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gap' field.
    public Style withGap(Size<LengthPercentage> gap) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'textAlign' field.
    public Style withTextAlign(TextAlign textAlign) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'flexDirection' field.
    public Style withFlexDirection(FlexDirection flexDirection) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'flexWrap' field.
    public Style withFlexWrap(FlexWrap flexWrap) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'flexLineCount' field.
    public Style withFlexLineCount(int flexLineCount) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'flexBasis' field.
    public Style withFlexBasis(Dimension flexBasis) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'flexGrow' field.
    public Style withFlexGrow(float flexGrow) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'flexShrink' field.
    public Style withFlexShrink(float flexShrink) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridTemplateRows' field.
    public Style withGridTemplateRows(List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateRows) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridTemplateColumns' field.
    public Style withGridTemplateColumns(List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateColumns) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridAutoRows' field.
    public Style withGridAutoRows(List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoRows) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridAutoColumns' field.
    public Style withGridAutoColumns(List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoColumns) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridAutoFlow' field.
    public Style withGridAutoFlow(GridAutoFlow gridAutoFlow) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridTemplateAreas' field.
    public Style withGridTemplateAreas(@Nullable GridTemplateAreas gridTemplateAreas) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridTemplateColumnNames' field.
    public Style withGridTemplateColumnNames(List<List<String>> gridTemplateColumnNames) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridTemplateRowNames' field.
    public Style withGridTemplateRowNames(List<List<String>> gridTemplateRowNames) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridRow' field.
    public Style withGridRow(Line<GridPlacement> gridRow) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'gridColumn' field.
    public Style withGridColumn(Line<GridPlacement> gridColumn) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'floatValue' field.
    public Style withFloatValue(Float floatValue) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

    /// Copies this Style, replacing only the 'clear' field.
    public Style withClear(Clear clear) {
        return new Style(display, itemIsTable, itemIsReplaced, itemIsCompressibleReplaced, boxSizing, direction, overflow, scrollbarWidth, contain, floatValue, clear, position, inset, size, minSize, maxSize, aspectRatio, margin, padding, border, alignItems, alignSelf, justifyItems, justifySelf, alignContent, justifyContent, gap, textAlign, flexDirection, flexWrap, flexLineCount, flexBasis, flexGrow, flexShrink, gridTemplateRows, gridTemplateColumns, gridAutoRows, gridAutoColumns, gridAutoFlow, gridTemplateAreas, gridTemplateColumnNames, gridTemplateRowNames, gridRow, gridColumn);
    }

}
