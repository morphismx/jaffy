# jaffy

A byte-level Java port of [taffy](https://github.com/DioxusLabs/taffy) —
the Rust UI layout library — targeting **taffy 0.15.0 + 50 commits**
(upstream baseline `1abd8eea`, `git describe`: `v0.15.0-50-g1abd8eea`).

jaffy computes layout trees for **flexbox**, **grid**, and **block**
containers, with incremental re-layout through upstream's exact layout-
cache semantics. The `upstream/` git submodule is the sole source of
truth: observable behavior (including edge cases, panics, float bit
patterns, and iteration order) matches the Rust implementation.

## Modules

| Module | Contents |
|---|---|
| `net.morphismmc.jaffy` | The layout engine: `TaffyTree`, `Style`, geometry/style types, compute algorithms, layout cache |
| `jaffy-gson` | `JaffyGson` — Gson adapters reproducing taffy's `serde` wire format **byte-for-byte** (differential-tested against real `serde_json` output; see `tools/serde-golden`) |

## Quick start

```java
TaffyTree<Void> tree = new TaffyTree<>();

LengthPercentage lp1 = LengthPercentage.length(1.0f);
long child = tree.newLeaf(
        Style.DEFAULT.withSize(new Size<>(Dimension.length(10.0f), Dimension.length(10.0f))));
long root = tree.newWithChildren(
        Style.DEFAULT.withDisplay(Display.FLEX).withPadding(new Rect<>(lp1, lp1, lp1, lp1)),
        new long[] {child});

tree.computeLayout(root, new Size<>(
        new AvailableSpace.Definite(2000.0f), AvailableSpace.MAX_CONTENT));

Layout layout = tree.getFinalLayout(root); // positions, sizes, baselines
```

Incremental layout is automatic: `setStyle`/`markDirty` invalidates
exactly the entries upstream invalidates; unchanged subtrees are served
from the cache.

## Requirements

- **JDK 25** (toolchain-managed — Gradle resolves it automatically)
- `it.unimi.dsi:fastutil:7.1.0` — `compileOnlyApi`: Minecraft ships it at
  runtime; non-MC consumers must add it themselves
- `net.morphismmc:just:1.0-SNAPSHOT` — shared infra (`mavenLocal`; build
  and publish the sibling `just` project first)
- `org.jspecify:jspecify` — nullness annotations

## Build

```sh
./gradlew build        # compile + unit tests + staticCheck
./gradlew jmh          # benchmarks vs vfyjxf-taffy
```

`tools/serde-golden` is a small Rust binary that regenerates the golden
JSON corpus for `jaffy-gson`'s byte-exact differential test.

## Performance (this machine)

Cold layout beats both `vfyjxf-taffy` (+10–50%) and native Rust taffy
(~3–17× on allocation-heavy trees); incremental re-layout runs at
~20–30% of native — the documented cost of upstream's exact cache
semantics under Java's object model.

## License

MIT — see `LICENSE`. Upstream attributions in `NOTICE`; `upstream/` is a
read-only submodule, never modified.
