// JAFFY: upstream=src/util/math.rs
// JAFFY-NOTE: `MaybeMath` trait impls
//
// The trait itself is erased in Java; impls are distributed onto the Self
// type's own type per GAP-006 conventions:
//   Self = Option<f32>           -> OptF32.maybe*O / maybe*F
//   Self = f32                   -> F32.maybe*O
//   Self = AvailableSpace        -> AvailableSpace.maybe*F / maybe*O (defaults)
//   Self = Size<Option<f32>>     -> SizeOptF32.maybe*O / maybe*F
//   Self = Size<f32>             -> SizeF32.maybe*O
//   Self = Size<AvailableSpace>  -> statics HERE (object-param Size<T> keeps
//      generics — SPECIALIZATIONS Ext rule, hosted in this file's package)
package net.morphismmc.jaffy.util;

import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;

public final class MaybeMath {

    private MaybeMath() {}

    // ---- `MaybeMath<Size<f32>, Size<AvailableSpace>> for Size<AvailableSpace>` ----

    /// `maybe_min` — component-wise `AvailableSpace::maybe_min(f32)`
    public static Size<AvailableSpace> maybeMinF(Size<AvailableSpace> self, SizeF32 rhs) {
        return new Size<>(self.width().maybeMinF(rhs.width()), self.height().maybeMinF(rhs.height()));
    }

    /// `maybe_max` — component-wise
    public static Size<AvailableSpace> maybeMaxF(Size<AvailableSpace> self, SizeF32 rhs) {
        return new Size<>(self.width().maybeMaxF(rhs.width()), self.height().maybeMaxF(rhs.height()));
    }

    /// `maybe_clamp` — component-wise
    public static Size<AvailableSpace> maybeClampF(Size<AvailableSpace> self, SizeF32 min, SizeF32 max) {
        return new Size<>(
                self.width().maybeClampF(min.width(), max.width()),
                self.height().maybeClampF(min.height(), max.height()));
    }

    /// `maybe_add` — component-wise
    public static Size<AvailableSpace> maybeAddF(Size<AvailableSpace> self, SizeF32 rhs) {
        return new Size<>(self.width().maybeAddF(rhs.width()), self.height().maybeAddF(rhs.height()));
    }

    /// `maybe_sub` — component-wise
    public static Size<AvailableSpace> maybeSubF(Size<AvailableSpace> self, SizeF32 rhs) {
        return new Size<>(self.width().maybeSubF(rhs.width()), self.height().maybeSubF(rhs.height()));
    }

    // ---- `MaybeMath<Size<Option<f32>>, Size<AvailableSpace>> for Size<AvailableSpace>` ----

    /// `maybe_min` — component-wise `AvailableSpace::maybe_min(Option<f32>)`
    public static Size<AvailableSpace> maybeMinO(Size<AvailableSpace> self, SizeOptF32 rhs) {
        return new Size<>(self.width().maybeMinO(rhs.width()), self.height().maybeMinO(rhs.height()));
    }

    /// `maybe_max` — component-wise
    public static Size<AvailableSpace> maybeMaxO(Size<AvailableSpace> self, SizeOptF32 rhs) {
        return new Size<>(self.width().maybeMaxO(rhs.width()), self.height().maybeMaxO(rhs.height()));
    }

    /// `maybe_clamp` — component-wise
    public static Size<AvailableSpace> maybeClampO(Size<AvailableSpace> self, SizeOptF32 min, SizeOptF32 max) {
        return new Size<>(
                self.width().maybeClampO(min.width(), max.width()),
                self.height().maybeClampO(min.height(), max.height()));
    }

    /// `maybe_add` — component-wise
    public static Size<AvailableSpace> maybeAddO(Size<AvailableSpace> self, SizeOptF32 rhs) {
        return new Size<>(self.width().maybeAddO(rhs.width()), self.height().maybeAddO(rhs.height()));
    }

    /// `maybe_sub` — component-wise
    public static Size<AvailableSpace> maybeSubO(Size<AvailableSpace> self, SizeOptF32 rhs) {
        return new Size<>(self.width().maybeSubO(rhs.width()), self.height().maybeSubO(rhs.height()));
    }
}
