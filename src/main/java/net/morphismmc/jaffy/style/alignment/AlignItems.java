// JAFFY: port of upstream src/style/alignment.rs — `AlignItems` (and aliases
// `JustifyItems`/`AlignSelf`/`JustifySelf`, which are the same type in Rust).
// serde/parse impls are feature-gated: deferred.
package net.morphismmc.jaffy.style.alignment;
import net.morphismmc.jaffy.style.Direction;

/// Used to control how child nodes are aligned.
/// For Flexbox it controls alignment in the cross axis.
/// For Grid it controls alignment in the block axis.
public record AlignItems(AlignItemsKeyword keyword, AlignmentSafety safety) {

    /// The layout mode's default alignment. See `AlignItemsKeyword.NORMAL`.
    public static final AlignItems NORMAL = new AlignItems(AlignItemsKeyword.NORMAL, AlignmentSafety.DEFAULT);
    /// Items are packed toward the start of the axis.
    public static final AlignItems START = new AlignItems(AlignItemsKeyword.START, AlignmentSafety.DEFAULT);
    /// Items are packed toward the end of the axis.
    public static final AlignItems END = new AlignItems(AlignItemsKeyword.END, AlignmentSafety.DEFAULT);
    /// Items are packed towards the flex-relative start of the axis.
    public static final AlignItems FLEX_START =
            new AlignItems(AlignItemsKeyword.FLEX_START, AlignmentSafety.DEFAULT);
    /// Items are packed towards the flex-relative end of the axis.
    public static final AlignItems FLEX_END =
            new AlignItems(AlignItemsKeyword.FLEX_END, AlignmentSafety.DEFAULT);
    /// Packed toward the start of the axis per the item's own direction.
    public static final AlignItems SELF_START =
            new AlignItems(AlignItemsKeyword.SELF_START, AlignmentSafety.DEFAULT);
    /// Packed toward the end of the axis per the item's own direction.
    public static final AlignItems SELF_END =
            new AlignItems(AlignItemsKeyword.SELF_END, AlignmentSafety.DEFAULT);
    /// Items are packed along the center of the cross axis.
    public static final AlignItems CENTER = new AlignItems(AlignItemsKeyword.CENTER, AlignmentSafety.DEFAULT);
    /// Items are aligned such as their baselines align.
    public static final AlignItems BASELINE = new AlignItems(AlignItemsKeyword.BASELINE, AlignmentSafety.DEFAULT);
    /// Stretch to fill the container.
    public static final AlignItems STRETCH = new AlignItems(AlignItemsKeyword.STRETCH, AlignmentSafety.DEFAULT);
    /// Like `START`, but falls back to `START` on overflow to avoid data loss.
    public static final AlignItems SAFE_START = new AlignItems(AlignItemsKeyword.START, AlignmentSafety.SAFE);
    /// Like `END`, but falls back to `START` on overflow to avoid data loss.
    public static final AlignItems SAFE_END = new AlignItems(AlignItemsKeyword.END, AlignmentSafety.SAFE);
    /// Like `FLEX_START`, but falls back to `START` on overflow.
    public static final AlignItems SAFE_FLEX_START =
            new AlignItems(AlignItemsKeyword.FLEX_START, AlignmentSafety.SAFE);
    /// Like `FLEX_END`, but falls back to `START` on overflow.
    public static final AlignItems SAFE_FLEX_END =
            new AlignItems(AlignItemsKeyword.FLEX_END, AlignmentSafety.SAFE);
    /// Like `CENTER`, but falls back to `START` on overflow.
    public static final AlignItems SAFE_CENTER = new AlignItems(AlignItemsKeyword.CENTER, AlignmentSafety.SAFE);
    /// Like `SELF_START`, but falls back to `START` on overflow.
    public static final AlignItems SAFE_SELF_START =
            new AlignItems(AlignItemsKeyword.SELF_START, AlignmentSafety.SAFE);
    /// Like `SELF_END`, but falls back to `START` on overflow.
    public static final AlignItems SAFE_SELF_END =
            new AlignItems(AlignItemsKeyword.SELF_END, AlignmentSafety.SAFE);

    /// `Default` impl — `#[default]` derives from field defaults = `NORMAL`
    public static final AlignItems DEFAULT = NORMAL;

    /// Returns `true` iff this carries the `safe` overflow-position modifier.
    public boolean isSafe() {
        return safety == AlignmentSafety.SAFE;
    }

    /// Returns this alignment with the given overflow-position modifier.
    public AlignItems withSafety(AlignmentSafety safety) {
        return new AlignItems(keyword, safety);
    }

    /// Returns true if the keyword is `Stretch` or `Normal` (ignoring any
    /// overflow-position modifier).
    public boolean isStretchOrNormal() {
        return keyword == AlignItemsKeyword.STRETCH || keyword == AlignItemsKeyword.NORMAL;
    }

    /// `resolve_self_relative` — resolve `SelfStart`/`SelfEnd` to `Start`/`End`
    /// based on the item's own `direction` per CSS Box Alignment §5.2. All
    /// other keywords are returned unchanged.
    ///
    /// `Start`/`End` in the compute paths are relative to the *container's*
    /// writing mode/direction, so in the inline axis `SelfStart` resolves to
    /// `Start` when the item's direction matches the container's and to `End`
    /// when it differs. Taffy only supports `horizontal-tb`, so in the block
    /// axis `SelfStart`/`SelfEnd` always resolve to `Start`/`End`.
    public AlignItems resolveSelfRelative(
            Direction itemDirection, Direction containerDirection, boolean axisIsInline) {
        boolean flip = axisIsInline && itemDirection != containerDirection;
        AlignItemsKeyword resolved =
                switch (keyword) {
                    case SELF_START -> flip ? AlignItemsKeyword.END : AlignItemsKeyword.START;
                    case SELF_END -> flip ? AlignItemsKeyword.START : AlignItemsKeyword.END;
                    default -> keyword;
                };
        return new AlignItems(resolved, safety);
    }
}
