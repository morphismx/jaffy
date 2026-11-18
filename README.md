<p align="center">
  <img src="assets/logo.svg" alt="jaffy" width="420">
</p>

<p align="center">
  <b>jaffy</b> — a byte-level Java port of <a href="https://github.com/DioxusLabs/taffy">taffy</a>, the Rust UI layout engine.
</p>

---

> **Upstream baseline**: [taffy](https://github.com/DioxusLabs/taffy)
> `v0.15.0-50-g1abd8eea`: `0.15.0` plus 50 commits, commit `1abd8eea`.

**jaffy** computes layout trees for **flexbox**, **grid**, **block**, and **float**
containers, and supports incremental relayout with upstream's exact layout-cache
semantics. **Edge cases**, **panic paths**, **float bit patterns**, and **iteration
order behave** like the Rust original.

## Quick start

```java
TaffyTree<Void> tree = new TaffyTree<>();

LengthPercentage lp = LengthPercentage.length(1.0f);
long child = tree.newLeaf(Style.DEFAULT.withSize(
        new Size<>(Dimension.length(10.0f), Dimension.length(10.0f))));
long root = tree.newWithChildren(
        Style.DEFAULT.withDisplay(Display.FLEX)
                .withPadding(new Rect<>(lp, lp, lp, lp)),
        new long[] {child});

tree.computeLayout(root, new Size<>(
        new AvailableSpace.Definite(2000.0f), AvailableSpace.MAX_CONTENT));

Layout layout = tree.getFinalLayout(root); // position, size, baselines
```

Incremental relayout is automatic: `setStyle` and `markDirty` invalidate exactly
the cache entries upstream invalidates, and untouched subtrees are served from
the cache.

## Modules

| Module       | Contents                                                                                                                                                 |
|:-------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `jaffy`      | Layout engine: `TaffyTree`, `Style`, geometry and style types, layout algorithms, layout cache                                                           |
| `jaffy-gson` | `serde` support: `JaffyGson.gson()` returns a Gson whose output matches taffy's wire format byte-for-byte, differential-tested against real `serde_json` |

## Requirements

| Requirement        | Scope                       | Notes                                                                                    |
|:-------------------|:----------------------------|:-----------------------------------------------------------------------------------------|
| **JDK 25**         | toolchain                   | Selected through Gradle's Java toolchain                                                 |
| **FastUtil** 7.1.0 | `compileOnlyApi`            | `it.unimi.dsi:fastutil` — shipped by Minecraft; add it yourself otherwise                |
| **Just**           | `api`                       | `net.morphismmc:just` — resolved from `mavenLocal`, so publish the sibling project first |
| **JSpecify** 1.0.0 | `api`                       | `org.jspecify:jspecify` — nullness annotations; consumers must carry it                  |
| **Gson** 2.8.0     | `jaffy-gson`, `compileOnly` | Pinned to the Minecraft 1.12.2 baseline                                                  |

## Build

```sh
./gradlew build        # compile, unit tests, staticCheck
./gradlew jmh          # benchmarks against dev.vfyjxf:taffy
```

`staticCheck` keeps the port honest: it bans constructs that diverge from Rust
semantics (`Math.max`/`min`, platform float formatting, …) and verifies that
every `JAFFY(GAP-xxx)` marker resolves to a known gap.

`tools/serde-golden` regenerates the golden JSON corpus behind `jaffy-gson`'s
byte-exact differential test. The project version is derived from the
submodule's `git describe` (`v0.15.0-50-g1abd8eea` → `0.15.0.50`).

## Performance

Figures are from the development machine; `./gradlew jmh` reproduces the
comparison with [**taffy-java**](https://github.com/vfyjxf/taffy-java).

| Workload             | vs taffy-java | vs native taffy                        |
|:---------------------|:--------------|:---------------------------------------|
| Cold layout          | 10–50% faster | 3–17× faster on allocation-heavy trees |
| Incremental relayout | —             | ~20–30% of native time                 |

The incremental gap is the documented cost of reproducing upstream's cache
semantics exactly under Java's object model.

## License

MIT — see [`LICENSE`](LICENSE). Upstream attributions are listed in
[`NOTICE`](NOTICE); taffy itself is dual-licensed MIT/Apache-2.0. `upstream/`
is a read-only submodule and is never modified.
