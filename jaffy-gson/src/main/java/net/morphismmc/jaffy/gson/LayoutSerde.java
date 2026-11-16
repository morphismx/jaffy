// JAFFY: serde impls of upstream/src/tree/layout.rs.
//
// Every type here derives `Serialize` only upstream — there is no Deserialize
// impl, so the read side fails (a JsonParseException is the runtime equivalent
// of upstream's missing impl). `ScrollableOverflow` has a hand-written
// Serialize emitting `{rect, reversed}` (the packed sign bits decoded);
// `LayoutOutput.oof_candidates` is `#[serde(skip_serializing)]`.
package net.morphismmc.jaffy.gson;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
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

final class LayoutSerde {

    private LayoutSerde() {}

    private static JsonParseException serializeOnly(String type) {
        return new JsonParseException(type + " has no Deserialize impl upstream (Serialize only)");
    }

    // ---- Unit-variant enums (serialize-only) ----

    static final TypeAdapter<RunMode> RUN_MODE = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, RunMode v) throws IOException {
            out.value(JsonSupport.serdeName(v));
        }

        @Override
        public RunMode read(JsonReader in) {
            throw serializeOnly("RunMode");
        }
    };

    static final TypeAdapter<SizingMode> SIZING_MODE = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, SizingMode v) throws IOException {
            out.value(JsonSupport.serdeName(v));
        }

        @Override
        public SizingMode read(JsonReader in) {
            throw serializeOnly("SizingMode");
        }
    };

    static final TypeAdapter<RequestedAxis> REQUESTED_AXIS = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, RequestedAxis v) throws IOException {
            out.value(JsonSupport.serdeName(v));
        }

        @Override
        public RequestedAxis read(JsonReader in) {
            throw serializeOnly("RequestedAxis");
        }
    };

    // ---- CollapsibleMarginSet: {positive, negative} ----

    static final TypeAdapter<CollapsibleMarginSet> COLLAPSIBLE_MARGIN_SET = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, CollapsibleMarginSet v) throws IOException {
            out.beginObject();
            out.name("positive");
            JsonSupport.writeF32(out, v.positive());
            out.name("negative");
            JsonSupport.writeF32(out, v.negative());
            out.endObject();
        }

        @Override
        public CollapsibleMarginSet read(JsonReader in) {
            throw serializeOnly("CollapsibleMarginSet");
        }
    };

    // ---- Baselines: {first: Option<f32>, last: Option<f32>} ----

    static final TypeAdapter<Baselines> BASELINES = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Baselines v) throws IOException {
            out.beginObject();
            out.name("first");
            JsonSupport.writeOptF32(out, v.first());
            out.name("last");
            JsonSupport.writeOptF32(out, v.last());
            out.endObject();
        }

        @Override
        public Baselines read(JsonReader in) {
            throw serializeOnly("Baselines");
        }
    };

    // ---- OofPositioningArea: {size, offset} ----

    static final TypeAdapter<OofPositioningArea> OOF_POSITIONING_AREA = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, OofPositioningArea v) throws IOException {
            out.beginObject();
            out.name("size");
            GeometrySerde.SIZE_F32.write(out, v.size());
            out.name("offset");
            GeometrySerde.POINT_F32.write(out, v.offset());
            out.endObject();
        }

        @Override
        public OofPositioningArea read(JsonReader in) {
            throw serializeOnly("OofPositioningArea");
        }
    };

    // ---- ScrollableOverflow (custom Serialize: {rect, reversed}) ----

    static final TypeAdapter<ScrollableOverflow> SCROLLABLE_OVERFLOW = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, ScrollableOverflow v) throws IOException {
            out.beginObject();
            out.name("rect");
            GeometrySerde.RECT_F32.write(out, v.rect());
            out.name("reversed");
            GeometrySerde.POINT_BOOL.write(out, v.isReversed());
            out.endObject();
        }

        @Override
        public ScrollableOverflow read(JsonReader in) {
            throw serializeOnly("ScrollableOverflow");
        }
    };

    // ---- LayoutInput ----

    private static final TypeAdapter<Size<AvailableSpace>> AVAILABLE_SPACE_SIZE =
            GeometrySerde.sizeAdapter(StyleSerde.AVAILABLE_SPACE);

    static final TypeAdapter<LayoutInput> LAYOUT_INPUT = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, LayoutInput v) throws IOException {
            out.beginObject();
            out.name("run_mode");
            RUN_MODE.write(out, v.runMode());
            out.name("sizing_mode");
            SIZING_MODE.write(out, v.sizingMode());
            out.name("axis");
            REQUESTED_AXIS.write(out, v.axis());
            out.name("known_dimensions");
            GeometrySerde.SIZE_OPT_F32.write(out, v.knownDimensions());
            out.name("known_dimensions_are_definite");
            GeometrySerde.SIZE_BOOL.write(out, v.knownDimensionsAreDefinite());
            out.name("parent_size");
            GeometrySerde.SIZE_OPT_F32.write(out, v.parentSize());
            out.name("available_space");
            AVAILABLE_SPACE_SIZE.write(out, v.availableSpace());
            out.name("vertical_margins_are_collapsible");
            GeometrySerde.LINE_BOOL.write(out, v.verticalMarginsAreCollapsible());
            out.endObject();
        }

        @Override
        public LayoutInput read(JsonReader in) {
            throw serializeOnly("LayoutInput");
        }
    };

    // ---- LayoutOutput (oof_candidates skipped) ----

    static final TypeAdapter<LayoutOutput> LAYOUT_OUTPUT = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, LayoutOutput v) throws IOException {
            out.beginObject();
            out.name("size");
            GeometrySerde.SIZE_F32.write(out, v.size());
            out.name("scrollable_overflow_rect");
            SCROLLABLE_OVERFLOW.write(out, v.scrollableOverflowRect());
            out.name("baselines");
            BASELINES.write(out, v.baselines());
            out.name("top_margin");
            COLLAPSIBLE_MARGIN_SET.write(out, v.topMargin());
            out.name("bottom_margin");
            COLLAPSIBLE_MARGIN_SET.write(out, v.bottomMargin());
            out.name("margins_can_collapse_through");
            out.value(v.marginsCanCollapseThrough());
            // oof_candidates: #[serde(skip_serializing)]
            out.name("oof_positioning_area");
            if (v.oofPositioningArea() == null) {
                out.nullValue();
            } else {
                OOF_POSITIONING_AREA.write(out, v.oofPositioningArea());
            }
            out.endObject();
        }

        @Override
        public LayoutOutput read(JsonReader in) {
            throw serializeOnly("LayoutOutput");
        }
    };

    // ---- Layout ----

    static final TypeAdapter<Layout> LAYOUT = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Layout v) throws IOException {
            out.beginObject();
            out.name("order");
            out.value(Integer.toUnsignedLong(v.order())); // u32
            out.name("location");
            GeometrySerde.POINT_F32.write(out, v.location());
            out.name("size");
            GeometrySerde.SIZE_F32.write(out, v.size());
            out.name("scrollable_overflow_rect");
            SCROLLABLE_OVERFLOW.write(out, v.scrollableOverflowRect());
            out.name("scrollbar_size");
            GeometrySerde.SIZE_F32.write(out, v.scrollbarSize());
            out.name("border");
            GeometrySerde.RECT_F32.write(out, v.border());
            out.name("padding");
            GeometrySerde.RECT_F32.write(out, v.padding());
            out.name("margin");
            GeometrySerde.RECT_F32.write(out, v.margin());
            out.endObject();
        }

        @Override
        public Layout read(JsonReader in) {
            throw serializeOnly("Layout");
        }
    };
}
