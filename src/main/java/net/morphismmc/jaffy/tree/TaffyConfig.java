// JAFFY-UPSTREAM: src/tree/taffy_tree.rs
// JAFFY-NOTE: `TaffyConfig`
//
// `pub(crate) struct` -> package-private mutable class.
package net.morphismmc.jaffy.tree;

/// Global configuration values for a TaffyTree instance
final class TaffyConfig {

    /// Whether to round layout values
    boolean useRounding;

    TaffyConfig() {
        this.useRounding = true;
    }
}
