// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-NOTE: `Line<bool>` specialization
// (SPECIALIZATIONS.md). `impl Line<bool>` contributes only TRUE/FALSE.
package net.morphismmc.jaffy.geometry;

public record LineBool(boolean start, boolean end) {

    /// A `Line<bool>` with both start and end set to `true`
    public static final LineBool TRUE = new LineBool(true, true);
    /// A `Line<bool>` with both start and end set to `false`
    public static final LineBool FALSE = new LineBool(false, false);
}
