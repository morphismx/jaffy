// JAFFY-UPSTREAM: src/style/dimension.rs
// JAFFY-NOTE: `LengthPercentage` (GAP-013)
//
// Rust `pub struct LengthPercentage(pub(crate) CompactLength)` newtype ->
// sealed interface with Primitive(bits)/Calc(bits, calc) records, same bit
// layout as CompactLength. serde/parse impls are feature-gated: deferred.
//
// MaybeResolve/ResolveOrZero impls (util/resolve.rs) are default methods here
// per the "trait impls live on Self" convention; Size<LP>/Rect<LP> blanket
// instantiations live in util.*Ext classes.
package net.morphismmc.jaffy.style.dimension;

import net.morphismmc.jaffy.style.CompactBits;
import net.morphismmc.jaffy.style.CompactLength;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

public sealed interface LengthPercentage {

    /// `TaffyZero::ZERO` = `Self(CompactLength::ZERO)`
    LengthPercentage ZERO = length(0.0f);

    /// Non-pointer variant: `tagged_ptr = (f32_bits << 32) | tag`.
    record Primitive(long bits) implements LengthPercentage {

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
        public boolean rustEq(LengthPercentage other) {
            return other instanceof Primitive(long oBits) && bits == oBits;
        }
    }

    /// Pointer variant: `bits` is the tag (CALC_TAG), `calc` the Object handle.
    record Calc(long bits, Object calc) implements LengthPercentage {

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
        public boolean rustEq(LengthPercentage other) {
            return other instanceof Calc(long oBits, Object oCalc) && bits == oBits && calc == oCalc;
        }
    }

    // ---- `self.0.*` delegates (pub(crate) equivalents) ----

    /// `self.0.tag()`
    int tag();

    /// `self.0.calc_tag()` — used by isPlainCalc
    int calcTag();

    /// `self.0.value()`
    float value();

    /// `self.0.calc_value()`
    @Nullable
    Object calcValue();

    /// `self.0.is_plain_calc()`
    default boolean isPlainCalc() {
        return CompactBits.isPlainCalc(calcTag());
    }

    /// `PartialEq` -> rustEq (GAP-004)
    boolean rustEq(LengthPercentage other);

    // ---- Constructors ----

    /// An absolute length in some abstract units.
    static LengthPercentage length(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.LENGTH_TAG));
    }

    /// A percentage length relative to the size of the containing block.
    /// Percentages are in the range [0.0, 1.0], NOT [0.0, 100.0].
    static LengthPercentage percent(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.PERCENT_TAG));
    }

    /// A `calc()` value (`#[cfg(feature = "calc")]`).
    static LengthPercentage calc(Object ptr) {
        return new Calc(CompactLength.CALC_TAG, ptr);
    }

    /// Create from a raw `CompactLength`.
    /// Caller must ensure `val` is a valid variant for LengthPercentage (was `unsafe`).
    static LengthPercentage fromRaw(CompactLength val) {
        return switch (val) {
            case CompactLength.Primitive p -> new Primitive(p.bits());
            case CompactLength.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// Get the underlying `CompactLength` representation.
    CompactLength intoRaw();

    /// Expand the compact representation into an [`ExpandedLengthPercentage`].
    default ExpandedLengthPercentage expand() {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return new ExpandedLengthPercentage.Length(value());
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return new ExpandedLengthPercentage.Percent(value());
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return new ExpandedLengthPercentage.Calc(calcValue());
        }
        throw new IllegalStateException("LengthPercentage contains a value with an invalid tag"); // JAFFY(GAP-010)
    }

    // ---- MaybeResolve / ResolveOrZero impls (util/resolve.rs) ----
    // Suffix per GAP-006: O = Option<f32> context, F = f32 context.

    /// `MaybeResolve<Option<f32>, Option<f32>>::maybe_resolve`
    /// Converts the given `LengthPercentage` into an absolute length.
    /// Can return `None` (when context is None for Percent variants).
    default @Opt float maybeResolveO(@Opt float context, CalcResolver calc) {
        int tag = tag();
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

    // ---- Trait impls ----

    /// `FromLength::from_length` — `Self::length(value.into() as f32)`
    static LengthPercentage fromLength(double value) {
        return length((float) value);
    }

    /// `FromPercent::from_percent`
    static LengthPercentage fromPercent(double value) {
        return percent((float) value);
    }

    /// `From<ExpandedLengthPercentage>`
    static LengthPercentage from(ExpandedLengthPercentage value) {
        return switch (value) {
            case ExpandedLengthPercentage.Length l -> length(l.val());
            case ExpandedLengthPercentage.Percent p -> percent(p.val());
            case ExpandedLengthPercentage.Calc c -> calc(c.handle());
        };
    }
}
