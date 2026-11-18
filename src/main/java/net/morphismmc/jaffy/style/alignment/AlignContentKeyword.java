// JAFFY-UPSTREAM: src/style/alignment.rs
// JAFFY-NOTE: `AlignContentKeyword`
package net.morphismmc.jaffy.style.alignment;

/// The position-keyword half of [`AlignContent`] (and its alias
/// `JustifyContent`).
///
/// Compute paths match on this enum directly so every match is exhaustive and
/// requires no `Safe*` siblings.
public enum AlignContentKeyword {
    /// The layout mode's default alignment: `Stretch` for flex and grid
    /// containers, `Start` for block containers. (Rust `#[default]`)
    NORMAL,
    /// Items are packed toward the start of the axis.
    START,
    /// Items are packed toward the end of the axis.
    END,
    /// Items are packed towards the flex-relative start of the axis.
    FLEX_START,
    /// Items are packed towards the flex-relative end of the axis.
    FLEX_END,
    /// Items are centered around the middle of the axis.
    CENTER,
    /// Items are stretched to fill the container.
    STRETCH,
    /// First and last items flush with container edges (no gap); the gap
    /// between items is distributed evenly.
    SPACE_BETWEEN,
    /// The gap between first/last items is the same as between items.
    SPACE_EVENLY,
    /// The gap between first/last items is half the gap between items.
    SPACE_AROUND;

    /// `Default` impl — `#[default] Normal`
    public static final AlignContentKeyword DEFAULT = NORMAL;

    /// `pub(crate) fn reversed` — reversed keyword for RTL contexts:
    /// `Start`<->`End`, `FlexStart`<->`FlexEnd`. `Stretch` maps to `End` to
    /// preserve the layout algorithms' historical handling. `Normal`,
    /// `Center` and the distribution keywords are direction-symmetric.
    public AlignContentKeyword reversed() {
        return switch (this) {
            case START -> END;
            case END -> START;
            case FLEX_START -> FLEX_END;
            case FLEX_END -> FLEX_START;
            case STRETCH -> END;
            default -> this;
        };
    }
}
