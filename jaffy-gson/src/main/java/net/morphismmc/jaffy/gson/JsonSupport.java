// JAFFY: serde support — raw f32 emission via just's Ryu (serde_json pretty
// format), enum unit-variant name mapping, u64 helpers. Gson 2.8.0 has no
// JsonWriter.jsonValue, so raw numbers go through a Number subclass (Gson
// writes Number.toString() verbatim).
package net.morphismmc.jaffy.gson;

import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import net.morphismmc.just.fmt.Ryu;
import net.morphismmc.just.math.OptF32;

final class JsonSupport {

    private JsonSupport() {}

    /// `serialize_f32` — ryu "pretty" format via a raw-number wrapper. serde_json
    /// silently serializes non-finite floats as `null` (NaN and ±Infinity are
    /// not JSON numbers); Option<f32> Some(NaN) therefore writes `null` and reads
    /// back as None — the same lossy asymmetry as upstream.
    static void writeF32(JsonWriter out, float v) throws IOException {
        if (!java.lang.Float.isFinite(v)) {
            out.nullValue();
            return;
        }
        out.value(new RyuNumber(v));
    }

    /// `deserialize_f32` — serde_json parses to f64 then casts `as f32`; Java's
    /// narrowing double->float is the same IEEE round-to-nearest. A JSON `null`
    /// is an invalid f32 upstream ("invalid type: null, expected f32").
    static float readF32(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            throw new JsonParseException("invalid type: null, expected f32");
        }
        return (float) in.nextDouble();
    }

    /// serde_json parses strict JSON only; Gson's JsonReader is lenient by
    /// default (accepts NaN/Infinity literals, unquoted names, single-quoted
    /// strings). Every adapter read() pins the reader to strict mode — an
    /// idempotent one-store call.
    static void strict(JsonReader in) {
        in.setLenient(false);
    }

    /// u8 read — serde `u8::deserialize` range-checks the integer.
    static int readU8(JsonReader in) throws IOException {
        int v = in.nextInt();
        if ((v & ~0xFF) != 0) {
            throw new JsonParseException("invalid value " + v + ", expected u8 range");
        }
        return v;
    }

    /// u16 read — serde `u16::deserialize` range-checks the integer.
    static int readU16(JsonReader in) throws IOException {
        int v = in.nextInt();
        if ((v & ~0xFFFF) != 0) {
            throw new JsonParseException("invalid value " + v + ", expected u16 range");
        }
        return v;
    }

    /// i16 read — serde `i16::deserialize` range-checks the integer.
    static int readI16(JsonReader in) throws IOException {
        int v = in.nextInt();
        if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
            throw new JsonParseException("invalid value " + v + ", expected i16 range");
        }
        return v;
    }

    /// `Option<f32>` read: null -> OptF32.NONE sentinel.
    static float readOptF32(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return OptF32.NONE;
        }
        return readF32(in);
    }

    /// `Option<f32>` write: OptF32.NONE -> null.
    static void writeOptF32(JsonWriter out, float v) throws IOException {
        if (OptF32.isNone(v)) {
            out.nullValue();
        } else {
            writeF32(out, v);
        }
    }

    /// u64 write — serialized CompactLength values fit in a Java long
    /// (tag <= 0x3B in the high word).
    static void writeU64(JsonWriter out, long v) throws IOException {
        out.value(v);
    }

    /// u64 read — serde `u64::deserialize` accepts JSON integers only.
    static long readU64(JsonReader in) throws IOException {
        return in.nextLong();
    }

    /// Rust variant name for a Java enum constant. Java constants are the
    /// UPPER_SNAKE spellings of the Rust PascalCase variants, so the mapping is
    /// mechanical: split on `_`, capitalise each segment.
    static String serdeName(Enum<?> value) {
        String name = value.name();
        StringBuilder b = new StringBuilder(name.length());
        for (String part : name.split("_")) {
            b.append(part.charAt(0));
            for (int i = 1; i < part.length(); i++) {
                b.append(Character.toLowerCase(part.charAt(i)));
            }
        }
        return b.toString();
    }

    /// Reverse lookup: serde variant name -> Java enum constant.
    static <E extends Enum<E>> E readEnum(JsonReader in, Class<E> type) throws IOException {
        String tag = in.nextString();
        for (E c : type.getEnumConstants()) {
            if (serdeName(c).equals(tag)) {
                return c;
            }
        }
        throw new JsonParseException("unknown variant `" + tag + "` for " + type.getSimpleName());
    }

    /// A Number whose toString() is the ryu representation; Gson's
    /// JsonWriter.value(Number) emits it verbatim (Gson 2.8.0 lacks jsonValue).
    static final class RyuNumber extends Number {
        private final float value;

        RyuNumber(float value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return Ryu.format(value);
        }

        @Override
        public int intValue() {
            return (int) value;
        }

        @Override
        public long longValue() {
            return (long) value;
        }

        @Override
        public float floatValue() {
            return value;
        }

        @Override
        public double doubleValue() {
            return value;
        }
    }
}
