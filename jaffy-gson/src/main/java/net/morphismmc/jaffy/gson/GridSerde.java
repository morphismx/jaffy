// JAFFY: upstream=src/style/grid.rs
// JAFFY-FROM: src/compute/grid/types/coordinates.rs
// JAFFY-NOTE: serde impls
// src/compute/grid/types/coordinates.rs (GridLine).
//
// Enum wire shapes are serde's default external tagging: unit variants as
// "Name", newtype variants as {"Name": inner}, tuple variants as
// {"Name": [a, b]}. `S: CheapCloneStr` is String in jaffy (GAP-017); u16/i16
// map to int; `Vec`/`GridTrackVec` map to List.
package net.morphismmc.jaffy.gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.morphismmc.jaffy.compute.grid.types.coordinates.GridLine;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GenericRepetition;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateArea;
import net.morphismmc.jaffy.style.grid.GridTemplateAreas;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.NamedGridLine;
import net.morphismmc.jaffy.style.grid.RepetitionCount;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

final class GridSerde {

    private GridSerde() {}

    private static void checkDuplicate(Set<String> seen, String name) {
        if (!seen.add(name)) {
            throw new JsonParseException("duplicate field `" + name + "`");
        }
    }

    /// `GridLine(i16)` — repr(transparent) tuple newtype serializes as the bare
    /// integer.
    static final TypeAdapter<GridLine> GRID_LINE = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, GridLine v) throws IOException {
            out.value(v.asI16());
        }

        @Override
        public GridLine read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return GridLine.from(JsonSupport.readI16(in));
        }
    };

    // ---- GridPlacement<S>: externally tagged ----

    static final TypeAdapter<GridPlacement> GRID_PLACEMENT = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, GridPlacement v) throws IOException {
            switch (v) {
                case GridPlacement.Auto a -> out.value("Auto");
                case GridPlacement.Line l -> {
                    out.beginObject();
                    out.name("Line");
                    GRID_LINE.write(out, l.line());
                    out.endObject();
                }
                case GridPlacement.NamedLine n -> {
                    out.beginObject();
                    out.name("NamedLine");
                    out.beginArray();
                    // JAFFY(GAP-020): Gson escapes U+2028/U+2029 in names; serde_json does not.
                    out.value(n.name());
                    out.value(n.index());
                    out.endArray();
                    out.endObject();
                }
                case GridPlacement.Span s -> {
                    out.beginObject();
                    out.name("Span");
                    out.value(s.span());
                    out.endObject();
                }
                case GridPlacement.NamedSpan n -> {
                    out.beginObject();
                    out.name("NamedSpan");
                    out.beginArray();
                    // JAFFY(GAP-020): Gson escapes U+2028/U+2029 in names; serde_json does not.
                    out.value(n.name());
                    out.value(n.span());
                    out.endArray();
                    out.endObject();
                }
            }
        }

        @Override
        public GridPlacement read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            if (in.peek() == JsonToken.STRING) {
                String tag = in.nextString();
                if ("Auto".equals(tag)) {
                    return GridPlacement.AUTO;
                }
                throw new JsonParseException("unknown variant `" + tag + "` for GridPlacement");
            }
            in.beginObject();
            if (!in.hasNext()) {
                throw new JsonParseException("expected externally tagged GridPlacement");
            }
            String tag = in.nextName();
            GridPlacement value = switch (tag) {
                case "Line" -> new GridPlacement.Line(GRID_LINE.read(in));
                case "NamedLine" -> {
                    in.beginArray();
                    String name = in.nextString();
                    int index = JsonSupport.readI16(in);
                    in.endArray();
                    yield new GridPlacement.NamedLine(name, index);
                }
                case "Span" -> new GridPlacement.Span(JsonSupport.readU16(in));
                case "NamedSpan" -> {
                    in.beginArray();
                    String name = in.nextString();
                    int span = JsonSupport.readU16(in);
                    in.endArray();
                    yield new GridPlacement.NamedSpan(name, span);
                }
                default -> throw new JsonParseException("unknown variant `" + tag + "` for GridPlacement");
            };
            if (in.hasNext()) {
                throw new JsonParseException("expected single variant key in GridPlacement");
            }
            in.endObject();
            return value;
        }
    };

    // ---- RepetitionCount ----

    static final TypeAdapter<RepetitionCount> REPETITION_COUNT = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, RepetitionCount v) throws IOException {
            switch (v) {
                case RepetitionCount.AutoFill a -> out.value("AutoFill");
                case RepetitionCount.AutoFit a -> out.value("AutoFit");
                case RepetitionCount.Count c -> {
                    out.beginObject();
                    out.name("Count");
                    out.value(c.count());
                    out.endObject();
                }
            }
        }

        @Override
        public RepetitionCount read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            if (in.peek() == JsonToken.STRING) {
                String tag = in.nextString();
                return switch (tag) {
                    case "AutoFill" -> RepetitionCount.AUTO_FILL;
                    case "AutoFit" -> RepetitionCount.AUTO_FIT;
                    default -> throw new JsonParseException("unknown variant `" + tag + "` for RepetitionCount");
                };
            }
            in.beginObject();
            if (!in.hasNext()) {
                throw new JsonParseException("expected externally tagged RepetitionCount");
            }
            String tag = in.nextName();
            RepetitionCount value = switch (tag) {
                case "Count" -> new RepetitionCount.Count(JsonSupport.readU16(in));
                default -> throw new JsonParseException("unknown variant `" + tag + "` for RepetitionCount");
            };
            if (in.hasNext()) {
                throw new JsonParseException("expected single variant key in RepetitionCount");
            }
            in.endObject();
            return value;
        }
    };

    // ---- GridTemplateRepetition: {count, tracks, line_names} ----

    static final TypeAdapter<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> TRACK_SIZING =
            new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> v)
                        throws IOException {
                    out.beginObject();
                    out.name("min");
                    DimensionSerde.MIN_TRACK_SIZING.write(out, v.min());
                    out.name("max");
                    DimensionSerde.MAX_TRACK_SIZING.write(out, v.max());
                    out.endObject();
                }

                @Override
                public MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> read(JsonReader in) throws IOException {
                    JsonSupport.strict(in);
                    Set<String> seen = new HashSet<>();
                    MinTrackSizingFunction min = null;
                    MaxTrackSizingFunction max = null;
                    in.beginObject();
                    while (in.hasNext()) {
                        String name = in.nextName();
                        checkDuplicate(seen, name);
                        switch (name) {
                            case "min" -> min = DimensionSerde.MIN_TRACK_SIZING.read(in);
                            case "max" -> max = DimensionSerde.MAX_TRACK_SIZING.read(in);
                            default -> in.skipValue();
                        }
                    }
                    in.endObject();
                    if (min == null || max == null) {
                        throw new JsonParseException("missing field in TrackSizingFunction (min/max required)");
                    }
                    return new MinMax<>(min, max);
                }
            };

    static final TypeAdapter<GridTemplateRepetition> GRID_TEMPLATE_REPETITION = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, GridTemplateRepetition v) throws IOException {
            out.beginObject();
            out.name("count");
            REPETITION_COUNT.write(out, v.count());
            out.name("tracks");
            writeTracks(out, v.tracks());
            out.name("line_names");
            GeometrySerde.writeStringLists(out, v.lineNames());
            out.endObject();
        }

        @Override
        public GridTemplateRepetition read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            RepetitionCount count = null;
            List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> tracks = null;
            List<List<String>> lineNames = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "count" -> count = REPETITION_COUNT.read(in);
                    case "tracks" -> tracks = readTracks(in);
                    case "line_names" -> lineNames = GeometrySerde.readStringLists(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (count == null || tracks == null || lineNames == null) {
                throw new JsonParseException(
                        "missing field in GridTemplateRepetition (count/tracks/line_names required)");
            }
            return new GridTemplateRepetition(count, tracks, lineNames);
        }
    };

    private static void writeTracks(JsonWriter out, List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> v)
            throws IOException {
        out.beginArray();
        for (MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> t : v) {
            TRACK_SIZING.write(out, t);
        }
        out.endArray();
    }

    private static List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> readTracks(JsonReader in)
            throws IOException {
        List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> tracks = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            tracks.add(TRACK_SIZING.read(in));
        }
        in.endArray();
        return tracks;
    }

    // ---- GenericGridTemplateComponent<R>: {"Single": tsf} | {"Repeat": rep} ----

    /// Construct a component adapter for a concrete repetition adapter.
    static <R extends GenericRepetition> TypeAdapter<GenericGridTemplateComponent<R>> componentAdapter(
            TypeAdapter<R> repAdapter) {
        return new ComponentAdapter<>(repAdapter);
    }

    private static final class ComponentAdapter<R extends GenericRepetition>
            extends TypeAdapter<GenericGridTemplateComponent<R>> {
        private final TypeAdapter<R> repAdapter;

        ComponentAdapter(TypeAdapter<R> repAdapter) {
            this.repAdapter = repAdapter;
        }

        @Override
        public void write(JsonWriter out, GenericGridTemplateComponent<R> v) throws IOException {
            out.beginObject();
            switch (v) {
                case GenericGridTemplateComponent.Single<R> s -> {
                    out.name("Single");
                    TRACK_SIZING.write(out, s.size());
                }
                case GenericGridTemplateComponent.Repeat<R> r -> {
                    out.name("Repeat");
                    repAdapter.write(out, r.repetition());
                }
            }
            out.endObject();
        }

        @Override
        public GenericGridTemplateComponent<R> read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            in.beginObject();
            if (!in.hasNext()) {
                throw new JsonParseException("expected externally tagged GridTemplateComponent");
            }
            String tag = in.nextName();
            GenericGridTemplateComponent<R> value = switch (tag) {
                case "Single" -> new GenericGridTemplateComponent.Single<>(TRACK_SIZING.read(in));
                case "Repeat" -> new GenericGridTemplateComponent.Repeat<>(repAdapter.read(in));
                default -> throw new JsonParseException("unknown variant `" + tag + "` for GridTemplateComponent");
            };
            if (in.hasNext()) {
                throw new JsonParseException("expected single variant key in GridTemplateComponent");
            }
            in.endObject();
            return value;
        }
    }

    // ---- Named structs ----

    static final TypeAdapter<GridTemplateArea> GRID_TEMPLATE_AREA = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, GridTemplateArea v) throws IOException {
            out.beginObject();
            out.name("name");
            // JAFFY(GAP-020): Gson escapes U+2028/U+2029 in names; serde_json does not.
            out.value(v.name());
            out.name("row_start");
            out.value(v.rowStart());
            out.name("row_end");
            out.value(v.rowEnd());
            out.name("column_start");
            out.value(v.columnStart());
            out.name("column_end");
            out.value(v.columnEnd());
            out.endObject();
        }

        @Override
        public GridTemplateArea read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            String name = null;
            Integer rowStart = null;
            Integer rowEnd = null;
            Integer columnStart = null;
            Integer columnEnd = null;
            in.beginObject();
            while (in.hasNext()) {
                String n = in.nextName();
                checkDuplicate(seen, n);
                switch (n) {
                    case "name" -> name = in.nextString();
                    case "row_start" -> rowStart = JsonSupport.readU16(in);
                    case "row_end" -> rowEnd = JsonSupport.readU16(in);
                    case "column_start" -> columnStart = JsonSupport.readU16(in);
                    case "column_end" -> columnEnd = JsonSupport.readU16(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (name == null || rowStart == null || rowEnd == null || columnStart == null || columnEnd == null) {
                throw new JsonParseException("missing field in GridTemplateArea");
            }
            return new GridTemplateArea(name, rowStart, rowEnd, columnStart, columnEnd);
        }
    };

    static final TypeAdapter<GridTemplateAreas> GRID_TEMPLATE_AREAS = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, GridTemplateAreas v) throws IOException {
            out.beginObject();
            out.name("areas");
            out.beginArray();
            for (GridTemplateArea a : v.areas()) {
                GRID_TEMPLATE_AREA.write(out, a);
            }
            out.endArray();
            out.name("row_count");
            out.value(v.rowCount());
            out.name("column_count");
            out.value(v.columnCount());
            out.endObject();
        }

        @Override
        public GridTemplateAreas read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            List<GridTemplateArea> areas = null;
            Integer rowCount = null;
            Integer columnCount = null;
            in.beginObject();
            while (in.hasNext()) {
                String n = in.nextName();
                checkDuplicate(seen, n);
                switch (n) {
                    case "areas" -> {
                        areas = new ArrayList<>();
                        in.beginArray();
                        while (in.hasNext()) {
                            areas.add(GRID_TEMPLATE_AREA.read(in));
                        }
                        in.endArray();
                    }
                    case "row_count" -> rowCount = JsonSupport.readU16(in);
                    case "column_count" -> columnCount = JsonSupport.readU16(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (areas == null || rowCount == null || columnCount == null) {
                throw new JsonParseException("missing field in GridTemplateAreas");
            }
            return new GridTemplateAreas(areas, rowCount, columnCount);
        }
    };

    static final TypeAdapter<NamedGridLine> NAMED_GRID_LINE = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, NamedGridLine v) throws IOException {
            out.beginObject();
            out.name("name");
            // JAFFY(GAP-020): Gson escapes U+2028/U+2029 in names; serde_json does not.
            out.value(v.name());
            out.name("index");
            out.value(v.index());
            out.endObject();
        }

        @Override
        public NamedGridLine read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            String name = null;
            Integer index = null;
            in.beginObject();
            while (in.hasNext()) {
                String n = in.nextName();
                checkDuplicate(seen, n);
                switch (n) {
                    case "name" -> name = in.nextString();
                    case "index" -> index = JsonSupport.readU16(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (name == null || index == null) {
                throw new JsonParseException("missing field in NamedGridLine");
            }
            return new NamedGridLine(name, index);
        }
    };
}
