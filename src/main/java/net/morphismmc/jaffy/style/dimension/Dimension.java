// JAFFY-UPSTREAM: src/style/dimension.rs
// JAFFY-NOTE: `Dimension` (GAP-013)
//
// `impl Rect<Dimension>` (from_length/from_percent) lives in RectDimensionExt.
// serde/parse impls are feature-gated: deferred.
package net.morphismmc.jaffy.style.dimension;

import net.morphismmc.jaffy.style.CompactBits;
import net.morphismmc.jaffy.style.CompactLength;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

public sealed interface Dimension {

    /// `TaffyZero::ZERO` = `Self(CompactLength::ZERO)`
    Dimension ZERO = length(0.0f);
    /// `TaffyAuto::AUTO` = `Self(CompactLength::AUTO)`
    Dimension AUTO = auto();

    /// Non-pointer variant: `tagged_ptr = (f32_bits << 32) | tag`.
    record Primitive(long bits) implements Dimension {

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
        public boolean rustEq(Dimension other) {
            return other instanceof Primitive(long oBits) && bits == oBits;
        }
    }

    /// Pointer variant: `bits` is the tag, `calc` the Object handle.
    record Calc(long bits, Object calc) implements Dimension {

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
        public boolean rustEq(Dimension other) {
            return other instanceof Calc(long oBits, Object oCalc) && bits == oBits && calc == oCalc;
        }
    }

    // ---- `self.0.*` delegates ----

    /// `self.0.tag()` — Get the raw `CompactLength` tag (also `pub` on Dimension)
    int tag();

    /// `self.0.calc_tag()`
    int calcTag();

    /// `self.0.value()` — raw `CompactLength` value for non-calc variants (also `pub`)
    float value();

    /// `self.0.calc_value()` — calc() pointer for `calc()`/`fit-content(calc())` (also `pub`)
    @Nullable
    Object calcValue();

    default boolean isPlainCalc() {
        return CompactBits.isPlainCalc(calcTag());
    }

    /// `PartialEq` -> rustEq (GAP-004)
    boolean rustEq(Dimension other);

    // ---- Constructors ----

