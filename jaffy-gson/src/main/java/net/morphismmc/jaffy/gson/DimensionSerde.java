// JAFFY: upstream=src/style/compact_length.rs
// JAFFY-FROM: src/style/dimension.rs
// JAFFY-FROM: src/style/grid.rs
// JAFFY-NOTE: serde impls
// src/style/dimension.rs + the Min/MaxTrackSizingFunction Deserialize impls of
// src/style/grid.rs.
//
// Wire format: a CompactLength serializes as `serialize_u64(self.0.serialized())`
// — the tagged word rotated left 32 (`tag << 32 | f32_bits`), identical on
// upstream's 64-bit (rotate) and 32-bit (explicit compose) paths.
// Deserialize reconstructs via `from_serialized` then validates the tag set
// per wrapper type; calc() tags are rejected ("Cannot deserialize Calc value")
// because Java has no pointer to reattach (upstream rejects them identically).
package net.morphismmc.jaffy.gson;

import java.io.IOException;

import net.morphismmc.jaffy.style.CompactLength;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

final class DimensionSerde {

    private DimensionSerde() {}

    /// `CompactLengthInner::serialized` — `(f32 << 32 | tag).rotate_left(32)`.
    /// Calc variants carry a pointer upstream and are un-serializable
    /// ("Cannot serialize Calc value").
    static long serialized(CompactLength value) {
        return switch (value) {
            case CompactLength.Primitive p -> Long.rotateLeft(p.bits(), 32);
            case CompactLength.Calc c -> throw new JsonParseException("Cannot serialize Calc value");
        };
    }

    /// `CompactLengthInner::from_serialized` — `word.rotate_right(32)`. Always
    /// produces Primitive bits: pointer tags can only be calc()s, which every
    /// caller's tag validation rejects before the value is observable.
    static long fromSerialized(long word) {
        return Long.rotateRight(word, 32);
    }

    // Tag sets accepted by each wrapper's Deserialize impl (upstream order).
    private static final int[] COMPACT_LENGTH_TAGS = {
        CompactLength.LENGTH_TAG,
        CompactLength.PERCENT_TAG,
        CompactLength.AUTO_TAG,
        CompactLength.MIN_CONTENT_TAG,
        CompactLength.MAX_CONTENT_TAG,
        CompactLength.FIT_CONTENT_KEYWORD_TAG,
        CompactLength.FIT_CONTENT_PX_TAG,
        CompactLength.FIT_CONTENT_PERCENT_TAG,
        CompactLength.STRETCH_TAG,
        CompactLength.CONTENT_TAG,
        CompactLength.FR_TAG,
    };
    private static final int[] LENGTH_PERCENTAGE_TAGS = {
        CompactLength.LENGTH_TAG, CompactLength.PERCENT_TAG,
    };
    private static final int[] LENGTH_PERCENTAGE_AUTO_TAGS = {
        CompactLength.LENGTH_TAG, CompactLength.PERCENT_TAG, CompactLength.AUTO_TAG,
    };
    private static final int[] DIMENSION_TAGS = {
        CompactLength.LENGTH_TAG,
        CompactLength.PERCENT_TAG,
        CompactLength.AUTO_TAG,
        CompactLength.MIN_CONTENT_TAG,
        CompactLength.MAX_CONTENT_TAG,
        CompactLength.FIT_CONTENT_KEYWORD_TAG,
        CompactLength.FIT_CONTENT_PX_TAG,
        CompactLength.FIT_CONTENT_PERCENT_TAG,
        CompactLength.STRETCH_TAG,
        CompactLength.CONTENT_TAG,
    };
    private static final int[] MIN_TRACK_SIZING_TAGS = {
        CompactLength.LENGTH_TAG,
        CompactLength.PERCENT_TAG,
        CompactLength.AUTO_TAG,
        CompactLength.MIN_CONTENT_TAG,
        CompactLength.MAX_CONTENT_TAG,
        CompactLength.FIT_CONTENT_PX_TAG,
        CompactLength.FIT_CONTENT_PERCENT_TAG,
    };
    private static final int[] MAX_TRACK_SIZING_TAGS = {
        CompactLength.LENGTH_TAG,
        CompactLength.PERCENT_TAG,
        CompactLength.AUTO_TAG,
        CompactLength.MIN_CONTENT_TAG,
        CompactLength.MAX_CONTENT_TAG,
        CompactLength.FIT_CONTENT_PX_TAG,
        CompactLength.FIT_CONTENT_PERCENT_TAG,
        CompactLength.FR_TAG,
    };

