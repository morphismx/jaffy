// JAFFY: upstream=src/style/block.rs
// JAFFY-NOTE: `TextAlign`
// plain enum). The BlockContainerStyle/BlockItemStyle traits land with
// style/mod.rs (CoreStyle/Style). serde/parse impls are feature-gated: deferred.
package net.morphismmc.jaffy.style.block;

/// Used by block layout to implement the legacy behaviour of `<center>` and
/// `<div align="left | right | center">`
public enum TextAlign {
    /// No special legacy text align behaviour. (Rust `#[default]`)
    AUTO,
    /// Corresponds to `-webkit-left` or `-moz-left` in browsers
    LEGACY_LEFT,
    /// Corresponds to `-webkit-right` or `-moz-right` in browsers
    LEGACY_RIGHT,
    /// Corresponds to `-webkit-center` or `-moz-center` in browsers
    LEGACY_CENTER,
}
