package net.morphismmc.jaffy.bench;

import java.util.ArrayList;
import java.util.List;

import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Rect;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.Display;
import net.morphismmc.jaffy.style.Position;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.dimension.Dimension;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;
import net.morphismmc.jaffy.style.dimension.LengthPercentageAuto;
import net.morphismmc.jaffy.style.flex.FlexDirection;
import net.morphismmc.jaffy.style.grid.GenericGridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridTemplateComponent;
import net.morphismmc.jaffy.style.grid.GridTemplateRepetition;
import net.morphismmc.jaffy.style.grid.MaxTrackSizingFunction;
import net.morphismmc.jaffy.style.grid.MinTrackSizingFunction;

/// jaffy style factories — counterparts of the free functions in upstream
/// `benches/benches/flexbox.rs`, `grid.rs`, `mixed.rs`, `relayout.rs`.
public final class JaffyStyles {

    private JaffyStyles() {}

    /// `random_dimension`: <0.2 auto, <0.8 length(0..500), else percent(0..1)
    public static Dimension dimension(Rng rng) {
        float r = rng.nextFloat();
        if (r < 0.2f) {
            return Dimension.auto();
        }
        if (r < 0.8f) {
            return Dimension.length(rng.nextFloat500());
        }
        return Dimension.percent(rng.nextFloat());
    }

    /// `random_inset`: <0.4 auto, <0.8 length(0..500), else percent(0..1)
    public static LengthPercentageAuto inset(Rng rng) {
        float r = rng.nextFloat();
        if (r < 0.4f) {
            return LengthPercentageAuto.auto();
        }
        if (r < 0.8f) {
            return LengthPercentageAuto.length(rng.nextFloat500());
        }
        return LengthPercentageAuto.percent(rng.nextFloat());
    }

    /// `random_grid_track` (grid.rs / mixed.rs):
    /// <0.1 auto / <0.2 min_content / <0.3 max_content / <0.5 fr(1) /
    /// <0.6 minmax(length(0), fr(1)) / <0.8 length(40) / else percent(0.3)
    public static GenericGridTemplateComponent<GridTemplateRepetition> gridTrack(Rng rng) {
        float r = rng.nextFloat();
        if (r < 0.1f) {
            return GridTemplateComponent.AUTO;
        }
        if (r < 0.2f) {
            return GridTemplateComponent.MIN_CONTENT;
        }
        if (r < 0.3f) {
            return GridTemplateComponent.MAX_CONTENT;
        }
        if (r < 0.5f) {
            return GridTemplateComponent.fromFr(1.0);
        }
        if (r < 0.6f) {
            return new GenericGridTemplateComponent.Single<>(
                    new MinMax<>(MinTrackSizingFunction.length(0.0f), MaxTrackSizingFunction.fr(1.0f)));
        }
        if (r < 0.8f) {
            return GridTemplateComponent.fromLength(40.0);
        }
        return GridTemplateComponent.fromPercent(0.3);
    }

    /// `random_nxn_grid_style` (grid.rs / mixed.rs): `display: GRID` +
    /// track_count random track components per axis, cols drawn first.
    public static Style gridStyle(Rng rng, int trackCount) {
        List<GenericGridTemplateComponent<GridTemplateRepetition>> cols = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            cols.add(gridTrack(rng));
        }
        List<GenericGridTemplateComponent<GridTemplateRepetition>> rows = new ArrayList<>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            rows.add(gridTrack(rng));
        }
        return Style.DEFAULT.withDisplay(Display.GRID)
                .withGridTemplateColumns(cols)
                .withGridTemplateRows(rows);
    }

    /// `random_flex_style` (mixed.rs): flex, 50% Row/Column, 50% Wrap/NoWrap
    public static Style flexStyle(Rng rng) {
        return Style.DEFAULT.withDisplay(Display.FLEX)
                .withFlexDirection(rng.nextBool() ? FlexDirection.ROW : FlexDirection.COLUMN)
                .withFlexWrap(rng.nextBool()
                        ? net.morphismmc.jaffy.style.flex.FlexWrap.WRAP
                        : net.morphismmc.jaffy.style.flex.FlexWrap.NO_WRAP);
    }

    /// `leaf_style` (relayout.rs): `size: { width: 10, height }`
    public static Style leafStyle(float height) {
        return Style.DEFAULT.withSize(new Size<>(Dimension.length(10.0f), Dimension.length(height)));
    }

    private static final LengthPercentage LP1 = LengthPercentage.length(1.0f);
    private static final Rect<LengthPercentage> PADDING_1 = new Rect<>(LP1, LP1, LP1, LP1);
    private static final List<GenericGridTemplateComponent<GridTemplateRepetition>> TWO_AUTO_COLS =
            List.of(GridTemplateComponent.AUTO, GridTemplateComponent.AUTO);

    /// `container_style` (relayout.rs): display cycles by depth through the
    /// `containers` list, flex_direction Column/Row by depth % 2,
    /// grid_template_columns=[auto,auto], padding=1.
    public static Style containerStyle(String containers, int depth) {
        Display display = switch (containers) {
            case "mixed" -> switch (depth % 3) {
                case 0 -> Display.FLEX;
                case 1 -> Display.BLOCK;
                default -> Display.GRID;
            };
            case "block" -> Display.BLOCK;
            case "grid" -> Display.GRID;
            default -> Display.FLEX;
        };
        return Style.DEFAULT.withDisplay(display)
                .withFlexDirection(depth % 2 == 0 ? FlexDirection.COLUMN : FlexDirection.ROW)
                .withGridTemplateColumns(TWO_AUTO_COLS)
                .withPadding(PADDING_1);
    }

    /// `huge_nested` fixed style (flexbox.rs): `size: length(10), flex_grow: 1`
    public static final Style HUGE_NESTED =
            Style.DEFAULT.withSize(new Size<>(Dimension.length(10.0f), Dimension.length(10.0f)))
                    .withFlexGrow(1.0f);

    /// `deep_auto` fixed style (flexbox.rs): `flex_grow: 1, margin: length(10)`
    private static final LengthPercentageAuto LPA10 = LengthPercentageAuto.length(10.0f);
    public static final Style DEEP_AUTO =
            Style.DEFAULT.withFlexGrow(1.0f).withMargin(new Rect<>(LPA10, LPA10, LPA10, LPA10));

    /// `SuperDeepStyleGen` style (flexbox.rs): Row + grow 1 + margin 10
    public static final Style SUPER_DEEP =
            Style.DEFAULT.withFlexDirection(FlexDirection.ROW)
                    .withFlexGrow(1.0f)
                    .withMargin(new Rect<>(LPA10, LPA10, LPA10, LPA10));

    /// `RandomAbsoluteStyleGenerator::create_leaf_style` — abs pos, random insets
    public static Style absoluteLeaf(Rng rng) {
        return Style.DEFAULT.withPosition(Position.ABSOLUTE)
                .withInset(new Rect<>(inset(rng), inset(rng), inset(rng), inset(rng)))
                .withSize(new Size<>(dimension(rng), dimension(rng)));
    }

    /// `RandomAbsoluteStyleGenerator::create_container_style` — rel pos, random size
    public static Style absoluteContainer(Rng rng) {
        return Style.DEFAULT.withPosition(Position.RELATIVE)
                .withSize(new Size<>(dimension(rng), dimension(rng)));
    }
}
