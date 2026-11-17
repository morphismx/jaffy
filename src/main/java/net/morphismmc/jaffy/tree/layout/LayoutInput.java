// JAFFY-UPSTREAM: src/tree/layout.rs
// JAFFY-NOTE: `LayoutInput` struct
// serde impl deferred.
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.StyleHelpers;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;

/// A struct containing the inputs constraints/hints for laying out a node, which are passed in by the parent
///
/// @param runMode whether we only need to know the Node's size, or whether we need to perform a full layout
/// @param sizingMode whether a Node's style sizes should be taken into account or ignored
/// @param axis which axis we need the size of
/// @param knownDimensions known dimensions (width/height) which should be taken as fixed when
///        performing layout. For example, `knownDimensions.width = Some(WIDTH)` means
///        "what would the height of this node be, assuming the width is WIDTH". Layout functions
///        are called with both known dimensions set for final layout: "the exact size of this
///        node is WIDTHxHEIGHT, please lay out your children".
/// @param knownDimensionsAreDefinite whether each known dimension should be treated as a
///        *definite* size when laying out the node's own content. Ignored (treated as `true`)
///        for axes where the corresponding known dimension is `None`.
/// @param parentSize parent size dimensions, used for percentage resolution
/// @param availableSpace the space available to lay out into; a soft constraint for wrapping
/// @param verticalMarginsAreCollapsible specific to CSS block layout (margin collapsing)
public record LayoutInput(
        RunMode runMode,
        SizingMode sizingMode,
        RequestedAxis axis,
        SizeOptF32 knownDimensions,
        SizeBool knownDimensionsAreDefinite,
        SizeOptF32 parentSize,
        Size<AvailableSpace> availableSpace,
        LineBool verticalMarginsAreCollapsible) {

    /// A LayoutInput that can be used to request hidden layout
    public static final LayoutInput HIDDEN = new LayoutInput(
            // The important property for hidden layout
            RunMode.PERFORM_HIDDEN_LAYOUT,
            // The rest will be ignored
            SizingMode.INHERENT_SIZE,
            RequestedAxis.BOTH,
            SizeOptF32.NONE,
            SizeBool.TRUE,
            SizeOptF32.NONE,
            StyleHelpers.SizeAvailableSpaceExt.maxContent(),
            LineBool.FALSE);

    /// Swap the horizontal and vertical axes of every per-axis input.
    ///
    /// Lets a tree lay out a node in a vertical writing mode: transpose the inputs, run the
    /// (horizontal-tb) algorithm, then transpose the `LayoutOutput` back.
    /// `verticalMarginsAreCollapsible` is reset to `FALSE` as margins cannot collapse
    /// across a change of block axis.
    public LayoutInput transpose() {
        return new LayoutInput(
                runMode,
                sizingMode,
                axis.transpose(),
                knownDimensions.transpose(),
                knownDimensionsAreDefinite.transpose(),
                parentSize.transpose(),
                availableSpace.transpose(),
                LineBool.FALSE);
    }
}
