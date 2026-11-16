// JAFFY: port of upstream src/compute/block.rs — `BlockContext<'_>`
// (`float_layout` feature always on).
//
// Rust `&'bfc mut BlockFormattingContext` -> a direct reference; root and
// sub-contexts share the same `BlockFormattingContext` object (GAP-018).
package net.morphismmc.jaffy.compute.block;

import net.morphismmc.jaffy.compute.float_.BfcSlot;
import net.morphismmc.jaffy.compute.float_.ContentSlot;
import net.morphismmc.jaffy.compute.float_.PlacedFloatedBox;
import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;
import net.morphismmc.jaffy.util.Sys;
import org.jspecify.annotations.Nullable;


/// Context for each individual Block within a Block Formatting Context.
///
/// Contains a mutable reference to the BlockFormattingContext + block-specific
/// data.
public final class BlockContext {

    /// The id + position of a floated box placed since a {@link FloatMark}
    /// (`floats_placed_since` item `(u64, Point<f32>)`).
    public record PlacedFloat(long id, PointF32 position) {}

    /// A mutable reference to the root BlockFormattingContext that this
    /// BlockContext belongs to.
    private final BlockFormattingContext bfc;
    /// The y-offset of the border-top of the block node, relative to the
    /// border-top of the root node of the Block Formatting Context it belongs
    /// to.
    private final float yOffset;
    /// The x-inset of the border-box in from each side of the block node,
    /// relative to the root node of the Block Formatting Context it belongs
    /// to.
    private final FloatInsets insets;
    /// The x-insets of the content box.
    private FloatInsets contentBoxInsets;
    /// The height that floats take up in the element (`NEGATIVE_INFINITY` if
    /// the element's subtree does not contain any floats).
    private float floatContentContribution;
    /// Whether the node is the root of the Block Formatting Context it belongs
    /// to.
    private final boolean isRoot;
    /// Whether a float has been placed (on each side) whose position adjoins
    /// the current margin-collapse strut of this block (i.e. whose final
    /// position can still be moved by margins that collapse into that strut).
    /// Such floats force clearance on cleared elements whose margins adjoin
    /// the same strut.
    private final boolean[] adjoiningFloats;
    /// The value of `adjoiningFloats` frozen at the first point at which
    /// in-flow content was committed within this block (resolving the position
    /// of the block's top margin strut). `null` if no in-flow content has been
    /// committed yet.
    private boolean @Nullable [] topAdjoiningFloats;

    BlockContext(
            BlockFormattingContext bfc,
            float yOffset,
            FloatInsets insets,
            FloatInsets contentBoxInsets,
            float floatContentContribution,
            boolean isRoot,
            boolean[] adjoiningFloats,
            boolean @Nullable [] topAdjoiningFloats) {
        this.bfc = bfc;
        this.yOffset = yOffset;
        this.insets = insets;
        this.contentBoxInsets = contentBoxInsets;
        this.floatContentContribution = floatContentContribution;
        this.isRoot = isRoot;
        this.adjoiningFloats = adjoiningFloats;
        this.topAdjoiningFloats = topAdjoiningFloats;
    }

    /// Create a sub-`BlockContext` for a child block node.
    public BlockContext subContext(float additionalYOffset, FloatInsets insets) {
        FloatInsets merged = this.insets.plus(insets);
        return new BlockContext(
                this.bfc,
                this.yOffset + additionalYOffset,
                merged,
                merged,
                Float.NEGATIVE_INFINITY,
                false,
                // Floats adjoining the parent's current strut also adjoin this
                // block's top strut (if this block's top margin collapses with
                // its first child's, which is checked separately).
                new boolean[] {this.adjoiningFloats[0], this.adjoiningFloats[1]},
                null);
    }

    /// Returns whether this block is the root block of its Block Formatting
    /// Context.
    public boolean isBfcRoot() {
        return this.isRoot;
    }

    /// Set the width of the overall Block Formatting Context. This is used to
    /// resolve positions that are relative to the right of the context such as
    /// right-floated boxes.
    ///
    /// Sub-blocks within a Block Formatting Context should use
    /// {@link #subContext} to create a sub-`BlockContext` with `insets`
    /// instead.
    public void setWidth(float availableWidth) {
        this.bfc.floatContext.setWidth(availableWidth);
    }

    /// Set the x-axis content-box insets of the `BlockContext`. These are the
    /// difference between the border-box and the content-box of the box
    /// (padding + border + scrollbar_gutter).
    public void applyContentBoxInset(FloatInsets contentBoxXInsets) {
        this.contentBoxInsets = this.insets.plus(contentBoxXInsets);
    }

    /// Whether the float context contains any floats.
    public boolean hasFloats() {
        return this.bfc.floatContext.hasFloats();
    }

    /// Whether the float context contains any floats that extend to or below
    /// minY.
    public boolean hasActiveFloats(float minY) {
        return this.bfc.floatContext.hasActiveFloats(minY + this.yOffset);
    }

    /// Position a floated box with the context. `id` identifies the box in
    /// {@link #floatsPlacedSince}.
    public PointF32 placeFloatedBox(
            long id,
            SizeF32 floatedBox,
            float minY,
            FloatDirection direction,
            Clear clear,
            boolean adjoinsUnresolvedStrut) {
        if (adjoinsUnresolvedStrut) {
            this.adjoiningFloats[direction.ordinal()] = true;
        }
        PointF32 pos = this.bfc.floatContext.placeFloatedBox(
                id,
                floatedBox,
                minY + this.yOffset,
                this.contentBoxInsets,
                direction,
                clear);
        pos = new PointF32(pos.x() - this.insets.left(), pos.y() - this.yOffset);
        this.floatContentContribution =
                Sys.f32Max(this.floatContentContribution, pos.y() + floatedBox.height());
        return pos;
    }

