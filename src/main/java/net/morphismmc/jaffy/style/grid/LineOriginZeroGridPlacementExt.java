// JAFFY-UPSTREAM: src/style/grid.rs
// `impl Line<OriginZeroGridPlacement>` (alias of
// `GenericGridPlacement<OriginZeroLine>`; SPECIALIZATIONS Ext pattern).
package net.morphismmc.jaffy.style.grid;

import net.morphismmc.jaffy.compute.grid.types.coordinates.OriginZeroLine;
import net.morphismmc.jaffy.geometry.Line;

import org.jspecify.annotations.Nullable;

public final class LineOriginZeroGridPlacementExt {

    private LineOriginZeroGridPlacementExt() {}

    /// Whether the track position is definite in this axis (or the item will
    /// need auto placement) — at least one of start/end is a track index.
    public static boolean isDefinite(Line<GenericGridPlacement<OriginZeroLine>> self) {
        return self.start() instanceof GenericGridPlacement.Line<OriginZeroLine>
                || self.end() instanceof GenericGridPlacement.Line<OriginZeroLine>;
    }

    /// If at least one of the start and end positions is a track index then
    /// the other end can be resolved into a track index purely based on the
    /// information contained within the placement specification.
    public static Line<OriginZeroLine> resolveDefiniteGridLines(Line<GenericGridPlacement<OriginZeroLine>> self) {
        var start = self.start();
        var end = self.end();
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> l1
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> l2) {
            if (l1.line().equals(l2.line())) {
                return new Line<>(l1.line(), l1.line().add(1));
            }
            return l1.line().value() < l2.line().value()
                    ? new Line<>(l1.line(), l2.line())
                    : new Line<>(l2.line(), l1.line());
        }
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> l
                && end instanceof GenericGridPlacement.Span<OriginZeroLine> s) {
            return new Line<>(l.line(), l.line().add(s.span()));
        }
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> l
                && end instanceof GenericGridPlacement.Auto<OriginZeroLine>) {
            return new Line<>(l.line(), l.line().add(1));
        }
        if (start instanceof GenericGridPlacement.Span<OriginZeroLine> s
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> l) {
            return new Line<>(l.line().sub(s.span()), l.line());
        }
        if (start instanceof GenericGridPlacement.Auto<OriginZeroLine>
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> l) {
            return new Line<>(l.line().sub(1), l.line());
        }
        throw new IllegalStateException(
                "resolve_definite_grid_tracks should only be called on definite grid tracks"); // JAFFY(GAP-010)
    }

    /// For absolutely positioned items:
    ///   - Tracks resolve to definite tracks
    ///   - For Spans: if the other position is a Track, they resolve to a
    ///     definite track relative to the other track; else null (None)
    ///   - Auto resolves to null (None)
    /// A null component means the item's grid area is bounded by the grid
    /// container's border box on that side. (Components are `Option` — GAP-001.)
    public static Line<@Nullable OriginZeroLine> resolveAbsolutelyPositionedGridTracks(
            Line<GenericGridPlacement<OriginZeroLine>> self) {
        var start = self.start();
        var end = self.end();
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> t1
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> t2) {
            if (t1.line().equals(t2.line())) {
                return new Line<>(t1.line(), t1.line().add(1));
            }
            return t1.line().value() < t2.line().value()
                    ? new Line<>(t1.line(), t2.line())
                    : new Line<>(t2.line(), t1.line());
        }
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> t
                && end instanceof GenericGridPlacement.Span<OriginZeroLine> s) {
            return new Line<>(t.line(), t.line().add(s.span()));
        }
        if (start instanceof GenericGridPlacement.Line<OriginZeroLine> t
                && end instanceof GenericGridPlacement.Auto<OriginZeroLine>) {
            return new Line<>(t.line(), null);
        }
        if (start instanceof GenericGridPlacement.Span<OriginZeroLine> s
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> t) {
            return new Line<>(t.line().sub(s.span()), t.line());
        }
        if (start instanceof GenericGridPlacement.Auto<OriginZeroLine>
                && end instanceof GenericGridPlacement.Line<OriginZeroLine> t) {
            return new Line<>(null, t.line());
        }
        return new Line<>(null, null);
    }

    /// If neither of the start and end positions is a track index then the
    /// other end can be resolved into a track index if a definite start
    /// position is supplied externally.
    public static Line<OriginZeroLine> resolveIndefiniteGridTracks(
            Line<GenericGridPlacement<OriginZeroLine>> self, OriginZeroLine start) {
        var s0 = self.start();
        var s1 = self.end();
        if (s0 instanceof GenericGridPlacement.Auto<OriginZeroLine>
                && s1 instanceof GenericGridPlacement.Auto<OriginZeroLine>) {
            return new Line<>(start, start.add(1));
        }
        if (s0 instanceof GenericGridPlacement.Span<OriginZeroLine> s
                && s1 instanceof GenericGridPlacement.Auto<OriginZeroLine>) {
            return new Line<>(start, start.add(s.span()));
        }
        if (s0 instanceof GenericGridPlacement.Auto<OriginZeroLine>
                && s1 instanceof GenericGridPlacement.Span<OriginZeroLine> s) {
            return new Line<>(start, start.add(s.span()));
        }
        if (s0 instanceof GenericGridPlacement.Span<OriginZeroLine> s
                && s1 instanceof GenericGridPlacement.Span<OriginZeroLine>) {
            return new Line<>(start, start.add(s.span()));
        }
        throw new IllegalStateException(
                "resolve_indefinite_grid_tracks should only be called on indefinite grid tracks"); // JAFFY(GAP-010)
    }
}
