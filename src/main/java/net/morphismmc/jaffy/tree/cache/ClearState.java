// JAFFY: port of upstream src/tree/cache.rs — `ClearState` enum.
package net.morphismmc.jaffy.tree.cache;

/// Clear operation outcome. See `Cache.clear`.
public enum ClearState {
    /// Cleared some values
    CLEARED,
    /// Everything was already cleared
    ALREADY_EMPTY
}