    /// A mark of the floats placed in the Block Formatting Context so far, for
    /// {@link #floatsPlacedSince}.
    public FloatMark floatMark() {
        return new FloatMark(
                this.bfc.floatContext.leftFloats().size(),
                this.bfc.floatContext.rightFloats().size());
    }

    /// The id and position of each floated box placed since `mark` was taken:
    /// left floats in the order they were placed, then right floats. Positions
    /// are relative to this block, as returned by {@link #placeFloatedBox}.
    public java.util.List<PlacedFloat> floatsPlacedSince(FloatMark mark) {
        var floatContext = this.bfc.floatContext;
        java.util.List<PlacedFloat> out = new java.util.ArrayList<>();
        for (PlacedFloatedBox placed : floatContext.leftFloats().subList(mark.left(), floatContext.leftFloats().size())) {
            PointF32 pos = floatContext.placedPosition(placed, FloatDirection.LEFT);
            out.add(new PlacedFloat(placed.id(), new PointF32(pos.x() - this.insets.left(), pos.y() - this.yOffset)));
        }
        for (PlacedFloatedBox placed : floatContext.rightFloats().subList(mark.right(), floatContext.rightFloats().size())) {
            PointF32 pos = floatContext.placedPosition(placed, FloatDirection.RIGHT);
            out.add(new PlacedFloat(placed.id(), new PointF32(pos.x() - this.insets.left(), pos.y() - this.yOffset)));
        }
        return out;
    }

    /// Search a space suitable for laying out non-floated content of the given
    /// height into.
    public ContentSlot findContentSlot(float minY, float height, Clear clear, @Opt @Unsigned int after) {
        ContentSlot slot = this.bfc.floatContext.findContentSlot(
                minY + this.yOffset, height, this.contentBoxInsets, clear, after);
        return new ContentSlot(
                slot.segmentId(),
                slot.x() - this.insets.left(),
                slot.y() - this.yOffset,
                slot.width(),
                slot.height());
    }

    /// Search for a space suitable for laying out a box that establishes an
    /// independent formatting context (whose border box must not overlap
    /// floats).
    public BfcSlot findBfcSlot(
            float minY,
            float minHeight,
            FloatInsets margins,
            Direction direction,
            Clear clear,
            @Opt @Unsigned int after) {
        BfcSlot slot = this.bfc.floatContext.findBfcSlot(
                minY + this.yOffset, minHeight, this.contentBoxInsets, margins, direction, clear, after);
        return new BfcSlot(
                slot.segmentId(),
                slot.x() - this.insets.left(),
                slot.y() - this.yOffset,
                slot.borderWidth(),
                slot.stretchWidth(),
                slot.height());
    }

    /// Get the bottom of lowest relevant float for the specific clear
    /// property.
    public @Opt float clearedThreshold(Clear clear) {
        @Opt float threshold = this.bfc.floatContext.clearedThreshold(clear);
        return OptF32.isSome(threshold)
                ? threshold - this.yOffset
                : threshold;
    }

    /// Whether a float that is adjoining the current margin-collapse strut has
    /// been placed on the side(s) relevant to the passed clear property.
    public boolean hasAdjoiningFloat(Clear clear) {
        return switch (clear) {
            case LEFT -> this.adjoiningFloats[0];
            case RIGHT -> this.adjoiningFloats[1];
            case BOTH -> this.adjoiningFloats[0] || this.adjoiningFloats[1];
            case NONE -> false;
        };
    }

    /// Merge adjoining float flags propagated from a child block into this
    /// block's flags.
    void mergeAdjoiningFloats(boolean[] flags) {
        this.adjoiningFloats[0] |= flags[0];
        this.adjoiningFloats[1] |= flags[1];
    }

    /// Record that in-flow content has been committed within this block,
    /// resolving the position of the current margin-collapse strut. Floats
    /// placed before this point no longer adjoin the current strut. The flags
    /// for the block's top strut are frozen at the first commit.
    void commitStrut() {
        if (this.topAdjoiningFloats == null) {
            this.topAdjoiningFloats = new boolean[] {this.adjoiningFloats[0], this.adjoiningFloats[1]};
        }
        this.adjoiningFloats[0] = false;
        this.adjoiningFloats[1] = false;
    }

    /// The adjoining float flags for this block's top margin strut: floats
    /// placed while the position of the block's top strut was still
    /// unresolved.
    boolean[] topAdjoiningFloats() {
        return this.topAdjoiningFloats != null
                ? this.topAdjoiningFloats
                : this.adjoiningFloats;
    }

    /// Update the height that descendant floats consume with the height that
    /// floats consume within a particular child (`childContribution` is
    /// relative to this block's border-top).
    public void addChildFloatedContentHeightContribution(float childContribution) {
        this.floatContentContribution = Sys.f32Max(this.floatContentContribution, childContribution);
    }

    /// Returns the height that descendant floats consume.
    public float floatedContentHeightContribution() {
        return this.floatContentContribution;
    }
}
