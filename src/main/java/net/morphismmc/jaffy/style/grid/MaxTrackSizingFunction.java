// JAFFY: port of upstream src/style/grid.rs — `MaxTrackSizingFunction`
// (CompactLength newtype, same Primitive/Calc representation as Dimension).
// serde/parse impls deferred.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.style.CompactBits;
import net.morphismmc.jaffy.style.CompactLength;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

/// Maximum track sizing function: specifies the maximum size of a grid track.
public sealed interface MaxTrackSizingFunction {

    /// `TaffyZero::ZERO`
    MaxTrackSizingFunction ZERO = length(0.0f);
    /// `TaffyAuto::AUTO`
    MaxTrackSizingFunction AUTO = auto();
    /// `TaffyMinContent::MIN_CONTENT`
    MaxTrackSizingFunction MIN_CONTENT = minContent();
    /// `TaffyMaxContent::MAX_CONTENT`
    MaxTrackSizingFunction MAX_CONTENT = maxContent();

    /// Non-pointer variant: `tagged_ptr = (f32_bits << 32) | tag`.
    record Primitive(long bits) implements MaxTrackSizingFunction {

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
            return null;
        }

        @Override
        public CompactLength intoRaw() {
            return new CompactLength.Primitive(bits);
        }

        @Override
        public boolean rustEq(MaxTrackSizingFunction other) {
            return other instanceof Primitive(long oBits) && bits == oBits;
        }
    }

    /// Pointer variant: `bits` is the tag (CALC_TAG|FIT_CONTENT_CALC_TAG).
    record Calc(long bits, Object calc) implements MaxTrackSizingFunction {

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
            return 0.0f; // Dead path (see CompactLength.Calc)
        }

        @Override
        public Object calcValue() {
            return calc;
        }

        @Override
        public CompactLength intoRaw() {
            return new CompactLength.Calc(bits, calc);
        }

        @Override
        public boolean rustEq(MaxTrackSizingFunction other) {
            return other instanceof Calc(long oBits, Object oCalc) && bits == oBits && calc == oCalc;
        }
    }

    // ---- `self.0.*` delegates ----

    int tag();

    int calcTag();

    float value();

    @Nullable
    Object calcValue();

    default boolean isPlainCalc() {
        return CompactBits.isPlainCalc(calcTag());
    }

    default boolean isFitContentCalc() {
        return CompactBits.isFitContentCalc(tag());
    }

    /// `PartialEq` -> rustEq (GAP-004)
    boolean rustEq(MaxTrackSizingFunction other);

    // ---- Constructors ----

    /// An absolute length in some abstract units.
    static MaxTrackSizingFunction length(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.LENGTH_TAG));
    }

    /// A percentage length relative to the size of the containing block.
    /// Percentages are in the range [0.0, 1.0], NOT [0.0, 100.0].
    static MaxTrackSizingFunction percent(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.PERCENT_TAG));
    }

    /// The dimension should be automatically computed according to
    /// algorithm-specific rules regarding the default size of boxes.
    static MaxTrackSizingFunction auto() {
        return new Primitive(CompactLength.AUTO_TAG);
    }

    /// The `min-content` size (all soft line-wrapping opportunities taken).
    static MaxTrackSizingFunction minContent() {
        return new Primitive(CompactLength.MIN_CONTENT_TAG);
    }

    /// The `max-content` size (no soft line-wrapping opportunities taken).
    static MaxTrackSizingFunction maxContent() {
        return new Primitive(CompactLength.MAX_CONTENT_TAG);
    }

    /// `fit-content(<length>)` — size of `limit` clamped by min/max-content.
    static MaxTrackSizingFunction fitContentPx(float limit) {
        return new Primitive(CompactBits.packVal(limit, CompactLength.FIT_CONTENT_PX_TAG));
    }

    /// `fit-content(<percentage>)` — size of `limit` clamped by min/max-content.
    static MaxTrackSizingFunction fitContentPercent(float limit) {
        return new Primitive(CompactBits.packVal(limit, CompactLength.FIT_CONTENT_PERCENT_TAG));
    }

    /// A fraction of the total available grid space (`fr` units in CSS).
    static MaxTrackSizingFunction fr(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.FR_TAG));
    }

    /// A `calc()` value (`#[cfg(feature = "calc")]`).
    static MaxTrackSizingFunction calc(Object ptr) {
        return new Calc(CompactLength.CALC_TAG, ptr);
    }

    /// A `fit-content()` track sizing function whose limit is a `calc()` value.
    static MaxTrackSizingFunction fitContentCalc(Object ptr) {
        return new Calc(CompactLength.FIT_CONTENT_CALC_TAG, ptr);
    }

    /// Create from a raw `CompactLength` (was `unsafe` — caller must ensure
    /// `val` is a valid variant for this type).
    static MaxTrackSizingFunction fromRaw(CompactLength val) {
        return switch (val) {
            case CompactLength.Primitive p -> new Primitive(p.bits());
            case CompactLength.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// Get the underlying `CompactLength` representation of the value
    CompactLength intoRaw();

    // ---- Predicates ----

    /// True if `MinContent`, `MaxContent`, `FitContent` or `Auto`
    default boolean isIntrinsic() {
        return CompactBits.isIntrinsic(tag());
    }

    /// True if `MaxContent`, `FitContent` or `Auto`
    default boolean isMaxContentAlike() {
        return CompactBits.isMaxContentAlike(tag());
    }

    /// True if an Fr value
    default boolean isFr() {
        return tag() == CompactLength.FR_TAG;
    }

    /// True if `Auto`
    default boolean isAuto() {
        return tag() == CompactLength.AUTO_TAG;
    }

    /// True if `MinContent`
    default boolean isMinContent() {
        return tag() == CompactLength.MIN_CONTENT_TAG;
    }

    /// True if `MaxContent`
    default boolean isMaxContent() {
        return tag() == CompactLength.MAX_CONTENT_TAG;
    }

    /// True if `FitContent(...)`
    default boolean isFitContent() {
        return tag() == CompactLength.FIT_CONTENT_PX_TAG
                || tag() == CompactLength.FIT_CONTENT_PERCENT_TAG
                || isFitContentCalc();
    }

    /// True if `MaxContent` or `FitContent(...)`
    default boolean isMaxOrFitContent() {
        return CompactBits.isMaxOrFitContent(tag());
    }

    // ---- Resolution ----

    /// Returns whether the value can be resolved using `definiteValue`
    default boolean hasDefiniteValue(@Opt float parentSize) {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return true;
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return OptF32.isSome(parentSize);
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return OptF32.isSome(parentSize);
        }
        return false;
    }

    /// Returns fixed point values directly. Attempts to resolve percentage
    /// values against the passed parent size (when it is `Some`).
    default @Opt float definiteValue(@Opt float parentSize, CalcResolver calcResolver) {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return value();
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return OptF32.map(parentSize, size -> value() * size);
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return OptF32.map(parentSize, size -> calcResolver.resolve(calcValue(), size));
        }
        return OptF32.NONE;
    }

    /// Resolve the `fit-content()` limit of this track sizing function against
    /// the passed `parent_size`. Returns `None` if this is not a
    /// `fit-content()` function, or if it has a percentage or `calc()`
    /// argument and `parent_size` is indefinite.
    default @Opt float fitContentLimit(@Opt float parentSize, CalcResolver calcResolver) {
        int tag = tag();
        if (tag == CompactLength.FIT_CONTENT_PX_TAG) {
            return value();
        }
        if (tag == CompactLength.FIT_CONTENT_PERCENT_TAG) {
            return OptF32.map(parentSize, size -> value() * size);
        }
        if (isFitContentCalc()) { // #[cfg(feature = "calc")]
            return OptF32.map(parentSize, size -> calcResolver.resolve(calcValue(), size));
        }
        return OptF32.NONE;
    }

    /// Resolve the maximum size of the track as defined by either a fixed
    /// track sizing function, a percentage function (with definite available
    /// space), a fit-content function with fixed argument, or a fit-content
    /// function with percentage argument (with definite space). All other
    /// kinds return None.
    default @Opt float definiteLimit(@Opt float parentSize, CalcResolver calcResolver) {
        int tag = tag();
        if (tag == CompactLength.FIT_CONTENT_PX_TAG) {
            return value();
        }
        if (tag == CompactLength.FIT_CONTENT_PERCENT_TAG) {
            return OptF32.map(parentSize, size -> value() * size);
        }
        if (isFitContentCalc()) { // #[cfg(feature = "calc")]
            return OptF32.map(parentSize, size -> calcResolver.resolve(calcValue(), size));
        }
        return definiteValue(parentSize, calcResolver);
    }

    /// Resolve percentage values against the passed parent_size, returning
    /// Some(value). Non-percentage values always return None.
    default @Opt float resolvedPercentageSize(float parentSize, CalcResolver calcResolver) {
        return intoRaw().resolvedPercentageSize(parentSize, calcResolver);
    }

    /// Whether the track sizing function depends on the size of the parent node
    default boolean usesPercentage() {
        return CompactBits.usesPercentage(tag());
    }

    /// Expand the compact representation into an
    /// [`ExpandedMaxTrackSizingFunction`].
    default ExpandedMaxTrackSizingFunction expand() {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return new ExpandedMaxTrackSizingFunction.Length(value());
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return new ExpandedMaxTrackSizingFunction.Percent(value());
        }
        if (tag == CompactLength.AUTO_TAG) {
            return new ExpandedMaxTrackSizingFunction.Auto();
        }
        if (tag == CompactLength.MIN_CONTENT_TAG) {
            return new ExpandedMaxTrackSizingFunction.MinContent();
        }
        if (tag == CompactLength.MAX_CONTENT_TAG) {
            return new ExpandedMaxTrackSizingFunction.MaxContent();
        }
        if (tag == CompactLength.FIT_CONTENT_PX_TAG) {
            return new ExpandedMaxTrackSizingFunction.FitContentPx(value());
        }
        if (tag == CompactLength.FIT_CONTENT_PERCENT_TAG) {
            return new ExpandedMaxTrackSizingFunction.FitContentPercent(value());
        }
        if (isFitContentCalc()) { // #[cfg(feature = "calc")]
            return new ExpandedMaxTrackSizingFunction.FitContentCalc(calcValue());
        }
        if (tag == CompactLength.FR_TAG) {
            return new ExpandedMaxTrackSizingFunction.Fr(value());
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return new ExpandedMaxTrackSizingFunction.Calc(calcValue());
        }
        throw new IllegalStateException(
                "MaxTrackSizingFunction contains a value with an invalid tag"); // JAFFY(GAP-010)
    }

    // ---- Trait impls ----

    /// `FromLength::from_length`
    static MaxTrackSizingFunction fromLength(double value) {
        return length((float) value);
    }

    /// `FromPercent::from_percent`
    static MaxTrackSizingFunction fromPercent(double value) {
        return percent((float) value);
    }

    /// `TaffyFitContent::fit_content` — converts a LengthPercentage into a
    /// fit-content track sizing function
    static MaxTrackSizingFunction fitContent(LengthPercentage argument) {
        return fromRaw(CompactLength.fitContent(argument));
    }

    /// `FromFr::from_fr`
    static MaxTrackSizingFunction fromFr(double value) {
        return fr((float) value);
    }

    /// `From<LengthPercentage>` = `Self(input.0)`
    static MaxTrackSizingFunction from(LengthPercentage input) {
        return fromRaw(input.intoRaw());
    }

    /// `From<LengthPercentageAuto>` = `Self(input.0)`
    static MaxTrackSizingFunction from(LengthPercentageAuto input) {
        return fromRaw(input.intoRaw());
    }

    /// `From<Dimension>` — Dimension supports values that are not valid max
    /// track sizing functions; those map to `auto`.
    static MaxTrackSizingFunction from(Dimension input) {
        int tag = input.tag();
        if (tag == CompactLength.FIT_CONTENT_KEYWORD_TAG
                || tag == CompactLength.STRETCH_TAG
                || tag == CompactLength.CONTENT_TAG) {
            return auto();
        }
        return fromRaw(input.intoRaw());
    }

    /// `From<MinTrackSizingFunction>` = `Self(input.0)`
    static MaxTrackSizingFunction from(MinTrackSizingFunction input) {
        return fromRaw(input.intoRaw());
    }

    /// `From<ExpandedMaxTrackSizingFunction>`
    static MaxTrackSizingFunction from(ExpandedMaxTrackSizingFunction value) {
        return switch (value) {
            case ExpandedMaxTrackSizingFunction.Length l -> length(l.val());
            case ExpandedMaxTrackSizingFunction.Percent p -> percent(p.val());
            case ExpandedMaxTrackSizingFunction.Auto a -> auto();
            case ExpandedMaxTrackSizingFunction.MinContent m -> minContent();
            case ExpandedMaxTrackSizingFunction.MaxContent m -> maxContent();
            case ExpandedMaxTrackSizingFunction.FitContentPx f -> fitContentPx(f.val());
            case ExpandedMaxTrackSizingFunction.FitContentPercent f -> fitContentPercent(f.val());
            case ExpandedMaxTrackSizingFunction.FitContentCalc f -> fitContentCalc(f.handle());
            case ExpandedMaxTrackSizingFunction.Fr f -> fr(f.val());
            case ExpandedMaxTrackSizingFunction.Calc c -> calc(c.handle());
        };
    }
}
