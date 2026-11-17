// JAFFY: upstream=src/compute/float.rs
// JAFFY-NOTE: `FloatContext`
// `FIT_TOLERANCE` + `float_fits_horizontally` (module items).
//
// Type mappings:
//   `Range<f32>` (segment y)         -> mutable `yStart`/`yEnd` fields on Segment
//   `Range<usize>` (last_placed)     -> parallel `int[]` start/end arrays
//   `[Option<f32>; 2]` clear_bottoms -> @Opt float[] (NaN payload, GAP-001)
//   `Option<usize>` params/returns   -> @Opt @Unsigned int (OptI32.NONE)
//   `Vec<Segment>`/`Vec<PlacedFloatedBox>` -> ArrayList
package net.morphismmc.jaffy.compute.float_;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.morphismmc.jaffy.Features;
import net.morphismmc.jaffy.geometry.FloatInsets;
import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.float_.Clear;
import net.morphismmc.jaffy.style.float_.FloatDirection;
import net.morphismmc.jaffy.util.DebugLogger;
import net.morphismmc.just.fmt.RustFmt;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

/// A context for placing floated boxes
public final class FloatContext {

    /// Tolerance used when checking whether a box fits in a horizontal space, to absorb `f32`
    /// rounding errors (e.g. percentage widths and margins that sum to exactly 100% may otherwise
    /// exceed the container width and spuriously wrap)
    ///
    /// JAFFY: `pub(crate)` upstream -> `public` (callers cross package boundaries).
    public static final float FIT_TOLERANCE = 0.001f;

    /// Whether a floated box of the given width fits horizontally given the float insets
    /// (`float_insets`) and containing block insets (`cb_insets`) that apply to it.
    ///
    /// A float is normally placed at the further-in of the float edge and the containing block
    /// edge on the side it is floated towards (its "lead" side). From that position it must:
    ///
    ///   - not overlap any float on the opposite ("trail") side (CSS2 §9.5.1 rule 3), and
    ///   - not extend past the containing block's trail edge if there is a float on its lead
    ///     side (CSS2 §9.5.1 rule 7: a float may only stick out of its containing block if it
    ///     is already as far towards its float direction as possible).
    ///
    /// Note that a float which is not constrained by other floats *may* overflow its
    /// containing block's trail edge (rules 1 and 7).
    static boolean floatFitsHorizontally(
            float width, FloatDirection direction, float bfcWidth, FloatInsets floatInsets, FloatInsets cbInsets) {
        FloatDirection lead = direction;
        FloatDirection trail = direction.opposite();
        float xInset = F32.max(
                FloatInsetsFloatDirectionExt.get(floatInsets, lead), FloatInsetsFloatDirectionExt.get(cbInsets, lead));
        boolean fitsOppositeFloats = FloatInsetsFloatDirectionExt.get(floatInsets, trail) == 0.0f
                || xInset + width <= bfcWidth - FloatInsetsFloatDirectionExt.get(floatInsets, trail) + FIT_TOLERANCE;
        boolean fitsContainingBlock = FloatInsetsFloatDirectionExt.get(floatInsets, lead) == 0.0f
                || xInset + width <= bfcWidth - FloatInsetsFloatDirectionExt.get(cbInsets, trail) + FIT_TOLERANCE;
        return fitsOppositeFloats && fitsContainingBlock;
    }

    /// The available space constraint that applies to the root Block Formatting Context
    /// for which this `FloatContext` manages floats.
    private float availableWidth;
    /// Whether the float context contains any floats
    private boolean hasFloats;
    /// A list of left-floated boxes within the context
    private final List<PlacedFloatedBox> leftFloats;
    /// A list of right-floated boxes within the context
    private final List<PlacedFloatedBox> rightFloats;
    /// A list of non-overlapping horizontal "segments" within the context.
    /// Each segment has the same available width for it's entire height.
    private final List<Segment> segments;
    /// A closed-open range indicating which segment the last placed float
    /// was placed (on each side). `[Range<usize>; 2]` -> parallel int[2] arrays.
    private final int[] lastPlacedFloatStarts;
    private final int[] lastPlacedFloatEnds;
    /// The bottom (y + height) of the lowest float placed on each side, including
    /// zero-sized floats (which occupy no segment). Left in slot 0, right in slot 1.
    private final @Opt float[] clearBottoms;
    /// The topmost y position allowed for a new float (CSS2 float rule 5), including
    /// the tops of zero-sized floats (which occupy no segment)
    private @Opt float floatCeiling;

