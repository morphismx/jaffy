// JAFFY-UPSTREAM: none
// JAFFY-NOTE: entry point of the `serde` feature port — a Gson configured to
// produce serde_json-identical JSON for every serde-decorated upstream type.
//
// Registration mirrors upstream's impl coverage:
//   - serde derives -> exact-type TypeAdapters (records/enums/structs)
//   - serde'd sealed families -> type-hierarchy adapters (runtime dispatch is
//     a Java necessity; upstream resolves statically)
//   - `impl<T: Serialize>` generics -> a reflective-argument factory
//   - Serialize-only types (tree/layout.rs, AvailableSpace) -> write support
//     only; read() throws like upstream's missing Deserialize
//   - custom impls (CompactLength u64, AlignItems/AlignContent tag strings,
//     ScrollableOverflow {rect, reversed}) -> dedicated adapters
//
// `disableHtmlEscaping` is required for byte-identical output: serde_json only
// escapes quotes, backslashes and control characters.
package net.morphismmc.jaffy.gson;

import java.io.IOException;

import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.PointBool;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.BoxGenerationMode;
import net.morphismmc.jaffy.style.BoxSizing;
import net.morphismmc.jaffy.style.CompactLength;
import net.morphismmc.jaffy.style.Contain;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.block.TextAlign;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.jaffy.style.grid.GenericGridPlacement;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GenericRepetition;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateArea;
import net.morphismmc.jaffy.style.grid.GridTemplateAreas;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
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
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.ScrollableOverflow;
import net.morphismmc.jaffy.tree.layout.SizingMode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public final class JaffyGson {

    private JaffyGson() {}

    private static final Gson GSON = builder().create();

    /// A shared configured instance (`serde_json`'s global behaviour is
    /// stateless; the adapter set is immutable).
    public static Gson gson() {
        return GSON;
    }

    /// A GsonBuilder pre-populated with every jaffy serde adapter. Callers may
    /// layer their own registrations on top.
    public static GsonBuilder builder() {
        // serializeNulls: serde emits Option::None fields as explicit `null`;
        // without this Gson's JsonWriter silently drops name+null pairs.
        GsonBuilder b = new GsonBuilder().disableHtmlEscaping().serializeNulls();

        // ---- Generic impl<T: Serialize/Deserialize> factories ----
        b.registerTypeAdapterFactory(GeometrySerde.GENERIC);

        // ---- Primitive specializations / standalone structs ----
        b.registerTypeAdapter(LineF32.class, GeometrySerde.LINE_F32);
        b.registerTypeAdapter(SizeF32.class, GeometrySerde.SIZE_F32);
        b.registerTypeAdapter(PointF32.class, GeometrySerde.POINT_F32);
        b.registerTypeAdapter(RectF32.class, GeometrySerde.RECT_F32);
        b.registerTypeAdapter(SizeOptF32.class, GeometrySerde.SIZE_OPT_F32);
        b.registerTypeAdapter(SizeBool.class, GeometrySerde.SIZE_BOOL);
        b.registerTypeAdapter(LineBool.class, GeometrySerde.LINE_BOOL);
        b.registerTypeAdapter(PointBool.class, GeometrySerde.POINT_BOOL);
        b.registerTypeAdapter(FloatInsets.class, GeometrySerde.FLOAT_INSETS);

        // serde f32 (ryu pretty) for bare Float values — every f32 in the serde
        // surface goes through this or JsonSupport.writeF32.
        TypeAdapter<Float> f32Adapter = new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, Float v) throws IOException {
                if (v == null) {
                    out.nullValue();
                } else {
                    JsonSupport.writeF32(out, v);
                }
            }

            @Override
            public Float read(JsonReader in) throws IOException {
                if (in.peek() == JsonToken.NULL) {
                    in.nextNull();
                    return null;
                }
                return JsonSupport.readF32(in);
            }
        };
        b.registerTypeAdapter(Float.class, f32Adapter);
        b.registerTypeAdapter(float.class, f32Adapter);

        // ---- CompactLength + dimension newtypes (custom impls) ----
        b.registerTypeHierarchyAdapter(CompactLength.class, DimensionSerde.COMPACT_LENGTH);
        b.registerTypeHierarchyAdapter(LengthPercentage.class, DimensionSerde.LENGTH_PERCENTAGE);
        b.registerTypeHierarchyAdapter(LengthPercentageAuto.class, DimensionSerde.LENGTH_PERCENTAGE_AUTO);
        b.registerTypeHierarchyAdapter(Dimension.class, DimensionSerde.DIMENSION);
        b.registerTypeHierarchyAdapter(MinTrackSizingFunction.class, DimensionSerde.MIN_TRACK_SIZING);
        b.registerTypeHierarchyAdapter(MaxTrackSizingFunction.class, DimensionSerde.MAX_TRACK_SIZING);

        // ---- Alignment ----
        b.registerTypeAdapter(AlignItems.class, AlignmentSerde.ALIGN_ITEMS);
        b.registerTypeAdapter(AlignContent.class, AlignmentSerde.ALIGN_CONTENT);
        b.registerTypeAdapter(AlignItemsKeyword.class, AlignmentSerde.unitEnum(AlignItemsKeyword.class));
        b.registerTypeAdapter(AlignContentKeyword.class, AlignmentSerde.unitEnum(AlignContentKeyword.class));
        b.registerTypeAdapter(AlignmentSafety.class, AlignmentSerde.unitEnum(AlignmentSafety.class));

        // ---- Grid ----
        b.registerTypeAdapter(GridLine.class, GridSerde.GRID_LINE);
        b.registerTypeAdapter(RepetitionCount.class, GridSerde.REPETITION_COUNT);
        b.registerTypeAdapter(GridTemplateRepetition.class, GridSerde.GRID_TEMPLATE_REPETITION);
        b.registerTypeAdapter(GridTemplateArea.class, GridSerde.GRID_TEMPLATE_AREA);
        b.registerTypeAdapter(GridTemplateAreas.class, GridSerde.GRID_TEMPLATE_AREAS);
        b.registerTypeAdapter(NamedGridLine.class, GridSerde.NAMED_GRID_LINE);
        b.registerTypeHierarchyAdapter(GridPlacement.class, GridSerde.GRID_PLACEMENT);
        b.registerTypeHierarchyAdapter(RepetitionCount.class, GridSerde.REPETITION_COUNT);
        b.registerTypeHierarchyAdapter(
                GenericGridTemplateComponent.class, GridSerde.componentAdapter(GridSerde.GRID_TEMPLATE_REPETITION));
        b.registerTypeHierarchyAdapter(GenericRepetition.class, GridSerde.GRID_TEMPLATE_REPETITION);
        b.registerTypeHierarchyAdapter(GenericGridPlacement.class, genericPlacementRuntime());

        // ---- Style enums + Contain + Style ----
        b.registerTypeAdapter(Display.class, StyleSerde.DISPLAY);
        b.registerTypeAdapter(BoxGenerationMode.class, StyleSerde.BOX_GENERATION_MODE);
        b.registerTypeAdapter(Position.class, StyleSerde.POSITION);
        b.registerTypeAdapter(BoxSizing.class, StyleSerde.BOX_SIZING);
        b.registerTypeAdapter(Overflow.class, StyleSerde.OVERFLOW);
        b.registerTypeAdapter(Direction.class, StyleSerde.DIRECTION);
        b.registerTypeAdapter(net.morphismmc.jaffy.style.float_.Float.class, StyleSerde.FLOAT);
        b.registerTypeAdapter(FloatDirection.class, StyleSerde.FLOAT_DIRECTION);
        b.registerTypeAdapter(Clear.class, StyleSerde.CLEAR);
        b.registerTypeAdapter(TextAlign.class, StyleSerde.TEXT_ALIGN);
        b.registerTypeAdapter(FlexDirection.class, StyleSerde.FLEX_DIRECTION);
        b.registerTypeAdapter(FlexWrap.class, StyleSerde.FLEX_WRAP);
        b.registerTypeAdapter(GridAutoFlow.class, StyleSerde.GRID_AUTO_FLOW);
        b.registerTypeAdapter(Contain.class, StyleSerde.CONTAIN);
        b.registerTypeHierarchyAdapter(AvailableSpace.class, StyleSerde.AVAILABLE_SPACE);
        b.registerTypeAdapter(Style.class, StyleSerde.STYLE);

        // ---- Layout/tree types (Serialize-only upstream) ----
        b.registerTypeAdapter(RunMode.class, LayoutSerde.RUN_MODE);
        b.registerTypeAdapter(SizingMode.class, LayoutSerde.SIZING_MODE);
        b.registerTypeAdapter(RequestedAxis.class, LayoutSerde.REQUESTED_AXIS);
        b.registerTypeAdapter(CollapsibleMarginSet.class, LayoutSerde.COLLAPSIBLE_MARGIN_SET);
        b.registerTypeAdapter(Baselines.class, LayoutSerde.BASELINES);
        b.registerTypeAdapter(OofPositioningArea.class, LayoutSerde.OOF_POSITIONING_AREA);
        b.registerTypeAdapter(ScrollableOverflow.class, LayoutSerde.SCROLLABLE_OVERFLOW);
        b.registerTypeAdapter(LayoutInput.class, LayoutSerde.LAYOUT_INPUT);
        b.registerTypeAdapter(LayoutOutput.class, LayoutSerde.LAYOUT_OUTPUT);
        b.registerTypeAdapter(Layout.class, LayoutSerde.LAYOUT);

        return b;
    }

    /// Raw `GenericGridPlacement` lookup (runtime record class — the coordinate
    /// type argument is erased). Dispatch on the Line payload's runtime class;
    /// upstream's `LineType: Deserialize` bound only holds for `GridLine`.
    private static TypeAdapter<GenericGridPlacement<?>> genericPlacementRuntime() {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, GenericGridPlacement<?> v) throws IOException {
                switch (v) {
                    case GenericGridPlacement.Auto<?> a -> out.value("Auto");
                    case GenericGridPlacement.Line<?> l -> {
                        if (!(l.line() instanceof GridLine gl)) {
                            throw new JsonParseException("no serde impl for coordinate type "
                                    + l.line().getClass());
                        }
                        out.beginObject();
                        out.name("Line");
                        GridSerde.GRID_LINE.write(out, gl);
                        out.endObject();
                    }
                    case GenericGridPlacement.Span<?> s -> {
                        out.beginObject();
                        out.name("Span");
                        out.value(s.span());
                        out.endObject();
                    }
                }
            }

            @Override
            public GenericGridPlacement<?> read(JsonReader in) throws IOException {
                JsonSupport.strict(in);
                if (in.peek() == JsonToken.STRING) {
                    if ("Auto".equals(in.nextString())) {
                        return new GenericGridPlacement.Auto<>();
                    }
                    throw new JsonParseException("unknown variant for GenericGridPlacement");
                }
                in.beginObject();
                String tag = in.nextName();
                GenericGridPlacement<?> value = switch (tag) {
                    case "Line" -> new GenericGridPlacement.Line<>(GridSerde.GRID_LINE.read(in));
                    case "Span" -> new GenericGridPlacement.Span<>(JsonSupport.readU16(in));
                    default -> throw new JsonParseException("unknown variant `" + tag + "` for GenericGridPlacement");
                };
                if (in.hasNext()) {
                    throw new JsonParseException("expected single variant key");
                }
                in.endObject();
                return value;
            }
        };
    }
}
