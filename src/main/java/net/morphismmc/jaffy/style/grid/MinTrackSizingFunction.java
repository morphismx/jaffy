// JAFFY-UPSTREAM: src/style/grid.rs
// JAFFY-NOTE: `MinTrackSizingFunction`
// (CompactLength newtype). serde/parse impls deferred.
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

/// Minimum track sizing function: specifies the minimum size of a grid track.
public sealed interface MinTrackSizingFunction {

    /// `TaffyZero::ZERO`
    MinTrackSizingFunction ZERO = length(0.0f);
    /// `TaffyAuto::AUTO`
    MinTrackSizingFunction AUTO = auto();
    /// `TaffyMinContent::MIN_CONTENT`
    MinTrackSizingFunction MIN_CONTENT = minContent();
    /// `TaffyMaxContent::MAX_CONTENT`
    MinTrackSizingFunction MAX_CONTENT = maxContent();

    /// Non-pointer variant: `tagged_ptr = (f32_bits << 32) | tag`.
    record Primitive(long bits) implements MinTrackSizingFunction {

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
        public boolean rustEq(MinTrackSizingFunction other) {
            return other instanceof Primitive(long oBits) && bits == oBits;
        }
    }

    /// Pointer variant: `bits` is the tag (CALC_TAG).
    record Calc(long bits, Object calc) implements MinTrackSizingFunction {

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
        public boolean rustEq(MinTrackSizingFunction other) {
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

    /// `PartialEq` -> rustEq (GAP-004)
    boolean rustEq(MinTrackSizingFunction other);

    // ---- Constructors ----

    /// An absolute length in some abstract units.
    static MinTrackSizingFunction length(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.LENGTH_TAG));
    }

    /// A percentage length relative to the size of the containing block.
    /// Percentages are in the range [0.0, 1.0], NOT [0.0, 100.0].
    static MinTrackSizingFunction percent(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.PERCENT_TAG));
    }

    /// The dimension should be automatically computed according to
    /// algorithm-specific rules regarding the default size of boxes.
    static MinTrackSizingFunction auto() {
        return new Primitive(CompactLength.AUTO_TAG);
    }

    /// The `min-content` size.
    static MinTrackSizingFunction minContent() {
        return new Primitive(CompactLength.MIN_CONTENT_TAG);
    }

    /// The `max-content` size.
    static MinTrackSizingFunction maxContent() {
        return new Primitive(CompactLength.MAX_CONTENT_TAG);
    }

    /// A `calc()` value (`#[cfg(feature = "calc")]`).
    static MinTrackSizingFunction calc(Object ptr) {
        return new Calc(CompactLength.CALC_TAG, ptr);
    }

    /// Create from a raw `CompactLength` (was `unsafe`).
    static MinTrackSizingFunction fromRaw(CompactLength val) {
        return switch (val) {
            case CompactLength.Primitive p -> new Primitive(p.bits());
            case CompactLength.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// Get the underlying `CompactLength` representation of the value
    CompactLength intoRaw();

    // ---- Predicates ----

    /// True if `MinContent`, `MaxContent` or `Auto`
    default boolean isIntrinsic() {
        return CompactBits.isIntrinsic(tag());
    }

    /// True if `MinContent` or `MaxContent`
    default boolean isMinOrMaxContent() {
        return CompactBits.isMinOrMaxContent(tag());
    }

    /// True if an Fr value
    default boolean isFr() {
        return tag() == CompactLength.FR_TAG;
    }

    /// True if `Auto`
    default boolean isAuto() {
        return tag() == CompactLength.AUTO_TAG;
    }

    /// True if `Auto`, or a percentage when `parent_size` is `None`
    default boolean behavesAsAuto(@Opt float parentSize) {
        return isAuto() || (OptF32.isNone(parentSize) && usesPercentage());
    }

    /// True if `MinContent`
    default boolean isMinContent() {
        return tag() == CompactLength.MIN_CONTENT_TAG;
    }

    /// True if `MaxContent`
    default boolean isMaxContent() {
        return tag() == CompactLength.MAX_CONTENT_TAG;
    }

    /// True if `FitContentCalc` — used by `from(Dimension)`
    private boolean isFitContentCalc() {
        return CompactBits.isFitContentCalc(tag());
    }

    // ---- Resolution ----

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
    /// [`ExpandedMinTrackSizingFunction`].
    default ExpandedMinTrackSizingFunction expand() {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return new ExpandedMinTrackSizingFunction.Length(value());
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return new ExpandedMinTrackSizingFunction.Percent(value());
        }
        if (tag == CompactLength.AUTO_TAG) {
            return new ExpandedMinTrackSizingFunction.Auto();
        }
        if (tag == CompactLength.MIN_CONTENT_TAG) {
            return new ExpandedMinTrackSizingFunction.MinContent();
        }
        if (tag == CompactLength.MAX_CONTENT_TAG) {
            return new ExpandedMinTrackSizingFunction.MaxContent();
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return new ExpandedMinTrackSizingFunction.Calc(calcValue());
        }
        throw new IllegalStateException(
                "MinTrackSizingFunction contains a value with an invalid tag"); // JAFFY(GAP-010)
    }

    // ---- Trait impls ----

    /// `FromLength::from_length`
    static MinTrackSizingFunction fromLength(double value) {
        return length((float) value);
    }

    /// `FromPercent::from_percent`
    static MinTrackSizingFunction fromPercent(double value) {
        return percent((float) value);
    }

    /// `From<LengthPercentage>` = `Self(input.0)`
    static MinTrackSizingFunction from(LengthPercentage input) {
        return fromRaw(input.intoRaw());
    }

    /// `From<LengthPercentageAuto>` = `Self(input.0)`
    static MinTrackSizingFunction from(LengthPercentageAuto input) {
        return fromRaw(input.intoRaw());
    }

    /// `From<Dimension>` — Dimension supports values that are not valid min
    /// track sizing functions; those map to `auto`.
    static MinTrackSizingFunction from(Dimension input) {
        int tag = input.tag();
        if (tag == CompactLength.FIT_CONTENT_PX_TAG
                || tag == CompactLength.FIT_CONTENT_PERCENT_TAG
                || tag == CompactLength.FIT_CONTENT_KEYWORD_TAG
                || tag == CompactLength.STRETCH_TAG
                || tag == CompactLength.CONTENT_TAG
                || CompactBits.isFitContentCalc(tag)) {
            return auto();
        }
        return fromRaw(input.intoRaw());
    }

    /// `From<MaxTrackSizingFunction>` — `fr`/`fit_content` values are not
    /// valid min functions and map to `auto`.
    static MinTrackSizingFunction from(MaxTrackSizingFunction input) {
        if (input.isFr() || input.isFitContent()) {
            return auto();
        }
        return fromRaw(input.intoRaw());
    }

    /// `From<ExpandedMinTrackSizingFunction>`
    static MinTrackSizingFunction from(ExpandedMinTrackSizingFunction value) {
        return switch (value) {
            case ExpandedMinTrackSizingFunction.Length l -> length(l.val());
            case ExpandedMinTrackSizingFunction.Percent p -> percent(p.val());
            case ExpandedMinTrackSizingFunction.Auto a -> auto();
            case ExpandedMinTrackSizingFunction.MinContent m -> minContent();
            case ExpandedMinTrackSizingFunction.MaxContent m -> maxContent();
            case ExpandedMinTrackSizingFunction.Calc c -> calc(c.handle());
        };
    }
}