    /// `impl Default for FloatContext`
    public FloatContext() {
        this.availableWidth = 0.0f;
        this.hasFloats = false;
        this.leftFloats = new ArrayList<>();
        this.rightFloats = new ArrayList<>();
        this.segments = new ArrayList<>();
        this.lastPlacedFloatStarts = new int[2];
        this.lastPlacedFloatEnds = new int[2];
        this.clearBottoms = new float[] {OptF32.NONE, OptF32.NONE};
        this.floatCeiling = OptF32.NONE;
    }

    /// Create a new empty `FloatContext` — `FloatContext::new()`
    public static FloatContext newFloatContext() {
        return new FloatContext();
    }

    /// Whether the float context contains any floats
    public boolean hasFloats() {
        return hasFloats;
    }

    /// Whether the float context contains any floats that extend to or below minY
    public boolean hasActiveFloats(float minY) {
        return hasFloats && !segments.isEmpty() && segments.getLast().yEnd > minY;
    }

    /// Set the width of the `FloatContext`
    public void setWidth(float availableWidth) {
        this.availableWidth = availableWidth;
    }

    /// Returns a slice of placed left floats
    public List<PlacedFloatedBox> leftFloats() {
        return Collections.unmodifiableList(leftFloats);
    }

    /// Returns a slice of placed right floats
    public List<PlacedFloatedBox> rightFloats() {
        return Collections.unmodifiableList(rightFloats);
    }

    /// Divide a segment into two segments so that a new float can be placed and have it's
    /// vertical start and end at exact segment boundaries
    private void subdivideSegment(int idx, float divideAtY) {
        Segment oldSegment = segments.get(idx);
        Segment newSegment = new Segment(divideAtY, oldSegment.yEnd, oldSegment.insets, oldSegment.hasFloat.clone());
        if (!(divideAtY >= oldSegment.yStart && divideAtY < oldSegment.yEnd) || oldSegment.yStart == divideAtY) {
            if (Features.DEBUG) {
                DebugLogger.NODE_LOGGER.labelledDebugLog("old_segment", oldSegment.toString());
                DebugLogger.NODE_LOGGER.labelledDebugLog("divide_at_y", RustFmt.debug(divideAtY));
            }
            throw new AssertionError(
                    "assertion failed: old_segment.y.contains(&divide_at_y) && old_segment.y.start != divide_at_y");
        }
        oldSegment.yEnd = divideAtY;

        segments.add(idx + 1, newSegment);
    }

    /// The (x, y) coordinates of a box previously placed in the context on the `direction` side
    public PointF32 placedPosition(PlacedFloatedBox placed, FloatDirection direction) {
        return switch (direction) {
            case LEFT -> new PointF32(placed.xInset(), placed.y());
            case RIGHT -> new PointF32(availableWidth - placed.xInset() - placed.width(), placed.y());
        };
    }

    /// Update the last placed float start and end values
    private void updateLastPlacedFloat(FloatDirection direction, int start, int end) {
        int slot = direction.ordinal();
        lastPlacedFloatStarts[slot] = Integer.max(lastPlacedFloatStarts[slot], start);
        lastPlacedFloatEnds[slot] = Integer.max(lastPlacedFloatEnds[slot], end);
    }

