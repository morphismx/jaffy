// JAFFY: port of upstream src/style/mod.rs — `CoreStyle` trait.
// `type CustomIdent = String` (GAP-017). Defaults that route through
// `Style::<S>::DEFAULT` are expressed as `Style.DEFAULT` accessors.
// `impl CoreStyle for &T` has no Java counterpart (references are not types).
package net.morphismmc.jaffy.style;

import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.just.math.Opt;

/// The core set of styles that are shared between all CSS layout nodes
///
/// Note that all methods come with a default implementation which simply returns the default value for that style
/// property
/// but this is a just a convenience to save on boilerplate for styles that your implementation doesn't support. You
/// will need
/// to override the default implementation for each style property that your style type actually supports.
public interface CoreStyle {

    /// Which box generation mode should be used
    default BoxGenerationMode boxGenerationMode() {
        return BoxGenerationMode.DEFAULT;
    }

    /// Is block layout?
    ///
    /// This should only return `true` for `display: block`, and NOT for `display: flow-root`.
    /// Flow-root boxes establish a new block formatting context and must not be treated as
    /// being part of their parent's block formatting context (which is what this method controls).
    default boolean isBlock() {
        return false;
    }

    /// Is it a compressible replaced element: a replaced element (image, video, canvas, ...) or a
    /// form control with an intrinsic size (`input`, `select`, `textarea`, `meter`, ...)?
    /// Such boxes resolve an `auto` width to their intrinsic size rather than being stretched by
    /// block layout, and have a zero min-content contribution in grid layout.
    /// <https://drafts.csswg.org/css-sizing-3/#compressible>
    default boolean isCompressibleReplaced() {
        return false;
    }

    /// Is it a replaced element (image, video, canvas, ...)? Unlike other compressible replaced
    /// elements (form controls), an `auto`-sized replaced element is not stretched between its
    /// insets under `normal` alignment.
    /// <https://html.spec.whatwg.org/multipage/rendering.html#replaced-elements>
    default boolean isReplaced() {
        return false;
    }

    /// Which box do size styles apply to
    default BoxSizing boxSizing() {
        return BoxSizing.BORDER_BOX;
    }

    /// The direction of text, table and grid columns, and horizontal overflow.
    default Direction direction() {
        return Direction.LTR;
    }

    // Overflow properties

    /// How children overflowing their container should affect layout
    default Point<Overflow> overflow() {
        return Style.DEFAULT.overflow();
    }

    /// How much space (in points) should be reserved for the scrollbars of `SCROLL` nodes.
    default float scrollbarWidth() {
        return 0.0f;
    }

    // Position properties

    /// What should the `position` value of this struct use as a base offset?
    default Position position() {
        return Style.DEFAULT.position();
    }

    /// How should the position of this element be tweaked relative to the layout defined?
    default Rect<LengthPercentageAuto> inset() {
        return Style.DEFAULT.inset();
    }

    /// Which out-of-flow (`absolute`/`fixed`) descendants this node establishes the containing block for.
    ///
    /// This is the single policy point for containing-block establishment: it decides both which
    /// out-of-flow boxes a node claims and lays out, and (for grid containers) whether the static
    /// position of an out-of-flow child is derived from its grid area.
    ///
    /// The default matches plain CSS positioning: a node is the containing block for `absolute` boxes
    /// when its own `position` is not `static`, and never for `fixed` boxes (which are claimed by the
    /// root). Implementations may override this so that other style properties (such as `transform`,
    /// `filter` or `will-change` in a full CSS implementation) establish a containing block.
    default ContainingBlockClaims isContainingBlock() {
        return new ContainingBlockClaims(position().isPositioned(), false);
    }

    // Size properies

    /// Sets the initial size of the item
    default Size<Dimension> size() {
        return Style.DEFAULT.size();
    }

    /// Controls the minimum size of the item
    default Size<LengthPercentageAuto> minSize() {
        return Style.DEFAULT.minSize();
    }

    /// Controls the maximum size of the item
    default Size<LengthPercentageAuto> maxSize() {
        return Style.DEFAULT.maxSize();
    }

    /// Sets the preferred aspect ratio for the item
    /// The ratio is calculated as width divided by height.
    default @Opt float aspectRatio() {
        return Style.DEFAULT.aspectRatio();
    }

    // Spacing Properties

    /// How large should the margin be on each side?
    default Rect<LengthPercentageAuto> margin() {
        return Style.DEFAULT.margin();
    }

    /// How large should the padding be on each side?
    default Rect<LengthPercentage> padding() {
        return Style.DEFAULT.padding();
    }

    /// How large should the border be on each side?
    default Rect<LengthPercentage> border() {
        return Style.DEFAULT.border();
    }

    /// The layout-affecting parts of the CSS `contain` property that apply to this node
    default Contain contain() {
        return Contain.NONE;
    }
}
