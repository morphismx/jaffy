// JAFFY-UPSTREAM: src/style/dimension.rs
// JAFFY-NOTE: `LengthPercentageAuto` (GAP-013)
package net.morphismmc.jaffy.style.dimension;

import net.morphismmc.jaffy.style.CompactBits;
import net.morphismmc.jaffy.style.CompactLength;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

public sealed interface LengthPercentageAuto {

    /// `TaffyZero::ZERO` = `Self(CompactLength::ZERO)`
    LengthPercentageAuto ZERO = length(0.0f);
    /// `TaffyAuto::AUTO` = `Self(CompactLength::AUTO)`
    LengthPercentageAuto AUTO = auto();

    /// Non-pointer variant: `tagged_ptr = (f32_bits << 32) | tag`.
    record Primitive(long bits) implements LengthPercentageAuto {

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
        public boolean rustEq(LengthPercentageAuto other) {
            return other instanceof Primitive(long oBits) && bits == oBits;
        }
    }

    /// Pointer variant: `bits` is the tag (CALC_TAG), `calc` the Object handle.
    record Calc(long bits, Object calc) implements LengthPercentageAuto {

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
        public boolean rustEq(LengthPercentageAuto other) {
            return other instanceof Calc(long oBits, Object oCalc) && bits == oBits && calc == oCalc;
        }
    }

    // ---- `self.0.*` delegates (pub(crate) equivalents) ----

    int tag();

    int calcTag();

    float value();

    @Nullable
    Object calcValue();

    default boolean isPlainCalc() {
        return CompactBits.isPlainCalc(calcTag());
    }

    /// `PartialEq` -> rustEq (GAP-004)
    boolean rustEq(LengthPercentageAuto other);

    // ---- Constructors ----

    /// An absolute length in some abstract units.
    static LengthPercentageAuto length(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.LENGTH_TAG));
    }

    /// A percentage length relative to the size of the containing block.
    /// Percentages are in the range [0.0, 1.0], NOT [0.0, 100.0].
    static LengthPercentageAuto percent(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.PERCENT_TAG));
    }

    /// The dimension should be automatically computed according to
    /// algorithm-specific rules regarding the default size of boxes.
    static LengthPercentageAuto auto() {
        return new Primitive(CompactLength.AUTO_TAG);
    }

    /// A `calc()` value (`#[cfg(feature = "calc")]`).
    static LengthPercentageAuto calc(Object ptr) {
        return new Calc(CompactLength.CALC_TAG, ptr);
    }

    /// Create from a raw `CompactLength`.
    /// Caller must ensure `val` is a valid variant for LengthPercentageAuto (was `unsafe`).
    static LengthPercentageAuto fromRaw(CompactLength val) {
        return switch (val) {
            case CompactLength.Primitive p -> new Primitive(p.bits());
            case CompactLength.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// Get the underlying `CompactLength` representation.
    CompactLength intoRaw();

    // ---- Methods ----

    /// `resolve_to_option`:
    ///   - Some(length) for Length variants
    ///   - Some(resolved) using the provided context for Percent variants
    ///   - None for Auto variants
    default @Opt float resolveToOption(float context, CalcResolver calcResolver) {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return value();
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return context * value();
        }
        if (tag == CompactLength.AUTO_TAG) {
            return OptF32.NONE;
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return calcResolver.resolve(calcValue(), context);
        }
        throw new IllegalStateException(
                "LengthPercentageAuto values cannot be constructed with other tags"); // JAFFY(GAP-010)
    }

    /// Returns true if value is Auto
    default boolean isAuto() {
        return tag() == CompactLength.AUTO_TAG; // self.0.is_auto()
    }

    // ---- MaybeResolve / ResolveOrZero impls (util/resolve.rs) ----

    /// `MaybeResolve<Option<f32>, Option<f32>>::maybe_resolve`
    /// Converts the given `LengthPercentageAuto` into an absolute length.
    /// Can return `None` (Auto, or None context for Percent variants).
    default @Opt float maybeResolveO(@Opt float context, CalcResolver calc) {
        int tag = tag();
        if (tag == CompactLength.AUTO_TAG) {
            return OptF32.NONE;
        }
        if (tag == CompactLength.LENGTH_TAG) {
            return value();
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return OptF32.map(context, dim -> dim * value());
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return OptF32.map(context, dim -> calc.resolve(calcValue(), dim));
        }
        throw new IllegalStateException(); // unreachable!
    }

    /// Blanket `MaybeResolve<f32, Option<f32>>` — `self.maybe_resolve(Some(context), calc)`
    default @Opt float maybeResolveF(float context, CalcResolver calc) {
        return maybeResolveO(context, calc);
    }

    /// `ResolveOrZero<Option<f32>, f32>::resolve_or_zero`
    /// Returns 0.0 if the value is unable to resolve.
    default float resolveOrZero(@Opt float context, CalcResolver calc) {
        return OptF32.unwrapOr(maybeResolveO(context, calc), 0.0f);
    }

    /// Expand the compact representation into an [`ExpandedLengthPercentageAuto`].
    default ExpandedLengthPercentageAuto expand() {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return new ExpandedLengthPercentageAuto.Length(value());
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return new ExpandedLengthPercentageAuto.Percent(value());
        }
        if (tag == CompactLength.AUTO_TAG) {
            return new ExpandedLengthPercentageAuto.Auto();
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return new ExpandedLengthPercentageAuto.Calc(calcValue());
        }
        throw new IllegalStateException("LengthPercentageAuto contains a value with an invalid tag"); // JAFFY(GAP-010)
    }

    // ---- Trait impls ----

    /// `FromLength::from_length`
    static LengthPercentageAuto fromLength(double value) {
        return length((float) value);
    }

    /// `FromPercent::from_percent`
    static LengthPercentageAuto fromPercent(double value) {
        return percent((float) value);
    }

    /// `From<LengthPercentage>` = `Self(input.0)`
    static LengthPercentageAuto from(LengthPercentage input) {
        return switch (input) {
            case LengthPercentage.Primitive p -> new Primitive(p.bits());
            case LengthPercentage.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// `From<ExpandedLengthPercentageAuto>`
    static LengthPercentageAuto from(ExpandedLengthPercentageAuto value) {
        return switch (value) {
            case ExpandedLengthPercentageAuto.Length l -> length(l.val());
            case ExpandedLengthPercentageAuto.Percent p -> percent(p.val());
            case ExpandedLengthPercentageAuto.Auto a -> auto();
            case ExpandedLengthPercentageAuto.Calc c -> calc(c.handle());
        };
    }
}
