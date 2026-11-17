// JAFFY: port of upstream src/util/resolve.rs — `MaybeResolve`/`ResolveOrZero`
// trait impls.
//
// Distribution per established conventions:
//   Self = LengthPercentage/LengthPercentageAuto/Dimension (scalar impls)
//     -> default methods on those interfaces (maybeResolveO/maybeResolveF/
//        resolveOrZero)
//   Self = Size<T>/Rect<T> (generic blanket impls, object type params)
//     -> nested Ext statics here (SPECIALIZATIONS rule — erased signatures
//        collide across instantiations, e.g. Size<LP> vs Size<Dimension>)
package net.morphismmc.jaffy.util;

import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.geometry.SizeF32;
import net.morphismmc.jaffy.geometry.SizeOptF32;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.just.math.Opt;

public final class MaybeResolve {

    private MaybeResolve() {}

    // ---- `Size<LengthPercentage>` instantiations ----

    public static final class SizeLengthPercentageExt {
        private SizeLengthPercentageExt() {}

        /// `maybe_resolve` — resolve each component against `Size<Option<f32>>` context
        public static SizeOptF32 maybeResolveO(Size<LengthPercentage> self, SizeOptF32 context, CalcResolver calc) {
            return new SizeOptF32(
                    self.width().maybeResolveO(context.width(), calc),
                    self.height().maybeResolveO(context.height(), calc));
        }

        /// `maybe_resolve` — `Size<f32>` context via the f32-context blanket impl
        public static SizeOptF32 maybeResolveF(Size<LengthPercentage> self, SizeF32 context, CalcResolver calc) {
            return new SizeOptF32(
                    self.width().maybeResolveF(context.width(), calc),
                    self.height().maybeResolveF(context.height(), calc));
        }

        /// `resolve_or_zero` — resolve each component, `None` becomes 0.0
        public static SizeF32 resolveOrZeroO(Size<LengthPercentage> self, SizeOptF32 context, CalcResolver calc) {
            return new SizeF32(
                    self.width().resolveOrZero(context.width(), calc),
                    self.height().resolveOrZero(context.height(), calc));
        }
    }

    // ---- `Size<LengthPercentageAuto>` instantiations ----

    public static final class SizeLengthPercentageAutoExt {
        private SizeLengthPercentageAutoExt() {}

        /// `maybe_resolve` — `Size<Option<f32>>` context
        public static SizeOptF32 maybeResolveO(Size<LengthPercentageAuto> self, SizeOptF32 context, CalcResolver calc) {
            return new SizeOptF32(
                    self.width().maybeResolveO(context.width(), calc),
                    self.height().maybeResolveO(context.height(), calc));
        }

        /// `maybe_resolve` — `Size<f32>` context
        public static SizeOptF32 maybeResolveF(Size<LengthPercentageAuto> self, SizeF32 context, CalcResolver calc) {
            return new SizeOptF32(
                    self.width().maybeResolveF(context.width(), calc),
                    self.height().maybeResolveF(context.height(), calc));
        }

        /// `resolve_or_zero` — `Size<Option<f32>>` context, `None` becomes 0.0
        public static SizeF32 resolveOrZeroO(Size<LengthPercentageAuto> self, SizeOptF32 context, CalcResolver calc) {
            return new SizeF32(
                    self.width().resolveOrZero(context.width(), calc),
                    self.height().resolveOrZero(context.height(), calc));
        }
    }

    // ---- `Size<Dimension>` instantiations ----

    public static final class SizeDimensionExt {
        private SizeDimensionExt() {}

        /// `maybe_resolve` — `Size<Option<f32>>` context
        public static SizeOptF32 maybeResolveO(Size<Dimension> self, SizeOptF32 context, CalcResolver calc) {
            return new SizeOptF32(
                    self.width().maybeResolveO(context.width(), calc),
                    self.height().maybeResolveO(context.height(), calc));
        }

        /// `maybe_resolve` — `Size<f32>` context
        public static SizeOptF32 maybeResolveF(Size<Dimension> self, SizeF32 context, CalcResolver calc) {
            return new SizeOptF32(
                    self.width().maybeResolveF(context.width(), calc),
                    self.height().maybeResolveF(context.height(), calc));
        }

