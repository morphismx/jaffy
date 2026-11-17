// JAFFY: upstream=benches/benches/grid.rs

package net.morphismmc.jaffy.bench.grid;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.morphismmc.jaffy.bench.JaffyLayoutTree;
import net.morphismmc.jaffy.bench.JaffyStyles;
import net.morphismmc.jaffy.bench.Rng;
import net.morphismmc.jaffy.bench.VfyjxfLayoutTree;
import net.morphismmc.jaffy.bench.VfyjxfStyles;
import net.morphismmc.jaffy.geometry.Line;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.grid.GridAutoFlow;
import net.morphismmc.jaffy.style.grid.GridPlacement;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;
import net.morphismmc.jaffy.tree.TaffyTree;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/// Port of upstream `benches/benches/grid.rs` — `grid/wide` (flat
/// track_count² hierarchy), `grid/deep` (nested n×n grids),
/// `grid/superdeep` (1×1 tracks, many levels), and
/// `grid/alternating_spans` (upstream `grid-alternating-spans` feature).
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class GridBench {

    private static final Size<AvailableSpace> DEFINITE_12000 =
            new Size<>(new AvailableSpace.Definite(12000.0f), new AvailableSpace.Definite(12000.0f));
    private static final dev.vfyjxf.taffy.geometry.TaffySize<dev.vfyjxf.taffy.style.AvailableSpace> VFYJXF_12000 =
            dev.vfyjxf.taffy.geometry.TaffySize.of(
                    dev.vfyjxf.taffy.style.AvailableSpace.definite(12000.0f),
                    dev.vfyjxf.taffy.style.AvailableSpace.definite(12000.0f));
    private static final Size<AvailableSpace> MAX_CONTENT =
            new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT);
    private static final Size<Dimension> SIZE_20 = new Size<>(Dimension.length(20.0f), Dimension.length(20.0f));
    private static final MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> AUTO_TRACK =
            new MinMax<>(MinTrackSizingFunction.AUTO, MaxTrackSizingFunction.AUTO);
    private static final MinMax<MinTrackSizingFunction, MaxTrackSizingFunction> LEN1_TRACK =
            new MinMax<>(MinTrackSizingFunction.length(1.0f), MaxTrackSizingFunction.length(1.0f));

    // -- build_grid_flat_hierarchy (upstream grid.rs) -------------------------

    static TaffyTree<Void> jaffyFlatGrid(int trackCount, long rootHolder[]) {
        Rng rng = new Rng(12345);
        TaffyTree<Void> tree = new TaffyTree<>();
        // `build_random_leaf`: `size: length(20)`
        long[] children = new long[trackCount * trackCount];
        for (int i = 0; i < children.length; i++) {
            children[i] = tree.newLeaf(Style.DEFAULT.withSize(SIZE_20));
        }
        rootHolder[0] = tree.newWithChildren(JaffyStyles.gridStyle(rng, trackCount), children);
        return tree;
    }

    static dev.vfyjxf.taffy.tree.TaffyTree vfyjxfFlatGrid(int trackCount, dev.vfyjxf.taffy.tree.NodeId[] rootHolder) {
        Rng rng = new Rng(12345);
        dev.vfyjxf.taffy.tree.TaffyTree tree = new dev.vfyjxf.taffy.tree.TaffyTree();
        List<dev.vfyjxf.taffy.tree.NodeId> children = new ArrayList<>(trackCount * trackCount);
        for (int i = 0; i < trackCount * trackCount; i++) {
            dev.vfyjxf.taffy.style.TaffyStyle ls = new dev.vfyjxf.taffy.style.TaffyStyle();
            ls.size = dev.vfyjxf.taffy.geometry.TaffySize.of(
                    dev.vfyjxf.taffy.style.TaffyDimension.length(20.0f),
                    dev.vfyjxf.taffy.style.TaffyDimension.length(20.0f));
            children.add(tree.newLeaf(ls));
        }
        rootHolder[0] = tree.newWithChildren(VfyjxfStyles.gridStyle(rng, trackCount), children);
        return tree;
    }

    // -- build_taffy_deep_grid_hierarchy (upstream grid.rs) -------------------
    // `build_deep_grid_tree`: levels==1 → tracks² leaves, else tracks² nested
    // n×n grid containers; root = `new_with_children(Style::DEFAULT, &tree)`.

    private static long[] jaffyDeepGrid(TaffyTree<Void> tree, Rng rng, int levels, int tracks, List<Long> sink) {
        int childCount = tracks * tracks;
        long[] out = new long[childCount];
        if (levels == 1) {
            for (int i = 0; i < childCount; i++) {
                out[i] = tree.newLeaf(Style.DEFAULT.withSize(SIZE_20));
                sink.add(out[i]);
            }
            return out;
        }
        for (int i = 0; i < childCount; i++) {
            long[] grand = jaffyDeepGrid(tree, rng, levels - 1, tracks, sink);
            out[i] = tree.newWithChildren(JaffyStyles.gridStyle(rng, tracks), grand);
            sink.add(out[i]);
        }
        return out;
    }

    static TaffyTree<Void> jaffyDeepGridTree(int levels, int tracks, long[] rootHolder) {
        Rng rng = new Rng(12345);
        TaffyTree<Void> tree = new TaffyTree<>();
        List<Long> sink = new ArrayList<>();
        long[] children = jaffyDeepGrid(tree, rng, levels, tracks, sink);
        rootHolder[0] = tree.newWithChildren(Style.DEFAULT, children);
        return tree;
    }

    private static List<dev.vfyjxf.taffy.tree.NodeId> vfyjxfDeepGrid(
            dev.vfyjxf.taffy.tree.TaffyTree tree,
            Rng rng,
            int levels,
            int tracks,
            List<dev.vfyjxf.taffy.tree.NodeId> sink) {
        int childCount = tracks * tracks;
        List<dev.vfyjxf.taffy.tree.NodeId> out = new ArrayList<>(childCount);
        if (levels == 1) {
            for (int i = 0; i < childCount; i++) {
                dev.vfyjxf.taffy.style.TaffyStyle ls = new dev.vfyjxf.taffy.style.TaffyStyle();
                ls.size = dev.vfyjxf.taffy.geometry.TaffySize.of(
                        dev.vfyjxf.taffy.style.TaffyDimension.length(20.0f),
                        dev.vfyjxf.taffy.style.TaffyDimension.length(20.0f));
                dev.vfyjxf.taffy.tree.NodeId n = tree.newLeaf(ls);
                out.add(n);
                sink.add(n);
            }
            return out;
        }
        for (int i = 0; i < childCount; i++) {
            List<dev.vfyjxf.taffy.tree.NodeId> grand = vfyjxfDeepGrid(tree, rng, levels - 1, tracks, sink);
            dev.vfyjxf.taffy.tree.NodeId n = tree.newWithChildren(VfyjxfStyles.gridStyle(rng, tracks), grand);
            out.add(n);
            sink.add(n);
        }
        return out;
    }

    static dev.vfyjxf.taffy.tree.TaffyTree vfyjxfDeepGridTree(
            int levels, int tracks, dev.vfyjxf.taffy.tree.NodeId[] rootHolder) {
        Rng rng = new Rng(12345);
        dev.vfyjxf.taffy.tree.TaffyTree tree = new dev.vfyjxf.taffy.tree.TaffyTree();
        List<dev.vfyjxf.taffy.tree.NodeId> sink = new ArrayList<>();
        List<dev.vfyjxf.taffy.tree.NodeId> children = vfyjxfDeepGrid(tree, rng, levels, tracks, sink);
        rootHolder[0] = tree.newWithChildren(new dev.vfyjxf.taffy.style.TaffyStyle(), children);
        return tree;
    }

    // -- build_grid_alternating_spans (upstream grid.rs, grid-alternating-spans)
    // Items alternate between `line(1)..span(2)` on the flow's locked axis and
    // `line(2)`; container gets `auto_flow`, `auto_rows/cols=length(1)` and a
    // `length(1)` template × item_count on the flow axis.

    private static final GridPlacement LINE1 = GridPlacement.fromLineIndex(1);
    private static final GridPlacement LINE2 = GridPlacement.fromLineIndex(2);
    private static final GridPlacement SPAN2 = GridPlacement.fromSpan(2);
    private static final Line<GridPlacement> LOCKED = new Line<>(LINE1, SPAN2);
    private static final Line<GridPlacement> SECOND = new Line<>(LINE2, LINE2);

    static TaffyTree<Void> jaffyAlternatingSpans(int itemCount, GridAutoFlow flow, long[] rootHolder) {
        TaffyTree<Void> tree = new TaffyTree<>();
        boolean isRow = flow == GridAutoFlow.ROW || flow == GridAutoFlow.ROW_DENSE;
        long[] children = new long[itemCount];
        for (int i = 0; i < itemCount; i++) {
            Line<GridPlacement> lockedAxis = i % 2 == 0 ? LOCKED : SECOND;
            Style style = isRow ? Style.DEFAULT.withGridRow(lockedAxis) : Style.DEFAULT.withGridColumn(lockedAxis);
            children[i] = tree.newLeaf(style);
        }
        Style container = Style.DEFAULT
                .withDisplay(Display.GRID)
                .withGridAutoFlow(flow)
                .withGridAutoColumns(List.of(LEN1_TRACK))
                .withGridAutoRows(List.of(LEN1_TRACK));
        container = isRow
                ? container.withGridTemplateColumns(java.util.Collections.nCopies(
                        itemCount, net.morphismmc.jaffy.style.grid.GridTemplateComponent.fromLength(1.0)))
                : container.withGridTemplateRows(java.util.Collections.nCopies(
                        itemCount, net.morphismmc.jaffy.style.grid.GridTemplateComponent.fromLength(1.0)));
        rootHolder[0] = tree.newWithChildren(container, children);
        return tree;
    }

    static dev.vfyjxf.taffy.tree.TaffyTree vfyjxfAlternatingSpans(
            int itemCount, String flow, dev.vfyjxf.taffy.tree.NodeId[] rootHolder) {
        dev.vfyjxf.taffy.tree.TaffyTree tree = new dev.vfyjxf.taffy.tree.TaffyTree();
        boolean isRow = "Row".equals(flow) || "RowDense".equals(flow);
        dev.vfyjxf.taffy.style.GridAutoFlow vflow = switch (flow) {
            case "RowDense" -> dev.vfyjxf.taffy.style.GridAutoFlow.ROW_DENSE;
            case "Column" -> dev.vfyjxf.taffy.style.GridAutoFlow.COLUMN;
            case "ColumnDense" -> dev.vfyjxf.taffy.style.GridAutoFlow.COLUMN_DENSE;
            default -> dev.vfyjxf.taffy.style.GridAutoFlow.ROW;
        };
        List<dev.vfyjxf.taffy.tree.NodeId> children = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
            dev.vfyjxf.taffy.geometry.TaffyLine<dev.vfyjxf.taffy.style.GridPlacement> locked = i % 2 == 0
                    ? new dev.vfyjxf.taffy.geometry.TaffyLine<>(
                            dev.vfyjxf.taffy.style.GridPlacement.line(1), dev.vfyjxf.taffy.style.GridPlacement.span(2))
                    : new dev.vfyjxf.taffy.geometry.TaffyLine<>(
                            dev.vfyjxf.taffy.style.GridPlacement.line(2), dev.vfyjxf.taffy.style.GridPlacement.line(2));
            if (isRow) {
                s.gridRow = locked;
            } else {
                s.gridColumn = locked;
            }
            children.add(tree.newLeaf(s));
        }
        dev.vfyjxf.taffy.style.TaffyStyle c = new dev.vfyjxf.taffy.style.TaffyStyle();
        c.display = dev.vfyjxf.taffy.style.TaffyDisplay.GRID;
        c.gridAutoFlow = vflow;
        c.gridAutoColumns = new ArrayList<>(List.of(dev.vfyjxf.taffy.style.TrackSizingFunction.fixed(1.0f)));
        c.gridAutoRows = new ArrayList<>(List.of(dev.vfyjxf.taffy.style.TrackSizingFunction.fixed(1.0f)));
        List<dev.vfyjxf.taffy.style.TrackSizingFunction> template = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            template.add(dev.vfyjxf.taffy.style.TrackSizingFunction.fixed(1.0f));
        }
        if (isRow) {
            c.gridTemplateColumns = template;
        } else {
            c.gridTemplateRows = template;
        }
        rootHolder[0] = tree.newWithChildren(c, children);
        return tree;
    }

    // -- states ---------------------------------------------------------------

    /// `grid/wide`: flat track_count × track_count grid, `length(12000)` space.
    @State(Scope.Benchmark)
    public static class WideState {
        @Param({"jaffy", "vfyjxf"})
        String lib;

        @Param({"31", "100"})
        int trackCount;

        JaffyLayoutTree j;
        VfyjxfLayoutTree v;

        @Setup(Level.Trial)
        public void build() {
            if ("jaffy".equals(lib)) {
                long[] r = new long[1];
                j = new JaffyLayoutTree(jaffyFlatGrid(trackCount, r), r[0]);
            } else {
                dev.vfyjxf.taffy.tree.NodeId[] r = new dev.vfyjxf.taffy.tree.NodeId[1];
                v = new VfyjxfLayoutTree(vfyjxfFlatGrid(trackCount, r), r[0]);
            }
        }

        @Setup(Level.Iteration)
        public void dirty() {
            if (j != null) {
                j.markAllDirty();
            } else {
                v.markAllDirty();
            }
        }
    }

    @Benchmark
    public void gridWide(WideState s, Blackhole bh) {
        if (s.j != null) {
            s.j.computeLayout(DEFINITE_12000);
            bh.consume(s.j.tree);
        } else {
            s.v.computeLayout(VFYJXF_12000);
            bh.consume(s.v.tree);
        }
    }

    /// `grid/deep`: nested n×n grids — `(tracks x levels)` upstream matrix.
    @State(Scope.Benchmark)
    public static class DeepState {
        @Param({"jaffy", "vfyjxf"})
        String lib;

        @Param({"2x5", "3x4", "2x7"})
        String shape;

        JaffyLayoutTree j;
        VfyjxfLayoutTree v;

        @Setup(Level.Trial)
        public void build() {
            int x = shape.indexOf('x');
            int tracks = Integer.parseInt(shape.substring(0, x));
            int levels = Integer.parseInt(shape.substring(x + 1));
            if ("jaffy".equals(lib)) {
                long[] r = new long[1];
                j = new JaffyLayoutTree(jaffyDeepGridTree(levels, tracks, r), r[0]);
            } else {
                dev.vfyjxf.taffy.tree.NodeId[] r = new dev.vfyjxf.taffy.tree.NodeId[1];
                v = new VfyjxfLayoutTree(vfyjxfDeepGridTree(levels, tracks, r), r[0]);
            }
        }

        @Setup(Level.Iteration)
        public void dirty() {
            if (j != null) {
                j.markAllDirty();
            } else {
                v.markAllDirty();
            }
        }
    }

    @Benchmark
    public void gridDeep(DeepState s, Blackhole bh) {
        if (s.j != null) {
            s.j.computeLayout(DEFINITE_12000);
            bh.consume(s.j.tree);
        } else {
            s.v.computeLayout(VFYJXF_12000);
            bh.consume(s.v.tree);
        }
    }

    /// `grid/superdeep`: 1×1 tracks, {100,1000} levels, `max_content` space.
    @State(Scope.Benchmark)
    public static class SuperdeepState {
        @Param({"jaffy", "vfyjxf"})
        String lib;

        @Param({"100", "1000"})
        int levels;

        JaffyLayoutTree j;
        VfyjxfLayoutTree v;

        @Setup(Level.Trial)
        public void build() {
            if ("jaffy".equals(lib)) {
                long[] r = new long[1];
                j = new JaffyLayoutTree(jaffyDeepGridTree(levels, 1, r), r[0]);
            } else {
                dev.vfyjxf.taffy.tree.NodeId[] r = new dev.vfyjxf.taffy.tree.NodeId[1];
                v = new VfyjxfLayoutTree(vfyjxfDeepGridTree(levels, 1, r), r[0]);
            }
        }

        @Setup(Level.Iteration)
        public void dirty() {
            if (j != null) {
                j.markAllDirty();
            } else {
                v.markAllDirty();
            }
        }
    }

    @Benchmark
    public void gridSuperdeep(SuperdeepState s, Blackhole bh) {
        if (s.j != null) {
            s.j.computeLayout(MAX_CONTENT);
            bh.consume(s.j.tree);
        } else {
            s.v.computeLayout(dev.vfyjxf.taffy.geometry.TaffySize.maxContent());
            bh.consume(s.v.tree);
        }
    }

    /// `grid/alternating_spans`: item_count × flow, `Size::MAX_CONTENT` space.
    @State(Scope.Benchmark)
    public static class SpansState {
        @Param({"jaffy", "vfyjxf"})
        String lib;

        @Param({"100", "1000"})
        int itemCount;

        @Param({"Row", "RowDense", "Column", "ColumnDense"})
        String flow;

        JaffyLayoutTree j;
        VfyjxfLayoutTree v;

        @Setup(Level.Trial)
        public void build() {
            if ("jaffy".equals(lib)) {
                GridAutoFlow f = switch (flow) {
                    case "RowDense" -> GridAutoFlow.ROW_DENSE;
                    case "Column" -> GridAutoFlow.COLUMN;
                    case "ColumnDense" -> GridAutoFlow.COLUMN_DENSE;
                    default -> GridAutoFlow.ROW;
                };
                long[] r = new long[1];
                j = new JaffyLayoutTree(jaffyAlternatingSpans(itemCount, f, r), r[0]);
            } else {
                dev.vfyjxf.taffy.tree.NodeId[] r = new dev.vfyjxf.taffy.tree.NodeId[1];
                v = new VfyjxfLayoutTree(vfyjxfAlternatingSpans(itemCount, flow, r), r[0]);
            }
        }

        @Setup(Level.Iteration)
        public void dirty() {
            if (j != null) {
                j.markAllDirty();
            } else {
                v.markAllDirty();
            }
        }
    }

    @Benchmark
    public void gridSpans(SpansState s, Blackhole bh) {
        if (s.j != null) {
            s.j.computeLayout(MAX_CONTENT);
            bh.consume(s.j.tree);
        } else {
            s.v.computeLayout(dev.vfyjxf.taffy.geometry.TaffySize.maxContent());
            bh.consume(s.v.tree);
        }
    }
}
