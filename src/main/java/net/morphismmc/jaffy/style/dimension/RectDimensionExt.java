// JAFFY: port of upstream src/style/dimension.rs — `impl Rect<Dimension>` block
// (deferred there until the geometry batch; see Dimension.java note).
package net.morphismmc.jaffy.style.dimension;

import net.morphismmc.jaffy.geometry.Rect;

public final class RectDimensionExt {

    private RectDimensionExt() {}

    /// Create a new Rect with length values (`from_length`)
    public static Rect<Dimension> fromLength(float start, float end, float top, float bottom) {
        return new Rect<>(
                Dimension.length(start), Dimension.length(end), Dimension.length(top), Dimension.length(bottom));
    }

    /// Create a new Rect with percentage values (`from_percent`)
    public static Rect<Dimension> fromPercent(float start, float end, float top, float bottom) {
        return new Rect<>(
                Dimension.percent(start), Dimension.percent(end), Dimension.percent(top), Dimension.percent(bottom));
    }
}
