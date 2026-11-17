// JAFFY-UPSTREAM: src/style/compact_length.rs
// JAFFY-NOTE: (GAP-013)
//
// Rust `struct CompactLength(CompactLengthInner)` is a 64-bit tagged pointer.
// Per GAP-013 this becomes a sealed interface: `Primitive` carries the full
// tagged_bits word (f32 bits in high 32, tag in low 8); `Calc` replaces the
// `*const ()` pointer with an `Object` handle and stores only the tag.
//
// The 32-bit-platform `inner` module is not ported (Java targets 64-bit
// semantics; only `serialized()` differed and it is serde-gated).
// serde impls and `from_serialized`/`serialized` are serde-gated: deferred.
package net.morphismmc.jaffy.style;

import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

public sealed interface CompactLength {

    // ---- Tag layout (usize constants -> int, GAP-014) ----

    /// Bit set for all non-pointer (i.e. not `calc()`) tags
    int NON_POINTER_BIT = 0b0001;
    /// Bit set for all tags that are not a plain length, percentage or calc() value
    int KEYWORD_BIT = 0b0010;
    /// Bit set for all tags whose value depends on a percentage basis (including `calc()`)
    int PERCENTAGE_BIT = 0b0100;
    /// Bit set (in addition to `KEYWORD_BIT`) for all CSS sizing keyword tags
    int SIZING_KEYWORD_BIT = 0b1000;

    /// The tag indicating a calc() value (`#[cfg(feature = "calc")]`)
    int CALC_TAG = 0b0000_0100;
    /// The tag indicating a fit-content() value with a calc() limit (`#[cfg(feature = "calc")]`)
    int FIT_CONTENT_CALC_TAG = 0b0000_0110;
    /// The tag indicating a length value
    int LENGTH_TAG = 0b0000_0001;
    /// The tag indicating a percentage value
    int PERCENT_TAG = 0b0000_0101;
    /// The tag indicating an auto value
    int AUTO_TAG = 0b0000_0011;
    /// The tag indicating an fr value
    int FR_TAG = 0b0001_0011;
    /// The tag indicating a content keyword value
    int CONTENT_TAG = 0b0010_0011;
    /// The tag indicating a min-content value
    int MIN_CONTENT_TAG = 0b0000_1011;
    /// The tag indicating a max-content value
    int MAX_CONTENT_TAG = 0b0001_1011;
    /// The tag indicating a fit-content value with px limit
    int FIT_CONTENT_PX_TAG = 0b0010_1011;
    /// The tag indicating a plain fit-content keyword value (no limit)
    int FIT_CONTENT_KEYWORD_TAG = 0b0011_1011;
    /// The tag indicating a stretch keyword value
    int STRETCH_TAG = 0b0100_1011;
    /// The tag indicating a fit-content value with percent limit
    int FIT_CONTENT_PERCENT_TAG = 0b0000_1111;

    // ---- Associated constants (TaffyZero/TaffyAuto/TaffyMinContent/TaffyMaxContent) ----

    /// `TaffyZero::ZERO` = `Self::length(0.0)`
    CompactLength ZERO = length(0.0f);
    /// `TaffyAuto::AUTO` = `Self::auto()`
    CompactLength AUTO = auto();
    /// `TaffyMinContent::MIN_CONTENT` = `Self::min_content()`
    CompactLength MIN_CONTENT = minContent();
    /// `TaffyMaxContent::MAX_CONTENT` = `Self::max_content()`
    CompactLength MAX_CONTENT = maxContent();

    // ---- Variants ----

    /// Non-pointer variant: `tagged_ptr = (f32_bits << 32) | tag`.
    record Primitive(long bits) implements CompactLength {

        @Override
        public int tag() {
            return CompactBits.tagOf(bits);
        }

        @Override
        public int calcTag() {
            return CompactBits.calcTagOf(bits);
        }

        @Override
        public float value() {
            return CompactBits.valueOf(bits);
        }

        @Override
        public @Nullable Object calcValue() {
            // Rust returns tagged_ptr & !7 (a garbage pointer) here; only
            // reachable on calc values, so null is honest and never observed.
            return null;
        }

        @Override
        public boolean rustEq(CompactLength other) {
            // `inner` derives PartialEq on the raw tagged_ptr: bitwise compare.
            return other instanceof Primitive(long oBits) && bits == oBits;
        }
    }

