// JAFFY: upstream=src/geometry.rs
// JAFFY-NOTE: `Line<T>` (generic record)
package net.morphismmc.jaffy.geometry;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import net.morphismmc.just.function.ToFloatFunction;

public record Line<T>(T start, T end) {

    /// Applies the function `f` to both the start and end
    public <R> Line<R> map(Function<T, R> f) {
        return new Line<>(f.apply(start), f.apply(end));
    }

    /// `map` producing `Line<f32>` (GAP-006 F suffix)
    public LineF32 mapF(ToFloatFunction<T> f) {
        return new LineF32(f.applyAsFloat(start), f.applyAsFloat(end));
    }

    /// `map` producing `Line<Option<f32>>` (GAP-006 O suffix)
    public LineOptF32 mapO(ToFloatFunction<T> f) {
        return new LineOptF32(f.applyAsFloat(start), f.applyAsFloat(end));
    }

    /// `map` producing `Line<Option<u32>>` — f sees the element (including null for
    /// `Line<@Nullable T>`) and returns the raw `@Opt @Unsigned int` encoding
    /// (`OptI32.NONE` for None, GAP-001/GAP-006)
    public LineOptI32 mapOptI32(ToIntFunction<T> f) {
        return new LineOptI32(f.applyAsInt(start), f.applyAsInt(end));
    }

    /// `impl<T: Add> sum` — adds the start and end values together.
    /// Java takes the add function explicitly.
    public <U> U sum(BiFunction<T, T, U> add) {
        return add.apply(start, end);
    }

    /// `PartialEq` -> rustEq with caller-supplied element equality (GAP-004)
    public boolean rustEq(Line<T> other, BiPredicate<T, T> eq) {
        return eq.test(start, other.start) && eq.test(end, other.end);
    }
}
