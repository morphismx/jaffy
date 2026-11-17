// JAFFY: upstream=src/style/mod.rs
// JAFFY-NOTE: `Display` enum
// Variants behind disabled features are omitted (block_layout/flexbox/grid
// are all in the default set, so all five variants land). The `parse` impl
// is deferred with the feature.
package net.morphismmc.jaffy.style;

/// Sets the layout used for the children of this node
///
/// The default values depends on on which feature flags are enabled. The order of precedence is: Flex, Grid, Block,
/// None.
public enum Display {

    /// The children will follow the block layout algorithm (`block_layout`)
    BLOCK,
    /// The children will follow the block layout algorithm and establish a new block formatting context
    /// (`block_layout`)
    FLOW_ROOT,
    /// The children will follow the flexbox layout algorithm (`flexbox`)
    FLEX,
    /// The children will follow the CSS Grid layout algorithm (`grid`)
    GRID,
    /// The node is hidden, and it's children will also be hidden
    NONE;

    /// The default Display mode (`flexbox` enabled: `Display::Flex`)
    public static final Display DEFAULT = FLEX;

    /// `core::fmt::Display` impl
    @Override
    public String toString() {
        return switch (this) {
            case NONE -> "NONE";
            case BLOCK -> "BLOCK";
            case FLOW_ROOT -> "FLOW-ROOT";
            case FLEX -> "FLEX";
            case GRID -> "GRID";
        };
    }
}
