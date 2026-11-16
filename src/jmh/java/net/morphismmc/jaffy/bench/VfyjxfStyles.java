package net.morphismmc.jaffy.bench;

import java.util.ArrayList;
import java.util.List;

/// dev.vfyjxf:taffy style factories — counterparts of the free functions in
/// upstream `benches/benches/flexbox.rs`, `grid.rs`, `mixed.rs`, `relayout.rs`,
/// mirroring {@link JaffyStyles}.
public final class VfyjxfStyles {

    private VfyjxfStyles() {}

    /// `random_dimension`
    public static dev.vfyjxf.taffy.style.TaffyDimension dimension(Rng rng) {
        float r = rng.nextFloat();
        if (r < 0.2f) {
            return dev.vfyjxf.taffy.style.TaffyDimension.auto();
        }
        if (r < 0.8f) {
            return dev.vfyjxf.taffy.style.TaffyDimension.length(rng.nextFloat500());
        }
        return dev.vfyjxf.taffy.style.TaffyDimension.percent(rng.nextFloat());
    }

    /// `random_inset`
    public static dev.vfyjxf.taffy.style.LengthPercentageAuto inset(Rng rng) {
        float r = rng.nextFloat();
        if (r < 0.4f) {
            return dev.vfyjxf.taffy.style.LengthPercentageAuto.auto();
        }
        if (r < 0.8f) {
            return dev.vfyjxf.taffy.style.LengthPercentageAuto.length(rng.nextFloat500());
        }
        return dev.vfyjxf.taffy.style.LengthPercentageAuto.percent(rng.nextFloat());
    }

    /// `random_grid_track` — non-repeat tracks go into `gridTemplateColumns`
    /// (`List<TrackSizingFunction>`); `*WithRepeat` is for `repeat()` only.
    public static dev.vfyjxf.taffy.style.TrackSizingFunction gridTrack(Rng rng) {
        float r = rng.nextFloat();
        if (r < 0.1f) {
            return dev.vfyjxf.taffy.style.TrackSizingFunction.auto();
        }
        if (r < 0.2f) {
            return dev.vfyjxf.taffy.style.TrackSizingFunction.minContent();
        }
        if (r < 0.3f) {
            return dev.vfyjxf.taffy.style.TrackSizingFunction.maxContent();
        }
        if (r < 0.5f) {
            return dev.vfyjxf.taffy.style.TrackSizingFunction.fr(1.0f);
        }
        if (r < 0.6f) {
            return dev.vfyjxf.taffy.style.TrackSizingFunction.minmax(
                    dev.vfyjxf.taffy.style.TrackSizingFunction.fixed(0.0f),
                    dev.vfyjxf.taffy.style.TrackSizingFunction.fr(1.0f));
        }
        if (r < 0.8f) {
            return dev.vfyjxf.taffy.style.TrackSizingFunction.fixed(40.0f);
        }
        return dev.vfyjxf.taffy.style.TrackSizingFunction.percent(0.3f);
    }

