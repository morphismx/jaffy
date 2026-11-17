// JAFFY: upstream=benches/benches/relayout.rs

package net.morphismmc.jaffy.bench.relayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.morphismmc.jaffy.bench.JaffyStyles;
import net.morphismmc.jaffy.bench.VfyjxfStyles;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
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

/// Port of upstream `benches/benches/relayout.rs` — incremental re-layout
/// dominated by layout cache lookups (rounding disabled; it would visit every
/// node and dominate). `relayout/{shape}` resizes the first leaf 10 <-> 20;
/// `relayout/resize` re-lays out with a different available width while all
/// caches stay valid.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class RelayoutBench {

    /// `build_subtree`/`build_tree` params per upstream shape name.
    static int fanoutOf(String shape) {
        return switch (shape) {
            case "wide" -> 100;
            case "balanced" -> 10;
            default -> 2; // deep
        };
    }

    static int depthOf(String shape) {
        return switch (shape) {
            case "wide" -> 2;
            case "balanced" -> 4;
            default -> 12; // deep
        };
    }

    /// `build_subtree` — auto-sized containers with `fanout` children each and
    /// fixed-size leaves at `depth`; returns the first leaf via `firstLeaf`.
    private static long jaffySubtree(
            TaffyTree<Void> tree, String containers, int fanout, int depth, int maxDepth, long[] firstLeaf) {
        if (depth == maxDepth) {
            long n = tree.newLeaf(JaffyStyles.leafStyle(10.0f));
            if (firstLeaf[0] == 0) {
                firstLeaf[0] = n;
            }
            return n;
        }
        long[] children = new long[fanout];
        for (int i = 0; i < fanout; i++) {
            children[i] = jaffySubtree(tree, containers, fanout, depth + 1, maxDepth, firstLeaf);
        }
        return tree.newWithChildren(JaffyStyles.containerStyle(containers, depth), children);
    }

    private static dev.vfyjxf.taffy.tree.NodeId vfyjxfSubtree(
            dev.vfyjxf.taffy.tree.TaffyTree tree,
            String containers,
            int fanout,
            int depth,
            int maxDepth,
            dev.vfyjxf.taffy.tree.NodeId[] firstLeaf) {
        if (depth == maxDepth) {
            dev.vfyjxf.taffy.tree.NodeId n = tree.newLeaf(VfyjxfStyles.leafStyle(10.0f));
            if (firstLeaf[0] == null) {
                firstLeaf[0] = n;
            }
            return n;
        }
        List<dev.vfyjxf.taffy.tree.NodeId> children = new ArrayList<>(fanout);
        for (int i = 0; i < fanout; i++) {
            children.add(vfyjxfSubtree(tree, containers, fanout, depth + 1, maxDepth, firstLeaf));
        }
        return tree.newWithChildren(VfyjxfStyles.containerStyle(containers, depth), children);
    }

    // -- relayout/{shape} -----------------------------------------------------
    // `build_tree` (relayout.rs): disable_rounding, build subtree, lay out once
    // (Size{2000, max_content}) so caches are warm. Each iteration resizes the
    // first leaf 10 <-> 20 and lays the tree out again; ancestors recompute,
    // siblings hit their caches.

    @State(Scope.Benchmark)
    public static class JaffyRelayoutState {
        @Param({"wide", "balanced", "deep"})
        String shape;

        @Param({"flex", "block", "grid", "mixed"})
        String containers;

        TaffyTree<Void> tree;
        long root;
        long leaf;
        boolean tall;

        @Setup(Level.Trial)
        public void build() {
            tree = new TaffyTree<>();
            tree.disableRounding();
            long[] firstLeaf = {0};
            root = jaffySubtree(tree, containers, fanoutOf(shape), 0, depthOf(shape), firstLeaf);
            leaf = firstLeaf[0];
            tree.computeLayout(root, SPACE);
        }
    }

    @State(Scope.Benchmark)
    public static class VfyjxfRelayoutState {
        @Param({"wide", "balanced", "deep"})
        String shape;

        @Param({"flex", "block", "grid", "mixed"})
        String containers;

        dev.vfyjxf.taffy.tree.TaffyTree tree;
        dev.vfyjxf.taffy.tree.NodeId root;
        dev.vfyjxf.taffy.tree.NodeId leaf;
        boolean tall;

        @Setup(Level.Trial)
        public void build() {
            tree = new dev.vfyjxf.taffy.tree.TaffyTree();
            tree.disableRounding();
            dev.vfyjxf.taffy.tree.NodeId[] firstLeaf = new dev.vfyjxf.taffy.tree.NodeId[1];
            root = vfyjxfSubtree(tree, containers, fanoutOf(shape), 0, depthOf(shape), firstLeaf);
            leaf = firstLeaf[0];
            tree.computeLayout(root, VFYJXF_SPACE);
        }
    }

    private static final Size<AvailableSpace> SPACE =
            new Size<>(new AvailableSpace.Definite(2000.0f), AvailableSpace.MAX_CONTENT);
    private static final dev.vfyjxf.taffy.geometry.TaffySize<dev.vfyjxf.taffy.style.AvailableSpace> VFYJXF_SPACE =
            dev.vfyjxf.taffy.geometry.TaffySize.of(
                    dev.vfyjxf.taffy.style.AvailableSpace.definite(2000.0f),
                    dev.vfyjxf.taffy.style.AvailableSpace.MAX_CONTENT);

    @Benchmark
    public void jaffyRelayout(JaffyRelayoutState s, Blackhole bh) {
        s.tall = !s.tall;
        s.tree.setStyle(s.leaf, JaffyStyles.leafStyle(s.tall ? 20.0f : 10.0f));
        s.tree.computeLayout(s.root, SPACE);
        bh.consume(s.tree);
    }

    @Benchmark
    public void vfyjxfRelayout(VfyjxfRelayoutState s, Blackhole bh) {
        s.tall = !s.tall;
        s.tree.setStyle(s.leaf, VfyjxfStyles.leafStyle(s.tall ? 20.0f : 10.0f));
        s.tree.computeLayout(s.root, VFYJXF_SPACE);
        bh.consume(s.tree);
    }

    // -- relayout/resize -------------------------------------------------------
    // All caches stay valid for a different available space: the root is laid
    // out again and every unchanged input is answered from its cache.
    // `build_tree(containers, 10, 4)` then alternate width 2000 <-> 2500.

    @State(Scope.Benchmark)
    public static class JaffyResizeState {
        @Param({"flex", "block", "grid", "mixed"})
        String containers;

        TaffyTree<Void> tree;
        long root;
        boolean wide;

        @Setup(Level.Trial)
        public void build() {
            tree = new TaffyTree<>();
            tree.disableRounding();
            long[] firstLeaf = {0};
            root = jaffySubtree(tree, containers, 10, 0, 4, firstLeaf);
            tree.computeLayout(root, SPACE);
        }
    }

    @State(Scope.Benchmark)
    public static class VfyjxfResizeState {
        @Param({"flex", "block", "grid", "mixed"})
        String containers;

        dev.vfyjxf.taffy.tree.TaffyTree tree;
        dev.vfyjxf.taffy.tree.NodeId root;
        boolean wide;

        @Setup(Level.Trial)
        public void build() {
            tree = new dev.vfyjxf.taffy.tree.TaffyTree();
            tree.disableRounding();
            dev.vfyjxf.taffy.tree.NodeId[] firstLeaf = new dev.vfyjxf.taffy.tree.NodeId[1];
            root = vfyjxfSubtree(tree, containers, 10, 0, 4, firstLeaf);
            tree.computeLayout(root, VFYJXF_SPACE);
        }
    }

    private static final Size<AvailableSpace> SPACE_2500 =
            new Size<>(new AvailableSpace.Definite(2500.0f), AvailableSpace.MAX_CONTENT);
    private static final dev.vfyjxf.taffy.geometry.TaffySize<dev.vfyjxf.taffy.style.AvailableSpace> VFYJXF_2500 =
            dev.vfyjxf.taffy.geometry.TaffySize.of(
                    dev.vfyjxf.taffy.style.AvailableSpace.definite(2500.0f),
                    dev.vfyjxf.taffy.style.AvailableSpace.MAX_CONTENT);

    @Benchmark
    public void jaffyResize(JaffyResizeState s, Blackhole bh) {
        s.wide = !s.wide;
        s.tree.computeLayout(s.root, s.wide ? SPACE_2500 : SPACE);
        bh.consume(s.tree);
    }

    @Benchmark
    public void vfyjxfResize(VfyjxfResizeState s, Blackhole bh) {
        s.wide = !s.wide;
        s.tree.computeLayout(s.root, s.wide ? VFYJXF_2500 : VFYJXF_SPACE);
        bh.consume(s.tree);
    }
}
