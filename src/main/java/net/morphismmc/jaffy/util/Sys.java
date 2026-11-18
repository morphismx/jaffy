// JAFFY-UPSTREAM: src/util/sys.rs
//
// Rust item correspondence:
//   mod std / mod alloc                -> merged; jaffy always targets the std set (Features.STD, GAP-007)
//   mod polyfill                       -> not ported; only used by cfg(not(feature = "std"))
//   type String                        -> java.lang.String (call sites)
//   type DefaultCheapStr               -> java.lang.String (call sites, GAP-017)
//   type Map<K, V>                     -> java.util.HashMap (call sites)
//   type Vec<A>                        -> java.util.ArrayList; primitive specializations use fastutil (DECISIONS)
//   type Box<A>                        -> plain reference (call sites)
//   type ChildrenVec<A>                -> java.util.ArrayList; ChildrenVec<NodeId> uses fastutil LongList (GAP-015)
//   type GridTrackVec<A>               -> java.util.ArrayList
//   fn new_vec_with_capacity           -> newVecWithCapacity
//   fn new_const_children_vec          -> newConstChildrenVec
//   fn round                           -> round
//   fn ceil                            -> ceil
//   fn floor                           -> floor
//   fn f32_max                         -> f32Max
//   fn f32_min                         -> f32Min
//
// Upstream has no #[cfg(test)] module in this file.
package net.morphismmc.jaffy.util;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.just.math.F32;

public final class Sys {
    private Sys() {}

    /// `new_vec_with_capacity`
    public static <A> List<A> newVecWithCapacity(int capacity) {
        return new ArrayList<>(capacity);
    }

    /// `new_const_children_vec`
    public static <A> List<A> newConstChildrenVec() {
        return new ArrayList<>();
    }

    /// `round` (sys.rs std branch): `(value + 0.5).floor()` — this is NOT `f32::round`
    /// (see F32.round). The double round-trip through Math.floor is exact (GAP-008).
    public static float round(float value) {
        return (float) Math.floor(value + 0.5f);
    }

    /// `ceil`
    public static float ceil(float value) {
        return F32.ceil(value);
    }

    /// `floor`
    public static float floor(float value) {
        return F32.floor(value);
    }

    /// `f32_max`
    public static float f32Max(float a, float b) {
        return F32.max(a, b);
    }

    /// `f32_min`
    public static float f32Min(float a, float b) {
        return F32.min(a, b);
    }
}
