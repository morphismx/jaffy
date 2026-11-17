// JAFFY: port of upstream src/compute/common/sizing_keyword.rs — shared
// resolution logic for sizing keywords on the `width`/`height` style
// properties.
//
// `pub(crate)` -> public (Java has no crate visibility; called from
// `compute.{flexbox,grid,block,oof}`). `Option<f32>` -> @Opt float;
// `Option<SizingKeywordResolution>` -> @Nullable; `impl Fn(*const (), f32)`
// -> `CalcResolver` (GAP-019 pattern); `&mut Size<Option<f32>>` ->
// `resolveAbsoluteSizingKeywords` returns the updated value (GAP-018:
// `SizeOptF32` is an immutable record — the caller reassigns).
package net.morphismmc.jaffy.compute.common;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.RectOptF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.CompactLength;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.tree.layout.SizingMode;
import net.morphismmc.jaffy.tree.traits.LayoutPartialTree;
import net.morphismmc.jaffy.util.CalcResolver;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;

import org.jspecify.annotations.Nullable;

public final class SizingKeyword {
    private SizingKeyword() {}

    /// How a sizing keyword resolves to a used size
    public sealed interface SizingKeywordResolution {
        /// The size is the result of measuring the item under the given
        /// available space constraint
        record Measure(AvailableSpace availableSpace) implements SizingKeywordResolution {}

        /// The size resolves to an exact value without measuring the item
        record Exact(float value) implements SizingKeywordResolution {}
    }

    /// Resolve an item's size style in one axis if it is a sizing keyword
    /// (`min-content`, `max-content`, `fit-content`, `fit-content(...)`, or
    /// `stretch`).
    ///
    /// - `stretchSize` is the size the item would take if stretched to fill
    ///   the available space (available space minus margins). Used by
    ///   `fit-content` and `stretch`.
    /// - `percentResolutionBasis` is the size that percentages resolve
    ///   against in this axis. Used by `fit-content(<percentage>)`.
    ///
    /// Returns `null` if the size style is not a sizing keyword, or if it
    /// cannot be resolved in the current context (in which case it behaves
    /// as `auto`).
    public static @Nullable SizingKeywordResolution resolveSizingKeyword(
            Dimension style, @Opt float stretchSize, @Opt float percentResolutionBasis, CalcResolver calcResolver) {
        int tag = style.tag();
        if (tag == CompactLength.MIN_CONTENT_TAG) {
            return new SizingKeywordResolution.Measure(AvailableSpace.MIN_CONTENT);
        }
        if (tag == CompactLength.MAX_CONTENT_TAG) {
            return new SizingKeywordResolution.Measure(AvailableSpace.MAX_CONTENT);
        }
        if (tag == CompactLength.FIT_CONTENT_PX_TAG) {
            return new SizingKeywordResolution.Measure(new AvailableSpace.Definite(style.value()));
        }
        if (tag == CompactLength.FIT_CONTENT_PERCENT_TAG) {
            return OptF32.isSome(percentResolutionBasis)
                    ? new SizingKeywordResolution.Measure(
                            new AvailableSpace.Definite(percentResolutionBasis * style.value()))
                    : null;
        }
        if (tag == CompactLength.FIT_CONTENT_KEYWORD_TAG) {
            return OptF32.isSome(stretchSize)
                    ? new SizingKeywordResolution.Measure(new AvailableSpace.Definite(stretchSize))
                    : null;
        }
        // `#[cfg(feature = "calc")]` — always on (Features.CALC); the match
        // arm order matters: this check sits between FIT_CONTENT_KEYWORD_TAG
        // and STRETCH_TAG, matching upstream's `_ if` guard position.
        if (style.isFitContentCalc()) {
            return OptF32.isSome(percentResolutionBasis)
                    ? new SizingKeywordResolution.Measure(new AvailableSpace.Definite(
                            calcResolver.resolve(style.calcValue(), percentResolutionBasis)))
                    : null;
        }
        if (tag == CompactLength.STRETCH_TAG) {
            return OptF32.isSome(stretchSize) ? new SizingKeywordResolution.Exact(stretchSize) : null;
        }
        return null;
    }

