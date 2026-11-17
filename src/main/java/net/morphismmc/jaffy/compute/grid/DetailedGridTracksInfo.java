// JAFFY: port of upstream src/compute/grid/mod.rs —
// `DetailedGridTracksInfo<S: CheapCloneStr>` (specialised to String, GAP-017).
//
// `impl core::fmt::Write` -> StringBuilder (infallible; upstream's
// `.expect("writing to a String cannot fail")` drops).
// `Option<f32>` -> @Opt float; `Range<usize>` -> TrackCounts.TrackRange;
// `Vec<T>` -> List<T>; `partition_point` -> binary search.
// `Clone` -> copy(); `PartialEq` -> equals (f32 fields use IEEE `==` to match
// Rust PartialEq); `Debug` -> toString.
package net.morphismmc.jaffy.compute.grid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.morphismmc.jaffy.compute.grid.types.TrackCounts;
import net.morphismmc.jaffy.compute.grid.types.TrackCounts.TrackRange;
import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrack;
import net.morphismmc.jaffy.compute.grid.types.grid_track.GridTrackKind;
import net.morphismmc.jaffy.compute.grid.types.named.AreaLines;
import net.morphismmc.jaffy.compute.grid.types.named.GridLineNames;
import net.morphismmc.jaffy.compute.grid.types.named.GridLineNamesIter;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.style.grid.GridAreaAxis;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.LineNonNamedGridPlacementExt;
import net.morphismmc.jaffy.style.grid.LineOriginZeroGridPlacementExt;
import net.morphismmc.just.fmt.RustFmt;
import net.morphismmc.just.math.F32;
import net.morphismmc.just.math.Opt;
import net.morphismmc.just.math.OptF32;
import net.morphismmc.just.math.OptI32;
import net.morphismmc.just.math.Unsigned;

import org.jspecify.annotations.Nullable;

/// Information from the computation of grids tracks
public final class DetailedGridTracksInfo {

    /// Number of leading implicit grid tracks
    public int negativeImplicitTracks;
    /// Number of explicit grid tracks
    public int explicitTracks;
    /// Number of trailing implicit grid tracks
    public int positiveImplicitTracks;

    /// The start and end position of each track relative to the grid container's border box.
    /// These positions account for the container's border and padding, the `gap` property,
    /// content alignment (`align-content`/`justify-content`), and collapsed tracks.
    public List<LineF32> positions;

    /// Sorted, disjoint ranges of collapsed `auto-fit` tracks (indices into `positions`), whose
    /// adjacent gutters overlap when resolving absolute grid-area edges. Empty (and never
    /// allocated) unless a track collapsed.
    public List<TrackRange> collapsedTracks;

    /// The position of the axis' single grid line relative to the grid container's border box
    /// when the axis has no tracks (an empty grid still contains one grid line in each axis,
    /// positioned by content alignment). `None` when the axis has tracks.
    public @Opt float emptyAxisLine;

    /// The names of each *explicit* grid line. Stored line `i` (0-indexed) bounds the start of
    /// explicit track `i`; use {@link DetailedGridTracksInfo#namesForLine} or
    /// {@link DetailedGridTracksInfo#iterLineNames} for indices relative to the full grid
    /// (including implicit tracks). Empty if the grid has no named lines.
    public GridLineNames lineNames;

    private DetailedGridTracksInfo() {}

    /// `Clone` — GAP-005 deep copy of the list fields (Rust `Vec::clone`).
    public DetailedGridTracksInfo copy() {
        DetailedGridTracksInfo copy = new DetailedGridTracksInfo();
        copy.negativeImplicitTracks = negativeImplicitTracks;
        copy.explicitTracks = explicitTracks;
        copy.positiveImplicitTracks = positiveImplicitTracks;
        copy.positions = new ArrayList<>(positions);
        copy.collapsedTracks = new ArrayList<>(collapsedTracks);
        copy.emptyAxisLine = emptyAxisLine;
        copy.lineNames = lineNames.copy();
        return copy;
    }

    /// `(Vec<Line<f32>>, Vec<Range<usize>>)` return of `positions_from_grid_track_layout`
    private record PositionsAndCollapsed(List<LineF32> positions, List<TrackRange> collapsed) {}

