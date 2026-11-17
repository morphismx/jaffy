// JAFFY: upstream=src/style/float.rs
// JAFFY-NOTE: `Float` enum
//
package net.morphismmc.jaffy.style.float_;

import org.jspecify.annotations.Nullable;

// NOTE: this type shadows java.lang.Float within this package; any use of
// java.lang.Float in style/ must be fully qualified.
// impl_parse_for_keyword_enum is parse-gated: deferred.
public enum Float {
    /// The box is floated to the left
    LEFT,
    /// The box is floated to the right
    RIGHT,
    /// The box is not floated (`#[default]`)
    NONE;

    /// Whether the box is floated
    public boolean isFloated() {
        return this == LEFT || this == RIGHT;
    }

    /// Converts [`Float`] into `Option<FloatDirection>` — null for None.
    public @Nullable FloatDirection floatDirection() {
        return switch (this) {
            case LEFT -> FloatDirection.LEFT;
            case RIGHT -> FloatDirection.RIGHT;
            case NONE -> null;
        };
    }
}
