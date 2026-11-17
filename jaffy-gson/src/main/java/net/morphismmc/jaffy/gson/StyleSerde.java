// JAFFY: upstream=src/style/mod.rs
// JAFFY-FROM: src/style/float.rs
// JAFFY-FROM: src/style/flex.rs
// JAFFY-FROM: src/style/block.rs
// JAFFY-FROM: src/style/available_space.rs
// JAFFY-FROM: src/style/grid.rs
// JAFFY-NOTE: serde impls
// Contain), src/style/float.rs (Float/FloatDirection/Clear),
// src/style/flex.rs (FlexDirection/FlexWrap), src/style/block.rs (TextAlign),
// src/style/available_space.rs (AvailableSpace, serialize-only),
// src/style/grid.rs (GridAutoFlow).
//
// Style's field order on the wire is the Rust declaration order. `#[serde(default)]`
// on the container fills missing fields from `Style::default()` on read — the
// Java adapter starts from Style.DEFAULT for the same result. The `dummy`
// PhantomData field has no Java counterpart (GAP-017) but occupies its slot on
// the wire: `null` out, unit expected in.
package net.morphismmc.jaffy.gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.BoxGenerationMode;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.block.TextAlign;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.Float;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateAreas;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

final class StyleSerde {

    private StyleSerde() {}

    // ---- Unit-variant enum adapters (derived impls) ----

    static final TypeAdapter<Display> DISPLAY = AlignmentSerde.unitEnum(Display.class);
    static final TypeAdapter<BoxGenerationMode> BOX_GENERATION_MODE = AlignmentSerde.unitEnum(BoxGenerationMode.class);
    static final TypeAdapter<Position> POSITION = AlignmentSerde.unitEnum(Position.class);
    static final TypeAdapter<BoxSizing> BOX_SIZING = AlignmentSerde.unitEnum(BoxSizing.class);
    static final TypeAdapter<Overflow> OVERFLOW = AlignmentSerde.unitEnum(Overflow.class);
    static final TypeAdapter<Direction> DIRECTION = AlignmentSerde.unitEnum(Direction.class);
    static final TypeAdapter<Float> FLOAT = AlignmentSerde.unitEnum(Float.class);
    static final TypeAdapter<FloatDirection> FLOAT_DIRECTION = AlignmentSerde.unitEnum(FloatDirection.class);
    static final TypeAdapter<Clear> CLEAR = AlignmentSerde.unitEnum(Clear.class);
    static final TypeAdapter<TextAlign> TEXT_ALIGN = AlignmentSerde.unitEnum(TextAlign.class);
    static final TypeAdapter<FlexDirection> FLEX_DIRECTION = AlignmentSerde.unitEnum(FlexDirection.class);
    static final TypeAdapter<FlexWrap> FLEX_WRAP = AlignmentSerde.unitEnum(FlexWrap.class);
    static final TypeAdapter<GridAutoFlow> GRID_AUTO_FLOW = AlignmentSerde.unitEnum(GridAutoFlow.class);

