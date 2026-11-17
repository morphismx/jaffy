// JAFFY: port of upstream src/style/mod.rs — `BoxSizing` enum.
// The `parse` impl is deferred with the feature.
package net.morphismmc.jaffy.style;

/// Specifies whether size styles for this node are assigned to the node's "content box" or "border box"
///
/// - The "content box" is the node's inner size excluding padding, border and margin
/// - The "border box" is the node's outer size including padding and border (but still excluding margin)
///
/// This property modifies the application of the following styles:
///
///   - `size`
///   - `minSize`
///   - `maxSize`
///   - `flexBasis`
///
/// See <https://developer.mozilla.org/en-US/docs/Web/CSS/box-sizing>
public enum BoxSizing {

    /// Size styles such size, minSize, maxSize specify the box's "border box" (the size excluding margin but including
    /// padding/border)
    BORDER_BOX,
    /// Size styles such size, minSize, maxSize specify the box's "content box" (the size excluding
    /// padding/border/margin)
    CONTENT_BOX;

    /// Rust `#[default]` variant
    public static final BoxSizing DEFAULT = BORDER_BOX;
}