    private static boolean contains(int[] tags, int tag) {
        for (int t : tags) {
            if (t == tag) {
                return true;
            }
        }
        return false;
    }

    private static int tagOf(long bits) {
        return (int) bits & 0xFF;
    }

    /// `CompactLength::deserialize` — u64 -> Primitive + full non-calc tag set.
    static CompactLength readCompactLength(JsonReader in) throws IOException {
        long bits = fromSerialized(JsonSupport.readU64(in));
        if (contains(COMPACT_LENGTH_TAGS, tagOf(bits))) {
            return new CompactLength.Primitive(bits);
        }
        throw new JsonParseException("Cannot deserialize Calc value");
    }

    /// Read a CompactLength then validate against `tags`; on success return
    /// the raw Primitive `bits` for the caller's own wrapper constructor.
    private static long readChecked(JsonReader in, int[] tags) throws IOException {
        CompactLength inner = readCompactLength(in);
        long bits = ((CompactLength.Primitive) inner).bits();
        if (contains(tags, tagOf(bits))) {
            return bits;
        }
        throw new JsonParseException("Invalid tag");
    }

    static final TypeAdapter<CompactLength> COMPACT_LENGTH = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, CompactLength v) throws IOException {
            JsonSupport.writeU64(out, serialized(v));
        }

        @Override
        public CompactLength read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return readCompactLength(in);
        }
    };

    static final TypeAdapter<LengthPercentage> LENGTH_PERCENTAGE = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, LengthPercentage v) throws IOException {
            JsonSupport.writeU64(out, serialized(v.intoRaw()));
        }

        @Override
        public LengthPercentage read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return new LengthPercentage.Primitive(readChecked(in, LENGTH_PERCENTAGE_TAGS));
        }
    };

    static final TypeAdapter<LengthPercentageAuto> LENGTH_PERCENTAGE_AUTO = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, LengthPercentageAuto v) throws IOException {
            JsonSupport.writeU64(out, serialized(v.intoRaw()));
        }

        @Override
        public LengthPercentageAuto read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return new LengthPercentageAuto.Primitive(readChecked(in, LENGTH_PERCENTAGE_AUTO_TAGS));
        }
    };

    static final TypeAdapter<Dimension> DIMENSION = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, Dimension v) throws IOException {
            JsonSupport.writeU64(out, serialized(v.intoRaw()));
        }

        @Override
        public Dimension read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return new Dimension.Primitive(readChecked(in, DIMENSION_TAGS));
        }
    };

    static final TypeAdapter<MinTrackSizingFunction> MIN_TRACK_SIZING = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, MinTrackSizingFunction v) throws IOException {
            JsonSupport.writeU64(out, serialized(v.intoRaw()));
        }

        @Override
        public MinTrackSizingFunction read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return new MinTrackSizingFunction.Primitive(readChecked(in, MIN_TRACK_SIZING_TAGS));
        }
    };

    static final TypeAdapter<MaxTrackSizingFunction> MAX_TRACK_SIZING = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, MaxTrackSizingFunction v) throws IOException {
            JsonSupport.writeU64(out, serialized(v.intoRaw()));
        }

        @Override
        public MaxTrackSizingFunction read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            return new MaxTrackSizingFunction.Primitive(readChecked(in, MAX_TRACK_SIZING_TAGS));
        }
    };
}
