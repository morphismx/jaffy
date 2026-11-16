// JAFFY: port of upstream src/tree/layout.rs — `Baselines` struct.
// serde impl deferred.
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

/// The first and last baselines of a node in the horizontal axis (i.e. baselines for horizontal text,
/// measured as an offset from the top edge of the node's border box).
///
/// A baseline is the line on which text sits. See <https://www.w3.org/TR/css-writing-modes-3/#intro-baselines>
/// for details.
public record Baselines(@Opt float first, @Opt float last) {

    /// A `Baselines` with neither a first nor a last baseline
    public static final Baselines NONE = new Baselines(OptF32.NONE, OptF32.NONE);

    /// Create a `Baselines` from just a first baseline
    public static Baselines fromFirst(@Opt float first) {
        return new Baselines(first, OptF32.NONE);
    }
}
