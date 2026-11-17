// JAFFY: upstream=src/compute/block.rs
// JAFFY-NOTE: `BlockFormattingContext`
// (`float_layout` feature always on).
package net.morphismmc.jaffy.compute.block;

import net.morphismmc.jaffy.compute.float_.FloatContext;

/// Context for positioning Block and Float boxes within a Block Formatting
/// Context.
public final class BlockFormattingContext {

    /// The float positioning context that handles positioning floats within
    /// this Block Formatting Context.
    final FloatContext floatContext = new FloatContext();

    /// Create a new `BlockFormattingContext`.
    public static BlockFormattingContext create() {
        return new BlockFormattingContext();
    }

    /// Create an initial {@link BlockContext} for this
    /// `BlockFormattingContext` (`root_block_context`).
    public BlockContext rootBlockContext() {
        return new BlockContext(
                this,
                0.0f,
                net.morphismmc.jaffy.geometry.FloatInsets.ZERO,
                net.morphismmc.jaffy.geometry.FloatInsets.ZERO,
                Float.NEGATIVE_INFINITY,
                true,
                new boolean[] {false, false},
                null);
    }
}
