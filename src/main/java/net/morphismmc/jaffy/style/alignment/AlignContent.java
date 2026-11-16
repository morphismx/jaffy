// JAFFY: port of upstream src/style/alignment.rs — `AlignContent` (and alias
// `JustifyContent`, the same type in Rust). serde/parse impls deferred.
package net.morphismmc.jaffy.style.alignment;

/// Sets the distribution of space between and around content items.
/// For Flexbox it controls alignment in the cross axis.
/// For Grid it controls alignment in the block axis.
public record AlignContent(AlignContentKeyword keyword, AlignmentSafety safety) {

    /// The layout mode's default alignment. See `AlignContentKeyword.NORMAL`.
    public static final AlignContent NORMAL =
            new AlignContent(AlignContentKeyword.NORMAL, AlignmentSafety.DEFAULT);
    /// Items are packed toward the start of the axis.
    public static final AlignContent START = new AlignContent(AlignContentKeyword.START, AlignmentSafety.DEFAULT);
    /// Items are packed toward the end of the axis.
    public static final AlignContent END = new AlignContent(AlignContentKeyword.END, AlignmentSafety.DEFAULT);
    /// Items are packed towards the flex-relative start of the axis.
    public static final AlignContent FLEX_START =
            new AlignContent(AlignContentKeyword.FLEX_START, AlignmentSafety.DEFAULT);
    /// Items are packed towards the flex-relative end of the axis.
    public static final AlignContent FLEX_END =
            new AlignContent(AlignContentKeyword.FLEX_END, AlignmentSafety.DEFAULT);
    /// Items are centered around the middle of the axis.
    public static final AlignContent CENTER = new AlignContent(AlignContentKeyword.CENTER, AlignmentSafety.DEFAULT);
    /// Items are stretched to fill the container.
    public static final AlignContent STRETCH =
            new AlignContent(AlignContentKeyword.STRETCH, AlignmentSafety.DEFAULT);
    /// First and last items flush with container edges (no gap).
    public static final AlignContent SPACE_BETWEEN =
            new AlignContent(AlignContentKeyword.SPACE_BETWEEN, AlignmentSafety.DEFAULT);
    /// The gap between first/last items equals the gap between items.
    public static final AlignContent SPACE_EVENLY =
            new AlignContent(AlignContentKeyword.SPACE_EVENLY, AlignmentSafety.DEFAULT);
    /// The gap between first/last items is half the gap between items.
    public static final AlignContent SPACE_AROUND =
            new AlignContent(AlignContentKeyword.SPACE_AROUND, AlignmentSafety.DEFAULT);
    /// Like `START`, but falls back to `START` on overflow.
    public static final AlignContent SAFE_START =
            new AlignContent(AlignContentKeyword.START, AlignmentSafety.SAFE);
    /// Like `END`, but falls back to `START` on overflow.
    public static final AlignContent SAFE_END = new AlignContent(AlignContentKeyword.END, AlignmentSafety.SAFE);
    /// Like `FLEX_START`, but falls back to `START` on overflow.
    public static final AlignContent SAFE_FLEX_START =
            new AlignContent(AlignContentKeyword.FLEX_START, AlignmentSafety.SAFE);
    /// Like `FLEX_END`, but falls back to `START` on overflow.
    public static final AlignContent SAFE_FLEX_END =
            new AlignContent(AlignContentKeyword.FLEX_END, AlignmentSafety.SAFE);
    /// Like `CENTER`, but falls back to `START` on overflow.
    public static final AlignContent SAFE_CENTER =
            new AlignContent(AlignContentKeyword.CENTER, AlignmentSafety.SAFE);

    /// `Default` impl — `#[default]` derives from field defaults = `NORMAL`
    public static final AlignContent DEFAULT = NORMAL;

    /// Returns `true` iff this carries the `safe` overflow-position modifier.
    public boolean isSafe() {
        return safety == AlignmentSafety.SAFE;
    }

    /// Returns this alignment with the given overflow-position modifier.
    public AlignContent withSafety(AlignmentSafety safety) {
        return new AlignContent(keyword, safety);
    }
}
