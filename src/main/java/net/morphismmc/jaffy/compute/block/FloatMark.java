// JAFFY: upstream=src/compute/block.rs
// JAFFY-NOTE: `FloatMark`
// feature is always on).
package net.morphismmc.jaffy.compute.block;

/// A mark of how many floats a Block Formatting Context held when it was
/// taken. See {@link BlockContext#floatMark}.
///
/// `usize` -> `int` (GAP-014).
public record FloatMark(int left, int right) {}
