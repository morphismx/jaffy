// JAFFY: upstream=src/style/grid.rs
// JAFFY-NOTE: `GridAreaEnd`
// upstream; `public` here as there is no Java crate visibility).
package net.morphismmc.jaffy.style.grid;

/// Logical end (`Start` or `End`)
public enum GridAreaEnd {
    /// The `Start` end
    START,
    /// The `End` end
    END;
}
