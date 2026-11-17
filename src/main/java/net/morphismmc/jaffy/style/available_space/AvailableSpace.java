// JAFFY: port of upstream src/style/available_space.rs — `AvailableSpace` (GAP-012).
//
// `impl Size<AvailableSpace>` (into_options/maybe_set) lives in
// SizeAvailableSpaceExt.
// serde/parse impls are feature-gated: deferred.
package net.morphismmc.jaffy.style.available_space;

import java.util.function.Supplier;

import net.morphismmc.just.function.FloatSupplier;
import net.morphismmc.just.function.FloatUnaryOperator;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

public sealed interface AvailableSpace {

    /// `TaffyZero::ZERO` = `Self::Definite(0.0)`
    AvailableSpace ZERO = new Definite(0.0f);
    /// `TaffyMaxContent::MAX_CONTENT` = `Self::MaxContent`
    AvailableSpace MAX_CONTENT = new MaxContent();
    /// `TaffyMinContent::MIN_CONTENT` = `Self::MinContent`
    AvailableSpace MIN_CONTENT = new MinContent();

    /// The amount of space available is the specified number of pixels
    record Definite(float value) implements AvailableSpace {}

    /// The amount of space available is indefinite and the node should be
    /// laid out under a min-content constraint
    record MinContent() implements AvailableSpace {}

    /// The amount of space available is indefinite and the node should be
    /// laid out under a max-content constraint
    record MaxContent() implements AvailableSpace {}

    // ---- Methods ----

    /// Returns true for definite values, else false
    default boolean isDefinite() {
        return this instanceof Definite;
    }

    /// Convert to Option — Definite values become Some(value), constraints None.
    default @Opt float intoOption() {
        return this instanceof Definite d ? d.value : OptF32.NONE;
    }

    /// `unwrap_or` — the definite value or a default value
    default float unwrapOr(float def) {
        return OptF32.unwrapOr(intoOption(), def);
    }

    /// `unwrap` — the definite value; panics if not definite (GAP-010)
    default float unwrap() {
        return OptF32.unwrap(intoOption());
    }

    /// `or` — self if definite, else the default value
    default AvailableSpace or(AvailableSpace def) {
        return this instanceof Definite ? this : def;
    }

    /// `or_else` — self if definite, else the result of the callback
    default AvailableSpace orElse(Supplier<AvailableSpace> defaultCb) {
        return this instanceof Definite ? this : defaultCb.get();
    }

    /// `unwrap_or_else` — the definite value or the result of the callback
    default float unwrapOrElse(FloatSupplier defaultCb) {
        return OptF32.unwrapOrElse(intoOption(), defaultCb);
    }

    /// `maybe_set` — Some -> Definite(value), None -> self
    default AvailableSpace maybeSet(@Opt float value) {
        return OptF32.isSome(value) ? new Definite(value) : this;
    }

    /// `map_definite_value` — maps the contained value if definite
    default AvailableSpace mapDefiniteValue(FloatUnaryOperator mapFunction) {
        return this instanceof Definite d ? new Definite(mapFunction.applyAsFloat(d.value)) : this;
    }

    /// `compute_free_space` given the passed used_space
    default float computeFreeSpace(float usedSpace) {
        return switch (this) {
            case MaxContent m ->
                java.lang.Float.POSITIVE_INFINITY; // f32::INFINITY; java.lang.Float qualified — style.Float shadows
            case MinContent m -> 0.0f;
            case Definite d -> d.value - usedSpace;
        };
    }

    /// `is_roughly_equal` — definite values within f32::EPSILON compare equal
    default boolean isRoughlyEqual(AvailableSpace other) {
        if (this instanceof Definite a && other instanceof Definite b) {
            return Math.abs(a.value - b.value) < Math.ulp(1.0f); // f32::EPSILON == ulp(1.0)
        }
        if (this instanceof MinContent) {
            return other instanceof MinContent;
        }
        if (this instanceof MaxContent) {
            return other instanceof MaxContent;
        }
        return false;
    }

    // ---- MaybeMath impls for Self = `AvailableSpace` (util/math.rs) ----
    // Suffix per GAP-006: F = f32 rhs, O = Option<f32> rhs.

