// JAFFY: upstream=src/style/grid.rs
// JAFFY-NOTE: `GridTemplateComponent<S>` enum
// and its impls. Upstream's owned enum is isomorphic to
// `GenericGridTemplateComponent<S, GridTemplateRepetition<S>>`; in Java the
// value type is `GenericGridTemplateComponent<GridTemplateRepetition>`
// directly (same alias-to-host mapping as `TrackSizingFunction`), and this
// class hosts the associated items. `as_component_ref` is the identity map.
// `S: CheapCloneStr` -> `String` (GAP-017).
// The `parse`/`FromStr` impls are deferred with the feature.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;

public final class GridTemplateComponent {

    private GridTemplateComponent() {}

    /// `TaffyAuto::AUTO`
    public static final GenericGridTemplateComponent<GridTemplateRepetition> AUTO =
            new GenericGridTemplateComponent.Single<>(TrackSizingFunction.AUTO);
    /// `TaffyMinContent::MIN_CONTENT`
    public static final GenericGridTemplateComponent<GridTemplateRepetition> MIN_CONTENT =
            new GenericGridTemplateComponent.Single<>(TrackSizingFunction.MIN_CONTENT);
    /// `TaffyMaxContent::MAX_CONTENT`
    public static final GenericGridTemplateComponent<GridTemplateRepetition> MAX_CONTENT =
            new GenericGridTemplateComponent.Single<>(TrackSizingFunction.MAX_CONTENT);
    /// `TaffyZero::ZERO`
    public static final GenericGridTemplateComponent<GridTemplateRepetition> ZERO =
            new GenericGridTemplateComponent.Single<>(TrackSizingFunction.ZERO);

    /// A single non-repeated track (`Single` variant constructor)
    public static GenericGridTemplateComponent<GridTemplateRepetition> single(
            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> size) {
        return new GenericGridTemplateComponent.Single<>(size);
    }

    /// A `repeat()` (`Repeat` variant constructor). Automatically generates grid tracks to fit the
    /// available space using the specified definite track lengths.
    /// Only valid if every track in template (not just the repetition) has a fixed size.
    public static GenericGridTemplateComponent<GridTemplateRepetition> repeat(GridTemplateRepetition repetition) {
        return new GenericGridTemplateComponent.Repeat<>(repetition);
    }

    /// `as_component_ref` — identity under the host-class mapping (upstream
    /// maps the owned enum onto the generic enum, which is one type here).
    public static GenericGridTemplateComponent<GridTemplateRepetition> asComponentRef(
            GenericGridTemplateComponent<GridTemplateRepetition> self) {
        return self;
    }

    /// Whether the track definition is a auto-repeated fragment
    /// (`GenericGridTemplateComponent#isAutoRepetition`, identical semantics).
    public static boolean isAutoRepetition(GenericGridTemplateComponent<GridTemplateRepetition> self) {
        return self.isAutoRepetition();
    }

    /// `TaffyFitContent::fit_content`
    public static GenericGridTemplateComponent<GridTemplateRepetition> fitContent(LengthPercentage argument) {
        return single(TrackSizingFunction.fitContent(argument));
    }

    /// `FromLength::from_length`
    public static GenericGridTemplateComponent<GridTemplateRepetition> fromLength(double value) {
        return single(TrackSizingFunction.fromLength(value));
    }

    /// `FromPercent::from_percent`
    public static GenericGridTemplateComponent<GridTemplateRepetition> fromPercent(double percent) {
        return single(TrackSizingFunction.fromPercent(percent));
    }

    /// `FromFr::from_fr`
    public static GenericGridTemplateComponent<GridTemplateRepetition> fromFr(double flex) {
        return single(TrackSizingFunction.fromFr(flex));
    }

    /// `From<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>>`
    public static GenericGridTemplateComponent<GridTemplateRepetition> from(
            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> input) {
        return single(input);
    }
}
