// JAFFY-UPSTREAM: src/geometry.rs
// JAFFY-NOTE: `impl Size<Dimension>` block
// Per SPECIALIZATIONS.md: object type params keep generics; instance-specific
// impl blocks become `<Generic><Arg>Ext` static classes.
package net.morphismmc.jaffy.geometry;

import net.morphismmc.jaffy.style.dimension.Dimension;

public final class SizeDimensionExt {

    private SizeDimensionExt() {}

    /// Generates a `Size<Dimension>` using length values (`from_lengths`)
    public static Size<Dimension> fromLengths(float width, float height) {
        return new Size<>(Dimension.length(width), Dimension.length(height));
    }

    /// Generates a `Size<Dimension>` using percentage values (`from_percent`)
    public static Size<Dimension> fromPercent(float width, float height) {
        return new Size<>(Dimension.percent(width), Dimension.percent(height));
    }
}