    /// Get the start and end position of each track relative to the grid container's border box,
    /// along with the index ranges of any runs of collapsed `auto-fit` tracks
    private static PositionsAndCollapsed positionsFromGridTrackLayout(List<GridTrack> gridTracks) {
        List<LineF32> positions = new ArrayList<>(gridTracks.size() / 2);
        List<TrackRange> collapsedTracks = new ArrayList<>();
        for (GridTrack track : gridTracks) {
            if (track.kind != GridTrackKind.TRACK) {
                continue;
            }
            if (track.isCollapsed) {
                int index = positions.size();
                if (!collapsedTracks.isEmpty()
                        && collapsedTracks.get(collapsedTracks.size() - 1).end() == index) {
                    TrackRange last = collapsedTracks.get(collapsedTracks.size() - 1);
                    collapsedTracks.set(collapsedTracks.size() - 1, new TrackRange(last.start(), index + 1));
                } else {
                    collapsedTracks.add(new TrackRange(index, index + 1));
                }
            }
            positions.add(new LineF32(track.offset, track.offset + track.baseSize));
        }
        return new PositionsAndCollapsed(positions, collapsedTracks);
    }

    /// Construct DetailedGridTracksInfo from TrackCounts and GridTracks
    /// (`pub(super)`-module fn -> package-private factory)
    static DetailedGridTracksInfo fromGridTracksAndTrackCount(
            TrackCounts trackCount, List<GridTrack> gridTracks, GridLineNames lineNames) {
        PositionsAndCollapsed pc = positionsFromGridTrackLayout(gridTracks);
        // An axis with no tracks consists of a single gutter whose offset is where the axis'
        // single grid line was positioned by content alignment
        @Opt
        float emptyAxisLine =
                pc.positions().isEmpty() && !gridTracks.isEmpty() ? gridTracks.get(0).offset : OptF32.NONE;
        DetailedGridTracksInfo info = new DetailedGridTracksInfo();
        info.negativeImplicitTracks = trackCount.negativeImplicit();
        info.explicitTracks = trackCount.explicit();
        info.positiveImplicitTracks = trackCount.positiveImplicit();
        info.positions = pc.positions();
        info.collapsedTracks = pc.collapsed();
        info.emptyAxisLine = emptyAxisLine;
        info.lineNames = lineNames;
        return info;
    }

    /// The names of the grid line with the passed 0-indexed line index, where line `i` bounds
    /// the start of track `i` of the full grid (including implicit tracks).
    /// Returns an empty slice if the line has no names or the index is out of range.
    public List<String> namesForLine(int lineIndex) {
        int storedIndex = lineIndex - negativeImplicitTracks; // usize checked_sub -> negative check
        return storedIndex >= 0 ? lineNames.line(storedIndex) : Collections.emptyList();
    }

    /// Iterate over the name group (`&[S]`) of each grid line of the full grid (including
    /// implicit tracks) in line order, yielding empty groups for unnamed (implicit) lines.
    /// Yields nothing if the grid has no named lines.
    public GridLineNamesIter iterLineNames() {
        if (lineNames.isEmpty()) {
            return lineNames.iter();
        }
        int totalLineCount = positions.size() + 1;
        int leadingEmpty = negativeImplicitTracks;
        int trailingEmpty = Integer.max(0, totalLineCount - leadingEmpty - lineNames.lineCount());
        return lineNames.iterPadded(leadingEmpty, trailingEmpty);
    }

    /// Write the used track sizes and line names of this axis to the passed writer in the
    /// resolved value format of the `grid-template-rows`/`grid-template-columns` properties
    /// (see <https://www.w3.org/TR/css-grid-1/#resolved-track-list>)
    /// Track sizes are divided by `zoom` (the container's effective CSS `zoom`, or `1.0`) to give unzoomed px.
    /// (`impl fmt::Write` -> StringBuilder; upstream's fmt::Result drops as infallible)
    public void writeTrackList(StringBuilder out, float zoom) {
        if (positions.isEmpty()) {
            out.append("none");
            return;
        }

        boolean needsSpace = false;
        for (int trackIndex = 0; trackIndex < positions.size(); trackIndex++) {
            LineF32 position = positions.get(trackIndex);
            List<String> names = namesForLine(trackIndex);
            if (!names.isEmpty()) {
                if (needsSpace) {
                    out.append(' ');
                }
                writeLineNames(out, names);
                needsSpace = true;
            }
            if (needsSpace) {
                out.append(' ');
            }
            out.append(RustFmt.display((position.end() - position.start()) / zoom))
                    .append("px");
            needsSpace = true;
        }
        List<String> trailingNames = namesForLine(positions.size());
        if (!trailingNames.isEmpty()) {
            out.append(' ');
            writeLineNames(out, trailingNames);
        }
    }