    /// Position a floated box with the context, returning the (x, y) coordinates.
    /// `id` is stored with the placed box and is not otherwise interpreted.
    public PointF32 placeFloatedBox(
            long id,
            SizeF32 floatedBox,
            float minY,
            FloatInsets containingBlockInsets,
            FloatDirection direction,
            Clear clear) {
        hasFloats = true;

        PlacedFloatedBox placedFloatedBox =
                placeFloatedBoxInner(id, floatedBox, minY, containingBlockInsets, direction, clear);

        int slot = direction.ordinal();
        float bottom = placedFloatedBox.y() + placedFloatedBox.height();
        clearBottoms[slot] = OptF32.mapOr(clearBottoms[slot], bottom, b -> F32.max(b, bottom));
        float y = placedFloatedBox.y();
        floatCeiling = OptF32.mapOr(floatCeiling, y, ceiling -> F32.max(ceiling, y));

        PointF32 pos = placedPosition(placedFloatedBox, direction);
        switch (direction) {
            case LEFT -> leftFloats.add(placedFloatedBox);
            case RIGHT -> rightFloats.add(placedFloatedBox);
        }
        return pos;
    }

    /// Inner implementation of float placement, split into a separate function so that it can early-return
    private PlacedFloatedBox placeFloatedBoxInner(
            long id,
            SizeF32 floatedBox,
            float minY,
            FloatInsets containingBlockInsets,
            FloatDirection direction,
            Clear clear) {
        int slot = direction.ordinal();

        // Floats (including zero-sized floats) that occupy no segment still constrain the
        // position of later boxes: rule 5 (a float may not be higher than the top of any
        // earlier float) and `clear` (which clears past the bottom of floats on the
        // relevant side, regardless of their width)
        float minYv = F32.max(minY, OptF32.unwrapOr(floatCeiling, Float.NEGATIVE_INFINITY));
        minYv = F32.max(minYv, OptF32.unwrapOr(clearedThreshold(clear), Float.NEGATIVE_INFINITY));

        // Ensure that float:
        //    - Starts at or after the last placed float in either direction (CSS2 float rule 5:
        //      "The outer top of a floating box may not be higher than the outer top of any block
        //      or floated box generated by an element earlier in the source document")
        //    - Respects "clear"
        int floatStart = Integer.max(lastPlacedFloatStarts[0], lastPlacedFloatStarts[1]);
        int hwm = switch (clear) {
            case LEFT -> Integer.max(floatStart, lastPlacedFloatEnds[0] + 1);
            case RIGHT -> Integer.max(floatStart, lastPlacedFloatEnds[1] + 1);
            case BOTH -> Integer.max(lastPlacedFloatEnds[0], lastPlacedFloatEnds[1]) + 1;
            case NONE -> floatStart;
        };

        // Ensure that float is placed in a segment at or below "min_y"
        // (ensuring that it is placed at or below min_y within it's segment happens below)
        int startIdx = segments.size();
        for (int i = hwm; i < segments.size(); i++) {
            if (segments.get(i).yEnd > minYv) {
                startIdx = i;
                break;
            }
        }
        float startY = minYv;
        int endIdx = startIdx;

        // Loop over remaining segments, trying to place the float in a position
        // that has space to accomodate it.
        @Opt
        @Unsigned
        int start = OptI32.NONE;
        @Opt
        @Unsigned
        int end = OptI32.NONE;
        float placedInset;
        outer:
        while (true) {
            // Start segment does not exist:
            //
            // This means no existing segment can accomodate the float so we must create a new
            // segment below all existing segments. A new segment will always have space for
            // the float, so we can exit the loop at this point.
            if (startIdx >= segments.size()) {
                placedInset = FloatInsetsFloatDirectionExt.get(containingBlockInsets, direction);
                break;
            }
            Segment startSegment = segments.get(startIdx);

            // Candidate start segment doesn't have (horizontal) space for the float:
            // => retry with the next segment
            if (!startSegment.fitsFloatWidth(floatedBox, direction, availableWidth, containingBlockInsets)) {
                startIdx += 1;
                endIdx = Integer.max(endIdx, startIdx);
                continue;
            }

            startY = F32.max(startY, startSegment.yStart);
            float availableHeight = startSegment.yEnd - startY;
            FloatFitter fitter = new FloatFitter(availableWidth, availableHeight, containingBlockInsets);
            fitter.unionInsets(startSegment.insets);

            // Pinning the start segment, loop over segments starting with the start segment
            // to find the end segment:
            //   - The selected segment range must have enough height to contain the float
            //   - All of the segments in the range must have enough horizontal width to contain the float
            //     TODO: must ensure that width is in the correct place.
            while (true) {
                // End segment does not exist:
                //
                // This means no existing segment can accomodate the float so we must create a new
                // segment below all existing segments
                if (endIdx >= segments.size()) {
                    start = startIdx;
                    placedInset = fitter.placedInset(direction);
                    break outer;
                }
                Segment endSegment = segments.get(endIdx);

                // Check horizontal fit
                //
                // If it does not fit horizontally then it will never fit in this position, so
                // continue the outer loop to find and check a new position
                fitter.unionInsets(endSegment.insets);
                if (!fitter.fitsHoriontally(floatedBox.width(), direction)) {
                    startIdx += 1;
                    endIdx = Integer.max(endIdx, startIdx);
                    continue outer;
                }

                // Check vertical fit
                //
                // If it does not (yet) fit vertically then continue the inner loop to add another
                // segment to the range of segments we are placing the float in
                if (endIdx != startIdx) {
                    fitter.addHeight(endSegment.yEnd - endSegment.yStart);
                }
                if (!fitter.fitsVertically(floatedBox.height())) {
                    endIdx += 1;
                    continue;
                }

                start = startIdx;
                end = endIdx;
                placedInset = fitter.placedInset(direction);
                break outer;
            }
        }

        // Handle case where floated box is placed after all existing segments
        if (start == OptI32.NONE) {
            // Before the first float, the context is float-free all the way up: the first float
            // gets an empty segment above it like any other float placed below all segments, so
            // that content positioned above it (e.g. by a negative margin) is not moved down to
            // it. The first float itself may be above the top of the block formatting context
            // (`y < 0`): its containing block (CSS2 float rule 4) may be pulled up by a negative
            // margin.
            float lastYEnd = segments.isEmpty() ? Float.NEGATIVE_INFINITY : segments.getLast().yEnd;
            if (startY > lastYEnd) {
                segments.add(new Segment(lastYEnd, startY, FloatInsets.ZERO, new boolean[2]));
            }

            float startYv = F32.max(lastYEnd, startY);

            FloatInsets insets = containingBlockInsets;
            insets = FloatInsetsFloatDirectionExt.set(
                    insets, direction, FloatInsetsFloatDirectionExt.get(insets, direction) + floatedBox.width());
            boolean[] hasFloat = new boolean[2];
            hasFloat[slot] = true;
            segments.add(new Segment(startYv, startYv + floatedBox.height(), insets, hasFloat));

            // Update last_placed_float
            int startIdx2 = segments.size() - 1;
            updateLastPlacedFloat(direction, startIdx2, startIdx2 + 1);

            return new PlacedFloatedBox(
                    id,
                    floatedBox.width(),
                    floatedBox.height(),
                    FloatInsetsFloatDirectionExt.get(containingBlockInsets, direction),
                    startYv);
        }

        // Else unwrap the index of the segment that the start of the floating box is placed in
        int startIdxV = start;

        // If the floated box doesn't start at the exact same y-offset as the segment it starts in, then
        // subdivide that segment into two segments at the y-offset that the floated box starts at, and increment
        // `start_idx` so that the floating box is placed in the second of the two segments.
        if (startY != segments.get(startIdxV).yStart) {
            subdivideSegment(startIdxV, startY);
            startIdxV += 1;
            if (end != OptI32.NONE) {
                end += 1;
            }
        }

        // A zero-height float takes up no vertical space, but still narrows content and floats
        // whose vertical extent crosses its position, so it gets its own zero-height segment
        if (floatedBox.height() == 0.0f) {
            Segment segment = segments.get(startIdxV).copy();
            segment.yStart = startY;
            segment.yEnd = startY;
            segments.add(startIdxV, segment);
            end = startIdxV;
        }

        int endIdxV;
        if (end == OptI32.NONE) {
            // The float extends below all existing segments: add a segment for the part of it
            // below the last segment (its insets are set along with the other segments below)
            float lastYEnd = segments.getLast().yEnd;
            float endY = startY + floatedBox.height();
            if (endY > lastYEnd) {
                segments.add(new Segment(lastYEnd, endY, containingBlockInsets, new boolean[2]));
            }
            endIdxV = segments.size() - 1;
        } else {
            int e = end;
            float endY = startY + floatedBox.height();

            // Due to floating point imprecision, `end_y` may coincide with (or fall before)
            // the start of the end segment even though the sum of the candidate segment
            // heights was computed as sufficient. In that case the float effectively ends at
            // the boundary of the previous segment, so walk back to the segment that actually
            // contains `end_y`.
            while (e > startIdxV && endY <= segments.get(e).yStart) {
                e -= 1;
            }

            // Only subdivide if `end_y` falls strictly within the segment. If it lands on
            // (or, due to floating point imprecision, beyond) a segment boundary then no
            // subdivision is needed.
            if (segments.get(e).yStart < endY && endY < segments.get(e).yEnd) {
                subdivideSegment(e, endY);
            }

            endIdxV = e;
        }

        // Update inset for the range of segments that the float is placed in
        float placedInsetPlusWidth = placedInset + floatedBox.width();
        for (int i = startIdxV; i <= endIdxV; i++) {
            Segment segment = segments.get(i);
            segment.insets = FloatInsetsFloatDirectionExt.set(segment.insets, direction, placedInsetPlusWidth);
            segment.hasFloat[slot] = true;
        }

        // Update last_placed_float
        updateLastPlacedFloat(direction, startIdxV, endIdxV + 1);

        return new PlacedFloatedBox(id, floatedBox.width(), floatedBox.height(), placedInset, startY);
    }

