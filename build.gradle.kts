plugins {
    `java-library`
    // JMH benchmarks: `gradlew jmh`. Sources under src/jmh/java.
    id("me.champeau.jmh") version "0.7.4"
}

group = "net.morphismmc"

// The version is derived from `git describe` of the upstream submodule (see docs/porting/DECISIONS.md):
//   v0.15.0-0-g<sha>                     -> 0.15.0
//   v0.15.0-50-g<sha>                    -> 0.15.0.50
//   v0.15.0-50-g<sha> with jaffy.patch=N -> 0.15.0.50.N
val upstreamDescribe = providers.exec {
    commandLine(
        "git", "-C", layout.projectDirectory.dir("upstream").asFile.path,
        "describe", "--tags", "--long", "--match", "v[0-9]*",
    )
}.standardOutput.asText.map { it.trim() }

val jaffyPatch = providers.gradleProperty("jaffy.patch").map { it.toInt() }.orElse(0)

version = upstreamDescribe.zip(jaffyPatch) { describe, patch ->
    val match = Regex("""v(\d+\.\d+\.\d+)-(\d+)-g\p{XDigit}+""").matchEntire(describe)
        ?: error("Cannot derive a version from the upstream submodule's `git describe` output: '$describe'")
    val (base, distance) = match.destructured
    buildString {
        append(base)
        if (distance != "0" || patch > 0) append('.').append(distance)
        if (patch > 0) append('.').append(patch)
    }
}.get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Minecraft ships fastutil at runtime. 7.1.0 is the version bundled with MC 1.12.2, the oldest supported target,
    // so only APIs that exist in 7.1.0 may be used.
    compileOnlyApi("it.unimi.dsi:fastutil:7.1.0")
    testImplementation("it.unimi.dsi:fastutil:7.1.0")

    // Nullness annotations for reference-type `Option<T>` mappings (see docs/porting/GAPS.md).
    // `api` — like fastutil it is needed on the runtime classpath too (unlike fastutil it is not
    // shipped by MC, so consumers must carry it); annotations are harmless if absent at runtime.
    api("org.jspecify:jspecify:1.0.0")

    // Shared infrastructure extracted to the `just` library: F32/Opt/OptF32/Result
    // family, float functional interfaces, and Rust formatting (infra/fmt).
    // `api` because LongObjResult/Result appear in public signatures (TaffyTree).
    // Published via `just`'s `publishToMavenLocal` (mavenLocal above).
    api("net.morphismmc:just:1.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    // jmh-core + jmh-generator-annprocess are injected by the plugin at jmhVersion (1.37).
    "jmhImplementation"("dev.vfyjxf:taffy:1.1.5")
    "jmhImplementation"("it.unimi.dsi:fastutil:7.1.0")
}

jmh {
    // Compares jaffy against the independent dev.vfyjxf:taffy port
    // (upstream baseline see docs/porting/manifest.yml).
    jmhVersion = "1.37"
    // -Pjmh.args=<comma-separated JMH args> — our pass-through convention. It feeds
    // jmhOptions (verbatim passthrough), because --jmhArgs dedupes repeated flags and
    // cannot express multiple -p params (upstream applyCliArgs limitation).
    jmhOptions.addAll(
        providers.gradleProperty("jmh.args").map { it.split(",") },
    )
    // Result file handling goes through the plugin's own -rf/-rff wiring; passing
    // -rf/-rff inside jmh.args produces duplicate JMH options. Override per run:
    //   -Pjmh.resultFormat=csv -Pjmh.resultsFile=jmh-results.csv
    providers.gradleProperty("jmh.resultFormat").orNull?.let(resultFormat::set)
    providers.gradleProperty("jmh.resultsFile").orNull
        ?.let { resultsFile.set(layout.projectDirectory.file(it)) }
}

