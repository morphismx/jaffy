// JAFFY-UPSTREAM: src/compute/common/alignment.rs
// JAFFY-NOTE: generic CSS
// alignment code shared by the Flexbox and CSS Grid algorithms.
//
// `pub(crate)` fns -> public static methods on a class named for the module
// (callers live in `compute.{flexbox,grid,block}` — Java has no crate
// visibility). `Option<f32>` -> @Opt float (GAP-001); `usize` -> int
// (GAP-014); `debug_assert!` omitted per GAP-010 (release semantics — the
// NORMAL precondition is a caller contract documented in the javadoc).
package net.morphismmc.jaffy.compute.common;

import net.morphismmc.jaffy.style.Overflow;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.alignment.AlignmentSafety;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

public final class Alignment {
    private Alignment() {}

    /// The first baseline of a child box (measured from the top of its border
    /// box, like `Baselines`) as used for baseline alignment and for
    /// determining its container's baseline, given the baseline it reported,
    /// its border-box height, and its vertical `overflow`.
    ///
    /// Scroll containers' baselines are determined from their content as if
    /// scrolled to the initial position, but are additionally clamped to their
    /// border box. A scroll container with no baseline synthesizes one from
    /// its border-box bottom edge.
    /// See <https://github.com/w3c/csswg-drafts/issues/7660>
    public static @Opt float childFirstBaseline(@Opt float firstBaseline, float height, Overflow overflowY) {
        if (overflowY.isScrollContainer()) {
            return F32.max(F32.min(OptF32.unwrapOr(firstBaseline, height), height), 0.0f);
        }
        return firstBaseline;
    }

    /// Resolve the `safe`/`unsafe` overflow-position fallback for a self-level
    /// alignment value (used by `align-self` / `justify-self`-style sites and
    /// by absolutely-positioned items in flex/grid). If the alignment subject
    /// overflows its alignment container and the requested alignment is
    /// `safe`, fall back to logical `Start` per CSS Box Alignment
    /// <https://www.w3.org/TR/css-align-3/#overflow-values>. Otherwise drop
    /// the safety modifier and return the bare keyword.
    public static AlignItemsKeyword resolveSelfAlignmentSafety(AlignItems alignment, boolean overflows) {
        if (alignment.safety() == AlignmentSafety.SAFE && overflows) {
            return AlignItemsKeyword.START;
        }
        return alignment.keyword();
    }

    /// Resolve any spec-defined fallbacks for the given {@link AlignContent}
    /// value, returning the bare position keyword the alignment math should
    /// use.
    ///
    /// In addition to the spec at <https://www.w3.org/TR/css-align-3/> this
    /// implementation follows the resolution of
    /// <https://github.com/w3c/csswg-drafts/issues/10154>.
    ///
    /// `normal` behaves differently in each layout mode, so callers must
    /// resolve {@link AlignContentKeyword#NORMAL} to the keyword it behaves
    /// as before calling this function (if it is not resolved then it is
    /// treated as `stretch`). Upstream enforces this with a `debug_assert!`.
    public static AlignContentKeyword applyAlignmentFallback(
            float freeSpace, int numItems, AlignContent alignmentMode) {
        AlignContentKeyword keyword = switch (alignmentMode.keyword()) {
            case NORMAL -> AlignContentKeyword.STRETCH;
            default -> alignmentMode.keyword();
        };
        boolean isSafe = alignmentMode.safety() == AlignmentSafety.SAFE;

        // 1. If there is only a single item being aligned or the items
        //    overflow the container, the distributed alignment keywords
        //    (`stretch`, `space-*`) fall back to a positional keyword. The
        //    `space-*` fallbacks gain implicit `safe` semantics so step 2 can
        //    flip them to `Start` on overflow, whereas `stretch` falls back
        //    to plain `flex-start`.
        //    https://www.w3.org/TR/css-align-3/#distribution-values
        if (numItems <= 1 || freeSpace <= 0.0f) {
            switch (keyword) {
                case STRETCH -> keyword = AlignContentKeyword.FLEX_START;
                case SPACE_BETWEEN -> {
                    keyword = AlignContentKeyword.FLEX_START;
                    isSafe = true;
                }
                case SPACE_AROUND, SPACE_EVENLY -> {
                    keyword = AlignContentKeyword.CENTER;
                    isSafe = true;
                }
                default -> {}
            }
        }

        // 2. Safe alignment falls back to `Start` whenever the alignment
        //    subject would overflow the alignment container.
        if (freeSpace <= 0.0f && isSafe) {
            keyword = AlignContentKeyword.START;
        }

        return keyword;
    }

    /// Generic alignment function that is used:
    ///   - For both align-content and justify-content alignment
    ///   - For both the Flexbox and CSS Grid algorithms
    ///
    /// CSS Grid does not apply gaps as part of alignment, so the gap
    /// parameter should always be set to zero for CSS Grid.
    public static float computeAlignmentOffset(
            float freeSpace,
            int numItems,
            float gap,
            AlignContentKeyword alignmentMode,
            boolean layoutIsFlexReversed,
            boolean isFirst) {
        if (isFirst) {
            return switch (alignmentMode) {
                case START -> 0.0f;
                case FLEX_START -> layoutIsFlexReversed ? freeSpace : 0.0f;
                case END -> freeSpace;
                case FLEX_END -> layoutIsFlexReversed ? 0.0f : freeSpace;
                case CENTER -> freeSpace / 2.0f;
                case NORMAL, STRETCH -> 0.0f;
                case SPACE_BETWEEN -> 0.0f;
                case SPACE_AROUND -> freeSpace >= 0.0f ? (freeSpace / numItems) / 2.0f : freeSpace / 2.0f;
                case SPACE_EVENLY -> freeSpace >= 0.0f ? freeSpace / (numItems + 1) : freeSpace / 2.0f;
            };
        }
        float clamped = F32.max(freeSpace, 0.0f);
        return gap
                + switch (alignmentMode) {
                    case START, FLEX_START, END, FLEX_END, CENTER, NORMAL, STRETCH -> 0.0f;
                    case SPACE_BETWEEN -> clamped / (numItems - 1);
                    case SPACE_AROUND -> clamped / numItems;
                    case SPACE_EVENLY -> clamped / (numItems + 1);
                };
    }
}
