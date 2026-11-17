// JAFFY-UPSTREAM: none
// JAFFY-NOTE: no upstream inline tests in alignment.rs — these are behavior
// tests on the observable contracts of align_tracks / align_item_within_area
// / relative_offset (added per porting workflow for untested items).
package net.morphismmc.jaffy.compute.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.LineOptF32;
import net.morphismmc.jaffy.style.Direction;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.alignment.AlignItemsKeyword;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.just.math.OptF32;

import org.junit.jupiter.api.Test;

class AlignmentTest {

    private static List<GridTrack> tracks(float... baseSizes) {
        // tracks[0] is the leading gutter; tracks alternate gutter/track
        ArrayList<GridTrack> tracks = new ArrayList<>();
        tracks.add(GridTrack.gutter(LengthPercentage.ZERO));
        for (float size : baseSizes) {
            GridTrack track = GridTrack.newGridTrack(
                    MinTrackSizingFunction.fromLength(size), MaxTrackSizingFunction.fromLength(size));
            track.baseSize = size;
            tracks.add(track);
            tracks.add(GridTrack.gutter(LengthPercentage.ZERO));
        }
        return tracks;
    }

    private static List<Float> trackOffsets(List<GridTrack> tracks) {
        return tracks.stream().map(t -> t.offset).toList();
    }

