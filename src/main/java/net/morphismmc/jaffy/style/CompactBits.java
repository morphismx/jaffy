// JAFFY: upstream=none
// JAFFY-NOTE: shared raw-bit operations for the CompactLength tagged-pointer layout
// (part of the compact_length.rs port — implementation detail of the sealed
// cluster, not a separate Rust file). Public because Java
// package-private cannot span the file-named subpackages
// (style.dimension / style.grid / compute.common reach it).
//
// 64-bit inner representation (Rust `#[cfg(target_pointer_width = "64")]`):
//   Primitive: tagged_ptr = (f32_bits << 32) | tag8
//   Calc:      tagged_ptr = ptr_addr | tag3 — in Java the pointer is replaced
//              by an `Object` handle; the record stores only the tag.
package net.morphismmc.jaffy.style;

public final class CompactBits {

    private CompactBits() {}

    /// `CompactLengthInner::from_val` — `(val.to_bits() << 32) | tag`.
    public static long packVal(float val, int tag) {
        // JAFFY(GAP-014): u32 f32 bits packed unsigned into the u64 word.
        // JAFFY: java.lang.Float fully qualified — style.Float shadows it in this package.
        return (java.lang.Float.floatToRawIntBits(val) & 0xFFFFFFFFL) << 32 | tag;
    }

    /// `CompactLengthInner::tag` — low 8 bits.
    public static int tagOf(long bits) {
        return (int) bits & 0xFF;
    }

    /// `CompactLengthInner::calc_tag` — low 3 bits.
    public static int calcTagOf(long bits) {
        return (int) bits & 0b111;
    }

    /// `CompactLengthInner::value` — f32 from high 32 bits.
    public static float valueOf(long bits) {
        return java.lang.Float.intBitsToFloat((int) (bits >>> 32));
    }

    /// `is_plain_calc`: `calc_tag & (NON_POINTER_BIT | KEYWORD_BIT) == 0`
    public static boolean isPlainCalc(int calcTag) {
        return (calcTag & (CompactLength.NON_POINTER_BIT | CompactLength.KEYWORD_BIT)) == 0;
    }

    /// `is_fit_content_calc`: `tag & (NON_POINTER_BIT | KEYWORD_BIT) == KEYWORD_BIT`
    public static boolean isFitContentCalc(int tag) {
        return (tag & (CompactLength.NON_POINTER_BIT | CompactLength.KEYWORD_BIT)) == CompactLength.KEYWORD_BIT;
    }

    /// `has_calc`: `tag & NON_POINTER_BIT == 0`
    public static boolean hasCalc(int tag) {
        return (tag & CompactLength.NON_POINTER_BIT) == 0;
    }

    /// `is_length_or_percentage`: `tag & (NON_POINTER_BIT | KEYWORD_BIT) == NON_POINTER_BIT`
    public static boolean isLengthOrPercentage(int tag) {
        return (tag & (CompactLength.NON_POINTER_BIT | CompactLength.KEYWORD_BIT)) == CompactLength.NON_POINTER_BIT;
    }

    /// `is_length_percentage_or_calc`: `tag & KEYWORD_BIT == 0`
    public static boolean isLengthPercentageOrCalc(int tag) {
        return (tag & CompactLength.KEYWORD_BIT) == 0;
    }

    /// `is_sizing_keyword`: `tag & MASK == MASK || is_fit_content_calc`
    public static boolean isSizingKeyword(int tag) {
        int mask = CompactLength.NON_POINTER_BIT | CompactLength.KEYWORD_BIT | CompactLength.SIZING_KEYWORD_BIT;
        return (tag & mask) == mask || isFitContentCalc(tag);
    }

    /// `is_max_or_fit_content`
    public static boolean isMaxOrFitContent(int tag) {
        return tag == CompactLength.MAX_CONTENT_TAG
                || tag == CompactLength.FIT_CONTENT_PX_TAG
                || tag == CompactLength.FIT_CONTENT_PERCENT_TAG
                || isFitContentCalc(tag);
    }

    /// `is_max_content_alike`
    public static boolean isMaxContentAlike(int tag) {
        return tag == CompactLength.AUTO_TAG
                || tag == CompactLength.MAX_CONTENT_TAG
                || tag == CompactLength.FIT_CONTENT_PX_TAG
                || tag == CompactLength.FIT_CONTENT_PERCENT_TAG
                || isFitContentCalc(tag);
    }

    /// `is_min_or_max_content`
    public static boolean isMinOrMaxContent(int tag) {
        return tag == CompactLength.MIN_CONTENT_TAG || tag == CompactLength.MAX_CONTENT_TAG;
    }

    /// `is_intrinsic`
    public static boolean isIntrinsic(int tag) {
        return tag == CompactLength.AUTO_TAG
                || tag == CompactLength.MIN_CONTENT_TAG
                || tag == CompactLength.MAX_CONTENT_TAG
                || tag == CompactLength.FIT_CONTENT_PX_TAG
                || tag == CompactLength.FIT_CONTENT_PERCENT_TAG
                || isFitContentCalc(tag);
    }

    /// `uses_percentage`: `tag & PERCENTAGE_BIT != 0`
    public static boolean usesPercentage(int tag) {
        return (tag & CompactLength.PERCENTAGE_BIT) != 0;
    }
}