    /// Get the end segment of the last float on side(s) specified by the clear parameter (if any)
    ///
    /// Returns `OptI32.NONE` if no float has been placed on the relevant side(s)
    private @Opt @Unsigned int clearedSegment(Clear clear) {
        int leftEnd = lastPlacedFloatEnds[0];
        int rightEnd = lastPlacedFloatEnds[1];
        return switch (clear) {
            case LEFT -> leftEnd > 0 ? leftEnd : OptI32.NONE;
            case RIGHT -> rightEnd > 0 ? rightEnd : OptI32.NONE;
            case BOTH -> leftEnd > 0 || rightEnd > 0 ? Integer.max(leftEnd, rightEnd) : OptI32.NONE;
            case NONE -> OptI32.NONE;
        };
    }

    /// Get the bottom of lowest relevant float for the specific clear property
    public @Opt float clearedThreshold(Clear clear) {
        return switch (clear) {
            case LEFT -> clearBottoms[0];
            case RIGHT -> clearBottoms[1];
            case BOTH -> {
                float l = clearBottoms[0];
                float r = clearBottoms[1];
                yield OptF32.isSome(l) && OptF32.isSome(r) ? F32.max(l, r) : OptF32.isSome(l) ? l : r;
            }
            case NONE -> OptF32.NONE;
        };
    }

