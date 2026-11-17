// JAFFY: upstream=src/style_helpers.rs
//
// Rust traits (TaffyZero/TaffyAuto/TaffyMinContent/TaffyMaxContent/
// TaffyFitContent/FromLength/FromPercent/FromFr/TaffyGridLine/TaffyGridSpan)
// and their generic free functions (zero/auto/length/percent/...) erase in
// Java — call sites use the concrete per-type constants/statics:
//   zero::<T>()            -> T's ZERO constant / Ext zero()
//   auto::<T>()            -> T's AUTO constant / Ext auto()
//   length::<Input,T>(v)   -> Ext length(v) / T.fromLength(v)
//   percent::<Input,T>(v)  -> Ext percent(v) / T.fromPercent(v)
// Scalar impls live on the style types themselves (Dimension.ZERO,
// LengthPercentageAuto.AUTO, ..., already ported with dimension.rs).
// The Point<T>/Line<T> blanket impls have no upstream instantiation for the
// ported T args (only Point<f32>/Line<f32>, covered by spec-type constants)
// — omitted until needed. Grid-trait impls (TaffyGridLine/TaffyGridSpan/
// FromFr on GridPlacement/track types) land with style/grid.rs.
package net.morphismmc.jaffy.style;

import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.available_space.AvailableSpace;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;

public final class StyleHelpers {

    private StyleHelpers() {}

    // ---- `impl<T: TaffyZero|FromLength|FromPercent> … for Size<T>` on
    //      `Size<LengthPercentage>` ----

    public static final class SizeLengthPercentageExt {
        private SizeLengthPercentageExt() {}

        /// `Size::<T>::zero()` — both components `T::ZERO`
        public static Size<LengthPercentage> zero() {
            return new Size<>(LengthPercentage.ZERO, LengthPercentage.ZERO);
        }

        /// `Size::<T>::length(value)` — `T::from_length` for both components
        public static Size<LengthPercentage> length(double value) {
            return new Size<>(LengthPercentage.fromLength(value), LengthPercentage.fromLength(value));
        }

        /// `Size::<T>::percent(percent)` — `T::from_percent` for both components
        public static Size<LengthPercentage> percent(double percent) {
            return new Size<>(LengthPercentage.fromPercent(percent), LengthPercentage.fromPercent(percent));
        }
    }

    // ---- `Size<AvailableSpace>` — TaffyZero/TaffyMinContent/TaffyMaxContent/
    //      FromLength instantiations (upstream `Size::ZERO`/`MIN_CONTENT`/
    //      `MAX_CONTENT`/`Size::length`) ----

    public static final class SizeAvailableSpaceExt {
        private SizeAvailableSpaceExt() {}

        /// `Size::<AvailableSpace>::ZERO` — `Definite(0.0)` on both axes
        /// (upstream `TaffyZero for AvailableSpace` maps to `Definite(0.0)`;
        /// the constant itself is `AvailableSpace.ZERO` if present)
        public static Size<AvailableSpace> zero() {
            return new Size<>(AvailableSpace.ZERO, AvailableSpace.ZERO);
        }

        /// `Size::<AvailableSpace>::MIN_CONTENT`
        public static Size<AvailableSpace> minContent() {
            return new Size<>(AvailableSpace.MIN_CONTENT, AvailableSpace.MIN_CONTENT);
        }

        /// `Size::<AvailableSpace>::MAX_CONTENT`
        public static Size<AvailableSpace> maxContent() {
            return new Size<>(AvailableSpace.MAX_CONTENT, AvailableSpace.MAX_CONTENT);
        }

        /// `Size::<AvailableSpace>::length(value)`
        public static Size<AvailableSpace> length(double value) {
            return new Size<>(AvailableSpace.fromLength(value), AvailableSpace.fromLength(value));
        }
    }

    // ---- `Size<LengthPercentageAuto>` ----

    public static final class SizeLengthPercentageAutoExt {
        private SizeLengthPercentageAutoExt() {}

        /// `Size::<T>::zero()`
        public static Size<LengthPercentageAuto> zero() {
            return new Size<>(LengthPercentageAuto.ZERO, LengthPercentageAuto.ZERO);
        }

        /// `Size::<T>::auto()` — both components `T::AUTO`
        public static Size<LengthPercentageAuto> auto() {
            return new Size<>(LengthPercentageAuto.AUTO, LengthPercentageAuto.AUTO);
        }

        /// `Size::<T>::length(value)`
        public static Size<LengthPercentageAuto> length(double value) {
            return new Size<>(LengthPercentageAuto.fromLength(value), LengthPercentageAuto.fromLength(value));
        }

        /// `Size::<T>::percent(percent)`
        public static Size<LengthPercentageAuto> percent(double percent) {
            return new Size<>(LengthPercentageAuto.fromPercent(percent), LengthPercentageAuto.fromPercent(percent));
        }
    }

