// JAFFY: port of upstream src/tree/traits.rs — `CacheTree` trait.
//
// `&LayoutInput` -> `LayoutInput` (immutable record); `Option<LayoutOutput>`
// -> `@Nullable`.
package net.morphismmc.jaffy.tree.traits;

import net.morphismmc.jaffy.tree.layout.LayoutInput;
import net.morphismmc.jaffy.tree.layout.LayoutOutput;

import org.jspecify.annotations.Nullable;

/// Trait used by the `compute_cached_layout` method which allows cached
/// layout results to be stored and retrieved.
///
/// The {@link Cache} class implements a per-node cache that is compatible
/// with this trait.
public interface CacheTree {

    /// Try to retrieve a cached result from the cache
    @Nullable
    LayoutOutput cacheGet(long nodeId, LayoutInput inputs);

    /// Store a computed size in the cache
    void cacheStore(long nodeId, LayoutInput inputs, LayoutOutput layoutOutput);

    /// Clear all cache entries for the node
    void cacheClear(long nodeId);
}
