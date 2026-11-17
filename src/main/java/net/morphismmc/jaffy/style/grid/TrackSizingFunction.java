// JAFFY: upstream=src/style/grid.rs
// `type TrackSizingFunction = MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>`
// and its impl block. A Java class cannot alias a generic instantiation, so
// the associated items live as statics here and values stay
// `MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>`.
// The `parse` impl is deferred with the feature.
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.style.CompactBits;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;

public final class TrackSizingFunction {

    private TrackSizingFunction() {}

    /// `TaffyAuto::AUTO`
    public static final MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> AUTO =
            new MinMax<>(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.AUTO);
    /// `TaffyMinContent::MIN_CONTENT`
    public static final MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> MIN_CONTENT =
            new MinMax<>(MinTrackSizingFunction.MIN_CONTENT, MaxTrackSizingFunction.MIN_CONTENT);
    /// `TaffyMaxContent::MAX_CONTENT`
    public static final MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> MAX_CONTENT =
            new MinMax<>(MinTrackSizingFunction.MAX_CONTENT, MaxTrackSizingFunction.MAX_CONTENT);
    /// `TaffyZero::ZERO`
    public static final MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> ZERO =
            new MinMax<>(MinTrackSizingFunction.ZERO, MaxTrackSizingFunction.ZERO);

    /// Extract the min track sizing function
    public static MinTrackSizingFunction minSizingFunction(
            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> self) {
        return self.min();
    }

    /// Extract the max track sizing function
    public static MaxTrackSizingFunction maxSizingFunction(
            MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> self) {
        return self.max();
    }

    /// Determine whether at least one of the components ("min" and "max") are fixed sizing function
    ///
    /// A fixed sizing function is a `<length-percentage>`, which includes `calc()` values (and so
    /// `min()`, `max()` and `clamp()`) such as `minmax(min(100%, 300px), 1fr)`.
    /// <https://www.w3.org/TR/css-grid-1/#fixed-sizing-function>
    public static boolean hasFixedComponent(MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> self) {
        return CompactBits.isLengthPercentageOrCalc(self.min().tag())
                || CompactBits.isLengthPercentageOrCalc(self.max().tag());
    }

    /// `TaffyFitContent::fit_content`
    public static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> fitContent(LengthPercentage argument) {
        return new MinMax<>(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.fitContent(argument));
    }

    /// `FromLength::from_length`
    public static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> fromLength(double value) {
        return new MinMax<>(MinTrackSizingFunction.fromLength(value), MaxTrackSizingFunction.fromLength(value));
    }

    /// `FromPercent::from_percent`
    public static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> fromPercent(double percent) {
        return new MinMax<>(MinTrackSizingFunction.fromPercent(percent), MaxTrackSizingFunction.fromPercent(percent));
    }

    /// `FromFr::from_fr`
    public static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> fromFr(double flex) {
        return new MinMax<>(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.fromFr(flex));
    }

    /// `From<LengthPercentage>`
    public static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> from(LengthPercentage input) {
        return new MinMax<>(MinTrackSizingFunction.from(input), MaxTrackSizingFunction.from(input));
    }

    /// `From<LengthPercentageAuto>`
    public static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> from(LengthPercentageAuto input) {
        return new MinMax<>(MinTrackSizingFunction.from(input), MaxTrackSizingFunction.from(input));
    }

    /// `From<Dimension>`
    public static MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> from(Dimension input) {
        return new MinMax<>(MinTrackSizingFunction.from(input), MaxTrackSizingFunction.from(input));
    }
}
