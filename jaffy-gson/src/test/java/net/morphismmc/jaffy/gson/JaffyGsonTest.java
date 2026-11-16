// JAFFY: serde wire-format tests — every expectation is hand-derived from the
// upstream impls (serde_json output rules: externally-tagged enums, snake_case
// fields, ryu float formatting, `u64 = tag << 32 | f32_bits` for CompactLength).
package net.morphismmc.jaffy.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.BoxGenerationMode;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.StyleHelpers;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.block.TextAlign;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.Float;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateArea;
import net.morphismmc.jaffy.style.grid.GridTemplateAreas;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.LineGridPlacementExt;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.NamedGridLine;
import net.morphismmc.jaffy.style.grid.RepetitionCount;
import net.morphismmc.jaffy.tree.layout.Baselines;
import net.morphismmc.jaffy.tree.layout.CollapsibleMarginSet;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.OofPositioningArea;
import net.morphismmc.jaffy.tree.layout.ScrollableOverflow;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.just.math.OptF32;
import org.junit.jupiter.api.Test;

class JaffyGsonTest {

    private static final Gson GSON = JaffyGson.gson();

    /// `serialized()` of a Primitive word `(f32 << 32 | tag)` is `rotl 32`.
    private static long ser(int tag, float val) {
        return Long.rotateLeft((java.lang.Float.floatToRawIntBits(val) & 0xFFFFFFFFL) << 32 | tag, 32);
    }

    // Tag constants mirrored for readability of the expectations below.
    private static final int LENGTH = 0b0001;
    private static final int AUTO = 0b0011;
    private static final int PERCENT = 0b0101;

    @Test
    void f32_ryu_formatting() {
        assertEquals("0.1", GSON.toJson(0.1f));
        assertEquals("1.0", GSON.toJson(1.0f));
        assertEquals("-0.0", GSON.toJson(-0.0f));
        assertEquals("100.0", GSON.toJson(100.0f));
        assertEquals("1e+13", GSON.toJson(1e13f));
        assertEquals("0.00001", GSON.toJson(1e-5f));
        assertEquals("0.000001", GSON.toJson(1e-6f));
        assertEquals("1e-7", GSON.toJson(1e-7f));
        assertEquals("1.234e+33", GSON.toJson(1.234e33f));
        // serde_json serializes non-finite floats as null
        assertEquals("null", GSON.toJson(java.lang.Float.NaN));
        assertEquals("null", GSON.toJson(java.lang.Float.POSITIVE_INFINITY));
        assertEquals("null", GSON.toJson(java.lang.Float.NEGATIVE_INFINITY));
    }

    @Test
    void unit_enums() {
        assertEquals("\"Flex\"", GSON.toJson(Display.FLEX));
        assertEquals("\"FlowRoot\"", GSON.toJson(Display.FLOW_ROOT));
        assertEquals("\"NoWrap\"", GSON.toJson(FlexWrap.NO_WRAP));
        assertEquals("\"BalanceReverse\"", GSON.toJson(FlexWrap.BALANCE_REVERSE));
        assertEquals("\"RowDense\"", GSON.toJson(GridAutoFlow.ROW_DENSE));
        assertEquals("\"LegacyCenter\"", GSON.toJson(TextAlign.LEGACY_CENTER));
        assertEquals("\"Sticky\"", GSON.toJson(Position.STICKY));
        assertEquals("\"BorderBox\"", GSON.toJson(BoxSizing.BORDER_BOX));
        assertEquals("\"Ltr\"", GSON.toJson(Direction.LTR));
        assertEquals("\"Both\"", GSON.toJson(Clear.BOTH));
        assertEquals("\"None\"", GSON.toJson(Float.NONE));
        assertEquals("\"PerformHiddenLayout\"", GSON.toJson(RunMode.PERFORM_HIDDEN_LAYOUT));
        assertEquals("\"InherentSize\"", GSON.toJson(SizingMode.INHERENT_SIZE));
        assertEquals("\"Both\"", GSON.toJson(RequestedAxis.BOTH));
        assertEquals("\"Normal\"", GSON.toJson(BoxGenerationMode.NORMAL));
        // round-trip
        assertEquals(FlexWrap.WRAP_REVERSE, GSON.fromJson("\"WrapReverse\"", FlexWrap.class));
        assertEquals(Position.ABSOLUTE, GSON.fromJson("\"Absolute\"", Position.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"Bogus\"", Display.class));
    }