    /// `MaybeMath<f32>::maybe_min` — Min/MaxContent become Definite(rhs)
    default AvailableSpace maybeMinF(float rhs) {
        return switch (this) {
            case Definite d -> new Definite(F32.min(d.value, rhs));
            case MinContent m -> new Definite(rhs);
            case MaxContent m -> new Definite(rhs);
        };
    }

    /// `MaybeMath<f32>::maybe_max` — Min/MaxContent stay unchanged
    default AvailableSpace maybeMaxF(float rhs) {
        return switch (this) {
            case Definite d -> new Definite(F32.max(d.value, rhs));
            case MinContent m -> m;
            case MaxContent m -> m;
        };
    }

    /// `MaybeMath<f32>::maybe_clamp`
    default AvailableSpace maybeClampF(float min, float max) {
        return switch (this) {
            case Definite d -> new Definite(F32.max(F32.min(d.value, max), min));
            case MinContent m -> m;
            case MaxContent m -> m;
        };
    }

    /// `MaybeMath<f32>::maybe_add`
    default AvailableSpace maybeAddF(float rhs) {
        return this instanceof Definite d ? new Definite(d.value + rhs) : this;
    }

    /// `MaybeMath<f32>::maybe_sub`
    default AvailableSpace maybeSubF(float rhs) {
        return this instanceof Definite d ? new Definite(d.value - rhs) : this;
    }

    /// `MaybeMath<Option<f32>>::maybe_min`
    default AvailableSpace maybeMinO(@Opt float rhs) {
        if (this instanceof Definite d) {
            return OptF32.isSome(rhs) ? new Definite(F32.min(d.value, rhs)) : d;
        }
        if (this instanceof MinContent) {
            return OptF32.isSome(rhs) ? new Definite(rhs) : this;
        }
        // MaxContent
        return OptF32.isSome(rhs) ? new Definite(rhs) : this;
    }

    /// `MaybeMath<Option<f32>>::maybe_max` — Min/MaxContent always win
    default AvailableSpace maybeMaxO(@Opt float rhs) {
        if (this instanceof Definite d) {
            return OptF32.isSome(rhs) ? new Definite(F32.max(d.value, rhs)) : d;
        }
        return this;
    }

    /// `MaybeMath<Option<f32>>::maybe_clamp`
    default AvailableSpace maybeClampO(@Opt float min, @Opt float max) {
        if (!(this instanceof Definite d)) {
            return this;
        }
        if (OptF32.isSome(min) && OptF32.isSome(max)) {
            return new Definite(F32.max(F32.min(d.value, max), min));
        }
        if (OptF32.isNone(min) && OptF32.isSome(max)) {
            return new Definite(F32.min(d.value, max));
        }
        if (OptF32.isSome(min) && OptF32.isNone(max)) {
            return new Definite(F32.max(d.value, min));
        }
        return d;
    }

    /// `MaybeMath<Option<f32>>::maybe_add`
    default AvailableSpace maybeAddO(@Opt float rhs) {
        return this instanceof Definite d && OptF32.isSome(rhs) ? new Definite(d.value + rhs) : this;
    }

    /// `MaybeMath<Option<f32>>::maybe_sub`
    default AvailableSpace maybeSubO(@Opt float rhs) {
        return this instanceof Definite d && OptF32.isSome(rhs) ? new Definite(d.value - rhs) : this;
    }

    /// `PartialEq` -> rustEq: derived enum eq — Definite f32 compares IEEE (GAP-004)
    default boolean rustEq(AvailableSpace other) {
        return switch (this) {
            case Definite a -> other instanceof Definite b && a.value == b.value;
            case MinContent m -> other instanceof MinContent;
            case MaxContent m -> other instanceof MaxContent;
        };
    }

    // ---- From impls (GAP-006 suffix convention: F=f32, O=Option<f32>) ----

    /// `From<f32>` = `Self::Definite(value)`
    static AvailableSpace fromF(float value) {
        return new Definite(value);
    }

    /// `From<Option<f32>>` — Some -> Definite, None -> MaxContent
    static AvailableSpace fromO(@Opt float option) {
        return OptF32.isSome(option) ? new Definite(option) : new MaxContent();
    }

    /// `FromLength::from_length` — `value.into() as f32`
    static AvailableSpace fromLength(double value) {
        return new Definite((float) value);
    }
}
