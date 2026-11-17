// JAFFY: upstream=none
// JAFFY-NOTE: support type (GAP-019): resolves opaque calc() handles

package net.morphismmc.jaffy.util;

/// JAFFY support type (GAP-019 pattern): Rust `impl Fn(*const (), f32) -> f32`
/// parameters (named `calc_resolver` / `resolve_calc_value` upstream) — a
/// user-supplied callback that resolves an opaque calc() handle against a
/// percentage basis. The `*const ()` handle is an `Object` in Java.
@FunctionalInterface
public interface CalcResolver {
    /// `fn(&self, calc_ptr: *const (), context: f32) -> f32`
    float resolve(Object calc, float context);
}
