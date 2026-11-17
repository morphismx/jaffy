package net.morphismmc.jaffy.bench.flexbox;

import java.util.concurrent.TimeUnit;

import net.morphismmc.jaffy.bench.BenchTree;
import net.morphismmc.jaffy.bench.GenStyle;
import net.morphismmc.jaffy.bench.JaffyStyles;
import net.morphismmc.jaffy.bench.JaffyTreeBuilder;
import net.morphismmc.jaffy.bench.Rng;
import net.morphismmc.jaffy.bench.VfyjxfStyles;
import net.morphismmc.jaffy.bench.VfyjxfTreeBuilder;

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

/// Port of upstream `benches/benches/flexbox.rs` — all six benchmark groups:
/// `huge_nested`, `wide`, `absolute`, `deep_random`, `deep_auto`,
/// `super_deep`. Each runs both libraries via the `lib` param (the Java
/// counterpart of upstream `benchmark_each_library!`).
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class FlexboxBench {

    static final long STANDARD_RNG_SEED = 12345;

    // -- GenStyle impls (upstream flexbox.rs file-local generators) ----------

    /// `RandomStyleGenerator` — leaf and container both get `size: {random, random}`
    static final class JaffyRandom implements GenStyle<net.morphismmc.jaffy.style.Style> {
        @Override
        public net.morphismmc.jaffy.style.Style leafStyle(Rng rng) {
            return net.morphismmc.jaffy.style.Style.DEFAULT.withSize(
                    new net.morphismmc.jaffy.geometry.Size<>(JaffyStyles.dimension(rng), JaffyStyles.dimension(rng)));
        }

        @Override
        public net.morphismmc.jaffy.style.Style containerStyle(Rng rng) {
            return leafStyle(rng);
        }
    }

    static final class VfyjxfRandom implements GenStyle<dev.vfyjxf.taffy.style.TaffyStyle> {
        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle leafStyle(Rng rng) {
            dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
            s.size = dev.vfyjxf.taffy.geometry.TaffySize.of(VfyjxfStyles.dimension(rng), VfyjxfStyles.dimension(rng));
            return s;
        }

        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle containerStyle(Rng rng) {
            return leafStyle(rng);
        }
    }

    /// `FixedStyleGenerator` — a single fixed style for leaf and container
    static final class JaffyFixed implements GenStyle<net.morphismmc.jaffy.style.Style> {
        private final net.morphismmc.jaffy.style.Style style;

        JaffyFixed(net.morphismmc.jaffy.style.Style style) {
            this.style = style;
        }

        @Override
        public net.morphismmc.jaffy.style.Style leafStyle(Rng rng) {
            return style;
        }

        @Override
        public net.morphismmc.jaffy.style.Style containerStyle(Rng rng) {
            return style;
        }
    }

    static final class VfyjxfFixed implements GenStyle<dev.vfyjxf.taffy.style.TaffyStyle> {
        private final dev.vfyjxf.taffy.style.TaffyStyle style;

        VfyjxfFixed(dev.vfyjxf.taffy.style.TaffyStyle style) {
            this.style = style;
        }

        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle leafStyle(Rng rng) {
            return style;
        }

        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle containerStyle(Rng rng) {
            return style;
        }
    }

    /// `RandomAbsoluteStyleGenerator` — absolute leaves in relative containers
    static final class JaffyAbsolute implements GenStyle<net.morphismmc.jaffy.style.Style> {
        @Override
        public net.morphismmc.jaffy.style.Style leafStyle(Rng rng) {
            return JaffyStyles.absoluteLeaf(rng);
        }

        @Override
        public net.morphismmc.jaffy.style.Style containerStyle(Rng rng) {
            return JaffyStyles.absoluteContainer(rng);
        }
    }

    static final class VfyjxfAbsolute implements GenStyle<dev.vfyjxf.taffy.style.TaffyStyle> {
        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle leafStyle(Rng rng) {
            return VfyjxfStyles.absoluteLeaf(rng);
        }

        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle containerStyle(Rng rng) {
            return VfyjxfStyles.absoluteContainer(rng);
        }
    }

    /// `SuperDeepStyleGen` — Row + grow 1 + margin 10 for both leaf/container
    static final class JaffySuperDeep implements GenStyle<net.morphismmc.jaffy.style.Style> {
        @Override
        public net.morphismmc.jaffy.style.Style leafStyle(Rng rng) {
            return JaffyStyles.SUPER_DEEP;
        }

        @Override
        public net.morphismmc.jaffy.style.Style containerStyle(Rng rng) {
            return JaffyStyles.SUPER_DEEP;
        }
    }

    static final class VfyjxfSuperDeep implements GenStyle<dev.vfyjxf.taffy.style.TaffyStyle> {
        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle leafStyle(Rng rng) {
            return VfyjxfStyles.superDeep();
        }

        @Override
        public dev.vfyjxf.taffy.style.TaffyStyle containerStyle(Rng rng) {
            return VfyjxfStyles.superDeep();
        }
    }

    // -- shared state base ---------------------------------------------------

    /// A built tree held by its builder; `markAllDirty` before each iteration
    /// then `computeLayout(None, None)` — upstream `bench_layout` pattern.
    @State(Scope.Benchmark)
    public abstract static class TreeState {
        @Param({"jaffy", "vfyjxf"})
        String lib;

        BenchTree<?, ?> tree;

        abstract BenchTree<?, ?> build(String lib);

        @Setup(Level.Trial)
        public void setup() {
            tree = build(lib);
        }

        @Setup(Level.Iteration)
        public void dirty() {
            tree.markAllDirty();
        }
    }

    @SuppressWarnings("unchecked")
    static <S, N> BenchTree<S, N> builderFor(String lib, GenStyle<?> gen) {
        return switch (lib) {
            case "jaffy" ->
                (BenchTree<S, N>) new JaffyTreeBuilder(
                        new Rng(STANDARD_RNG_SEED), (GenStyle<net.morphismmc.jaffy.style.Style>) gen);
            default ->
                (BenchTree<S, N>) new VfyjxfTreeBuilder(
                        new Rng(STANDARD_RNG_SEED), (GenStyle<dev.vfyjxf.taffy.style.TaffyStyle>) gen);
        };
    }

    // -- yoga 'huge nested': FixedStyle(size 10, grow 1) + deep_hierarchy(n,10)

    @State(Scope.Benchmark)
    public static class HugeNestedState extends TreeState {
        @Param({"1000", "10000"})
        int nodeCount;

        @Override
        BenchTree<?, ?> build(String lib) {
            BenchTree<?, ?> t = builderFor(
                    lib,
                    "jaffy".equals(lib)
                            ? new JaffyFixed(JaffyStyles.HUGE_NESTED)
                            : new VfyjxfFixed(VfyjxfStyles.hugeNested()));
            t.buildDeepHierarchy(nodeCount, 10);
            t.enableRounding();
            return t;
        }
    }

    @Benchmark
    public void hugeNested(HugeNestedState s) {
        s.tree.computeLayout(null, null);
    }

    // -- Wide tree: RandomStyle + flat_hierarchy -----------------------------

    @State(Scope.Benchmark)
    public static class WideState extends TreeState {
        @Param({"1000", "10000"})
        int nodeCount;

        @Override
        BenchTree<?, ?> build(String lib) {
            BenchTree<?, ?> t = builderFor(lib, "jaffy".equals(lib) ? new JaffyRandom() : new VfyjxfRandom());
            t.buildFlatHierarchy(nodeCount);
            t.enableRounding();
            return t;
        }
    }

    @Benchmark
    public void wide(WideState s) {
        s.tree.computeLayout(null, null);
    }

    // -- Absolutely positioned leaves: RandomAbsoluteStyle + flat_hierarchy --

    @State(Scope.Benchmark)
    public static class AbsoluteState extends TreeState {
        @Param({"1000", "10000"})
        int nodeCount;

        @Override
        BenchTree<?, ?> build(String lib) {
            BenchTree<?, ?> t = builderFor(lib, "jaffy".equals(lib) ? new JaffyAbsolute() : new VfyjxfAbsolute());
            t.buildFlatHierarchy(nodeCount);
            t.enableRounding();
            return t;
        }
    }

    @Benchmark
    public void absolute(AbsoluteState s) {
        s.tree.computeLayout(null, null);
    }

    // -- Deep tree (random size): RandomStyle + deep_hierarchy(n, 2) ---------

    @State(Scope.Benchmark)
    public static class DeepRandomState extends TreeState {
        @Param({"4000", "10000"})
        int nodeCount;

        @Override
        BenchTree<?, ?> build(String lib) {
            BenchTree<?, ?> t = builderFor(lib, "jaffy".equals(lib) ? new JaffyRandom() : new VfyjxfRandom());
            t.buildDeepHierarchy(nodeCount, 2);
            t.enableRounding();
            return t;
        }
    }

    @Benchmark
    public void deepRandom(DeepRandomState s) {
        s.tree.computeLayout(null, null);
    }

    // -- Deep tree (auto size): FixedStyle(grow 1, margin 10) + deep(n, 2) ---

    @State(Scope.Benchmark)
    public static class DeepAutoState extends TreeState {
        @Param({"4000", "10000"})
        int nodeCount;

        @Override
        BenchTree<?, ?> build(String lib) {
            BenchTree<?, ?> t = builderFor(
                    lib,
                    "jaffy".equals(lib)
                            ? new JaffyFixed(JaffyStyles.DEEP_AUTO)
                            : new VfyjxfFixed(VfyjxfStyles.deepAuto()));
            t.buildDeepHierarchy(nodeCount, 2);
            t.enableRounding();
            return t;
        }
    }

    @Benchmark
    public void deepAuto(DeepAutoState s) {
        s.tree.computeLayout(null, null);
    }

    // -- super deep: SuperDeepStyleGen + super_deep_hierarchy(depth, 3) ------

    @State(Scope.Benchmark)
    public static class SuperDeepState extends TreeState {
        @Param({"50", "100"})
        int depth;

        @Override
        BenchTree<?, ?> build(String lib) {
            BenchTree<?, ?> t = builderFor(lib, "jaffy".equals(lib) ? new JaffySuperDeep() : new VfyjxfSuperDeep());
            t.buildSuperDeepHierarchy(depth, 3);
            t.enableRounding();
            return t;
        }
    }

    @Benchmark
    public void superDeep(SuperDeepState s) {
        s.tree.computeLayout(null, null);
    }
}
