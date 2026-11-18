// JAFFY-UPSTREAM: src/tree/layout.rs
// JAFFY-NOTE: `OofCandidate` struct
// `NodeId` -> `long`, `u32` order -> `int` (GAP-014/GAP-015).
package net.morphismmc.jaffy.tree.layout;

import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.style.Position;

/// A record of an out-of-flow (`position: absolute` or `position: fixed`) box which has not yet
/// been laid out because its containing block is an ancestor of its parent.
///
/// Candidates are produced by the layout algorithm of the box's parent (which computes the static
/// position) and bubble up through `LayoutOutput` until they reach a node which acts as the box's
/// containing block, which lays the box out.
///
/// @param node the id of the out-of-flow node
/// @param order the `order` value that the node's layout should be assigned (its child index
///        within its parent)
/// @param position the position style of the node (`Position.ABSOLUTE` or `Position.FIXED`)
/// @param staticPosition the static position of the box in each axis
public record OofCandidate(long node, int order, Position position, Point<AxisStaticPosition> staticPosition) {}