// Static checks for the porting rules (see docs/porting/TODO.md and docs/porting/GAPS.md):
//  - no Math.max/min/round/rint (they propagate NaN; Rust's f32/f64 ops don't — GAP-002, GAP-003)
//  - no Float.toString / Double.toString / String.valueOf / String.format / "" + concatenation (GAP-016)
//  - no System.out/err printing in ported code; the only allowed call sites live in infra (GAP-016)
//  - no @SuppressWarnings outside infra, which is Java-only support code
//  - every JAFFY(GAP-xxx) marker must reference a GAP heading that exists in docs/porting/GAPS.md
val staticCheck = tasks.register("staticCheck") {
    description = "Check ported sources for forbidden constructs (TODO.md static checks)."
    group = "verification"

    val sources = fileTree("src/main/java") { include("**/*.java") } +
        fileTree("jaffy-gson/src/main/java") { include("**/*.java") }
    val testSources = fileTree("src/test/java") { include("**/*.java") } +
        fileTree("jaffy-gson/src/test/java") { include("**/*.java") }
    val gapsFile = layout.projectDirectory.file("docs/porting/GAPS.md")
    inputs.files(sources, testSources, gapsFile)

    doLast {
        val knownGaps = Regex("""## (GAP-\d{3})""").findAll(gapsFile.asFile.readText())
            .map { it.groupValues[1] }.toSet()
        val violations = mutableListOf<String>()

        // Java-side support types (manifest.yml `support:`) stand on the same footing as
        // infra code: they may legitimately contain the primitives the bans protect against.
        // F32 is the *only* allowed host of Math.max/min/round calls (GAP-002, GAP-003).
        val supportSources = setOf(
            "Features.java",
            "util/F32.java",
            "util/OptF32.java",
            "util/Opt.java",
            "util/Result.java",
            "util/FloatSupplier.java",
            "util/FloatUnaryOperator.java",
            "util/FloatPredicate.java",
        )

        // Ported files whose Rust counterpart itself prints to stdout.
        val printSources = setOf(
            "util/DebugLogger.java", // println! call sites of util/debug.rs
            "util/PrintTree.java", // println! call site of util/print.rs
        )

        fun check(file: File, rel: String) {
            val path = rel.replace('\\', '/')
            val isInfra = path.contains("/infra/")
            val isSupport = supportSources.any { path.endsWith(it) }
            val isPrintSource = printSources.any { path.endsWith(it) }
            file.readLines().forEachIndexed { i, raw ->
                val line = raw.substringBefore("//").let { line ->
                    // strip string literals so rules don't fire inside quoted text
                    line.replace(Regex(""""(\\.|[^"\\])*""""), "\"\"")
                }
                val at = "$rel:${i + 1}"
                if (!isSupport && Regex("""Math\.(max|min|round|rint)\(""").containsMatchIn(line)) {
                    violations += "$at: Math.max/min/round/rint forbidden (GAP-002, GAP-003), use F32"
                }
                if (Regex("""(Float|Double)\.toString\(|String\.valueOf\(|String\.format\(""").containsMatchIn(line)) {
                    violations += "$at: platform formatting forbidden (GAP-016), use RustFmt"
                }
                if (!isInfra && !isSupport && !isPrintSource && Regex("""System\.(out|err)\.print""").containsMatchIn(line)) {
                    violations += "$at: System.out/err printing forbidden outside infra (GAP-016)"
                }
                if (!isInfra && !isSupport && line.contains("@SuppressWarnings")) {
                    violations += "$at: @SuppressWarnings only allowed in infra with a reason comment"
                }
                Regex("""JAFFY\((GAP-\d{3})\)""").findAll(raw).forEach { m ->
                    if (m.groupValues[1] !in knownGaps) {
                        violations += "$at: ${m.groupValues[1]} has no entry in GAPS.md"
                    }
                }
            }
        }

        sources.files.forEach { check(it, it.relativeTo(projectDir).path) }
        testSources.files.forEach { check(it, it.relativeTo(projectDir).path) }

        if (violations.isNotEmpty()) {
            throw GradleException("staticCheck failed:\n" + violations.joinToString("\n"))
        }
    }
}

tasks.named("check") { dependsOn(staticCheck) }