    @Test
    void alignTracksStart() {
        // 2 tracks of 20 each in a 100px container, start alignment: no shift.
        // Offsets accumulate through every track incl. gutters: [g, t, g, t, g]
        List<GridTrack> tracks = tracks(20f, 20f);
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.START, null),
                false);
        assertEquals(List.of(7f, 7f, 27f, 27f, 47f), trackOffsets(tracks));
    }

    @Test
    void alignTracksEnd() {
        List<GridTrack> tracks = tracks(20f, 20f);
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.END, null),
                false);
        // free = 60 lands on the first non-collapsed track; later tracks inherit
        // it through total_offset accumulation
        assertEquals(List.of(7f, 67f, 87f, 87f, 107f), trackOffsets(tracks));
    }

    @Test
    void alignTracksCenter() {
        List<GridTrack> tracks = tracks(20f, 20f);
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.CENTER, null),
                false);
        // free/2 = 30 on the first non-collapsed track
        assertEquals(List.of(7f, 37f, 57f, 57f, 77f), trackOffsets(tracks));
    }

    @Test
    void alignTracksSpaceBetween() {
        List<GridTrack> tracks = tracks(20f, 20f);
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.SPACE_BETWEEN, null),
                false);
        // first: 0; subsequent non-collapsed: free/(n-1) = 60
        assertEquals(List.of(7f, 7f, 27f, 87f, 107f), trackOffsets(tracks));
    }

    @Test
    void alignTracksSpaceAround() {
        List<GridTrack> tracks = tracks(20f, 20f);
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.SPACE_AROUND, null),
                false);
        // first: (60/2)/2 = 15; subsequent: 60/2 = 30
        assertEquals(List.of(7f, 22f, 42f, 72f, 92f), trackOffsets(tracks));
    }

    @Test
    void alignTracksCollapsedTracksGetOriginOffsetOnly() {
        List<GridTrack> tracks = tracks(20f, 20f);
        tracks.get(1).collapse();
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.CENTER, null),
                false);
        // One live track: isFirst center offset = (100-40)/2 = 30 at index 3
        assertEquals(List.of(7f, 7f, 27f, 57f, 77f), trackOffsets(tracks));
    }

    @Test
    void alignTracksEmptyGridOffsetAppliedToOrigin() {
        List<GridTrack> tracks = tracks(20f);
        tracks.get(1).collapse();
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.END, null),
                false);
        // No non-collapsed track: END offset (free=80) lands on the gutter origin
        assertEquals(List.of(87f, 87f, 107f), trackOffsets(tracks));
    }

    @Test
    void alignTracksReversed() {
        // axis_is_reversed applies `AlignContentKeyword::reversed` (START -> END)
        // and walks the track list backwards assigning physical offsets RTL
        List<GridTrack> tracks = tracks(10f, 20f);
        Alignment.alignTracks(
                100f,
                new LineF32(5f, 0f),
                new LineF32(2f, 0f),
                tracks,
                new AlignContent(AlignContentKeyword.START, null),
                true);
        assertEquals(List.of(107f, 97f, 97f, 77f, 7f), trackOffsets(tracks));
    }

    // ---- alignItemWithinArea ----

    private static LineOptF32 margin(float start, float end) {
        return new LineOptF32(start, end);
    }

    private static LineOptF32 autoMargin() {
        return LineOptF32.NONE;
    }

    @Test
    void alignItemStart() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.START, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(4f, 6f),
                0f,
                Direction.LTR);
        assertEquals(14f, r.offset()); // area.start 10 + margin.start 4
        assertEquals(new LineF32(4f, 6f), r.margin());
    }

    @Test
    void alignItemEnd() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.END, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(4f, 6f),
                0f,
                Direction.LTR);
        // 10 + (100 - 30 - 6) = 74
        assertEquals(74f, r.offset());
    }

    @Test
    void alignItemCenter() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.CENTER, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(4f, 6f),
                0f,
                Direction.LTR);
        // 10 + (100 - 30 + 4 - 6) / 2 = 10 + 34 = 44
        assertEquals(44f, r.offset());
    }

    @Test
    void alignItemStartRtl() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.START, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(4f, 6f),
                0f,
                Direction.RTL);
        // RTL start: area_size - resolved - margin.end = 100 - 30 - 6 = 64; +10
        assertEquals(74f, r.offset());
    }

    @Test
    void alignItemEndRtl() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.END, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(4f, 6f),
                0f,
                Direction.RTL);
        // RTL end: margin.start = 4; +10
        assertEquals(14f, r.offset());
    }

    @Test
    void alignItemStretchBehavesAsStart() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.STRETCH, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(4f, 6f),
                0f,
                Direction.LTR);
        assertEquals(14f, r.offset());
    }

    @Test
    void autoMarginsSplitFreeSpace() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.START, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                autoMargin(),
                0f,
                Direction.LTR);
        // free = 100 - 30 = 70 -> 35 each side
        assertEquals(new LineF32(35f, 35f), r.margin());
        assertEquals(45f, r.offset());
    }

    @Test
    void singleAutoMarginTakesAllFreeSpace() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.END, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(OptF32.NONE, 6f),
                0f,
                Direction.LTR);
        // start auto -> takes free (100-30-6=64); end = 64 - 30 - 6 -> offset = area.end - size - end
        assertEquals(new LineF32(64f, 6f), r.margin());
        assertEquals(74f, r.offset());
    }

    @Test
    void baselineShimAddedToStartMargin() {
        Alignment.AxisAlignment r = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.START, null),
                30f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(4f, 6f),
                7f,
                Direction.LTR);
        assertEquals(new LineF32(11f, 6f), r.margin());
        assertEquals(21f, r.offset());
    }

    @Test
    void alignItemUnsafeEndFallsBackToStartOnOverflow() {
        // Item larger than the area: safe end -> start; unsafe end -> negative offset
        Alignment.AxisAlignment safe = Alignment.alignItemWithinArea(
                new LineF32(0f, 50f),
                new AlignItems(AlignItemsKeyword.END, net.morphismmc.jaffy.style.alignment.AlignmentSafety.SAFE),
                80f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(0f, 0f),
                0f,
                Direction.LTR);
        assertEquals(0f, safe.offset());
        Alignment.AxisAlignment unsafe = Alignment.alignItemWithinArea(
                new LineF32(0f, 50f),
                new AlignItems(AlignItemsKeyword.END, null),
                80f,
                Position.RELATIVE,
                LineOptF32.NONE,
                margin(0f, 0f),
                0f,
                Direction.LTR);
        assertEquals(-30f, unsafe.offset());
    }

    @Test
    void outOfFlowInsetsOverrideAlignment() {
        Position abs = Position.ABSOLUTE;
        // Both inset edges set -> offset = start + margin.start
        Alignment.AxisAlignment both = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.END, null),
                30f,
                abs,
                new LineOptF32(15f, 25f),
                margin(4f, 6f),
                0f,
                Direction.LTR);
        assertEquals(29f, both.offset()); // 10 + 15 + 4
        // Start only
        Alignment.AxisAlignment startOnly = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.END, null),
                30f,
                abs,
                new LineOptF32(15f, OptF32.NONE),
                margin(4f, 6f),
                0f,
                Direction.LTR);
        assertEquals(29f, startOnly.offset());
        // End only: grid_area_size - end - size - margin.end
        Alignment.AxisAlignment endOnly = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.START, null),
                30f,
                abs,
                new LineOptF32(OptF32.NONE, 25f),
                margin(4f, 6f),
                0f,
                Direction.LTR);
        assertEquals(49f, endOnly.offset()); // 10 + (100 - 25 - 30 - 6)
        // Neither: alignment-based offset applies
        Alignment.AxisAlignment neither = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.END, null),
                30f,
                abs,
                LineOptF32.NONE,
                margin(4f, 6f),
                0f,
                Direction.LTR);
        assertEquals(74f, neither.offset());
    }

    @Test
    void outOfFlowInsetsRtl() {
        Position abs = Position.ABSOLUTE;
        // Both inset edges set in RTL: grid_area_size - end - size - margin.end
        Alignment.AxisAlignment both = Alignment.alignItemWithinArea(
                new LineF32(10f, 110f),
                new AlignItems(AlignItemsKeyword.START, null),
                30f,
                abs,
                new LineOptF32(15f, 25f),
                margin(4f, 6f),
                0f,
                Direction.RTL);
        assertEquals(49f, both.offset()); // 10 + (100 - 25 - 30 - 6)
    }

    @Test
    void unresolvedKeywordsAreUnreachable() {
        for (AlignItemsKeyword kw : new AlignItemsKeyword[] {
            AlignItemsKeyword.NORMAL, AlignItemsKeyword.SELF_START, AlignItemsKeyword.SELF_END
        }) {
            assertThrows(
                    IllegalStateException.class,
                    () -> Alignment.alignItemWithinArea(
                            new LineF32(0f, 100f),
                            new AlignItems(kw, null),
                            30f,
                            Position.RELATIVE,
                            LineOptF32.NONE,
                            margin(0f, 0f),
                            0f,
                            Direction.LTR));
        }
    }

    // ---- relativeOffset ----

    @Test
    void relativeOffsetOnlyAppliesToRelative() {
        LineOptF32 inset = new LineOptF32(5f, 9f);
        assertEquals(0f, Alignment.relativeOffset(Position.ABSOLUTE, inset, Direction.LTR));
        assertEquals(5f, Alignment.relativeOffset(Position.RELATIVE, inset, Direction.LTR));
    }

    @Test
    void relativeOffsetLtrPrefersStart() {
        assertEquals(5f, Alignment.relativeOffset(Position.RELATIVE, new LineOptF32(5f, 9f), Direction.LTR));
        // No start: use -end
        assertEquals(-9f, Alignment.relativeOffset(Position.RELATIVE, new LineOptF32(OptF32.NONE, 9f), Direction.LTR));
        assertEquals(0f, Alignment.relativeOffset(Position.RELATIVE, LineOptF32.NONE, Direction.LTR));
    }

    @Test
    void relativeOffsetRtlPrefersNegatedEnd() {
        assertEquals(-9f, Alignment.relativeOffset(Position.RELATIVE, new LineOptF32(5f, 9f), Direction.RTL));
        // No end: use start
        assertEquals(5f, Alignment.relativeOffset(Position.RELATIVE, new LineOptF32(5f, OptF32.NONE), Direction.RTL));
    }
}