    /// Resolve the sizing keywords (`min-content`, `max-content`,
    /// `fit-content`, `fit-content(...)`, and `stretch`) on the size styles of
    /// an absolutely positioned item, filling in the corresponding
    /// `knownDimensions` axes.
    ///
    /// - `areaSize` is the size of the item's containing block (which insets
    ///   and percentages resolve against).
    /// - The stretch size in each axis is the containing block minus the
    ///   item's insets and margins in that axis.
    ///
    /// Upstream takes `known_dimensions: &mut Size<Option<f32>>` and returns
    /// `()`; here the updated `SizeOptF32` is returned instead (GAP-018).
    public static SizeOptF32 resolveAbsoluteSizingKeywords(
            LayoutPartialTree tree,
            long node,
            SizeOptF32 knownDimensions,
            Size<Dimension> sizeStyle,
            SizeF32 areaSize,
            RectOptF32 inset,
            RectOptF32 margin,
            SizingMode sizingMode) {
        SizeF32 stretchSize = new SizeF32(
                F32.max(
                        areaSize.width()
                                - OptF32.unwrapOr(inset.left(), 0.0f)
                                - OptF32.unwrapOr(inset.right(), 0.0f)
                                - OptF32.unwrapOr(margin.left(), 0.0f)
                                - OptF32.unwrapOr(margin.right(), 0.0f),
                        0.0f),
                F32.max(
                        areaSize.height()
                                - OptF32.unwrapOr(inset.top(), 0.0f)
                                - OptF32.unwrapOr(inset.bottom(), 0.0f)
                                - OptF32.unwrapOr(margin.top(), 0.0f)
                                - OptF32.unwrapOr(margin.bottom(), 0.0f),
                        0.0f));

        SizingKeywordResolution keywordWidth = OptF32.isNone(knownDimensions.width())
                ? resolveSizingKeyword(sizeStyle.width(), stretchSize.width(), areaSize.width(), tree::calc)
                : null;
        SizingKeywordResolution keywordHeight = OptF32.isNone(knownDimensions.height())
                ? resolveSizingKeyword(sizeStyle.height(), stretchSize.height(), areaSize.height(), tree::calc)
                : null;

        if (keywordWidth instanceof SizingKeywordResolution.Measure(AvailableSpace width)
                && keywordHeight instanceof SizingKeywordResolution.Measure(AvailableSpace height)) {
            // If both axes need to be measured then resolve them with a
            // single measure call
            SizeF32 measuredSize = tree.measureChildSizeBoth(
                    node,
                    SizeOptF32.NONE,
                    areaSize.mapO(v -> v),
                    new Size<>(width, height),
                    sizingMode,
                    LineBool.FALSE);
            return measuredSize.mapO(v -> v);
        }
        if (keywordWidth != null) {
            float width = switch (keywordWidth) {
                case SizingKeywordResolution.Exact(float w) -> w;
                case SizingKeywordResolution.Measure(AvailableSpace available) ->
                    tree.measureChildSize(
                            node,
                            knownDimensions,
                            areaSize.mapO(v -> v),
                            new Size<>(available, new AvailableSpace.Definite(stretchSize.height())),
                            sizingMode,
                            AbsoluteAxis.HORIZONTAL,
                            LineBool.FALSE);
            };
            knownDimensions = new SizeOptF32(width, knownDimensions.height());
        }
        if (keywordHeight != null) {
            float height = switch (keywordHeight) {
                case SizingKeywordResolution.Exact(float h) -> h;
                case SizingKeywordResolution.Measure(AvailableSpace available) ->
                    tree.measureChildSize(
                            node,
                            knownDimensions,
                            areaSize.mapO(v -> v),
                            new Size<>(
                                    OptF32.isSome(knownDimensions.width())
                                            ? new AvailableSpace.Definite(knownDimensions.width())
                                            : new AvailableSpace.Definite(stretchSize.width()),
                                    available),
                            sizingMode,
                            AbsoluteAxis.VERTICAL,
                            LineBool.FALSE);
            };
            knownDimensions = new SizeOptF32(knownDimensions.width(), height);
        }
        return knownDimensions;
    }
}