        /// `resolve_or_zero` — `Size<Option<f32>>` context, `None` becomes 0.0
        public static SizeF32 resolveOrZeroO(Size<Dimension> self, SizeOptF32 context, CalcResolver calc) {
            return new SizeF32(
                    self.width().resolveOrZero(context.width(), calc),
                    self.height().resolveOrZero(context.height(), calc));
        }
    }

    // ---- `Rect<LengthPercentage>` instantiations ----

    public static final class RectLengthPercentageExt {
        private RectLengthPercentageExt() {}

        /// `resolve_or_zero` — `Size<Option<f32>>` context; horizontal sides use
        /// `context.width`, vertical sides use `context.height`
        public static RectF32 resolveOrZeroO(Rect<LengthPercentage> self, SizeOptF32 context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context.width(), calc),
                    self.right().resolveOrZero(context.width(), calc),
                    self.top().resolveOrZero(context.height(), calc),
                    self.bottom().resolveOrZero(context.height(), calc));
        }

        /// `resolve_or_zero` — `Option<f32>` context applied to all four sides
        public static RectF32 resolveOrZeroO(Rect<LengthPercentage> self, @Opt float context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context, calc),
                    self.right().resolveOrZero(context, calc),
                    self.top().resolveOrZero(context, calc),
                    self.bottom().resolveOrZero(context, calc));
        }

        /// `resolve_or_zero` — `f32` context applied to all four sides
        public static RectF32 resolveOrZeroF(Rect<LengthPercentage> self, float context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context, calc),
                    self.right().resolveOrZero(context, calc),
                    self.top().resolveOrZero(context, calc),
                    self.bottom().resolveOrZero(context, calc));
        }
    }

    // ---- `Rect<LengthPercentageAuto>` instantiations ----

    public static final class RectLengthPercentageAutoExt {
        private RectLengthPercentageAutoExt() {}

        /// `resolve_or_zero` — `Size<Option<f32>>` context
        public static RectF32 resolveOrZeroO(Rect<LengthPercentageAuto> self, SizeOptF32 context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context.width(), calc),
                    self.right().resolveOrZero(context.width(), calc),
                    self.top().resolveOrZero(context.height(), calc),
                    self.bottom().resolveOrZero(context.height(), calc));
        }

        /// `resolve_or_zero` — `Option<f32>` context applied to all four sides
        public static RectF32 resolveOrZeroO(Rect<LengthPercentageAuto> self, @Opt float context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context, calc),
                    self.right().resolveOrZero(context, calc),
                    self.top().resolveOrZero(context, calc),
                    self.bottom().resolveOrZero(context, calc));
        }

        /// `resolve_or_zero` — `f32` context applied to all four sides
        public static RectF32 resolveOrZeroF(Rect<LengthPercentageAuto> self, float context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context, calc),
                    self.right().resolveOrZero(context, calc),
                    self.top().resolveOrZero(context, calc),
                    self.bottom().resolveOrZero(context, calc));
        }
    }

    // ---- `Rect<Dimension>` instantiations ----

    public static final class RectDimensionExt {
        private RectDimensionExt() {}

        /// `resolve_or_zero` — `Size<Option<f32>>` context
        public static RectF32 resolveOrZeroO(Rect<Dimension> self, SizeOptF32 context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context.width(), calc),
                    self.right().resolveOrZero(context.width(), calc),
                    self.top().resolveOrZero(context.height(), calc),
                    self.bottom().resolveOrZero(context.height(), calc));
        }

        /// `resolve_or_zero` — `Option<f32>` context applied to all four sides
        public static RectF32 resolveOrZeroO(Rect<Dimension> self, @Opt float context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context, calc),
                    self.right().resolveOrZero(context, calc),
                    self.top().resolveOrZero(context, calc),
                    self.bottom().resolveOrZero(context, calc));
        }

        /// `resolve_or_zero` — `f32` context applied to all four sides
        public static RectF32 resolveOrZeroF(Rect<Dimension> self, float context, CalcResolver calc) {
            return new RectF32(
                    self.left().resolveOrZero(context, calc),
                    self.right().resolveOrZero(context, calc),
                    self.top().resolveOrZero(context, calc),
                    self.bottom().resolveOrZero(context, calc));
        }
    }
}