    /// `random_nxn_grid_style`
    public static dev.vfyjxf.taffy.style.TaffyStyle gridStyle(Rng rng, int trackCount) {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.display = dev.vfyjxf.taffy.style.TaffyDisplay.GRID;
        s.gridTemplateColumns = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            s.gridTemplateColumns.add(gridTrack(rng));
        }
        s.gridTemplateRows = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            s.gridTemplateRows.add(gridTrack(rng));
        }
        return s;
    }

    /// `random_flex_style` (mixed.rs)
    public static dev.vfyjxf.taffy.style.TaffyStyle flexStyle(Rng rng) {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.display = dev.vfyjxf.taffy.style.TaffyDisplay.FLEX;
        s.flexDirection = rng.nextBool()
                ? dev.vfyjxf.taffy.style.FlexDirection.ROW
                : dev.vfyjxf.taffy.style.FlexDirection.COLUMN;
        s.flexWrap = rng.nextBool()
                ? dev.vfyjxf.taffy.style.FlexWrap.WRAP
                : dev.vfyjxf.taffy.style.FlexWrap.NO_WRAP;
        return s;
    }

    /// `leaf_style` (relayout.rs)
    public static dev.vfyjxf.taffy.style.TaffyStyle leafStyle(float height) {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.size = dev.vfyjxf.taffy.geometry.TaffySize.of(
                dev.vfyjxf.taffy.style.TaffyDimension.length(10.0f),
                dev.vfyjxf.taffy.style.TaffyDimension.length(height));
        return s;
    }

    /// `container_style` (relayout.rs)
    public static dev.vfyjxf.taffy.style.TaffyStyle containerStyle(String containers, int depth) {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.display = switch (containers) {
            case "mixed" -> switch (depth % 3) {
                case 0 -> dev.vfyjxf.taffy.style.TaffyDisplay.FLEX;
                case 1 -> dev.vfyjxf.taffy.style.TaffyDisplay.BLOCK;
                default -> dev.vfyjxf.taffy.style.TaffyDisplay.GRID;
            };
            case "block" -> dev.vfyjxf.taffy.style.TaffyDisplay.BLOCK;
            case "grid" -> dev.vfyjxf.taffy.style.TaffyDisplay.GRID;
            default -> dev.vfyjxf.taffy.style.TaffyDisplay.FLEX;
        };
        s.flexDirection = depth % 2 == 0
                ? dev.vfyjxf.taffy.style.FlexDirection.COLUMN
                : dev.vfyjxf.taffy.style.FlexDirection.ROW;
        s.gridTemplateColumns = new ArrayList<>(List.of(
                dev.vfyjxf.taffy.style.TrackSizingFunction.auto(),
                dev.vfyjxf.taffy.style.TrackSizingFunction.auto()));
        s.padding = dev.vfyjxf.taffy.geometry.TaffyRect.all(
                dev.vfyjxf.taffy.style.LengthPercentage.length(1.0f));
        return s;
    }

    /// `huge_nested` fixed style (flexbox.rs): `size: length(10), flex_grow: 1`
    public static dev.vfyjxf.taffy.style.TaffyStyle hugeNested() {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.size = dev.vfyjxf.taffy.geometry.TaffySize.of(
                dev.vfyjxf.taffy.style.TaffyDimension.length(10.0f),
                dev.vfyjxf.taffy.style.TaffyDimension.length(10.0f));
        s.flexGrow = 1.0f;
        return s;
    }

    /// `deep_auto` fixed style (flexbox.rs): `flex_grow: 1, margin: length(10)`
    public static dev.vfyjxf.taffy.style.TaffyStyle deepAuto() {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.flexGrow = 1.0f;
        s.margin = dev.vfyjxf.taffy.geometry.TaffyRect.all(
                dev.vfyjxf.taffy.style.LengthPercentageAuto.length(10.0f));
        return s;
    }

    /// `SuperDeepStyleGen` style (flexbox.rs): Row + grow 1 + margin 10
    public static dev.vfyjxf.taffy.style.TaffyStyle superDeep() {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.flexDirection = dev.vfyjxf.taffy.style.FlexDirection.ROW;
        s.flexGrow = 1.0f;
        s.margin = dev.vfyjxf.taffy.geometry.TaffyRect.all(
                dev.vfyjxf.taffy.style.LengthPercentageAuto.length(10.0f));
        return s;
    }

    /// `RandomAbsoluteStyleGenerator::create_leaf_style`
    public static dev.vfyjxf.taffy.style.TaffyStyle absoluteLeaf(Rng rng) {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.position = dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE;
        s.inset = dev.vfyjxf.taffy.geometry.TaffyRect.of(inset(rng), inset(rng), inset(rng), inset(rng));
        s.size = dev.vfyjxf.taffy.geometry.TaffySize.of(dimension(rng), dimension(rng));
        return s;
    }

    /// `RandomAbsoluteStyleGenerator::create_container_style`
    public static dev.vfyjxf.taffy.style.TaffyStyle absoluteContainer(Rng rng) {
        dev.vfyjxf.taffy.style.TaffyStyle s = new dev.vfyjxf.taffy.style.TaffyStyle();
        s.position = dev.vfyjxf.taffy.style.TaffyPosition.RELATIVE;
        s.size = dev.vfyjxf.taffy.geometry.TaffySize.of(dimension(rng), dimension(rng));
        return s;
    }
}
