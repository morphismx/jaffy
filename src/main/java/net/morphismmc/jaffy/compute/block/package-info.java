// JAFFY-UPSTREAM: src/compute/block.rs

/// CSS Block layout.
///
/// Split per the porting rule "one Rust file -> one Java package": the public
/// items of `block.rs` (`compute_block_layout`,
/// `compute_block_align_content_offset`, `BlockFormattingContext`,
/// `BlockContext`, `FloatMark`) are public top-level classes; the private
/// `BlockItem` struct is package-private. All free functions of `block.rs`
/// live as static methods on {@link net.morphismmc.jaffy.compute.block.Block}.
///
/// `float_layout` and `content_size` cargo features are unconditionally on in
/// JAFFY (GAP-007 convention), so every `#[cfg]`-gated block is always
/// compiled in.
@NullMarked
package net.morphismmc.jaffy.compute.block;

import org.jspecify.annotations.NullMarked;
