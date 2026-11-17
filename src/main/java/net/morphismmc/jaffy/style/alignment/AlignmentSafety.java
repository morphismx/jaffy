// JAFFY: upstream=src/style/alignment.rs
// JAFFY-NOTE: `AlignmentSafety`
package net.morphismmc.jaffy.style.alignment;

/// The overflow-position modifier per [CSS Box Alignment §4.3][css-align-overflow].
///
/// `Safe` falls back to start-edge alignment when the alignment subject would
/// overflow the alignment container, so the start of the content stays visible.
/// `Unsafe` keeps the requested alignment even when that causes overflow at the
/// start edge. `Default` (no overflow-position keyword specified) behaves as
/// `Unsafe`, except for absolutely positioned boxes whose alignment is instead
/// adjusted to minimize overflow per [CSS Box Alignment §4.4.1.2][css-align-abspos].
public enum AlignmentSafety {
    /// No overflow-position keyword specified. (Rust `#[default]`)
    DEFAULT,
    /// Keeps the requested alignment even when the subject overflows the
    /// alignment container at the start edge.
    UNSAFE,
    /// Falls back to the start edge when the subject would overflow, to
    /// avoid data loss.
    SAFE;
}
