package net.morphismmc.jaffy.bench;

/// Port of upstream `benches/src/lib.rs` — `trait GenStyle<Style>`.
/// `S` is the library-specific style type (`Style` for jaffy,
/// `dev.vfyjxf.taffy.style.TaffyStyle` for vfyjxf).
public interface GenStyle<S> {
    /// `create_leaf_style`
    S leafStyle(Rng rng);

    /// `create_container_style`
    S containerStyle(Rng rng);

    /// `create_root_style` — `Default::default()`
    default S rootStyle(Rng rng) {
        return null; // adapters substitute the library default
    }
}
