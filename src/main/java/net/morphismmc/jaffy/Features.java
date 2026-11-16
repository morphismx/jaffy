// JAFFY: Java-side support type (no upstream counterpart).
// Corresponds to: Cargo feature flags of upstream/Cargo.toml — GAP-007.
//
// Rust `#[cfg(feature = "...")]` compile-time switches, fixed to upstream's
// default feature set. `debug`/`profile` are startup-time constants read once
// from system properties: Cargo unifies features per binary, so one flag per
// JVM is the faithful shape.
package net.morphismmc.jaffy;

public final class Features {
    private Features() {}

    // ---- Compile-time constants: upstream default features ----
    public static final boolean STD = true;
    public static final boolean TAFFY_TREE = true;
    public static final boolean FLEXBOX = true;
    public static final boolean FLEXBOX_BALANCE = true;
    public static final boolean GRID = true;
    public static final boolean BLOCK_LAYOUT = true;
    public static final boolean FLOAT_LAYOUT = true;
    public static final boolean CALC = true;
    public static final boolean CONTENT_SIZE = true;
    public static final boolean DETAILED_LAYOUT_INFO = true;

    // ---- Startup-time constants: folded by JIT ----
    public static final boolean DEBUG = Boolean.getBoolean("jaffy.debug");
    public static final boolean PROFILE = Boolean.getBoolean("jaffy.profile");

    // `serde` and `parse` are provided by separate modules (jaffy-gson, parse).
    // `alloc`, `strict_provenance`, `parse_faster` have no counterpart (GAP-007).
}