    /// Pointer variant: low 3 bits of the tag distinguish `calc()` (4) and
    /// `fit-content(calc())` (6). Rust also mixes pointer address bits 3..7
    /// into `tag()`; every predicate masks them out, so storing the bare tag
    /// preserves observable behaviour (see tag-layout comment upstream).
    record Calc(long bits, Object calc) implements CompactLength {

        /// `from_ptr`: Rust asserts `ptr != 0` and `ptr & 0b111 == 0`. Java has
        /// no address to align; the non-null check covers the meaningful half.
        /// `bits` carries only the tag (CALC_TAG or FIT_CONTENT_CALC_TAG).
        public Calc {
            if (calc == null) {
                throw new IllegalStateException("calc pointer must be non-null"); // JAFFY(GAP-010)
            }
        }

        @Override
        public int tag() {
            return (int) bits & 0xFF;
        }

        @Override
        public int calcTag() {
            return (int) bits & 0b111;
        }

        @Override
        public float value() {
            // Rust reads f32 from the pointer's high 32 bits — garbage there.
            // Dead path (never called on calc values); 0.0f is honest.
            return 0.0f;
        }

        @Override
        public Object calcValue() {
            return calc;
        }

        @Override
        public boolean rustEq(CompactLength other) {
            // Rust compares tagged pointers: same address + same tag. Java:
            // identity on the handle + tag equality.
            return other instanceof Calc(long oBits, Object oCalc) && bits == oBits && calc == oCalc;
        }
    }

    // ---- Accessors (inner::tag / calc_tag / value / ptr) ----

    /// Get the primary tag
    int tag();

    /// Get the calc tag (low 3 bits)
    int calcTag();

    /// Get the numeric value associated with the `CompactLength`
    float value();

    /// Get the calc pointer of the `CompactLength` (`Object` handle)
    @Nullable
    Object calcValue();

    // ---- Predicates ----

    /// `is_plain_calc` — a plain calc() value (not fit-content(calc()))
    default boolean isPlainCalc() {
        return CompactBits.isPlainCalc(calcTag());
    }

    /// `is_fit_content_calc` — fit-content() with a calc() limit
    default boolean isFitContentCalc() {
        return CompactBits.isFitContentCalc(tag());
    }

    /// `has_calc` — holds a calc() pointer: plain calc() or fit-content(calc())
    default boolean hasCalc() {
        return CompactBits.hasCalc(tag());
    }

    /// `is_zero` — `self.0 == Self::ZERO.0` (bitwise on the inner)
    default boolean isZero() {
        return rustEq(ZERO);
    }

    /// `is_length_or_percentage`
    default boolean isLengthOrPercentage() {
        return CompactBits.isLengthOrPercentage(tag());
    }

    /// `is_length_percentage_or_calc`
    default boolean isLengthPercentageOrCalc() {
        return CompactBits.isLengthPercentageOrCalc(tag());
    }

    /// `is_auto`
    default boolean isAuto() {
        return tag() == AUTO_TAG;
    }

    /// `is_content`
    default boolean isContent() {
        return tag() == CONTENT_TAG;
    }

    /// `is_min_content`
    default boolean isMinContent() {
        return tag() == MIN_CONTENT_TAG;
    }

    /// `is_max_content`
    default boolean isMaxContent() {
        return tag() == MAX_CONTENT_TAG;
    }

    /// `is_fit_content` — any fit-content(...) value
    default boolean isFitContent() {
        return tag() == FIT_CONTENT_PX_TAG || tag() == FIT_CONTENT_PERCENT_TAG || isFitContentCalc();
    }

    /// `is_sizing_keyword` — min-content, max-content, fit-content, fit-content(...), stretch
    default boolean isSizingKeyword() {
        return CompactBits.isSizingKeyword(tag());
    }

    /// `is_max_or_fit_content`
    default boolean isMaxOrFitContent() {
        return CompactBits.isMaxOrFitContent(tag());
    }

    /// `is_max_content_alike`
    default boolean isMaxContentAlike() {
        return CompactBits.isMaxContentAlike(tag());
    }

    /// `is_min_or_max_content`
    default boolean isMinOrMaxContent() {
        return CompactBits.isMinOrMaxContent(tag());
    }

    /// `is_intrinsic` — auto, min-content, max-content, or fit-content(...)
    default boolean isIntrinsic() {
        return CompactBits.isIntrinsic(tag());
    }

