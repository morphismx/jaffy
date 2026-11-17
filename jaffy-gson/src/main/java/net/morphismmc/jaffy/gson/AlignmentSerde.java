// JAFFY: upstream=src/style/alignment.rs
// JAFFY-NOTE: serde impls
//
// AlignItems/AlignContent use custom impls serializing a single tag string per
// public spelling (e.g. "FlexStart", "SafeCenter", "UnsafeEnd"); the keyword
// and safety enums themselves are plain derived unit variants.
package net.morphismmc.jaffy.gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

final class AlignmentSerde {

    private AlignmentSerde() {}

    // ---- AlignItems (keyword x safety -> one tag string) ----

    private static final Map<String, AlignItems> ALIGN_ITEMS_BY_NAME = new HashMap<>();

    static {
        Map<String, AlignItems> m = ALIGN_ITEMS_BY_NAME;
        m.put("Normal", AlignItems.NORMAL);
        m.put("Start", AlignItems.START);
        m.put("End", AlignItems.END);
        m.put("FlexStart", AlignItems.FLEX_START);
        m.put("FlexEnd", AlignItems.FLEX_END);
        m.put("SelfStart", AlignItems.SELF_START);
        m.put("SelfEnd", AlignItems.SELF_END);
        m.put("Center", AlignItems.CENTER);
        m.put("Baseline", AlignItems.BASELINE);
        m.put("Stretch", AlignItems.STRETCH);
        m.put("SafeStart", AlignItems.SAFE_START);
        m.put("SafeEnd", AlignItems.SAFE_END);
        m.put("SafeFlexStart", AlignItems.SAFE_FLEX_START);
        m.put("SafeFlexEnd", AlignItems.SAFE_FLEX_END);
        m.put("SafeSelfStart", AlignItems.SAFE_SELF_START);
        m.put("SafeSelfEnd", AlignItems.SAFE_SELF_END);
        m.put("SafeCenter", AlignItems.SAFE_CENTER);
        m.put("UnsafeStart", AlignItems.START.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeEnd", AlignItems.END.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeFlexStart", AlignItems.FLEX_START.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeFlexEnd", AlignItems.FLEX_END.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeSelfStart", AlignItems.SELF_START.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeSelfEnd", AlignItems.SELF_END.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeCenter", AlignItems.CENTER.withSafety(AlignmentSafety.UNSAFE));
    }

    private static String alignItemsName(AlignItems v) {
        AlignItemsKeyword kw = v.keyword();
        AlignmentSafety safety = v.safety();
        return switch (kw) {
            // (Normal | Baseline | Stretch, _) ignore the safety flag upstream
            case NORMAL -> "Normal";
            case BASELINE -> "Baseline";
            case STRETCH -> "Stretch";
            default ->
                switch (safety) {
                    case DEFAULT -> JsonSupport.serdeName(kw);
                    case SAFE -> "Safe" + JsonSupport.serdeName(kw);
                    case UNSAFE -> "Unsafe" + JsonSupport.serdeName(kw);
                };
        };
    }

    static final TypeAdapter<AlignItems> ALIGN_ITEMS = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, AlignItems v) throws IOException {
            out.value(alignItemsName(v));
        }

        @Override
        public AlignItems read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            String tag = in.nextString();
            AlignItems value = ALIGN_ITEMS_BY_NAME.get(tag);
            if (value == null) {
                throw new JsonParseException("unknown variant `" + tag + "` for AlignItems");
            }
            return value;
        }
    };

    // ---- AlignContent ----

    private static final Map<String, AlignContent> ALIGN_CONTENT_BY_NAME = new HashMap<>();

    static {
        Map<String, AlignContent> m = ALIGN_CONTENT_BY_NAME;
        m.put("Normal", AlignContent.NORMAL);
        m.put("Start", AlignContent.START);
        m.put("End", AlignContent.END);
        m.put("FlexStart", AlignContent.FLEX_START);
        m.put("FlexEnd", AlignContent.FLEX_END);
        m.put("Center", AlignContent.CENTER);
        m.put("Stretch", AlignContent.STRETCH);
        m.put("SpaceBetween", AlignContent.SPACE_BETWEEN);
        m.put("SpaceEvenly", AlignContent.SPACE_EVENLY);
        m.put("SpaceAround", AlignContent.SPACE_AROUND);
        m.put("SafeStart", AlignContent.SAFE_START);
        m.put("SafeEnd", AlignContent.SAFE_END);
        m.put("SafeFlexStart", AlignContent.SAFE_FLEX_START);
        m.put("SafeFlexEnd", AlignContent.SAFE_FLEX_END);
        m.put("SafeCenter", AlignContent.SAFE_CENTER);
        m.put("UnsafeStart", AlignContent.START.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeEnd", AlignContent.END.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeFlexStart", AlignContent.FLEX_START.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeFlexEnd", AlignContent.FLEX_END.withSafety(AlignmentSafety.UNSAFE));
        m.put("UnsafeCenter", AlignContent.CENTER.withSafety(AlignmentSafety.UNSAFE));
    }

    private static String alignContentName(AlignContent v) {
        AlignContentKeyword kw = v.keyword();
        AlignmentSafety safety = v.safety();
        return switch (kw) {
            // (Normal | Stretch | Space*, _) ignore the safety flag upstream
            case NORMAL -> "Normal";
            case STRETCH -> "Stretch";
            case SPACE_BETWEEN -> "SpaceBetween";
            case SPACE_EVENLY -> "SpaceEvenly";
            case SPACE_AROUND -> "SpaceAround";
            default ->
                switch (safety) {
                    case DEFAULT -> JsonSupport.serdeName(kw);
                    case SAFE -> "Safe" + JsonSupport.serdeName(kw);
                    case UNSAFE -> "Unsafe" + JsonSupport.serdeName(kw);
                };
        };
    }

    static final TypeAdapter<AlignContent> ALIGN_CONTENT = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, AlignContent v) throws IOException {
            out.value(alignContentName(v));
        }

        @Override
        public AlignContent read(JsonReader in) throws IOException {
            JsonSupport.strict(in);
            String tag = in.nextString();
            AlignContent value = ALIGN_CONTENT_BY_NAME.get(tag);
            if (value == null) {
                throw new JsonParseException("unknown variant `" + tag + "` for AlignContent");
            }
            return value;
        }
    };

    // ---- Keyword/safety enums: derived unit variants ("FlexStart", "Unsafe") ----

    static <E extends Enum<E>> TypeAdapter<E> unitEnum(Class<E> type) {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, E v) throws IOException {
                out.value(JsonSupport.serdeName(v));
            }

            @Override
            public E read(JsonReader in) throws IOException {
                JsonSupport.strict(in);
                return JsonSupport.readEnum(in, type);
            }
        };
    }
}
