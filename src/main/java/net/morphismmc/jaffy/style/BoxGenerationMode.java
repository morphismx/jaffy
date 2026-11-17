// JAFFY: upstream=src/style/mod.rs
// JAFFY-NOTE: `BoxGenerationMode` enum
package net.morphismmc.jaffy.style;

/// An abstracted version of the CSS `display` property where any value other than "none" is represented by "normal"
/// See: <https://www.w3.org/TR/css-display-3/#box-generation>
public enum BoxGenerationMode {

    /// The node generates a box in the regular way
    NORMAL,
    /// The node and it's descendants generate no boxes (they are hidden)
    NONE;

    /// The default of BoxGenerationMode
    public static final BoxGenerationMode DEFAULT = NORMAL;
}