    /// Search for a space suitable for laying out non-floated content into
    ///
    /// The returned slot avoids the floats beside any part of `height` below its top edge (a line
    /// box is shortened by every float its vertical extent intersects). The slot's `height` is
    /// how far down from its top edge its width stays valid: content taller than that may be
    /// beside further floats.
    public ContentSlot findContentSlot(
            float minY, float height, FloatInsets containingBlockInsets, Clear clear, @Opt @Unsigned int after) {
        // Subtract each inset separately (here and below): `- insets.sum()` rounds differently for large values
        if (!hasActiveFloats(minY)) {
            return new ContentSlot(
                    OptI32.NONE,
                    containingBlockInsets.left(),
                    minY,
                    availableWidth - containingBlockInsets.left() - containingBlockInsets.right(),
                    Float.POSITIVE_INFINITY);
        }

        // Clearance clears past the bottom of floats on the relevant side, including
        // zero-sized floats which occupy no segment
        float minYv = F32.max(minY, OptF32.unwrapOr(clearedThreshold(clear), Float.NEGATIVE_INFINITY));

        // The min starting segment index
        int atLeast = OptI32.isSome(after) ? after + 1 : 0;
        @Opt
        @Unsigned
        int clearedSeg = clearedSegment(clear);
        int hwm = Integer.max(atLeast, clearedSeg != OptI32.NONE ? clearedSeg + 1 : 0);

        int startIdx = segments.size();
        for (int i = hwm; i < segments.size(); i++) {
            if (segments.get(i).yEnd > minYv) {
                startIdx = i;
                break;
            }
        }
        if (startIdx < segments.size()) {
            Segment segment = segments.get(startIdx);
            float y = F32.max(segment.yStart, minYv);
            float endY = y + height;

            // Combine the insets of every segment beside the content's height, then extend
            // the slot down over any further segments that don't narrow it
            FloatInsets insets = containingBlockInsets.max(segment.insets);
            float slotHeight = Float.POSITIVE_INFINITY;
            for (int i = startIdx + 1; i < segments.size(); i++) {
                Segment seg = segments.get(i);
                if (seg.yStart < endY) {
                    insets = insets.max(seg.insets);
                } else if (seg.insets.left() > insets.left() || seg.insets.right() > insets.right()) {
                    slotHeight = seg.yStart - y;
                    break;
                }
            }

            return new ContentSlot(
                    startIdx, insets.left(), y, availableWidth - insets.left() - insets.right(), slotHeight);
        }
        return new ContentSlot(
                OptI32.NONE,
                containingBlockInsets.left(),
                minYv,
                availableWidth - containingBlockInsets.left() - containingBlockInsets.right(),
                Float.POSITIVE_INFINITY);
    }

