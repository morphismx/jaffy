// JAFFY: port of upstream src/style/grid.rs —
// `GenericGridTemplateComponent<S, Repetition>` enum. `S: CheapCloneStr` is
// concretised to `String` inside `GenericRepetition` (GAP-017).
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.geometry.MinMax;

/// A type representing a component in a `grid-template-*` defintion where the type
/// representing `repeat()`s is generic
public sealed interface GenericGridTemplateComponent<R extends GenericRepetition> {

    /// A single track sizing function
    record Single<R extends GenericRepetition>(MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> size)
            implements GenericGridTemplateComponent<R> {}

    /// A `repeat()`
    record Repeat<R extends GenericRepetition>(R repetition) implements GenericGridTemplateComponent<R> {}

    /// Whether the track definition is a auto-repeated fragment
    default boolean isAutoRepetition() {
        return switch (this) {
            case Single<R> s -> false;
            case Repeat<R> r -> r.repetition().count() instanceof RepetitionCount.AutoFit
                    || r.repetition().count() instanceof RepetitionCount.AutoFill;
        };
    }
}
