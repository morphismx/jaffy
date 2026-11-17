// JAFFY: port of upstream src/tree/layout.rs — `OofCandidates`.
// Lazily-allocated `Option<Box<Vec<OofCandidate>>>` -> nullable `List` field:
// the empty list performs no allocation. Mutable container (upstream `&mut self`
// methods). `IntoIterator for &OofCandidates` -> `Iterable<OofCandidate>`.
package net.morphismmc.jaffy.tree.layout;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.morphismmc.jaffy.geometry.LineF32;
import net.morphismmc.jaffy.geometry.Point;
import net.morphismmc.jaffy.geometry.PointF32;

import org.jspecify.annotations.Nullable;

/// A list of {@link OofCandidate}s.
///
/// This is a lazily-allocated heap vector (an empty list performs no allocation).
public final class OofCandidates implements Iterable<OofCandidate> {

    /// An empty list of candidates — a shared immutable empty instance
    private static final List<OofCandidate> EMPTY = List.of();

    /// Backing storage for the candidates (`null` == empty, unallocated)
    private @Nullable List<OofCandidate> inner;

    private OofCandidates(@Nullable List<OofCandidate> inner) {
        this.inner = inner;
    }

    /// An empty list of candidates (`OofCandidates::NONE`)
    public static OofCandidates none() {
        return new OofCandidates(null);
    }

    /// Create a new empty list of candidates
    public static OofCandidates create() {
        return none();
    }

    /// Whether the list contains no candidates
    public boolean isEmpty() {
        return asSlice().isEmpty();
    }

    /// The candidates as an unmodifiable list (`as_slice`)
    public List<OofCandidate> asSlice() {
        return inner != null ? inner : EMPTY;
    }

    /// Append a candidate to the list (`push`)
    public void push(OofCandidate candidate) {
        if (inner == null) {
            inner = new ArrayList<>();
        }
        inner.add(candidate);
    }

    /// Move all candidates from `other` into `this`, leaving `other` empty (`append`)
    public void append(OofCandidates other) {
        if (other.isEmpty()) {
            return;
        }
        if (inner == null) {
            inner = other.inner;
            other.inner = null;
        } else {
            inner.addAll(other.inner);
            other.inner = null;
        }
    }

    /// Take the candidates out of this list, leaving it empty (`take`)
    public OofCandidates take() {
        OofCandidates taken = new OofCandidates(inner);
        inner = null;
        return taken;
    }

    /// Remove all candidates from the list (`clear`)
    public void clear() {
        inner = null;
    }

    /// Iterate over the candidates (`iter`)
    @Override
    public Iterator<OofCandidate> iterator() {
        return asSlice().iterator();
    }

    /// Translate the static-position areas of every candidate in the list by `offset`.
    ///
    /// This is used to convert areas from being relative to a child node's border box to being
    /// relative to the parent node's border box as candidates bubble up the tree.
    public void translate(PointF32 offset) {
        if (inner == null) {
            return;
        }
        for (int i = 0; i < inner.size(); i++) {
            OofCandidate c = inner.get(i);
            inner.set(
                    i,
                    new OofCandidate(
                            c.node(),
                            c.order(),
                            c.position(),
                            new Point<>(
                                    translated(c.staticPosition().x(), offset.x()),
                                    translated(c.staticPosition().y(), offset.y()))));
        }
    }

    private static AxisStaticPosition translated(AxisStaticPosition pos, float delta) {
        LineF32 area = pos.area();
        return new AxisStaticPosition(new LineF32(area.start() + delta, area.end() + delta), pos.align());
    }

    // ---- Clone/PartialEq (`#[derive(Clone, PartialEq)]`) ----

    /// `Clone` — shallow list copy (candidates are immutable records)
    public OofCandidates copy() {
        return new OofCandidates(inner == null ? null : new ArrayList<>(inner));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof OofCandidates o && asSlice().equals(o.asSlice());
    }

    @Override
    public int hashCode() {
        return asSlice().hashCode();
    }

    @Override
    public String toString() {
        return "OofCandidates(" + asSlice() + ")";
    }
}
