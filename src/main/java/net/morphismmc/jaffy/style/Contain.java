// JAFFY-UPSTREAM: src/style/mod.rs
// JAFFY-NOTE: `Contain` u8 bitflag struct
// (u8 -> int per GAP-014; BitOr/BitOrAssign -> `or`).
// The `parse` impl is deferred with the feature.
package net.morphismmc.jaffy.style;

/// The layout-affecting parts of the CSS `contain` property.
///
/// Containment limits the ways in which a box's contents can affect layout outside of the box
/// (and vice versa). Taffy implements the layout-relevant containment types:
///
///   - `LAYOUT`: the box establishes an independent formatting context, and is treated
///     as having no baseline for baseline-alignment purposes (layout containment).
///   - `PAINT`: the box establishes an independent formatting context. Paint
///     containment's other effects (clipping, containing absolutely-positioned descendants,
///     stacking context) are outside of Taffy's scope.
///
/// The `style` containment type has no effect on layout and is therefore not represented
/// (it is accepted and ignored when parsing). Size and inline-size containment are not
/// currently implemented.
///
/// <https://developer.mozilla.org/en-US/docs/Web/CSS/contain>
public record Contain(int bits) {

    /// No containment (the default)
    public static final Contain NONE = new Contain(0);
    /// Layout containment: the box establishes an independent formatting context and is treated
    /// as having no baseline for baseline-alignment purposes.
    /// <https://drafts.csswg.org/css-contain-2/#containment-layout>
    public static final Contain LAYOUT = new Contain(1 << 0);
    /// Paint containment: the box establishes an independent formatting context. Its other
    /// effects don't affect layout.
    /// <https://drafts.csswg.org/css-contain-2/#containment-paint>
    public static final Contain PAINT = new Contain(1 << 1);
    /// The containment implied by `contain: content` (`layout paint`, ignoring style containment)
    public static final Contain CONTENT = new Contain(LAYOUT.bits | PAINT.bits);

    /// The default containment (no containment)
    public static final Contain DEFAULT = NONE;

    /// Returns whether `this` contains all of the containment types in `other`
    public boolean contains(Contain other) {
        return (bits & other.bits) == other.bits;
    }

    /// Returns whether `this` contains any of the containment types in `other`
    public boolean intersects(Contain other) {
        return (bits & other.bits) != 0;
    }

    /// Returns the union of the containment types in `this` and `other`
    public Contain union(Contain other) {
        return new Contain(bits | other.bits);
    }

    /// `core::ops::BitOr` — same as {@link #union}
    public Contain or(Contain rhs) {
        return union(rhs);
    }

    /// Whether this containment causes the box to establish an independent formatting context
    /// (both layout and paint containment do)
    public boolean establishesIndependentFormattingContext() {
        return intersects(LAYOUT.union(PAINT));
    }

    /// Whether this containment suppresses the box's baseline for baseline-alignment purposes
    /// (layout containment does, paint containment does not)
    public boolean suppressesBaseline() {
        return contains(LAYOUT);
    }

    /// Whether this containment prevents the box's overflowing content from contributing to an
    /// ancestor's scrollable overflow region (layout containment treats such overflow as ink
    /// overflow; paint containment clips it)
    public boolean containsScrollableOverflow() {
        return intersects(LAYOUT.union(PAINT));
    }
}
