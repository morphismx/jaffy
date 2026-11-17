// JAFFY-UPSTREAM: src/tree/traits.rs
// JAFFY-NOTE: `LayoutPartialTree` trait
// plus the `pub(crate)` `LayoutPartialTreeExt` extension trait.
//
// GAP-017 concretizations:
//   GAT `CoreContainerStyle<'a>` -> `Style` (both upstream implementors
//     instantiate it as `&'a Style`);
//   assoc type `CustomIdent` -> `String` (DefaultCheapStr = String);
//   `*const ()` calc pointer -> `@Nullable Object` (the calc token produced
//     by `CompactLength.calcValue()` etc., GAP-013; null when calc is absent).
//
// `LayoutPartialTreeExt` has no separate Java interface: Rust's blanket impl
// `impl<T: LayoutPartialTree> LayoutPartialTreeExt for T` makes its four
// methods available on every implementor, so they are `default` methods on
// this interface (a standalone `LayoutPartialTreeExt extends
// LayoutPartialTree` would not be inherited, and `LayoutPartialTree extends
// LayoutPartialTreeExt` makes the defaults' calls unresolvable — GAP-017).
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.LineBool;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeBool;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.jaffy.tree.layout.RequestedAxis;
import net.morphismmc.jaffy.tree.layout.RunMode;
import net.morphismmc.jaffy.tree.layout.SizingMode;

import org.jspecify.annotations.Nullable;

/// Any type that implements {@link LayoutPartialTree} can be laid out using
/// Taffy's algorithms.
///
/// Note that this trait extends {@link TraversePartialTree} (not
/// {@link TraverseTree}). Taffy's algorithm implementations are designed such
/// that they can be used for laying out a single node that only has access to
/// its immediate children.
public interface LayoutPartialTree extends TraversePartialTree {

    /// Get the container node's core styles.
    ///
    /// Only called for the node being laid out, never for its children (those
    /// go through the `get*ChildStyle` methods), so it may be expressed in the
    /// node's own axes while child styles are expressed in the axes of the
    /// node laying them out.
    Style getCoreContainerStyle(long nodeId);

    /// Resolve a calc value (`resolve_calc_value`). The default returns `0.0`,
    /// matching upstream (a tree that cannot evaluate calc expressions).
    default float resolveCalcValue(@Nullable Object val, float basis) {
        return 0.0f;
    }

    /// Set the node's unrounded layout
    void setUnroundedLayout(long nodeId, Layout layout);

    /// Compute the specified node's size or full layout given the specified
    /// constraints
    LayoutOutput computeChildLayout(long nodeId, LayoutInput inputs);

    // ---- `LayoutPartialTreeExt` methods (blanket impl -> defaults) ----

    /// Compute the size of the node given the specified constraints
    /// (`measure_child_size`)
    default float measureChildSize(
            long nodeId,
            SizeOptF32 knownDimensions,
            SizeOptF32 parentSize,
            Size<AvailableSpace> availableSpace,
            SizingMode sizingMode,
            AbsoluteAxis axis,
            LineBool verticalMarginsAreCollapsible) {
        return computeChildLayout(
                        nodeId,
                        new LayoutInput(
                                RunMode.COMPUTE_SIZE,
                                sizingMode,
                                RequestedAxis.from(axis),
                                knownDimensions,
                                SizeBool.TRUE,
                                parentSize,
                                availableSpace,
                                verticalMarginsAreCollapsible))
                .size()
                .getAbs(axis);
    }

    /// Compute the size of the node given the specified constraints, in both
    /// axes (`measure_child_size_both`)
    default SizeF32 measureChildSizeBoth(
            long nodeId,
            SizeOptF32 knownDimensions,
            SizeOptF32 parentSize,
            Size<AvailableSpace> availableSpace,
            SizingMode sizingMode,
            LineBool verticalMarginsAreCollapsible) {
        return computeChildLayout(
                        nodeId,
                        new LayoutInput(
                                RunMode.COMPUTE_SIZE,
                                sizingMode,
                                RequestedAxis.BOTH,
                                knownDimensions,
                                SizeBool.TRUE,
                                parentSize,
                                availableSpace,
                                verticalMarginsAreCollapsible))
                .size();
    }

    /// Perform a full layout on the node given the specified constraints
    /// (`perform_child_layout`)
    default LayoutOutput performChildLayout(
            long nodeId,
            SizeOptF32 knownDimensions,
            SizeOptF32 parentSize,
            Size<AvailableSpace> availableSpace,
            SizingMode sizingMode,
            LineBool verticalMarginsAreCollapsible) {
        return computeChildLayout(
                nodeId,
                new LayoutInput(
                        RunMode.PERFORM_LAYOUT,
                        sizingMode,
                        RequestedAxis.BOTH,
                        knownDimensions,
                        SizeBool.TRUE,
                        parentSize,
                        availableSpace,
                        verticalMarginsAreCollapsible));
    }

    /// Alias to {@link #resolveCalcValue} with a shorter function name
    /// (`calc`; `calc` feature is on — the `#[cfg(not(calc))]` `0.0` variant
    /// does not apply)
    default float calc(@Nullable Object val, float basis) {
        return resolveCalcValue(val, basis);
    }
}
