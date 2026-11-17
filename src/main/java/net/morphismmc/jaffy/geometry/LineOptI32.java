// JAFFY: port of upstream src/geometry.rs — `Line<Option<u32/usize>>` specialization
// (SPECIALIZATIONS.md). Distinct type from generic Line<T> (GAP-006).
// `Option<u32>` components -> `@Opt @Unsigned int` (`OptI32.NONE` = None, GAP-001).
package net.morphismmc.jaffy.geometry;

import java.util.function.IntUnaryOperator;

import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

public record LineOptI32(
        @Opt @Unsigned int start, @Opt @Unsigned int end) {

    /// A `Line<Option<u32>>` with both start and end `None`
    public static final LineOptI32 NONE = new LineOptI32(OptI32.NONE, OptI32.NONE);

    /// Applies the function `f` to both the start and end
    /// (f sees the raw @Opt encoding, including OptI32.NONE)
    public LineOptI32 map(IntUnaryOperator f) {
        return new LineOptI32(f.applyAsInt(start), f.applyAsInt(end));
    }
}
