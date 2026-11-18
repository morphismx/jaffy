// JAFFY-UPSTREAM: src/style/mod.rs
// JAFFY-NOTE: `Direction`
// needed by AlignItems.resolveSelfRelative). serde/parse impls deferred.
package net.morphismmc.jaffy.style;

/// Sets the direction of text, table and grid columns, and horizontal overflow.
public enum Direction {
    /// Left-to-right (Rust `#[default]`)
    LTR,
    /// Right-to-left
    RTL;

    /// `Default` impl — `#[default] Ltr`
    public static final Direction DEFAULT = LTR;

    /// Returns true if the direction is right-to-left
    public boolean isRtl() {
        return this == RTL;
    }
}
