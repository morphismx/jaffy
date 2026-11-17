// JAFFY: upstream=src/style/float.rs
// JAFFY-NOTE: `Clear` enum
// impl_parse_for_keyword_enum is parse-gated: deferred.
package net.morphismmc.jaffy.style.float_;

public enum Clear {
    /// The box clears left-floated boxes
    LEFT,
    /// The box clears right-floated boxes
    RIGHT,
    /// The box clears boxes floated in either direction
    BOTH,
    /// The box does not clear floated boxes (`#[default]`)
    NONE;
}