    /// An absolute length in some abstract units.
    static Dimension length(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.LENGTH_TAG));
    }

    /// A percentage length relative to the size of the containing block.
    /// Percentages are in the range [0.0, 1.0], NOT [0.0, 100.0].
    static Dimension percent(float val) {
        return new Primitive(CompactBits.packVal(val, CompactLength.PERCENT_TAG));
    }

    /// The dimension should be automatically computed according to
    /// algorithm-specific rules regarding the default size of boxes.
    static Dimension auto() {
        return new Primitive(CompactLength.AUTO_TAG);
    }

    /// `min-content` size.
    static Dimension minContent() {
        return new Primitive(CompactLength.MIN_CONTENT_TAG);
    }

    /// `max-content` size.
    static Dimension maxContent() {
        return new Primitive(CompactLength.MAX_CONTENT_TAG);
    }

    /// `fit-content` keyword — `max(min_content, min(max_content, stretch))`.
    static Dimension fitContent() {
        return new Primitive(CompactLength.FIT_CONTENT_KEYWORD_TAG);
    }

    /// `fit-content(px limit)` — `max(min_content, min(max_content, limit))`.
    static Dimension fitContentPx(float limit) {
        return new Primitive(CompactBits.packVal(limit, CompactLength.FIT_CONTENT_PX_TAG));
    }

    /// `fit-content(percent limit)`.
    static Dimension fitContentPercent(float limit) {
        return new Primitive(CompactBits.packVal(limit, CompactLength.FIT_CONTENT_PERCENT_TAG));
    }

    /// `stretch` keyword.
    static Dimension stretch() {
        return new Primitive(CompactLength.STRETCH_TAG);
    }

    /// `content` keyword (flex-basis only; elsewhere behaves as auto).
    static Dimension content() {
        return new Primitive(CompactLength.CONTENT_TAG);
    }

    /// A `calc()` value (`#[cfg(feature = "calc")]`).
    static Dimension calc(Object ptr) {
        return new Calc(CompactLength.CALC_TAG, ptr);
    }

    /// A `fit-content()` value whose limit is a `calc()` value
    /// (`#[cfg(feature = "calc")]`).
    static Dimension fitContentCalc(Object ptr) {
        return new Calc(CompactLength.FIT_CONTENT_CALC_TAG, ptr);
    }

    /// Create from a raw `CompactLength`.
    /// Caller must ensure `val` is a valid variant for Dimension (was `unsafe`).
    static Dimension fromRaw(CompactLength val) {
        return switch (val) {
            case CompactLength.Primitive p -> new Primitive(p.bits());
            case CompactLength.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// Get the underlying `CompactLength` representation.
    CompactLength intoRaw();

    // ---- Methods ----

    /// Returns true if the value is a `fit-content()` value with a `calc()` limit
    default boolean isFitContentCalc() {
        return CompactBits.isFitContentCalc(tag()); // self.0.is_fit_content_calc()
    }

    /// Get Length value if value is Length variant (`#[cfg(feature = "grid")]`)
    default @Opt float intoOption() {
        if (tag() == CompactLength.LENGTH_TAG) {
            return value();
        }
        return OptF32.NONE;
    }

    /// Returns true if value is Auto
    default boolean isAuto() {
        return tag() == CompactLength.AUTO_TAG; // self.0.is_auto()
    }

    /// Returns true if value is min-content, max-content, fit-content,
    /// fit-content(...), or stretch
    default boolean isSizingKeyword() {
        return CompactBits.isSizingKeyword(tag()); // self.0.is_sizing_keyword()
    }

    /// Returns true if value is the stretch keyword
    default boolean isStretch() {
        return tag() == CompactLength.STRETCH_TAG;
    }

    /// Returns true if value is the content keyword
    default boolean isContent() {
        return tag() == CompactLength.CONTENT_TAG; // self.0.is_content()
    }

    // ---- MaybeResolve / ResolveOrZero impls (util/resolve.rs) ----

    /// `MaybeResolve<Option<f32>, Option<f32>>::maybe_resolve`
    /// Converts the given `Dimension` into an absolute length.
    /// Can return `None` (Auto/Content/intrinsic keywords, or None context
    /// for Percent variants). Intrinsic sizing keywords cannot be resolved
    /// to a definite size out of context — layout algorithms that support
    /// them must handle them explicitly.
    default @Opt float maybeResolveO(@Opt float context, CalcResolver calc) {
        int tag = tag();
        if (tag == CompactLength.AUTO_TAG) {
            return OptF32.NONE;
        }
        // The content keyword is only valid for flex-basis. In any other
        // context it behaves as auto.
        if (tag == CompactLength.CONTENT_TAG) {
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
        if (isSizingKeyword()) {
            return OptF32.NONE;
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

    /// Expand the compact representation into an [`ExpandedDimension`].
    default ExpandedDimension expand() {
        int tag = tag();
        if (tag == CompactLength.LENGTH_TAG) {
            return new ExpandedDimension.Length(value());
        }
        if (tag == CompactLength.PERCENT_TAG) {
            return new ExpandedDimension.Percent(value());
        }
        if (tag == CompactLength.AUTO_TAG) {
            return new ExpandedDimension.Auto();
        }
        if (tag == CompactLength.MIN_CONTENT_TAG) {
            return new ExpandedDimension.MinContent();
        }
        if (tag == CompactLength.MAX_CONTENT_TAG) {
            return new ExpandedDimension.MaxContent();
        }
        if (tag == CompactLength.FIT_CONTENT_PX_TAG) {
            return new ExpandedDimension.FitContentPx(value());
        }
        if (tag == CompactLength.FIT_CONTENT_PERCENT_TAG) {
            return new ExpandedDimension.FitContentPercent(value());
        }
        if (tag == CompactLength.FIT_CONTENT_KEYWORD_TAG) {
            return new ExpandedDimension.FitContent();
        }
        if (isFitContentCalc()) { // #[cfg(feature = "calc")]
            return new ExpandedDimension.FitContentCalc(calcValue());
        }
        if (tag == CompactLength.STRETCH_TAG) {
            return new ExpandedDimension.Stretch();
        }
        if (tag == CompactLength.CONTENT_TAG) {
            return new ExpandedDimension.Content();
        }
        if (isPlainCalc()) { // #[cfg(feature = "calc")]
            return new ExpandedDimension.Calc(calcValue());
        }
        throw new IllegalStateException("Dimension contains a value with an invalid tag"); // JAFFY(GAP-010)
    }

    // ---- Trait impls ----

    /// `FromLength::from_length`
    static Dimension fromLength(double value) {
        return length((float) value);
    }

    /// `FromPercent::from_percent`
    static Dimension fromPercent(double value) {
        return percent((float) value);
    }

    /// `From<LengthPercentage>` = `Self(input.0)`
    static Dimension from(LengthPercentage input) {
        return switch (input) {
            case LengthPercentage.Primitive p -> new Primitive(p.bits());
            case LengthPercentage.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// `From<LengthPercentageAuto>` = `Self(input.0)`
    static Dimension from(LengthPercentageAuto input) {
        return switch (input) {
            case LengthPercentageAuto.Primitive p -> new Primitive(p.bits());
            case LengthPercentageAuto.Calc c -> new Calc(c.bits(), c.calc());
        };
    }

    /// `From<ExpandedDimension>`
    static Dimension from(ExpandedDimension value) {
        return switch (value) {
            case ExpandedDimension.Length l -> length(l.val());
            case ExpandedDimension.Percent p -> percent(p.val());
            case ExpandedDimension.Auto a -> auto();
            case ExpandedDimension.MinContent m -> minContent();
            case ExpandedDimension.MaxContent m -> maxContent();
            case ExpandedDimension.FitContentPx f -> fitContentPx(f.val());
            case ExpandedDimension.FitContentPercent f -> fitContentPercent(f.val());
            case ExpandedDimension.FitContentCalc f -> fitContentCalc(f.handle());
            case ExpandedDimension.FitContent f -> fitContent();
            case ExpandedDimension.Stretch s -> stretch();
            case ExpandedDimension.Content c -> content();
            case ExpandedDimension.Calc c -> calc(c.handle());
        };
    }
}
