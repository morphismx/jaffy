// JAFFY: port of upstream src/tree/layout.rs — `CollapsibleMarginSet`.
// `f32_max`/`f32_min` -> `F32.max`/`f32Min` (GAP-002). serde impl deferred.
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.just.math.F32;

/// A set of margins that are available for collapsing with for block layout's margin collapsing
///
/// @param positive the largest positive margin
/// @param negative the smallest negative margin (with largest absolute value)
public record CollapsibleMarginSet(float positive, float negative) {

    /// A default margin set with no collapsible margins
    public static final CollapsibleMarginSet ZERO = new CollapsibleMarginSet(0.0f, 0.0f);

    /// Create a set from a single margin
    public static CollapsibleMarginSet fromMargin(float margin) {
        if (margin >= 0.0f) {
            return new CollapsibleMarginSet(margin, 0.0f);
        }
        return new CollapsibleMarginSet(0.0f, margin);
    }

    /// Collapse a single margin with this set
    public CollapsibleMarginSet collapseWithMargin(float margin) {
        if (margin >= 0.0f) {
            return new CollapsibleMarginSet(F32.max(positive, margin), negative);
        }
        return new CollapsibleMarginSet(positive, F32.min(negative, margin));
    }

    /// Collapse another margin set with this set
    public CollapsibleMarginSet collapseWithSet(CollapsibleMarginSet other) {
        return new CollapsibleMarginSet(F32.max(positive, other.positive), F32.min(negative, other.negative));
    }

    /// Resolve the resultant margin from this set once all collapsible margins
    /// have been collapsed into it
    public float resolve() {
        return positive + negative;
    }
}