    // upstream: serde_align_items_round_trip (style/alignment.rs)
    @Test
    void serde_align_items_round_trip() {
        AlignItems[] items = {
            AlignItems.NORMAL, AlignItems.START, AlignItems.END,
            AlignItems.FLEX_START, AlignItems.FLEX_END, AlignItems.CENTER,
            AlignItems.BASELINE, AlignItems.STRETCH,
            AlignItems.SAFE_START, AlignItems.SAFE_END,
            AlignItems.SAFE_FLEX_START, AlignItems.SAFE_FLEX_END,
            AlignItems.SAFE_CENTER,
            AlignItems.SELF_START, AlignItems.SELF_END,
            AlignItems.SAFE_SELF_START, AlignItems.SAFE_SELF_END,
        };
        String[] json = {
            "\"Normal\"", "\"Start\"", "\"End\"", "\"FlexStart\"", "\"FlexEnd\"",
            "\"Center\"", "\"Baseline\"", "\"Stretch\"", "\"SafeStart\"", "\"SafeEnd\"",
            "\"SafeFlexStart\"", "\"SafeFlexEnd\"", "\"SafeCenter\"",
            "\"SelfStart\"", "\"SelfEnd\"", "\"SafeSelfStart\"", "\"SafeSelfEnd\"",
        };
        for (int i = 0; i < items.length; i++) {
            assertEquals(json[i], GSON.toJson(items[i]), "serialize " + items[i]);
            assertEquals(items[i], GSON.fromJson(json[i], AlignItems.class), "round-trip " + items[i]);
        }
        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"NotAVariant\"", AlignItems.class));
    }

    // upstream: serde_align_content_round_trip (style/alignment.rs)
    @Test
    void serde_align_content_round_trip() {
        AlignContent[] contents = {
            AlignContent.NORMAL, AlignContent.START, AlignContent.END,
            AlignContent.FLEX_START, AlignContent.FLEX_END, AlignContent.CENTER,
            AlignContent.STRETCH,
            AlignContent.SPACE_BETWEEN, AlignContent.SPACE_EVENLY, AlignContent.SPACE_AROUND,
            AlignContent.SAFE_START, AlignContent.SAFE_END,
            AlignContent.SAFE_FLEX_START, AlignContent.SAFE_FLEX_END,
            AlignContent.SAFE_CENTER,
        };
        String[] json = {
            "\"Normal\"", "\"Start\"", "\"End\"", "\"FlexStart\"", "\"FlexEnd\"",
            "\"Center\"", "\"Stretch\"",
            "\"SpaceBetween\"", "\"SpaceEvenly\"", "\"SpaceAround\"",
            "\"SafeStart\"", "\"SafeEnd\"", "\"SafeFlexStart\"", "\"SafeFlexEnd\"",
            "\"SafeCenter\"",
        };
        for (int i = 0; i < contents.length; i++) {
            assertEquals(json[i], GSON.toJson(contents[i]), "serialize " + contents[i]);
            assertEquals(contents[i], GSON.fromJson(json[i], AlignContent.class), "round-trip " + contents[i]);
        }
        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"NotAVariant\"", AlignContent.class));
    }

    @Test
    void align_tag_unsafe_and_ignored_safety() {
        // Unsafe* prefix composes with the keyword; keywords that ignore safety emit the bare name
        assertEquals("\"UnsafeEnd\"", GSON.toJson(AlignItems.END.withSafety(AlignmentSafety.UNSAFE)));
        assertEquals("\"Stretch\"", GSON.toJson(AlignItems.STRETCH.withSafety(AlignmentSafety.UNSAFE)));
        assertEquals("\"Normal\"", GSON.toJson(AlignItems.NORMAL.withSafety(AlignmentSafety.SAFE)));
        assertEquals("\"UnsafeFlexEnd\"", GSON.toJson(AlignContent.FLEX_END.withSafety(AlignmentSafety.UNSAFE)));
        assertEquals(
                "\"SpaceAround\"",
                GSON.toJson(AlignContent.SPACE_AROUND.withSafety(AlignmentSafety.SAFE)));

        // round-trips incl. Unsafe* -> withSafety reconstruction
        assertEquals(
                AlignItems.CENTER.withSafety(AlignmentSafety.UNSAFE),
                GSON.fromJson("\"UnsafeCenter\"", AlignItems.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"FlexMiddle\"", AlignItems.class));
    }

    // upstream: serde_can_deserialize_partial_values (tests/hand_written/serde.rs)
    // The upstream test parses into serde_json::Value only — it validates JSON
    // well-formedness, not Style deserialization (the {"Length": 22} shape
    // predates CompactLength and does not round-trip through Style).
    @Test
    void serde_can_deserialize_partial_values() {
        String json = "{"
                + "\"inset\": {\"left\": {\"Length\": 22}, \"right\": \"Auto\"},"
                + "\"size\": {\"width\": {\"Percent\": 50}},"
                + "\"min_size\": {\"height\": {\"Length\": 10}},"
                + "\"max_size\": {\"width\": \"Auto\"},"
                + "\"margin\": {\"right\": {\"Length\": 99.0}, \"bottom\": {\"Length\": 99.0}},"
                + "\"padding\": {\"left\": {\"Length\": 99.0}},"
                + "\"border\": {\"bottom\": {\"Length\": 99.0}},"
                + "\"gap\": {\"width\": {\"Length\": 99.0}},"
                + "\"grid_row\": {\"start\": \"Auto\"},"
                + "\"grid_column\": {\"end\": \"Auto\"}"
                + "}";
        com.google.gson.JsonObject value =
                new com.google.gson.JsonParser().parse(json).getAsJsonObject();
        assertEquals(10, value.size());
    }

    @Test
    void compact_length_u64() {
        // tag << 32 | f32_bits
        assertEquals(Long.toString(ser(LENGTH, 22.0f)), GSON.toJson(Dimension.length(22)));
        assertEquals(Long.toString(ser(PERCENT, 0.5f)), GSON.toJson(LengthPercentage.percent(0.5f)));
        assertEquals(Long.toString(ser(AUTO, 0.0f)), GSON.toJson(Dimension.AUTO));
        assertEquals(Long.toString(3L << 32), GSON.toJson(LengthPercentageAuto.auto()));
        // exact magic values pinned: auto = 3<<32, length(0) = 1<<32
        assertEquals("12884901888", GSON.toJson(Dimension.AUTO));
        assertEquals("4294967296", GSON.toJson(Dimension.length(0)));

        // round-trips
        assertEquals(Dimension.length(22), GSON.fromJson(Long.toString(ser(LENGTH, 22.0f)), Dimension.class));
        assertEquals(
                LengthPercentageAuto.auto(),
                GSON.fromJson(Long.toString(3L << 32), LengthPercentageAuto.class));

        // tag validation
        assertThrows(JsonParseException.class, () -> GSON.fromJson(Long.toString(4L << 32), Dimension.class)); // CALC
        assertThrows(
                JsonParseException.class,
                () -> GSON.fromJson(Long.toString(ser(AUTO, 0.0f)), LengthPercentage.class)); // auto in LP
        assertThrows(
                JsonParseException.class,
                () -> GSON.fromJson(
                        Long.toString(ser(0b0011_1011, 0.0f)), MinTrackSizingFunction.class)); // FIT_KW invalid
        // fr accepted by MaxTrackSizingFunction
        assertEquals(
                MaxTrackSizingFunction.fr(2),
                GSON.fromJson(Long.toString(ser(0b0001_0011, 2.0f)), MaxTrackSizingFunction.class));
        // calc tag -> "Cannot deserialize Calc value"
        assertThrows(JsonParseException.class, () -> GSON.fromJson(Long.toString(6L << 32), Dimension.class));
    }

    @Test
    void geometry() {
        assertEquals(
                "{\"x\":\"Visible\",\"y\":\"Hidden\"}",
                GSON.toJson(new Point<>(Overflow.VISIBLE, Overflow.HIDDEN), new TypeToken<Point<Overflow>>() {}.getType()));
        assertEquals(
                "{\"width\":10.0,\"height\":-2.5}",
                GSON.toJson(new SizeF32(10.0f, -2.5f)));
        assertEquals(
                "{\"left\":1.0,\"right\":2.0,\"top\":3.0,\"bottom\":4.0}",
                GSON.toJson(new RectF32(1, 2, 3, 4)));
        assertEquals(
                "{\"width\":null,\"height\":4.0}",
                GSON.toJson(new SizeOptF32(OptF32.NONE, 4.0f)));
        assertEquals(
                "{\"width\":true,\"height\":false}",
                GSON.toJson(new SizeBool(true, false)));
        assertEquals(
                "{\"start\":true,\"end\":false}",
                GSON.toJson(new LineBool(true, false)));
        assertEquals("{\"x\":false,\"y\":true}", GSON.toJson(new PointBool(false, true)));
        assertEquals("{\"left\":0.5,\"right\":1.5}", GSON.toJson(new FloatInsets(0.5f, 1.5f)));

        // generic Line<GridPlacement> via TypeToken
        assertEquals(
                "{\"start\":\"Auto\",\"end\":{\"Span\":2}}",
                GSON.toJson(
                        new net.morphismmc.jaffy.geometry.Line<>(GridPlacement.AUTO, GridPlacement.fromSpan(2)),
                        new TypeToken<net.morphismmc.jaffy.geometry.Line<GridPlacement>>() {}.getType()));

        // deserialization incl. missing-field defaults on Line
        assertEquals(
                new LineBool(false, false),
                GSON.fromJson("{}", LineBool.class));
        assertThrows(
                JsonParseException.class,
                () -> GSON.fromJson("{\"x\":\"Visible\"}", new TypeToken<Point<Overflow>>() {}.getType()));
        assertThrows(
                JsonParseException.class,
                () -> GSON.fromJson(
                        "{\"left\":1,\"right\":2,\"top\":3,\"bottom\":4,\"bottom\":5}", RectF32.class));
    }

    @Test
    void grid_placements() {
        assertEquals("\"Auto\"", GSON.toJson(GridPlacement.AUTO));
        assertEquals("{\"Line\":-2}", GSON.toJson(GridPlacement.fromLineIndex(-2)));
        assertEquals("{\"Span\":3}", GSON.toJson(GridPlacement.fromSpan(3)));
        assertEquals(
                "{\"NamedLine\":[\"foo\",2]}",
                GSON.toJson(new GridPlacement.NamedLine("foo", 2)));
        assertEquals(
                "{\"NamedSpan\":[\"main\",1]}",
                GSON.toJson(new GridPlacement.NamedSpan("main", 1)));

        assertEquals(GridPlacement.AUTO, GSON.fromJson("\"Auto\"", GridPlacement.class));
        assertEquals(
                new GridPlacement.Line(GridLine.from(-2)),
                GSON.fromJson("{\"Line\":-2}", GridPlacement.class));
        assertEquals(
                new GridPlacement.NamedLine("a", 4),
                GSON.fromJson("{\"NamedLine\":[\"a\",4]}", GridPlacement.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{\"Bogus\":1}", GridPlacement.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"Bogus\"", GridPlacement.class));
    }

    @Test
    void grid_track_types() {
        assertEquals("\"AutoFill\"", GSON.toJson(RepetitionCount.AUTO_FILL));
        assertEquals("\"AutoFit\"", GSON.toJson(RepetitionCount.AUTO_FIT));
        assertEquals("{\"Count\":4}", GSON.toJson(new RepetitionCount.Count(4)));

        MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> tsf = new MinMax<>(
                MinTrackSizingFunction.minContent(), MaxTrackSizingFunction.fr(2));
        long minSer = ser(0b1011, 0.0f); // MIN_CONTENT
        long maxSer = ser(0b0001_0011, 2.0f); // FR
        assertEquals(
                "{\"min\":" + minSer + ",\"max\":" + maxSer + "}",
                GSON.toJson(tsf, new TypeToken<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>>() {}.getType()));

        GridTemplateRepetition rep = new GridTemplateRepetition(
                new RepetitionCount.Count(2), List.of(tsf), List.of(List.of("row-a"), List.of()));
        assertEquals(
                "{\"count\":{\"Count\":2},\"tracks\":[{\"min\":" + minSer + ",\"max\":" + maxSer
                        + "}],\"line_names\":[[\"row-a\"],[]]}",
                GSON.toJson(rep));
        assertEquals(rep, GSON.fromJson(GSON.toJson(rep), GridTemplateRepetition.class));

        GenericGridTemplateComponent<GridTemplateRepetition> single =
                new GenericGridTemplateComponent.Single<>(tsf);
        GenericGridTemplateComponent<GridTemplateRepetition> repeat =
                new GenericGridTemplateComponent.Repeat<>(rep);
        assertEquals(
                "{\"Single\":{\"min\":" + minSer + ",\"max\":" + maxSer + "}}",
                GSON.toJson(single, new TypeToken<GenericGridTemplateComponent<GridTemplateRepetition>>() {}.getType()));
        assertEquals(
                "{\"Repeat\":{\"count\":{\"Count\":2},\"tracks\":[{\"min\":" + minSer + ",\"max\":"
                        + maxSer + "}],\"line_names\":[[\"row-a\"],[]]}}",
                GSON.toJson(repeat, new TypeToken<GenericGridTemplateComponent<GridTemplateRepetition>>() {}.getType()));

        GridTemplateArea area = new GridTemplateArea("header", 1, 2, 1, 3);
        assertEquals(
                "{\"name\":\"header\",\"row_start\":1,\"row_end\":2,\"column_start\":1,\"column_end\":3}",
                GSON.toJson(area));
        GridTemplateAreas areas = new GridTemplateAreas(List.of(area), 2, 3);
        assertEquals(
                "{\"areas\":[{\"name\":\"header\",\"row_start\":1,\"row_end\":2,\"column_start\":1,\"column_end\":3}],"
                        + "\"row_count\":2,\"column_count\":3}",
                GSON.toJson(areas));
        assertEquals(new NamedGridLine("x", 5), GSON.fromJson("{\"name\":\"x\",\"index\":5}", NamedGridLine.class));
    }

    @Test
    void available_space() {
        assertEquals("{\"Definite\":10.0}", GSON.toJson(new AvailableSpace.Definite(10.0f)));
        assertEquals("\"MinContent\"", GSON.toJson(AvailableSpace.MIN_CONTENT));
        assertEquals("\"MaxContent\"", GSON.toJson(AvailableSpace.MAX_CONTENT));
        // Serialize-only upstream
        assertThrows(JsonParseException.class, () -> GSON.fromJson("\"MinContent\"", AvailableSpace.class));
    }

    @Test
    void contain_and_grid_line() {
        assertEquals("0", GSON.toJson(Contain.NONE));
        assertEquals("3", GSON.toJson(Contain.LAYOUT.union(Contain.PAINT)));
        assertEquals(new Contain(3), GSON.fromJson("3", Contain.class));
        assertEquals("-3", GSON.toJson(GridLine.from(-3)));
        assertEquals(GridLine.from(7), GSON.fromJson("7", GridLine.class));
    }

    @Test
    void style_default_wire() {
        long auto = 3L << 32;
        long zero = 1L << 32;
        String rectA = "{\"left\":" + auto + ",\"right\":" + auto + ",\"top\":" + auto + ",\"bottom\":" + auto + "}";
        String rectZ = "{\"left\":" + zero + ",\"right\":" + zero + ",\"top\":" + zero + ",\"bottom\":" + zero + "}";
        String sizeA = "{\"width\":" + auto + ",\"height\":" + auto + "}";
        String sizeZ = "{\"width\":" + zero + ",\"height\":" + zero + "}";
        String expected = "{\"dummy\":null,"
                + "\"display\":\"Flex\",\"item_is_table\":false,\"item_is_replaced\":false,"
                + "\"item_is_compressible_replaced\":false,\"box_sizing\":\"BorderBox\",\"direction\":\"Ltr\","
                + "\"overflow\":{\"x\":\"Visible\",\"y\":\"Visible\"},\"scrollbar_width\":0.0,\"contain\":0,"
                + "\"float\":\"None\",\"clear\":\"None\",\"position\":\"Static\","
                + "\"inset\":" + rectA + ",\"size\":" + sizeA + ",\"min_size\":" + sizeA + ",\"max_size\":" + sizeA + ","
                + "\"aspect_ratio\":null,\"margin\":" + rectZ + ",\"padding\":" + rectZ + ",\"border\":" + rectZ + ","
                + "\"align_items\":\"Normal\",\"align_self\":null,\"justify_items\":\"Normal\",\"justify_self\":null,"
                + "\"align_content\":\"Normal\",\"justify_content\":\"Normal\",\"gap\":" + sizeZ + ","
                + "\"text_align\":\"Auto\",\"flex_direction\":\"Row\",\"flex_wrap\":\"NoWrap\",\"flex_line_count\":1,"
                + "\"flex_basis\":" + auto + ",\"flex_grow\":0.0,\"flex_shrink\":1.0,"
                + "\"grid_template_rows\":[],\"grid_template_columns\":[],\"grid_auto_rows\":[],\"grid_auto_columns\":[],"
                + "\"grid_auto_flow\":\"Row\",\"grid_template_areas\":null,"
                + "\"grid_template_column_names\":[],\"grid_template_row_names\":[],"
                + "\"grid_row\":{\"start\":\"Auto\",\"end\":\"Auto\"},"
                + "\"grid_column\":{\"start\":\"Auto\",\"end\":\"Auto\"}}";
        assertEquals(expected, GSON.toJson(Style.DEFAULT));
        // full round-trip of the default
        assertEquals(Style.DEFAULT, GSON.fromJson(expected, Style.class));
        // "{}" -> serde(default) fills everything
        assertEquals(Style.DEFAULT, GSON.fromJson("{}", Style.class));
    }

    @Test
    void style_round_trip_nondefault() {
        Style style = Style.DEFAULT
                .withDisplay(Display.FLEX)
                .withFlexDirection(FlexDirection.ROW_REVERSE)
                .withAlignItems(AlignItems.SAFE_CENTER)
                .withAlignSelf(AlignItems.BASELINE)
                .withAspectRatio(1.5f)
                .withPosition(Position.ABSOLUTE)
                .withGridTemplateAreas(new GridTemplateAreas(List.of(new GridTemplateArea("a", 1, 2, 1, 2)), 1, 1))
                .withGridRow(new net.morphismmc.jaffy.geometry.Line<>(
                        GridPlacement.fromLineIndex(3), GridPlacement.AUTO))
                .withGridTemplateRows(List.of(new GenericGridTemplateComponent.Single<>(
                        new MinMax<>(MinTrackSizingFunction.length(10), MaxTrackSizingFunction.maxContent()))))
                .withItemIsTable(true)
                .withContain(Contain.LAYOUT);
        Style back = GSON.fromJson(GSON.toJson(style), Style.class);
        assertEquals(style, back);
        // unknown fields are ignored (serde default behavior)
        Style back2 = GSON.fromJson("{\"future_field\":42,\"display\":\"Grid\"}", Style.class);
        assertEquals(Style.DEFAULT.withDisplay(Display.GRID), back2);
        // duplicate field -> serde "duplicate field" error
        assertThrows(
                JsonParseException.class,
                () -> GSON.fromJson("{\"display\":\"Flex\",\"display\":\"Grid\"}", Style.class));
    }

    @Test
    void layout_types_serialize_only() {
        Layout layout = new Layout(
                3,
                new PointF32(1.5f, -2.0f),
                new SizeF32(10.0f, 20.0f),
                ScrollableOverflow.ZERO,
                SizeF32.ZERO,
                new RectF32(1, 1, 2, 2),
                new RectF32(0.5f, 0.5f, 0.5f, 0.5f),
                RectF32.ZERO);
        assertEquals(
                "{\"order\":3,\"location\":{\"x\":1.5,\"y\":-2.0},\"size\":{\"width\":10.0,\"height\":20.0},"
                        + "\"scrollable_overflow_rect\":{\"rect\":{\"left\":0.0,\"right\":0.0,\"top\":0.0,\"bottom\":0.0},"
                        + "\"reversed\":{\"x\":false,\"y\":false}},\"scrollbar_size\":{\"width\":0.0,\"height\":0.0},"
                        + "\"border\":{\"left\":1.0,\"right\":1.0,\"top\":2.0,\"bottom\":2.0},"
                        + "\"padding\":{\"left\":0.5,\"right\":0.5,\"top\":0.5,\"bottom\":0.5},"
                        + "\"margin\":{\"left\":0.0,\"right\":0.0,\"top\":0.0,\"bottom\":0.0}}",
                GSON.toJson(layout));

        // reversed overflow round-trips the {rect, reversed} split
        ScrollableOverflow so = ScrollableOverflow.of(new RectF32(-5, 10, -2, 8), new PointBool(true, false));
        assertEquals(
                "{\"rect\":{\"left\":-5.0,\"right\":10.0,\"top\":-2.0,\"bottom\":8.0},\"reversed\":{\"x\":true,\"y\":false}}",
                GSON.toJson(so));

        // upstream: serialization_exposes_rect_and_flags_not_packed_signs (tree/layout.rs)
        ScrollableOverflow upstreamCase =
                ScrollableOverflow.of(new RectF32(-10, 20, 0, 0), new PointBool(true, true));
        assertEquals(
                "{\"rect\":{\"left\":-10.0,\"right\":20.0,\"top\":0.0,\"bottom\":0.0},"
                        + "\"reversed\":{\"x\":true,\"y\":true}}",
                GSON.toJson(upstreamCase));

        LayoutOutput out = new LayoutOutput(
                new SizeF32(4, 5),
                so,
                new Baselines(3.0f, OptF32.NONE),
                new CollapsibleMarginSet(1.5f, -0.5f),
                CollapsibleMarginSet.ZERO,
                true,
                net.morphismmc.jaffy.tree.layout.OofCandidates.none(),
                new OofPositioningArea(new SizeF32(6, 7), new PointF32(1, 2)));
        assertEquals(
                "{\"size\":{\"width\":4.0,\"height\":5.0},"
                        + "\"scrollable_overflow_rect\":{\"rect\":{\"left\":-5.0,\"right\":10.0,\"top\":-2.0,\"bottom\":8.0},"
                        + "\"reversed\":{\"x\":true,\"y\":false}},"
                        + "\"baselines\":{\"first\":3.0,\"last\":null},"
                        + "\"top_margin\":{\"positive\":1.5,\"negative\":-0.5},"
                        + "\"bottom_margin\":{\"positive\":0.0,\"negative\":0.0},"
                        + "\"margins_can_collapse_through\":true,"
                        + "\"oof_positioning_area\":{\"size\":{\"width\":6.0,\"height\":7.0},\"offset\":{\"x\":1.0,\"y\":2.0}}}",
                GSON.toJson(out));

        LayoutInput input = LayoutInput.HIDDEN;
        assertEquals(
                "{\"run_mode\":\"PerformHiddenLayout\",\"sizing_mode\":\"InherentSize\",\"axis\":\"Both\","
                        + "\"known_dimensions\":{\"width\":null,\"height\":null},"
                        + "\"known_dimensions_are_definite\":{\"width\":true,\"height\":true},"
                        + "\"parent_size\":{\"width\":null,\"height\":null},"
                        + "\"available_space\":{\"width\":\"MaxContent\",\"height\":\"MaxContent\"},"
                        + "\"vertical_margins_are_collapsible\":{\"start\":false,\"end\":false}}",
                GSON.toJson(input));

        // all serialize-only: read() must throw
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{}", Layout.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{}", LayoutInput.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{}", LayoutOutput.class));
    }

    @Test
    void serde_edge_behaviors() {
        // serde_json serializes non-finite floats as null (silently)
        Style nanStyle = Style.DEFAULT
                .withScrollbarWidth(java.lang.Float.NaN)
                .withFlexGrow(java.lang.Float.POSITIVE_INFINITY);
        String json = GSON.toJson(nanStyle);
        assertTrue(json.contains("\"scrollbar_width\":null"), json);
        assertTrue(json.contains("\"flex_grow\":null"), json);

        // Option<f32> Some(NaN) -> null, reads back as None (same lossy asymmetry)
        Style nanAspect = Style.DEFAULT.withAspectRatio(java.lang.Float.NaN);
        String json2 = GSON.toJson(nanAspect);
        assertTrue(json2.contains("\"aspect_ratio\":null"), json2);
        assertTrue(OptF32.isNone(GSON.fromJson(json2, Style.class).aspectRatio()));

        // null is not a valid f32
        assertThrows(
                Exception.class,
                () -> GSON.fromJson("{\"scrollbar_width\":null}", Style.class));

        // serde u16/i16/u8 range-check; Gson nextInt does not — our helpers do
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{\"Span\":65536}", GridPlacement.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{\"Span\":-1}", GridPlacement.class));
        assertEquals(GridPlacement.fromSpan(2), GSON.fromJson("{\"Span\":2}", GridPlacement.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("-40000", GridLine.class));
        assertEquals(GridLine.from(-32768), GSON.fromJson("-32768", GridLine.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("32768", GridLine.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{\"flex_line_count\":70000}", Style.class));
        assertThrows(JsonParseException.class, () -> GSON.fromJson("{\"contain\":256}", Style.class));

        // serde_json parses strict JSON: NaN/Infinity literals, single quotes,
        // unquoted names are rejected (Gson defaults to lenient — we pin strict)
        assertThrows(Exception.class, () -> GSON.fromJson("NaN", Float.class));
        assertThrows(Exception.class, () -> GSON.fromJson("Infinity", Float.class));
        assertThrows(Exception.class, () -> GSON.fromJson("{'display':'Flex'}", Style.class));
        assertThrows(Exception.class, () -> GSON.fromJson("{display:'Flex'}", Style.class));
    }
}
