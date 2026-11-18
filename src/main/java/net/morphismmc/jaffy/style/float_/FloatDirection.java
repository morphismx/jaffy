// JAFFY-UPSTREAM: src/style/float.rs
// JAFFY-NOTE: `FloatDirection` enum
package net.morphismmc.jaffy.style.float_;

public enum FloatDirection {
    /// The box is floated to the left (`Left = 0`)
    LEFT,
    /// The box is floated to the right (`Right = 1`)
    RIGHT;

    /// The opposite side: `Right` for `Left` and `Left` for `Right`
    public FloatDirection opposite() {
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
}
