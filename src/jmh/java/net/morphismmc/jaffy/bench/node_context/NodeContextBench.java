package net.morphismmc.jaffy.bench.node_context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.flex.FlexWrap;
import net.morphismmc.jaffy.tree.TaffyTree;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.just.math.OptF32;

/// Port of upstream `benches/benches/node_context.rs` — node context access,
/// once per measured leaf per layout and whenever a context is replaced.
/// A flex row of rows (16 leaves each, `flex_wrap: Wrap`) holding measured
/// leaves whose context is their width; trivial measure keeps lookup cost
/// visible. Scenarios: `create`, `layout`, `replace_and_layout`.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class NodeContextBench {

    private static final Size<AvailableSpace> MAX_CONTENT =
            new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT);
    private static final Style ROW_STYLE = Style.DEFAULT.withFlexWrap(FlexWrap.WRAP);
    private static final Style ROOT_STYLE = Style.DEFAULT.withFlexDirection(FlexDirection.COLUMN);

    /// `build_tree` — leaves with context `index as f32`, chunked rows of 16
    /// (`flex_wrap: Wrap`), root column.
    static Object[] jaffyTree(int leafCount) {
        TaffyTree<Float> tree = new TaffyTree<>();
        List<Long> leaves = new ArrayList<>(leafCount);
        for (int i = 0; i < leafCount; i++) {
            leaves.add(tree.newLeafWithContext(Style.DEFAULT, (float) i));
        }
        List<long[]> rows = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i += 16) {
            int n = Math.min(16, leaves.size() - i);
            long[] chunk = new long[n];
            for (int k = 0; k < n; k++) {
                chunk[k] = leaves.get(i + k);
            }
            rows.add(chunk);
        }
        List<Long> rowIds = new ArrayList<>(rows.size());
        for (long[] chunk : rows) {
            rowIds.add(tree.newWithChildren(ROW_STYLE, chunk));
        }
        long[] rootChildren = new long[rowIds.size()];
        for (int i = 0; i < rootChildren.length; i++) {
            rootChildren[i] = rowIds.get(i);
        }
        long root = tree.newWithChildren(ROOT_STYLE, rootChildren);
        return new Object[] {tree, root, leaves};
    }

    static Object[] vfyjxfTree(int leafCount) {
        dev.vfyjxf.taffy.tree.TaffyTree tree = new dev.vfyjxf.taffy.tree.TaffyTree();
        List<dev.vfyjxf.taffy.tree.NodeId> leaves = new ArrayList<>(leafCount);
        for (int i = 0; i < leafCount; i++) {
            float index = i;
            dev.vfyjxf.taffy.tree.NodeId n = tree.newLeafWithMeasure(
                    new dev.vfyjxf.taffy.style.TaffyStyle(),
                    (known, avail) -> vfyjxfMeasure(known, index));
            leaves.add(n);
        }
        List<dev.vfyjxf.taffy.tree.NodeId> rows = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i += 16) {
            List<dev.vfyjxf.taffy.tree.NodeId> chunk =
                    leaves.subList(i, Math.min(16 + i, leaves.size()));
            dev.vfyjxf.taffy.style.TaffyStyle rs = new dev.vfyjxf.taffy.style.TaffyStyle();
            rs.flexWrap = dev.vfyjxf.taffy.style.FlexWrap.WRAP;
            rows.add(tree.newWithChildren(rs, chunk));
        }
        dev.vfyjxf.taffy.style.TaffyStyle rootStyle = new dev.vfyjxf.taffy.style.TaffyStyle();
        rootStyle.flexDirection = dev.vfyjxf.taffy.style.FlexDirection.COLUMN;
        dev.vfyjxf.taffy.tree.NodeId root = tree.newWithChildren(rootStyle, rows);
        return new Object[] {tree, root, leaves};
    }

    /// `layout`'s measure: `width = known.w ?: (ctx % 100)`, `height = known.h ?: 10`
    static LayoutOutput jaffyMeasure(
            net.morphismmc.jaffy.tree.layout.LayoutInput input,
            long nodeId,
            Float ctx,
            Style style) {
        float kw = input.knownDimensions().width();
        float kh = input.knownDimensions().height();
        float w = OptF32.isSome(kw) ? kw : (ctx != null ? ctx % 100.0f : 0.0f);
        float h = OptF32.isSome(kh) ? kh : 10.0f;
        return LayoutOutput.fromOuterSize(new SizeF32(w, h));
    }

    static dev.vfyjxf.taffy.geometry.FloatSize vfyjxfMeasure(
            dev.vfyjxf.taffy.geometry.FloatSize known, float ctx) {
        float w = known.width == known.width ? known.width : ctx % 100.0f;
        float h = known.height == known.height ? known.height : 10.0f;
        return new dev.vfyjxf.taffy.geometry.FloatSize(w, h);
    }

    // -- states ----------------------------------------------------------------

    /** Prebuilt tree + leaf list for `layout` / `replace_and_layout`. */
    @State(Scope.Benchmark)
    public static class JaffyState {
        @Param({"1000", "10000"})
        int leafCount;

        TaffyTree<Float> tree;
        long root;
        List<Long> leaves;

        @Setup(Level.Trial)
        @SuppressWarnings("unchecked")
        public void build() {
            Object[] parts = jaffyTree(leafCount);
            tree = (TaffyTree<Float>) parts[0];
            root = (long) parts[1];
            leaves = (List<Long>) parts[2];
        }
    }

    @State(Scope.Benchmark)
    public static class VfyjxfState {
        @Param({"1000", "10000"})
        int leafCount;

        dev.vfyjxf.taffy.tree.TaffyTree tree;
        dev.vfyjxf.taffy.tree.NodeId root;
        List<dev.vfyjxf.taffy.tree.NodeId> leaves;

        @Setup(Level.Trial)
        @SuppressWarnings("unchecked")
        public void build() {
            Object[] parts = vfyjxfTree(leafCount);
            tree = (dev.vfyjxf.taffy.tree.TaffyTree) parts[0];
            root = (dev.vfyjxf.taffy.tree.NodeId) parts[1];
            leaves = (List<dev.vfyjxf.taffy.tree.NodeId>) parts[2];
        }
    }

    // -- `create`: tree build timed --------------------------------------------

    @Benchmark
    public void jaffyCreate(JaffyState s, Blackhole bh) {
        bh.consume(jaffyTree(s.leafCount));
    }

    @Benchmark
    public void vfyjxfCreate(VfyjxfState s, Blackhole bh) {
        bh.consume(vfyjxfTree(s.leafCount));
    }

    // -- `layout`: mark leaves dirty, lay out (layout timed) --------------------

    @Benchmark
    public void jaffyLayout(JaffyState s, Blackhole bh) {
        for (long leaf : s.leaves) {
            s.tree.markDirty(leaf);
        }
        s.tree.computeLayoutWithMeasure(
                s.root, MAX_CONTENT, NodeContextBench::jaffyMeasure);
        bh.consume(s.tree);
    }

    @Benchmark
    public void vfyjxfLayout(VfyjxfState s, Blackhole bh) {
        for (dev.vfyjxf.taffy.tree.NodeId leaf : s.leaves) {
            s.tree.markDirty(leaf);
        }
        s.tree.computeLayout(s.root, dev.vfyjxf.taffy.geometry.TaffySize.maxContent());
        bh.consume(s.tree);
    }

    // -- `replace_and_layout`: replace every context, lay out -------------------

    @Benchmark
    public void jaffyReplaceAndLayout(JaffyState s, Blackhole bh) {
        for (int i = 0; i < s.leaves.size(); i++) {
            s.tree.setNodeContext(s.leaves.get(i), (float) i + 1.0f);
        }
        s.tree.computeLayoutWithMeasure(
                s.root, MAX_CONTENT, NodeContextBench::jaffyMeasure);
        bh.consume(s.tree);
    }

    @Benchmark
    public void vfyjxfReplaceAndLayout(VfyjxfState s, Blackhole bh) {
        for (int i = 0; i < s.leaves.size(); i++) {
            float ctx = i + 1.0f;
            s.tree.setMeasureFunc(
                    s.leaves.get(i), (known, avail) -> vfyjxfMeasure(known, ctx));
        }
        s.tree.computeLayout(s.root, dev.vfyjxf.taffy.geometry.TaffySize.maxContent());
        bh.consume(s.tree);
    }
}