    // ---- `Size<Dimension>` ----
    // (Dimension impls TaffyZero/TaffyAuto/FromLength/FromPercent only —
    //  no TaffyMinContent/TaffyMaxContent/TaffyFitContent/FromFr upstream)

    public static final class SizeDimensionExt {
        private SizeDimensionExt() {}

        /// `Size::<T>::zero()`
        public static Size<Dimension> zero() {
            return new Size<>(Dimension.ZERO, Dimension.ZERO);
        }

        /// `Size::<T>::auto()`
        public static Size<Dimension> auto() {
            return new Size<>(Dimension.AUTO, Dimension.AUTO);
        }

        /// `Size::<T>::length(value)`
        public static Size<Dimension> length(double value) {
            return new Size<>(Dimension.fromLength(value), Dimension.fromLength(value));
        }

        /// `Size::<T>::percent(percent)`
        public static Size<Dimension> percent(double percent) {
            return new Size<>(Dimension.fromPercent(percent), Dimension.fromPercent(percent));
        }
    }

    // ---- `Rect<LengthPercentage>` ----

    public static final class RectLengthPercentageExt {
        private RectLengthPercentageExt() {}

        /// `Rect::<T>::zero()` — all four sides `T::ZERO`
        public static Rect<LengthPercentage> zero() {
            return new Rect<>(
                    LengthPercentage.ZERO, LengthPercentage.ZERO, LengthPercentage.ZERO, LengthPercentage.ZERO);
        }

        /// `Rect::<T>::length(value)` — `T::from_length` for all four sides
        public static Rect<LengthPercentage> length(double value) {
            return new Rect<>(
                    LengthPercentage.fromLength(value),
                    LengthPercentage.fromLength(value),
                    LengthPercentage.fromLength(value),
                    LengthPercentage.fromLength(value));
        }

        /// `Rect::<T>::percent(percent)` — `T::from_percent` for all four sides
        public static Rect<LengthPercentage> percent(double percent) {
            return new Rect<>(
                    LengthPercentage.fromPercent(percent),
                    LengthPercentage.fromPercent(percent),
                    LengthPercentage.fromPercent(percent),
                    LengthPercentage.fromPercent(percent));
        }
    }

    // ---- `Rect<LengthPercentageAuto>` ----

    public static final class RectLengthPercentageAutoExt {
        private RectLengthPercentageAutoExt() {}

        /// `Rect::<T>::zero()`
        public static Rect<LengthPercentageAuto> zero() {
            return new Rect<>(
                    LengthPercentageAuto.ZERO,
                    LengthPercentageAuto.ZERO,
                    LengthPercentageAuto.ZERO,
                    LengthPercentageAuto.ZERO);
        }

        /// `Rect::<T>::auto()` — all four sides `T::AUTO`
        public static Rect<LengthPercentageAuto> auto() {
            return new Rect<>(
                    LengthPercentageAuto.AUTO,
                    LengthPercentageAuto.AUTO,
                    LengthPercentageAuto.AUTO,
                    LengthPercentageAuto.AUTO);
        }

        /// `Rect::<T>::length(value)`
        public static Rect<LengthPercentageAuto> length(double value) {
            return new Rect<>(
                    LengthPercentageAuto.fromLength(value),
                    LengthPercentageAuto.fromLength(value),
                    LengthPercentageAuto.fromLength(value),
                    LengthPercentageAuto.fromLength(value));
        }

        /// `Rect::<T>::percent(percent)`
        public static Rect<LengthPercentageAuto> percent(double percent) {
            return new Rect<>(
                    LengthPercentageAuto.fromPercent(percent),
                    LengthPercentageAuto.fromPercent(percent),
                    LengthPercentageAuto.fromPercent(percent),
                    LengthPercentageAuto.fromPercent(percent));
        }
    }

    // ---- `Rect<Dimension>` ----

    public static final class RectDimensionExt {
        private RectDimensionExt() {}

        /// `Rect::<T>::zero()`
        public static Rect<Dimension> zero() {
            return new Rect<>(Dimension.ZERO, Dimension.ZERO, Dimension.ZERO, Dimension.ZERO);
        }

        /// `Rect::<T>::auto()`
        public static Rect<Dimension> auto() {
            return new Rect<>(Dimension.AUTO, Dimension.AUTO, Dimension.AUTO, Dimension.AUTO);
        }

        /// `Rect::<T>::length(value)`
        public static Rect<Dimension> length(double value) {
            return new Rect<>(
                    Dimension.fromLength(value),
                    Dimension.fromLength(value),
                    Dimension.fromLength(value),
                    Dimension.fromLength(value));
        }

        /// `Rect::<T>::percent(percent)`
        public static Rect<Dimension> percent(double percent) {
            return new Rect<>(
                    Dimension.fromPercent(percent),
                    Dimension.fromPercent(percent),
                    Dimension.fromPercent(percent),
                    Dimension.fromPercent(percent));
        }
    }
}
