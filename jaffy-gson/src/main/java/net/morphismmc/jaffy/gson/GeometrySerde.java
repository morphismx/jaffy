// JAFFY: serde impls of upstream/src/geometry.rs.
//
// `impl<T: Serialize> Serialize/Deserialize for Point/Size/Rect/Line<T>` and
// `MinMax<Min, Max>` map to TypeAdapterFactory adapters that resolve the
// element adapter reflectively. `#[serde(default)]` on `Line<T>` means missing
// fields become `T::default()` (wired per element type in `fieldDefault`).
// Primitive specializations (SizeF32/PointF32/RectF32/SizeOptF32/SizeBool/
// LineBool/PointBool) are jaffy's `Size<f32>`/… counterparts — same wire shape.
package net.morphismmc.jaffy.gson;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.LineF32;
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
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.GridTemplateTracks;

final class GeometrySerde {

    private GeometrySerde() {}

    /// `T::default()` for `Line<T>`'s missing-field fill (`#[serde(default)]`).
    /// Only element types with a Rust `Default` impl may appear here.
    private static @org.jspecify.annotations.Nullable Supplier<?> fieldDefault(Class<?> elementType) {
        if (elementType == GridPlacement.class) {
            return () -> GridPlacement.AUTO;
        }
        if (elementType == Boolean.class) {
            return () -> Boolean.FALSE;
        }
        return null;
    }

