// JAFFY: upstream=benches/benches/mixed.rs

package net.morphismmc.jaffy.bench.mixed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.morphismmc.jaffy.bench.JaffyStyles;
import net.morphismmc.jaffy.bench.Rng;
import net.morphismmc.jaffy.bench.VfyjxfStyles;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.tree.TaffyTree;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;
import net.morphismmc.just.math.OptF32;

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

/// Port of upstream `benches/benches/mixed.rs` — a depth × width tree whose
/// containers alternate grid/flex styles and whose leaves carry a measured
/// text context (`LOREM_IPSUM` at font_size 14). Upstream measures via parley;
/// the Java port substitutes a constant-factor approximation ({@link TextCtx})
/// exercising the same measure-callback path in both libraries.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class MixedBench {

    /// Constant approximation of upstream `ParleyTextContext` — LOREM_IPSUM
    /// (~445 chars) at font_size 14 ≈ 3200px total content width; ~50px
    /// min-content (longest word); ~17px line height (14 × 1.2).
    record TextCtx(float minW, float maxW, float lineH) {
        static final TextCtx LOREM = new TextCtx(50.0f, 3200.0f, 17.0f);

        /// `ParleyTextContext::measure` — clamp available width, then height
        /// from re-broken lines.
        SizeF32 measure(float knownW, float knownH, AvailableSpace aw, AvailableSpace ah) {
            float width = OptF32.isSome(knownW)
                    ? knownW
                    : switch (aw) {
                        case AvailableSpace.MinContent ignored -> minW;
                        case AvailableSpace.MaxContent ignored -> maxW;
                        case AvailableSpace.Definite d -> Math.min(d.value(), maxW);
                    };
            // `break_all_lines(width).height()`: line count × line height
            float height = OptF32.isSome(knownH) ? knownH : (float) Math.ceil(maxW / width) * lineH;
            return new SizeF32(width, height);
        }
    }

    /// `measure_function` — both known → return them; no context → ZERO;
    /// else `context.measure(known, avail)`.
    static LayoutOutput jaffyMeasure(
            net.morphismmc.jaffy.tree.layout.LayoutInput input,
            long nodeId,
            TextCtx ctx,
            net.morphismmc.jaffy.style.Style style) {
        float kw = input.knownDimensions().width();
        float kh = input.knownDimensions().height();
        if (OptF32.isSome(kw) && OptF32.isSome(kh)) {
            return LayoutOutput.fromOuterSize(new SizeF32(kw, kh));
        }
        if (ctx == null) {
            return LayoutOutput.fromOuterSize(SizeF32.ZERO);
        }
        return LayoutOutput.fromOuterSize(ctx.measure(
                kw, kh, input.availableSpace().width(), input.availableSpace().height()));
    }

    static dev.vfyjxf.taffy.geometry.FloatSize vfyjxfMeasure(
            dev.vfyjxf.taffy.geometry.FloatSize known,
            dev.vfyjxf.taffy.geometry.TaffySize<dev.vfyjxf.taffy.style.AvailableSpace> avail,
            TextCtx ctx) {
        if (known.width == known.width && known.height == known.height) {
            return known;
        }
        if (ctx == null) {
            return new dev.vfyjxf.taffy.geometry.FloatSize(0.0f, 0.0f);
        }
        float width = known.width == known.width
                ? known.width
                : switch (avail.width) {
                    case dev.vfyjxf.taffy.style.AvailableSpace s when s.isMinContent() -> ctx.minW;
                    case dev.vfyjxf.taffy.style.AvailableSpace s
                    when s.isDefinite() -> Math.min(s.getValue(), ctx.maxW);
                    default -> ctx.maxW;
                };
        float height = known.height == known.height ? known.height : (float) Math.ceil(ctx.maxW / width) * ctx.lineH;
        return new dev.vfyjxf.taffy.geometry.FloatSize(width, height);
    }

    /// `build_mixed_tree` — leaves at depth 0 carry a text context; containers
    /// alternate grid/flex (top call is_grid=true) with `width` children.
    private static long jaffyMixed(TaffyTree<TextCtx> tree, Rng rng, int depth, int width, boolean isGrid) {
        if (depth == 0) {
            return tree.newLeafWithContext(net.morphismmc.jaffy.style.Style.DEFAULT, TextCtx.LOREM);
        }
        long[] children = new long[width];
        for (int i = 0; i < width; i++) {
            children[i] = jaffyMixed(tree, rng, depth - 1, width, !isGrid);
        }
        net.morphismmc.jaffy.style.Style style =
                isGrid ? JaffyStyles.gridStyle(rng, (int) Math.ceil(Math.sqrt(width))) : JaffyStyles.flexStyle(rng);
        return tree.newWithChildren(style, children);
    }

    private static dev.vfyjxf.taffy.tree.NodeId vfyjxfMixed(
            dev.vfyjxf.taffy.tree.TaffyTree tree, Rng rng, int depth, int width, boolean isGrid) {
        if (depth == 0) {
            return tree.newLeafWithMeasure(
                    new dev.vfyjxf.taffy.style.TaffyStyle(),
                    (known, avail) -> vfyjxfMeasure(known, avail, TextCtx.LOREM));
        }
        List<dev.vfyjxf.taffy.tree.NodeId> children = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            children.add(vfyjxfMixed(tree, rng, depth - 1, width, !isGrid));
        }
        dev.vfyjxf.taffy.style.TaffyStyle style =
                isGrid ? VfyjxfStyles.gridStyle(rng, (int) Math.ceil(Math.sqrt(width))) : VfyjxfStyles.flexStyle(rng);
        return tree.newWithChildren(style, children);
    }

    private static final Size<AvailableSpace> MAX_CONTENT =
            new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT);

    @State(Scope.Benchmark)
    public static class JaffyMixedState {
        @Param({"2", "4"})
        int depth;

        @Param({"4", "8"})
        int width;

        TaffyTree<TextCtx> tree;
        long root;
        List<Long> nodes;

        @Setup(Level.Trial)
        public void build() {
            Rng rng = new Rng(12345);
            tree = new TaffyTree<>();
            root = jaffyMixed(tree, rng, depth, width, true);
            // `TaffyLayoutTree::new` — BFS collect all nodes for mark_all_dirty
            nodes = new ArrayList<>();
            nodes.add(root);
            for (int next = 0; next < nodes.size(); next++) {
                long node = nodes.get(next);
                int count = tree.childCount(node);
                for (int i = 0; i < count; i++) {
                    nodes.add(tree.getChildId(node, i));
                }
            }
        }

        @Setup(Level.Iteration)
        public void dirty() {
            for (long n : nodes) {
                tree.markDirty(n);
            }
        }
    }

    @State(Scope.Benchmark)
    public static class VfyjxfMixedState {
        @Param({"2", "4"})
        int depth;

        @Param({"4", "8"})
        int width;

        dev.vfyjxf.taffy.tree.TaffyTree tree;
        dev.vfyjxf.taffy.tree.NodeId root;
        List<dev.vfyjxf.taffy.tree.NodeId> nodes;

        @Setup(Level.Trial)
        public void build() {
            Rng rng = new Rng(12345);
            tree = new dev.vfyjxf.taffy.tree.TaffyTree();
            root = vfyjxfMixed(tree, rng, depth, width, true);
            nodes = new ArrayList<>();
            nodes.add(root);
            for (int next = 0; next < nodes.size(); next++) {
                nodes.addAll(tree.getChildren(nodes.get(next)));
            }
        }

        @Setup(Level.Iteration)
        public void dirty() {
            for (dev.vfyjxf.taffy.tree.NodeId n : nodes) {
                tree.markDirty(n);
            }
        }
    }

    @Benchmark
    public void jaffyMixed(JaffyMixedState s, Blackhole bh) {
        s.tree.computeLayoutWithMeasure(s.root, MAX_CONTENT, MixedBench::jaffyMeasure);
        bh.consume(s.tree);
    }

    @Benchmark
    public void vfyjxfMixed(VfyjxfMixedState s, Blackhole bh) {
        s.tree.computeLayout(s.root, dev.vfyjxf.taffy.geometry.TaffySize.maxContent());
        bh.consume(s.tree);
    }
}