    /// `is_fr`
    default boolean isFr() {
        return tag() == FR_TAG;
    }

    /// `uses_percentage`
    default boolean usesPercentage() {
        return CompactBits.usesPercentage(tag());
    }

    /// `PartialEq` on the inner tagged pointer (GAP-004: explicit rustEq).
    boolean rustEq(CompactLength other);

    // ---- resolved_percentage_size ----

    /// Resolve percentage values against the passed parent_size, returning Some(value).
    /// Non-percentage values always return None.
    default @Opt float resolvedPercentageSize(float parentSize, CalcResolver calcResolver) {
        if (tag() == PERCENT_TAG) {
            return value() * parentSize;
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return calcResolver.resolve(calcValue(), parentSize);
        }
        return OptF32.NONE;
    }

    // ---- Constructors (const fn per variant) ----

    /// An absolute length in some abstract units.
    static CompactLength length(float val) {
        return new Primitive(CompactBits.packVal(val, LENGTH_TAG));
    }

    /// A percentage length relative to the size of the containing block.
    /// Percentages are in the range [0.0, 1.0], NOT [0.0, 100.0].
    static CompactLength percent(float val) {
        return new Primitive(CompactBits.packVal(val, PERCENT_TAG));
    }

    /// A `calc()` value. `ptr` is an opaque handle to the caller's calc
    /// representation (an `Object` in Java; Rust takes `*const ()`).
    static CompactLength calc(Object ptr) {
        return new Calc(CALC_TAG, ptr);
    }

    /// A `fit-content(calc())` value.
    static CompactLength fitContentCalc(Object ptr) {
        return new Calc(FIT_CONTENT_CALC_TAG, ptr);
    }

    /// `auto` — algorithm-specific default sizing.
    static CompactLength auto() {
        return new Primitive(AUTO_TAG);
    }

    /// `fr(val)` — fraction of total available grid space.
    static CompactLength fr(float val) {
        return new Primitive(CompactBits.packVal(val, FR_TAG));
    }

    /// `min-content` size.
    static CompactLength minContent() {
        return new Primitive(MIN_CONTENT_TAG);
    }

    /// `max-content` size.
    static CompactLength maxContent() {
        return new Primitive(MAX_CONTENT_TAG);
    }

    /// `fit-content(px limit)` — `max(min_content, min(max_content, limit))`.
    static CompactLength fitContentPx(float limit) {
        return new Primitive(CompactBits.packVal(limit, FIT_CONTENT_PX_TAG));
    }

    /// `fit-content(percent limit)`.
    static CompactLength fitContentPercent(float limit) {
        return new Primitive(CompactBits.packVal(limit, FIT_CONTENT_PERCENT_TAG));
    }

    /// `fit-content` keyword (stretch limit).
    static CompactLength fitContentKeyword() {
        return new Primitive(FIT_CONTENT_KEYWORD_TAG);
    }

    /// `stretch` keyword.
    static CompactLength stretch() {
        return new Primitive(STRETCH_TAG);
    }

    /// `content` keyword (flex-basis only; elsewhere behaves as auto).
    static CompactLength content() {
        return new Primitive(CONTENT_TAG);
    }

    // ---- Trait factory impls (FromLength/FromPercent/FromFr/TaffyFitContent) ----

    /// `FromLength::from_length` — `value.into() as f32` (double -> float cast).
    static CompactLength fromLength(double value) {
        return length((float) value);
    }

    /// `FromPercent::from_percent`
    static CompactLength fromPercent(double value) {
        return percent((float) value);
    }

    /// `FromFr::from_fr`
    static CompactLength fromFr(double value) {
        return fr((float) value);
    }

    /// `TaffyFitContent::fit_content` — converts a LengthPercentage into a
    /// fit-content(...) limit preserving its unit.
    static CompactLength fitContent(LengthPercentage lp) {
        float value = lp.value();
        int tag = lp.tag();
        if (tag == LENGTH_TAG) {
            return fitContentPx(value);
        }
        if (tag == PERCENT_TAG) {
            return fitContentPercent(value);
        }
        if (lp.isPlainCalc()) { // #[cfg(feature = "calc")]
            return fitContentCalc(lp.calcValue());
        }
        throw new IllegalStateException("unreachable"); // JAFFY(GAP-010)
    }
}