    /// Write a bracketed line name group (e.g. `[foo bar]`)
    private static void writeLineNames(StringBuilder out, List<String> names) {
        out.append('[');
        for (int i = 0; i < names.size(); i++) {
            if (i != 0) {
                out.append(' ');
            }
            out.append(names.get(i));
        }
        out.append(']');
    }

    /// Serialize the used track sizes and line names of this axis in the resolved value format of
    /// the `grid-template-rows`/`grid-template-columns` properties
    /// (see <https://www.w3.org/TR/css-grid-1/#resolved-track-list>)
    /// Track sizes are divided by `zoom` (the container's effective CSS `zoom`, or `1.0`) to give unzoomed px.
    public String toTrackListString(float zoom) {
        StringBuilder out = new StringBuilder();
        writeTrackList(out, zoom);
        return out.toString();
    }

    /// Resolve an absolute placement in this axis to physical start and end coordinates
    /// (private upstream; package-private for tests)
    LineF32 resolveAbsoluteGridAxis(
            Line<GridPlacement> placement,
            Map<String, AreaLines> areas,
            GridAreaAxis axis,
            float paddingStart,
            float paddingEnd,
            boolean isReversed) {
        TrackCounts trackCounts = new TrackCounts(negativeImplicitTracks, explicitTracks, positiveImplicitTracks);
        int minLine = -trackCounts.negativeImplicit();
        int maxLine = trackCounts.explicit() + trackCounts.positiveImplicit();
        // Resolve the placement to a pair of (sorted) lines first, and only then treat lines
        // that fall outside the grid as `auto`. A placement whose lines end up swapped (e.g.
        // `grid-column: 3 / 1`, or a non-existent named line which resolves past the end of the
        // explicit grid) is normalized before the out-of-grid check, so e.g. `foo / 3` on a
        // 2-column grid resolves to the area between line 3 and the end padding edge.
        Line<@Nullable OriginZeroLine> resolved = LineOriginZeroGridPlacementExt.resolveAbsolutelyPositionedGridTracks(
                LineNonNamedGridPlacementExt.intoOriginZero(
                        lineNames.resolveLineNames(placement, areas, axis, explicitTracks), explicitTracks));
        @Nullable OriginZeroLine startLine = resolved.start();
        if (startLine != null && (startLine.value() < minLine || startLine.value() > maxLine)) {
            startLine = null;
        }
        @Nullable OriginZeroLine endLine = resolved.end();
        if (endLine != null && (endLine.value() < minLine || endLine.value() > maxLine)) {
            endLine = null;
        }
        // and_then(try_into_track_vec_index).map(index / 2)
        @Opt
        @Unsigned
        int startIdx = trackLineIndex(startLine, trackCounts);
        @Opt
        @Unsigned
        int endIdx = trackLineIndex(endLine, trackCounts);

        float startPosition;
        if (startIdx == OptI32.NONE) {
            startPosition = isReversed ? paddingEnd : paddingStart;
        } else {
            @Opt float pos;
            if (startIdx < positions.size()) {
                pos = absoluteTrackEdge(startIdx, true, isReversed);
            } else if (!positions.isEmpty()) {
                LineF32 track = positions.get(positions.size() - 1);
                pos = isReversed ? track.start() : track.end();
            } else {
                pos = emptyAxisLine;
            }
            startPosition = OptF32.isSome(pos) ? pos : (isReversed ? paddingEnd : paddingStart);
        }
        float endPosition;
        if (endIdx == OptI32.NONE) {
            endPosition = isReversed ? paddingStart : paddingEnd;
        } else {
            @Opt float pos = OptF32.NONE;
            int line = endIdx - 1; // usize checked_sub: 0 -> None branch
            if (endIdx > 0 && line < positions.size()) {
                pos = absoluteTrackEdge(line, false, isReversed);
            } else if (!positions.isEmpty()) {
                LineF32 track = positions.get(0);
                pos = isReversed ? track.end() : track.start();
            } else {
                pos = emptyAxisLine;
            }
            endPosition = OptF32.isSome(pos) ? pos : (isReversed ? paddingStart : paddingEnd);
        }

        // An area whose lines all fall within a collapsed run is zero-sized and anchored at its
        // logical start edge (as in Chrome; Gecko anchors it before the shared gutter instead).
        if (isReversed) {
            return new LineF32(F32.min(startPosition, endPosition), startPosition);
        }
        return new LineF32(startPosition, F32.max(startPosition, endPosition));
    }

