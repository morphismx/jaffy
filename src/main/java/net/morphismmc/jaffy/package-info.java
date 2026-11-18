// JAFFY-UPSTREAM: src/lib.rs
// JAFFY-NOTE: crate-root documentation.
//
// The `pub mod` declarations and `pub use` re-exports have no Java
// counterpart (packages + imports are the Java shape); the feature-flag
// documentation macro is superseded by {@link net.morphismmc.jaffy.Features}
// (GAP-007). `parse` re-exports are deferred with the parse module.

/// # jaffy
///
/// jaffy is a flexible, high-performance library for **UI layout** — a pure
/// Java port of [taffy](https://github.com/DioxusLabs/taffy). It implements
/// the Flexbox, Grid and Block layout algorithms from the CSS specification.
///
/// ## Architecture
///
/// jaffy is based on a tree of "UI nodes" similar to the tree of DOM nodes
/// that one finds in web-based UI. Each node has:
///   - A {@link net.morphismmc.jaffy.style.Style} which holds a set of CSS
///     styles which function as the primary input to the layout
///     computations.
///   - A {@link net.morphismmc.jaffy.tree.layout.Layout} containing a
///     position (x/y) and a size (width/height) which function as the output
///     of the layout computations.
///   - Optionally:
///       - A {@code List} set of child nodes
///       - "Context": arbitrary user-defined data (which you can access when
///         using a "measure function" to integrate jaffy with other kinds of
///         layout such as text layout)
///
/// Usage of jaffy consists of constructing a tree of UI nodes (with
/// associated styles, children and context), then calling function(s) from
/// jaffy to translate those styles, parent-child relationships and measure
/// functions into a size and position in 2d space for each node in the tree.
///
/// ## High-level API vs. Low-level API
///
/// jaffy has two APIs: a high-level API that is simpler and easier to get
/// started with, and a low-level API that is more flexible and gives greater
/// control. We would generally recommend the high-level API for users using
/// jaffy standalone and the low-level API for users wanting to embed jaffy
/// as part of a wider layout system or as part of a UI framework that
/// already has it's own node/widget tree representation.
///
/// ### High-level API
///
/// The high-level API consists of the
/// {@link net.morphismmc.jaffy.tree.TaffyTree} class which contains a tree
/// implementation and provides methods that allow you to construct a tree of
/// UI nodes. Once constructed, you can call
/// {@link net.morphismmc.jaffy.tree.TaffyTree#computeLayoutWithMeasure} to
/// compute the layout (passing in a "measure function" lambda which is used
/// to compute the size of leaf nodes), and then access the layout of each
/// node using {@link net.morphismmc.jaffy.tree.TaffyTree#layout}.
///
/// When using the high-level API, jaffy will take care of node storage,
/// caching and dispatching to the correct layout algorithm for a given node
/// for you.
///
/// ### Low-level API
///
/// The low-level API consists of a set of traits (notably
/// {@link net.morphismmc.jaffy.tree.traits.LayoutPartialTree}) which define
/// an interface behind which you must implement your own tree
/// implementation, and a set of functions (in
/// {@link net.morphismmc.jaffy.compute}, e.g.
/// {@link net.morphismmc.jaffy.compute.flexbox.Flexbox#computeFlexboxLayout}
/// and {@link net.morphismmc.jaffy.compute.grid.Grid#computeGridLayout})
/// which implement the layout algorithms (for a single node at a time), and
/// are designed to be flexible and easy to integrate into a wider layout or
/// UI system.
///
/// When using this API, you must handle node storage, caching, and
/// dispatching to the correct layout algorithm for a given node yourself.
/// See the {@link net.morphismmc.jaffy.tree.traits} package for more details
/// on this API.
@NullMarked
package net.morphismmc.jaffy;

import org.jspecify.annotations.NullMarked;