    /// `struct Contain(u8)` — tuple newtype serializes as the bare integer.
    static final TypeAdapter<Contain> CONTAIN = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Contain v) throws IOException {
            out.value(v.bits());
        }

        @Override
        public Contain read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return new Contain(JsonSupport.readU8(in));
        }
    };

    /// `AvailableSpace` — `derive(Serialize)` only upstream; the read side has
    /// no counterpart and fails like an unimplemented Deserialize.
    static final TypeAdapter<AvailableSpace> AVAILABLE_SPACE = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, AvailableSpace v) throws IOException {
            switch (v) {
                case AvailableSpace.Definite d -> {
                    out.beginObject();
                    out.name("Definite");
                    JsonSupport.writeF32(out, d.value());
                    out.endObject();
                }
                case AvailableSpace.MinContent m -> out.value("MinContent");
                case AvailableSpace.MaxContent m -> out.value("MaxContent");
            }
        }

        @Override
        public AvailableSpace read(JsonReader in) {
            throw new JsonParseException("AvailableSpace has no Deserialize impl upstream");
        }
    };

    // ---- Style ----

    private static final TypeAdapter<Point<Overflow>> OVERFLOW_POINT = GeometrySerde.pointAdapter(OVERFLOW);
    private static final TypeAdapter<Rect<LengthPercentageAuto>> RECT_LPA =
            GeometrySerde.rectAdapter(DimensionSerde.LENGTH_PERCENTAGE_AUTO);
    private static final TypeAdapter<Rect<LengthPercentage>> RECT_LP =
            GeometrySerde.rectAdapter(DimensionSerde.LENGTH_PERCENTAGE);
    private static final TypeAdapter<Size<Dimension>> SIZE_DIMENSION =
            GeometrySerde.sizeAdapter(DimensionSerde.DIMENSION);
    private static final TypeAdapter<Size<LengthPercentageAuto>> SIZE_LPA =
            GeometrySerde.sizeAdapter(DimensionSerde.LENGTH_PERCENTAGE_AUTO);
    private static final TypeAdapter<Size<LengthPercentage>> SIZE_LP =
            GeometrySerde.sizeAdapter(DimensionSerde.LENGTH_PERCENTAGE);
    private static final TypeAdapter<GenericGridTemplateComponent<GridTemplateRepetition>> TEMPLATE_COMPONENT =
            GridSerde.componentAdapter(GridSerde.GRID_TEMPLATE_REPETITION);
    private static final TypeAdapter<Line<GridPlacement>> GRID_LINE_LINE =
            GeometrySerde.lineAdapter(GridSerde.GRID_PLACEMENT, () -> GridPlacement.AUTO);

    static final TypeAdapter<Style> STYLE = new TypeAdapter<>() {

        @Override
        public void write(JsonWriter out, Style v) throws IOException {
            out.beginObject();
            out.name("dummy");
            out.nullValue(); // PhantomData<S> -> serialize_unit -> null
            out.name("display");
            DISPLAY.write(out, v.display());
            out.name("item_is_table");
            out.value(v.itemIsTable());
            out.name("item_is_replaced");
            out.value(v.itemIsReplaced());
            out.name("item_is_compressible_replaced");
            out.value(v.itemIsCompressibleReplaced());
            out.name("box_sizing");
            BOX_SIZING.write(out, v.boxSizing());
            out.name("direction");
            DIRECTION.write(out, v.direction());
            out.name("overflow");
            OVERFLOW_POINT.write(out, v.overflow());
            out.name("scrollbar_width");
            JsonSupport.writeF32(out, v.scrollbarWidth());
            out.name("contain");
            CONTAIN.write(out, v.contain());
            out.name("float");
            FLOAT.write(out, v.floatValue());
            out.name("clear");
            CLEAR.write(out, v.clear());
            out.name("position");
            POSITION.write(out, v.position());
            out.name("inset");
            RECT_LPA.write(out, v.inset());
            out.name("size");
            SIZE_DIMENSION.write(out, v.size());
            out.name("min_size");
            SIZE_LPA.write(out, v.minSize());
            out.name("max_size");
            SIZE_LPA.write(out, v.maxSize());
            out.name("aspect_ratio");
            JsonSupport.writeOptF32(out, v.aspectRatio());
            out.name("margin");
            RECT_LPA.write(out, v.margin());
            out.name("padding");
            RECT_LP.write(out, v.padding());
            out.name("border");
            RECT_LP.write(out, v.border());
            out.name("align_items");
            AlignmentSerde.ALIGN_ITEMS.write(out, v.alignItems());
            out.name("align_self");
            writeNullable(out, AlignmentSerde.ALIGN_ITEMS, v.alignSelf());
            out.name("justify_items");
            AlignmentSerde.ALIGN_ITEMS.write(out, v.justifyItems());
            out.name("justify_self");
            writeNullable(out, AlignmentSerde.ALIGN_ITEMS, v.justifySelf());
            out.name("align_content");
            AlignmentSerde.ALIGN_CONTENT.write(out, v.alignContent());
            out.name("justify_content");
            AlignmentSerde.ALIGN_CONTENT.write(out, v.justifyContent());
            out.name("gap");
            SIZE_LP.write(out, v.gap());
            out.name("text_align");
            TEXT_ALIGN.write(out, v.textAlign());
            out.name("flex_direction");
            FLEX_DIRECTION.write(out, v.flexDirection());
            out.name("flex_wrap");
            FLEX_WRAP.write(out, v.flexWrap());
            out.name("flex_line_count");
            out.value(v.flexLineCount());
            out.name("flex_basis");
            DimensionSerde.DIMENSION.write(out, v.flexBasis());
            out.name("flex_grow");
            JsonSupport.writeF32(out, v.flexGrow());
            out.name("flex_shrink");
            JsonSupport.writeF32(out, v.flexShrink());
            out.name("grid_template_rows");
            writeComponents(out, v.gridTemplateRows());
            out.name("grid_template_columns");
            writeComponents(out, v.gridTemplateColumns());
            out.name("grid_auto_rows");
            writeMinMaxes(out, v.gridAutoRows());
            out.name("grid_auto_columns");
            writeMinMaxes(out, v.gridAutoColumns());
            out.name("grid_auto_flow");
            GRID_AUTO_FLOW.write(out, v.gridAutoFlow());
            out.name("grid_template_areas");
            writeNullable(out, GridSerde.GRID_TEMPLATE_AREAS, v.gridTemplateAreas());
            out.name("grid_template_column_names");
            GeometrySerde.writeStringLists(out, v.gridTemplateColumnNames());
            out.name("grid_template_row_names");
            GeometrySerde.writeStringLists(out, v.gridTemplateRowNames());
            out.name("grid_row");
            GRID_LINE_LINE.write(out, v.gridRow());
            out.name("grid_column");
            GRID_LINE_LINE.write(out, v.gridColumn());
            out.endObject();
        }

        @Override
        public Style read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            // #[serde(default)] on the container: every missing field takes the
            // corresponding value of Style::default().
            Style d = Style.DEFAULT;
            Display display = d.display();
            boolean itemIsTable = d.itemIsTable();
            boolean itemIsReplaced = d.itemIsReplaced();
            boolean itemIsCompressibleReplaced = d.itemIsCompressibleReplaced();
            BoxSizing boxSizing = d.boxSizing();
            Direction direction = d.direction();
            Point<Overflow> overflow = d.overflow();
            float scrollbarWidth = d.scrollbarWidth();
            Contain contain = d.contain();
            Float floatValue = d.floatValue();
            Clear clear = d.clear();
            Position position = d.position();
            Rect<LengthPercentageAuto> inset = d.inset();
            Size<Dimension> size = d.size();
            Size<LengthPercentageAuto> minSize = d.minSize();
            Size<LengthPercentageAuto> maxSize = d.maxSize();
            float aspectRatio = d.aspectRatio();
            Rect<LengthPercentageAuto> margin = d.margin();
            Rect<LengthPercentage> padding = d.padding();
            Rect<LengthPercentage> border = d.border();
            AlignItems alignItems = d.alignItems();
            AlignItems alignSelf = d.alignSelf();
            AlignItems justifyItems = d.justifyItems();
            AlignItems justifySelf = d.justifySelf();
            AlignContent alignContent = d.alignContent();
            AlignContent justifyContent = d.justifyContent();
            Size<LengthPercentage> gap = d.gap();
            TextAlign textAlign = d.textAlign();
            FlexDirection flexDirection = d.flexDirection();
            FlexWrap flexWrap = d.flexWrap();
            int flexLineCount = d.flexLineCount();
            Dimension flexBasis = d.flexBasis();
            float flexGrow = d.flexGrow();
            float flexShrink = d.flexShrink();
            List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateRows = d.gridTemplateRows();
            List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateColumns = d.gridTemplateColumns();
            List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoRows = d.gridAutoRows();
            List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoColumns = d.gridAutoColumns();
            GridAutoFlow gridAutoFlow = d.gridAutoFlow();
            GridTemplateAreas gridTemplateAreas = d.gridTemplateAreas();
            List<List<String>> gridTemplateColumnNames = d.gridTemplateColumnNames();
            List<List<String>> gridTemplateRowNames = d.gridTemplateRowNames();
            Line<GridPlacement> gridRow = d.gridRow();
            Line<GridPlacement> gridColumn = d.gridColumn();

            Set<String> seen = new HashSet<>();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                if (!seen.add(name)) {
                    throw new JsonParseException("duplicate field `" + name + "`");
                }
                switch (name) {
                    case "dummy" -> {
                        if (in.peek() != JsonToken.NULL) {
                            throw new JsonParseException("expected unit for PhantomData");
                        }
                        in.nextNull();
                    }
                    case "display" -> display = DISPLAY.read(in);
                    case "item_is_table" -> itemIsTable = in.nextBoolean();
                    case "item_is_replaced" -> itemIsReplaced = in.nextBoolean();
                    case "item_is_compressible_replaced" -> itemIsCompressibleReplaced = in.nextBoolean();
                    case "box_sizing" -> boxSizing = BOX_SIZING.read(in);
                    case "direction" -> direction = DIRECTION.read(in);
                    case "overflow" -> overflow = OVERFLOW_POINT.read(in);
                    case "scrollbar_width" -> scrollbarWidth = JsonSupport.readF32(in);
                    case "contain" -> contain = CONTAIN.read(in);
                    case "float" -> floatValue = FLOAT.read(in);
                    case "clear" -> clear = CLEAR.read(in);
                    case "position" -> position = POSITION.read(in);
                    case "inset" -> inset = RECT_LPA.read(in);
                    case "size" -> size = SIZE_DIMENSION.read(in);
                    case "min_size" -> minSize = SIZE_LPA.read(in);
                    case "max_size" -> maxSize = SIZE_LPA.read(in);
                    case "aspect_ratio" -> aspectRatio = JsonSupport.readOptF32(in);
                    case "margin" -> margin = RECT_LPA.read(in);
                    case "padding" -> padding = RECT_LP.read(in);
                    case "border" -> border = RECT_LP.read(in);
                    case "align_items" -> alignItems = AlignmentSerde.ALIGN_ITEMS.read(in);
                    case "align_self" -> alignSelf = readNullable(in, AlignmentSerde.ALIGN_ITEMS);
                    case "justify_items" -> justifyItems = AlignmentSerde.ALIGN_ITEMS.read(in);
                    case "justify_self" -> justifySelf = readNullable(in, AlignmentSerde.ALIGN_ITEMS);
                    case "align_content" -> alignContent = AlignmentSerde.ALIGN_CONTENT.read(in);
                    case "justify_content" -> justifyContent = AlignmentSerde.ALIGN_CONTENT.read(in);
                    case "gap" -> gap = SIZE_LP.read(in);
                    case "text_align" -> textAlign = TEXT_ALIGN.read(in);
                    case "flex_direction" -> flexDirection = FLEX_DIRECTION.read(in);
                    case "flex_wrap" -> flexWrap = FLEX_WRAP.read(in);
                    case "flex_line_count" -> flexLineCount = JsonSupport.readU16(in);
                    case "flex_basis" -> flexBasis = DimensionSerde.DIMENSION.read(in);
                    case "flex_grow" -> flexGrow = JsonSupport.readF32(in);
                    case "flex_shrink" -> flexShrink = JsonSupport.readF32(in);
                    case "grid_template_rows" -> gridTemplateRows = readComponents(in);
                    case "grid_template_columns" -> gridTemplateColumns = readComponents(in);
                    case "grid_auto_rows" -> gridAutoRows = readMinMaxes(in);
                    case "grid_auto_columns" -> gridAutoColumns = readMinMaxes(in);
                    case "grid_auto_flow" -> gridAutoFlow = GRID_AUTO_FLOW.read(in);
                    case "grid_template_areas" -> gridTemplateAreas = readNullable(in, GridSerde.GRID_TEMPLATE_AREAS);
                    case "grid_template_column_names" -> gridTemplateColumnNames = GeometrySerde.readStringLists(in);
                    case "grid_template_row_names" -> gridTemplateRowNames = GeometrySerde.readStringLists(in);
                    case "grid_row" -> gridRow = GRID_LINE_LINE.read(in);
                    case "grid_column" -> gridColumn = GRID_LINE_LINE.read(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new Style(
                    display,
                    itemIsTable,
                    itemIsReplaced,
                    itemIsCompressibleReplaced,
                    boxSizing,
                    direction,
                    overflow,
                    scrollbarWidth,
                    contain,
                    floatValue,
                    clear,
                    position,
                    inset,
                    size,
                    minSize,
                    maxSize,
                    aspectRatio,
                    margin,
                    padding,
                    border,
                    alignItems,
                    alignSelf,
                    justifyItems,
                    justifySelf,
                    alignContent,
                    justifyContent,
                    gap,
                    textAlign,
                    flexDirection,
                    flexWrap,
                    flexLineCount,
                    flexBasis,
                    flexGrow,
                    flexShrink,
                    gridTemplateRows,
                    gridTemplateColumns,
                    gridAutoRows,
                    gridAutoColumns,
                    gridAutoFlow,
                    gridTemplateAreas,
                    gridTemplateColumnNames,
                    gridTemplateRowNames,
                    gridRow,
                    gridColumn);
        }

        private static <T> void writeNullable(JsonWriter out, TypeAdapter<T> adapter, T v) throws IOException {
            if (v == null) {
                out.nullValue();
            } else {
                adapter.write(out, v);
            }
        }

        private static <T> T readNullable(JsonReader in, TypeAdapter<T> adapter) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return adapter.read(in);
        }

        private static void writeComponents(
                JsonWriter out, List<GenericGridTemplateComponent<GridTemplateRepetition>> v) throws IOException {
            out.beginArray();
            for (GenericGridTemplateComponent<GridTemplateRepetition> c : v) {
                TEMPLATE_COMPONENT.write(out, c);
            }
            out.endArray();
        }

        private static List<GenericGridTemplateComponent<GridTemplateRepetition>> readComponents(JsonReader in)
                throws IOException {
            List<GenericGridTemplateComponent<GridTemplateRepetition>> list = new ArrayList<>();
            in.beginArray();
            while (in.hasNext()) {
                list.add(TEMPLATE_COMPONENT.read(in));
            }
            in.endArray();
            return list;
        }

        private static void writeMinMaxes(
                JsonWriter out, List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> v) throws IOException {
            out.beginArray();
            for (MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> t : v) {
                GridSerde.TRACK_SIZING.write(out, t);
            }
            out.endArray();
        }

        private static List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> readMinMaxes(JsonReader in)
                throws IOException {
            List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> list = new ArrayList<>();
            in.beginArray();
            while (in.hasNext()) {
                list.add(GridSerde.TRACK_SIZING.read(in));
            }
            in.endArray();
            return list;
        }
    };
}
