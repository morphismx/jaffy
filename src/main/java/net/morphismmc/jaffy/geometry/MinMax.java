// JAFFY: port of upstream src/geometry.rs — `MinMax<Min, Max>` struct.
package net.morphismmc.jaffy.geometry;

import java.util.function.BiPredicate;

public record MinMax<Min, Max>(Min min, Max max) {

    /// `PartialEq` -> rustEq with caller-supplied element equality (GAP-004)
    public boolean rustEq(MinMax<Min, Max> other, BiPredicate<Min, Min> eqMin, BiPredicate<Max, Max> eqMax) {
        return eqMin.test(min, other.min) && eqMax.test(max, other.max);
    }
}
