// JAFFY: port of upstream src/style/available_space.rs —
// `impl Size<AvailableSpace>` block (deferred until the geometry batch).
package net.morphismmc.jaffy.style.available_space;

import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;

public final class SizeAvailableSpaceExt {

    private SizeAvailableSpaceExt() {}

    /// Convert `Size<AvailableSpace>` into `Size<Option<f32>>` (`into_options`)
    public static SizeOptF32 intoOptions(Size<AvailableSpace> self) {
        return new SizeOptF32(self.width().intoOption(), self.height().intoOption());
    }

    /// If passed value is Some then return `AvailableSpace::Definite` containing
    /// that value, else return self (`maybe_set`)
    public static Size<AvailableSpace> maybeSet(Size<AvailableSpace> self, SizeOptF32 value) {
        return new Size<>(self.width().maybeSet(value.width()), self.height().maybeSet(value.height()));
    }

    /// Subtract other from definite values, but do nothing for non-definite
    /// values (`Size<AvailableSpace>::maybe_sub(Size<f32>)`)
    public static Size<AvailableSpace> maybeSub(Size<AvailableSpace> self, SizeF32 other) {
        return new Size<>(self.width().maybeSubF(other.width()), self.height().maybeSubF(other.height()));
    }

    /// Compute the max of two values, passing through `Maybe` values
    /// (`Size<AvailableSpace>::maybe_max(Size<f32>)`)
    public static Size<AvailableSpace> maybeMax(Size<AvailableSpace> self, SizeF32 other) {
        return new Size<>(self.width().maybeMaxF(other.width()), self.height().maybeMaxF(other.height()));
    }
}