    /// `.and_then(|line| line.try_into_track_vec_index(counts).map(|index| index / 2))`
    /// (-1 from `tryIntoTrackVecIndex` encodes None; `Option<usize>` -> @Opt @Unsigned int)
    private static @Opt @Unsigned int trackLineIndex(@Nullable OriginZeroLine line, TrackCounts counts) {
        if (line == null) {
            return OptI32.NONE;
        }
        int idx = line.tryIntoTrackVecIndex(counts);
        return idx >= 0 ? idx / 2 : OptI32.NONE;
    }

    /// Resolve a track edge, excluding gutters that overlap across a collapsed track run.
    private float absoluteTrackEdge(int index, boolean isStart, boolean isReversed) {
        // partition_point(|range| range.end <= index)
        int lo = 0;
        int hi = collapsedTracks.size();
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (collapsedTracks.get(mid).end() <= index) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        @Nullable TrackRange range = null;
        if (lo < collapsedTracks.size()) {
            TrackRange candidate = collapsedTracks.get(lo);
            if (candidate.start() <= index && index < candidate.end()) {
                range = candidate;
            }
        }
        LineF32 track;
        if (range != null) {
            int adjacent = isStart ? range.end() : range.start() - 1; // usize checked_sub on start
            track = adjacent >= 0 && adjacent < positions.size() ? positions.get(adjacent) : positions.get(index);
        } else {
            track = positions.get(index);
        }
        // Collapsed gutters overlap: a start edge follows the shared gutter, while an end
        // edge precedes it.
        return isStart != isReversed ? track.start() : track.end();
    }

    /// `PartialEq` — f32 components compare with IEEE `==` (Rust PartialEq on
    /// Option<f32>: NaN != NaN, -0.0 == 0.0).
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DetailedGridTracksInfo other)) {
            return false;
        }
        return negativeImplicitTracks == other.negativeImplicitTracks
                && explicitTracks == other.explicitTracks
                && positiveImplicitTracks == other.positiveImplicitTracks
                && positions.equals(other.positions)
                && collapsedTracks.equals(other.collapsedTracks)
                && emptyAxisLine == other.emptyAxisLine
                && lineNames.equals(other.lineNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                negativeImplicitTracks,
                explicitTracks,
                positiveImplicitTracks,
                positions,
                collapsedTracks,
                emptyAxisLine,
                lineNames);
    }

    /// `Debug` — approximate Rust field format (`positions`/`collapsed_tracks` abbreviated
    /// the same way Rust Debug prints the list contents).
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("DetailedGridTracksInfo { ");
        out.append("negative_implicit_tracks: ").append(negativeImplicitTracks);
        out.append(", explicit_tracks: ").append(explicitTracks);
        out.append(", positive_implicit_tracks: ").append(positiveImplicitTracks);
        out.append(", positions: [");
        for (int i = 0; i < positions.size(); i++) {
            if (i != 0) {
                out.append(", ");
            }
            LineF32 p = positions.get(i);
            out.append("Line { start: ")
                    .append(RustFmt.debug(p.start()))
                    .append(", end: ")
                    .append(RustFmt.debug(p.end()))
                    .append(" }");
        }
        out.append("], collapsed_tracks: [");
        for (int i = 0; i < collapsedTracks.size(); i++) {
            if (i != 0) {
                out.append(", ");
            }
            TrackRange r = collapsedTracks.get(i);
            out.append(r.start()).append("..").append(r.end());
        }
        out.append("], empty_axis_line: ")
                .append(OptF32.isSome(emptyAxisLine) ? "Some(" + RustFmt.debug(emptyAxisLine) + ")" : "None");
        out.append(", line_names: ").append(lineNames);
        return out.append(" }").toString();
    }
}