    /// Search for a space suitable for laying out a box that establishes an independent
    /// formatting context (whose border box must not overlap floats).
    ///
    /// The box's margins are resolved against the containing block's content edges. When there
    /// are floats beside the box:
    ///
    ///   - The leading margin (in flow direction) positions the border box's leading edge, but
    ///     may be partially or fully "absorbed" by a float on the leading side (the border edge
    ///     is the further-in of the float edge and the margin-inset containing block edge), and
    ///     a negative leading margin does not move the border edge past the float edge. When no
    ///     float intrudes on the leading side the margin applies as usual, and a negative margin
    ///     may move the border edge outside the containing block.
    ///   - The trailing margin is subtracted from the width an auto width resolves to (except
    ///     for any part of it that overlaps a float on the trailing side). When a float intrudes
    ///     on the trailing side, the trailing margin does not affect whether a box of a given
    ///     width fits in the slot: the trailing margin may overflow the containing block edge.
    ///     When no float intrudes on the trailing side the margin applies as usual, and a
    ///     negative margin lets the border box extend outside the containing block.
    ///
    /// When there are no floats beside the box, its (possibly negative) margins apply as usual.
    ///
    /// The box must not overlap floats beside any part of `minHeight` (a lower bound on the
    /// height of its border box) below its top edge: e.g. a box pulled up above a float by a
    /// negative top margin may only stay there if it also fits beside that float.
    public BfcSlot findBfcSlot(
            float minY,
            float minHeight,
            FloatInsets containingBlockInsets,
            FloatInsets margins,
            Direction direction,
            Clear clear,
            @Opt @Unsigned int after) {
        FloatInsets marginInsets = containingBlockInsets.plus(margins);
        float noFloatWidth = availableWidth - marginInsets.left() - marginInsets.right();
        if (!hasActiveFloats(minY)) {
            return new BfcSlot(
                    OptI32.NONE, marginInsets.left(), minY, noFloatWidth, noFloatWidth, Float.POSITIVE_INFINITY);
        }

        // Clearance clears past the bottom of floats on the relevant side, including
        // zero-sized floats which occupy no segment
        float minYv = F32.max(minY, OptF32.unwrapOr(clearedThreshold(clear), Float.NEGATIVE_INFINITY));

        // The min starting segment index
        int atLeast = OptI32.isSome(after) ? after + 1 : 0;
        @Opt
        @Unsigned
        int clearedSeg = clearedSegment(clear);
        int hwm = Integer.max(atLeast, clearedSeg != OptI32.NONE ? clearedSeg + 1 : 0);

        int startIdx = segments.size();
        for (int i = hwm; i < segments.size(); i++) {
            if (segments.get(i).yEnd > minYv) {
                startIdx = i;
                break;
            }
        }
        if (startIdx < segments.size()) {
            Segment segment = segments.get(startIdx);
            float y = F32.max(segment.yStart, minYv);

            // Combine the float insets of every segment beside the box's minimum height, then
            // extend the slot down over any further segments that don't narrow it
            Segment combined = segment.copy();
            int i = startIdx + 1;
            while (i < segments.size() && segments.get(i).yStart < y + minHeight) {
                combined.union(segments.get(i));
                i++;
            }
            float slotHeight = Float.POSITIVE_INFINITY;
            for (; i < segments.size(); i++) {
                Segment seg = segments.get(i);
                if (seg.isNarrowerThan(combined)) {
                    slotHeight = seg.yStart - y;
                    break;
                }
            }
            FloatInsets insets = combined.insets;
            boolean[] hasFloat = combined.hasFloat;

            FloatDirection lead = switch (direction) {
                case LTR -> FloatDirection.LEFT;
                case RTL -> FloatDirection.RIGHT;
            };
            FloatDirection trail = lead.opposite();
            boolean hasLeadFloat = hasFloat[lead.ordinal()];
            boolean hasTrailFloat = hasFloat[trail.ordinal()];
            float fitLead = hasLeadFloat
                    ? F32.max(
                            FloatInsetsFloatDirectionExt.get(insets, lead),
                            FloatInsetsFloatDirectionExt.get(marginInsets, lead))
                    : FloatInsetsFloatDirectionExt.get(marginInsets, lead);
            float fitTrail = hasTrailFloat
                    ? F32.max(
                            FloatInsetsFloatDirectionExt.get(insets, trail),
                            FloatInsetsFloatDirectionExt.get(containingBlockInsets, trail))
                    // A positive trailing margin may overflow the containing block edge (it does
                    // not affect fit), but a negative one widens the space for the border box
                    : F32.min(
                            FloatInsetsFloatDirectionExt.get(marginInsets, trail),
                            FloatInsetsFloatDirectionExt.get(containingBlockInsets, trail));
            float stretchTrail = hasTrailFloat
                    ? F32.max(
                            FloatInsetsFloatDirectionExt.get(insets, trail),
                            FloatInsetsFloatDirectionExt.get(marginInsets, trail))
                    : FloatInsetsFloatDirectionExt.get(marginInsets, trail);
            FloatInsets fitInsets = FloatInsetsFloatDirectionExt.set(
                    FloatInsetsFloatDirectionExt.set(FloatInsets.ZERO, lead, fitLead), trail, fitTrail);
            FloatInsets stretchInsets = FloatInsetsFloatDirectionExt.set(
                    FloatInsetsFloatDirectionExt.set(FloatInsets.ZERO, lead, fitLead), trail, stretchTrail);
            return new BfcSlot(
                    startIdx,
                    fitInsets.left(),
                    y,
                    availableWidth - fitInsets.left() - fitInsets.right(),
                    availableWidth - stretchInsets.left() - stretchInsets.right(),
                    slotHeight);
        }
        // Below all floats
        float yBelow = F32.max(segments.getLast().yEnd, minYv);
        return new BfcSlot(
                OptI32.NONE, marginInsets.left(), yBelow, noFloatWidth, noFloatWidth, Float.POSITIVE_INFINITY);
    }

    /// `Clone` — deep-copies the segment list (each `Segment` cloned) (GAP-005)
    public FloatContext copy() {
        FloatContext copy = new FloatContext();
        copy.availableWidth = availableWidth;
        copy.hasFloats = hasFloats;
        copy.leftFloats.addAll(leftFloats);
        copy.rightFloats.addAll(rightFloats);
        for (Segment segment : segments) {
            copy.segments.add(segment.copy());
        }
        System.arraycopy(lastPlacedFloatStarts, 0, copy.lastPlacedFloatStarts, 0, 2);
        System.arraycopy(lastPlacedFloatEnds, 0, copy.lastPlacedFloatEnds, 0, 2);
        System.arraycopy(clearBottoms, 0, copy.clearBottoms, 0, 2);
        copy.floatCeiling = floatCeiling;
        return copy;
    }
}