    static final TypeAdapterFactory GENERIC = new TypeAdapterFactory() {
        @Override
        // The TypeAdapter<T> casts below are an inherent requirement of Gson's
        // TypeAdapterFactory API (TypeToken cannot be proven at compile time);
        // javac unchecked warnings are expected here.
        public <T> @org.jspecify.annotations.Nullable TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> raw = type.getRawType();
            Type[] args = type.getType() instanceof ParameterizedType pt ? pt.getActualTypeArguments() : null;
            if (raw == Point.class && args != null) {
                return (TypeAdapter<T>) new PointAdapter<>(gson.getAdapter(TypeToken.get(args[0])));
            }
            if (raw == Size.class && args != null) {
                return (TypeAdapter<T>) new SizeAdapter<>(gson.getAdapter(TypeToken.get(args[0])));
            }
            if (raw == Rect.class && args != null) {
                return (TypeAdapter<T>) new RectAdapter<>(gson.getAdapter(TypeToken.get(args[0])));
            }
            if (raw == Line.class && args != null) {
                Type arg = args[0];
                Class<?> argRaw = arg instanceof Class<?> c ? c : (Class<?>) ((ParameterizedType) arg).getRawType();
                TypeAdapter<Object> elemAdapter = (TypeAdapter<Object>) gson.getAdapter(TypeToken.get(arg));
                return (TypeAdapter<T>) new LineAdapter<>(elemAdapter, (Supplier<Object>) fieldDefault(argRaw));
            }
            if (raw == MinMax.class && args != null) {
                return (TypeAdapter<T>) new MinMaxAdapter<>(
                        gson.getAdapter(TypeToken.get(args[0])), gson.getAdapter(TypeToken.get(args[1])));
            }
            if (raw == GridTemplateTracks.class && args != null) {
                return (TypeAdapter<T>) new GridTemplateTracksAdapter<>(gson.getAdapter(TypeToken.get(args[0])));
            }
            return null;
        }
    };

    private static void checkDuplicate(Set<String> seen, String name) {
        if (!seen.add(name)) {
            throw new JsonParseException("duplicate field `" + name + "`");
        }
    }

    // Constructors for the generic adapters — used by field adapters that know
    // the concrete element type (e.g. Style's `Point<Overflow>`).
    static <T> TypeAdapter<Point<T>> pointAdapter(TypeAdapter<T> element) {
        return new PointAdapter<>(element);
    }

    static <T> TypeAdapter<Size<T>> sizeAdapter(TypeAdapter<T> element) {
        return new SizeAdapter<>(element);
    }

    static <T> TypeAdapter<Rect<T>> rectAdapter(TypeAdapter<T> element) {
        return new RectAdapter<>(element);
    }

    static <T> TypeAdapter<Line<T>> lineAdapter(
            TypeAdapter<T> element, @org.jspecify.annotations.Nullable Supplier<T> fieldDefault) {
        return new LineAdapter<>(element, fieldDefault);
    }

    static <A, B> TypeAdapter<MinMax<A, B>> minMaxAdapter(TypeAdapter<A> min, TypeAdapter<B> max) {
        return new MinMaxAdapter<>(min, max);
    }

    private static final class PointAdapter<T> extends TypeAdapter<Point<T>> {
        private final TypeAdapter<T> element;

        PointAdapter(TypeAdapter<T> element) {
            this.element = element;
        }

        @Override
        public void write(JsonWriter out, Point<T> v) throws IOException {
            out.beginObject();
            out.name("x");
            element.write(out, v.x());
            out.name("y");
            element.write(out, v.y());
            out.endObject();
        }

        @Override
        public Point<T> read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            T x = null;
            T y = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "x" -> x = element.read(in);
                    case "y" -> y = element.read(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (x == null || y == null) {
                throw new JsonParseException("missing field in Point (x/y required)");
            }
            return new Point<>(x, y);
        }
    }

    private static final class SizeAdapter<T> extends TypeAdapter<Size<T>> {
        private final TypeAdapter<T> element;

        SizeAdapter(TypeAdapter<T> element) {
            this.element = element;
        }

        @Override
        public void write(JsonWriter out, Size<T> v) throws IOException {
            out.beginObject();
            out.name("width");
            element.write(out, v.width());
            out.name("height");
            element.write(out, v.height());
            out.endObject();
        }

        @Override
        public Size<T> read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            T width = null;
            T height = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "width" -> width = element.read(in);
                    case "height" -> height = element.read(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (width == null || height == null) {
                throw new JsonParseException("missing field in Size (width/height required)");
            }
            return new Size<>(width, height);
        }
    }

    private static final class RectAdapter<T> extends TypeAdapter<Rect<T>> {
        private final TypeAdapter<T> element;

        RectAdapter(TypeAdapter<T> element) {
            this.element = element;
        }

        @Override
        public void write(JsonWriter out, Rect<T> v) throws IOException {
            out.beginObject();
            out.name("left");
            element.write(out, v.left());
            out.name("right");
            element.write(out, v.right());
            out.name("top");
            element.write(out, v.top());
            out.name("bottom");
            element.write(out, v.bottom());
            out.endObject();
        }

        @Override
        public Rect<T> read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            T left = null;
            T right = null;
            T top = null;
            T bottom = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "left" -> left = element.read(in);
                    case "right" -> right = element.read(in);
                    case "top" -> top = element.read(in);
                    case "bottom" -> bottom = element.read(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (left == null || right == null || top == null || bottom == null) {
                throw new JsonParseException("missing field in Rect (left/right/top/bottom required)");
            }
            return new Rect<>(left, right, top, bottom);
        }
    }

    private static final class LineAdapter<T> extends TypeAdapter<Line<T>> {
        private final TypeAdapter<T> element;
        private final @org.jspecify.annotations.Nullable Supplier<T> fieldDefault;

        LineAdapter(TypeAdapter<T> element, @org.jspecify.annotations.Nullable Supplier<T> fieldDefault) {
            this.element = element;
            this.fieldDefault = fieldDefault;
        }

        @Override
        public void write(JsonWriter out, Line<T> v) throws IOException {
            out.beginObject();
            out.name("start");
            element.write(out, v.start());
            out.name("end");
            element.write(out, v.end());
            out.endObject();
        }

        @Override
        public Line<T> read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            T start = null;
            T end = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "start" -> start = element.read(in);
                    case "end" -> end = element.read(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            // #[serde(default)] on Line<T>: missing fields take T::default().
            if (start == null) {
                start = defaultOrFail("start");
            }
            if (end == null) {
                end = defaultOrFail("end");
            }
            return new Line<>(start, end);
        }

        private T defaultOrFail(String field) {
            if (fieldDefault == null) {
                throw new JsonParseException("missing field `" + field + "` in Line");
            }
            return fieldDefault.get();
        }
    }

    private static final class MinMaxAdapter<A, B> extends TypeAdapter<MinMax<A, B>> {
        private final TypeAdapter<A> minAdapter;
        private final TypeAdapter<B> maxAdapter;

        MinMaxAdapter(TypeAdapter<A> minAdapter, TypeAdapter<B> maxAdapter) {
            this.minAdapter = minAdapter;
            this.maxAdapter = maxAdapter;
        }

        @Override
        public void write(JsonWriter out, MinMax<A, B> v) throws IOException {
            out.beginObject();
            out.name("min");
            minAdapter.write(out, v.min());
            out.name("max");
            maxAdapter.write(out, v.max());
            out.endObject();
        }

        @Override
        public MinMax<A, B> read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            A min = null;
            B max = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "min" -> min = minAdapter.read(in);
                    case "max" -> max = maxAdapter.read(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (min == null || max == null) {
                throw new JsonParseException("missing field in MinMax (min/max required)");
            }
            return new MinMax<>(min, max);
        }
    }

    /// `GridTemplateTracks<S, Track>` — `{tracks: [...], line_names: [[...]]}`.
    /// `S: CheapCloneStr` is String in jaffy (GAP-017).
    private static final class GridTemplateTracksAdapter<T> extends TypeAdapter<GridTemplateTracks<T>> {
        private final TypeAdapter<T> trackAdapter;

        GridTemplateTracksAdapter(TypeAdapter<T> trackAdapter) {
            this.trackAdapter = trackAdapter;
        }

        @Override
        public void write(JsonWriter out, GridTemplateTracks<T> v) throws IOException {
            out.beginObject();
            out.name("tracks");
            out.beginArray();
            for (T track : v.tracks()) {
                trackAdapter.write(out, track);
            }
            out.endArray();
            out.name("line_names");
            writeStringLists(out, v.lineNames());
            out.endObject();
        }

        @Override
        public GridTemplateTracks<T> read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            List<T> tracks = null;
            List<List<String>> lineNames = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "tracks" -> {
                        tracks = new ArrayList<>();
                        in.beginArray();
                        while (in.hasNext()) {
                            tracks.add(trackAdapter.read(in));
                        }
                        in.endArray();
                    }
                    case "line_names" -> lineNames = readStringLists(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (tracks == null || lineNames == null) {
                throw new JsonParseException("missing field in GridTemplateTracks (tracks/line_names required)");
            }
            return new GridTemplateTracks<>(tracks, lineNames);
        }
    }

    static void writeStringLists(JsonWriter out, List<List<String>> v) throws IOException {
        out.beginArray();
        for (List<String> inner : v) {
            out.beginArray();
            for (String s : inner) {
                // JAFFY(GAP-020): Gson escapes U+2028/U+2029; serde_json does not.
                out.value(s);
            }
            out.endArray();
        }
        out.endArray();
    }

    static List<List<String>> readStringLists(JsonReader in) throws IOException {
        List<List<String>> outer = new ArrayList<>();
        in.beginArray();
        while (in.hasNext()) {
            List<String> inner = new ArrayList<>();
            in.beginArray();
            while (in.hasNext()) {
                inner.add(in.nextString());
            }
            in.endArray();
            outer.add(inner);
        }
        in.endArray();
        return outer;
    }

    // ---- Primitive specializations (same wire shape as the generics) ----

    static final TypeAdapter<SizeF32> SIZE_F32 = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, SizeF32 v) throws IOException {
            out.beginObject();
            out.name("width");
            JsonSupport.writeF32(out, v.width());
            out.name("height");
            JsonSupport.writeF32(out, v.height());
            out.endObject();
        }

        @Override
        public SizeF32 read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            Float width = null;
            Float height = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "width" -> width = JsonSupport.readF32(in);
                    case "height" -> height = JsonSupport.readF32(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (width == null || height == null) {
                throw new JsonParseException("missing field in Size (width/height required)");
            }
            return new SizeF32(width, height);
        }
    };

    static final TypeAdapter<PointF32> POINT_F32 = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, PointF32 v) throws IOException {
            out.beginObject();
            out.name("x");
            JsonSupport.writeF32(out, v.x());
            out.name("y");
            JsonSupport.writeF32(out, v.y());
            out.endObject();
        }

        @Override
        public PointF32 read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            Float x = null;
            Float y = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "x" -> x = JsonSupport.readF32(in);
                    case "y" -> y = JsonSupport.readF32(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (x == null || y == null) {
                throw new JsonParseException("missing field in Point (x/y required)");
            }
            return new PointF32(x, y);
        }
    };

    static final TypeAdapter<RectF32> RECT_F32 = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, RectF32 v) throws IOException {
            out.beginObject();
            out.name("left");
            JsonSupport.writeF32(out, v.left());
            out.name("right");
            JsonSupport.writeF32(out, v.right());
            out.name("top");
            JsonSupport.writeF32(out, v.top());
            out.name("bottom");
            JsonSupport.writeF32(out, v.bottom());
            out.endObject();
        }

        @Override
        public RectF32 read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            Float left = null;
            Float right = null;
            Float top = null;
            Float bottom = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "left" -> left = JsonSupport.readF32(in);
                    case "right" -> right = JsonSupport.readF32(in);
                    case "top" -> top = JsonSupport.readF32(in);
                    case "bottom" -> bottom = JsonSupport.readF32(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (left == null || right == null || top == null || bottom == null) {
                throw new JsonParseException("missing field in Rect (left/right/top/bottom required)");
            }
            return new RectF32(left, right, top, bottom);
        }
    };

    /// `Line<f32>` — `#[serde(default)]` fills missing fields with `0.0`.
    static final TypeAdapter<LineF32> LINE_F32 = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, LineF32 v) throws IOException {
            out.beginObject();
            out.name("start");
            JsonSupport.writeF32(out, v.start());
            out.name("end");
            JsonSupport.writeF32(out, v.end());
            out.endObject();
        }

        @Override
        public LineF32 read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            float start = 0.0f;
            float end = 0.0f;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "start" -> start = JsonSupport.readF32(in);
                    case "end" -> end = JsonSupport.readF32(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new LineF32(start, end);
        }
    };

    /// `Size<Option<f32>>` — elements serialize as `null` or a number.
    static final TypeAdapter<SizeOptF32> SIZE_OPT_F32 = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, SizeOptF32 v) throws IOException {
            out.beginObject();
            out.name("width");
            JsonSupport.writeOptF32(out, v.width());
            out.name("height");
            JsonSupport.writeOptF32(out, v.height());
            out.endObject();
        }

        @Override
        public SizeOptF32 read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            Float width = null;
            Float height = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "width" -> width = JsonSupport.readOptF32(in);
                    case "height" -> height = JsonSupport.readOptF32(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (width == null || height == null) {
                throw new JsonParseException("missing field in Size (width/height required)");
            }
            return new SizeOptF32(width, height);
        }
    };

    static final TypeAdapter<SizeBool> SIZE_BOOL = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, SizeBool v) throws IOException {
            out.beginObject();
            out.name("width");
            out.value(v.width());
            out.name("height");
            out.value(v.height());
            out.endObject();
        }

        @Override
        public SizeBool read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            Boolean width = null;
            Boolean height = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "width" -> width = in.nextBoolean();
                    case "height" -> height = in.nextBoolean();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (width == null || height == null) {
                throw new JsonParseException("missing field in Size (width/height required)");
            }
            return new SizeBool(width, height);
        }
    };

    /// `Line<bool>` — `#[serde(default)]` fills missing fields with `false`.
    static final TypeAdapter<LineBool> LINE_BOOL = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, LineBool v) throws IOException {
            out.beginObject();
            out.name("start");
            out.value(v.start());
            out.name("end");
            out.value(v.end());
            out.endObject();
        }

        @Override
        public LineBool read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            boolean start = false;
            boolean end = false;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "start" -> start = in.nextBoolean();
                    case "end" -> end = in.nextBoolean();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new LineBool(start, end);
        }
    };

    static final TypeAdapter<PointBool> POINT_BOOL = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, PointBool v) throws IOException {
            out.beginObject();
            out.name("x");
            out.value(v.x());
            out.name("y");
            out.value(v.y());
            out.endObject();
        }

        @Override
        public PointBool read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            Boolean x = null;
            Boolean y = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "x" -> x = in.nextBoolean();
                    case "y" -> y = in.nextBoolean();
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (x == null || y == null) {
                throw new JsonParseException("missing field in Point (x/y required)");
            }
            return new PointBool(x, y);
        }
    };

    static final TypeAdapter<FloatInsets> FLOAT_INSETS = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, FloatInsets v) throws IOException {
            out.beginObject();
            out.name("left");
            JsonSupport.writeF32(out, v.left());
            out.name("right");
            JsonSupport.writeF32(out, v.right());
            out.endObject();
        }

        @Override
        public FloatInsets read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            Set<String> seen = new HashSet<>();
            Float left = null;
            Float right = null;
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                checkDuplicate(seen, name);
                switch (name) {
                    case "left" -> left = JsonSupport.readF32(in);
                    case "right" -> right = JsonSupport.readF32(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            if (left == null || right == null) {
                throw new JsonParseException("missing field in FloatInsets (left/right required)");
            }
            return new FloatInsets(left, right);
        }
    };
}
