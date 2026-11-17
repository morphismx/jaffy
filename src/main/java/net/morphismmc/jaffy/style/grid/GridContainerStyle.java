// JAFFY-UPSTREAM: src/style/grid.rs
// JAFFY-NOTE: `GridContainerStyle` trait (`grid`)
// The GATs are concretised (GAP-017): `CustomIdent = String`,
// `Repetition = GridTemplateRepetition`, the iterator GATs are `List`s, and
// `Option<…List>` is a nullable `List` (GAP-001). `impl ... for &T` has no
// Java counterpart.
package net.morphismmc.jaffy.style.grid;

import java.util.List;

import net.morphismmc.jaffy.geometry.AbsoluteAxis;
import net.morphismmc.jaffy.geometry.AbstractAxis;
import net.morphismmc.jaffy.geometry.MinMax;
import net.morphismmc.jaffy.geometry.Size;
import net.morphismmc.jaffy.style.CoreStyle;
import net.morphismmc.jaffy.style.Style;
import net.morphismmc.jaffy.style.alignment.AlignContent;
import net.morphismmc.jaffy.style.alignment.AlignContentKeyword;
import net.morphismmc.jaffy.style.alignment.AlignItems;
import net.morphismmc.jaffy.style.dimension.LengthPercentage;

import org.jspecify.annotations.Nullable;

/// The set of styles required for a CSS Grid container
public interface GridContainerStyle extends CoreStyle {

    /// Defines the track sizing functions (heights) of the grid rows.
    /// `Option<TemplateTrackList>` -> nullable list. No upstream default.
    @Nullable
    List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateRows();

    /// Defines the track sizing functions (widths) of the grid columns.
    /// `Option<TemplateTrackList>` -> nullable list. No upstream default.
    @Nullable
    List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateColumns();

    /// Defines the size of implicitly created rows. No upstream default.
    List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoRows();

    /// Defined the size of implicitly created columns. No upstream default.
    List<MinMax<MinTrackSizingFunction, MaxTrackSizingFunction>> gridAutoColumns();

    /// Named grid areas. `Option<GridTemplateAreas<'a>>` (`IntoIterator<Item =
    /// GridTemplateArea>`) -> nullable `Iterable`. No upstream default.
    @Nullable
    Iterable<GridTemplateArea> gridTemplateAreas();

    /// The number of rows in the `grid-template-areas` template (0 if there is no template).
    /// May be greater than the extent of the named areas due to unnamed (`.`) cells.
    default int gridTemplateAreaRowCount() {
        var areas = gridTemplateAreas();
        if (areas == null) {
            return 0;
        }
        int max = 0;
        for (var area : areas) {
            int rowEnd = area.rowEnd() > 1 ? area.rowEnd() : 1;
            int v = rowEnd - 1;
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /// The number of columns in the `grid-template-areas` template (0 if there is no template).
    /// May be greater than the extent of the named areas due to unnamed (`.`) cells.
    default int gridTemplateAreaColumnCount() {
        var areas = gridTemplateAreas();
        if (areas == null) {
            return 0;
        }
        int max = 0;
        for (var area : areas) {
            int columnEnd = area.columnEnd() > 1 ? area.columnEnd() : 1;
            int v = columnEnd - 1;
            if (v > max) {
                max = v;
            }
        }
        return max;
    }

    /// Defines the line names for column lines (`Option<TemplateLineNames>` -> nullable list).
    /// No upstream default.
    @Nullable
    List<List<String>> gridTemplateColumnNames();

    /// Defines the line names for row lines (`Option<TemplateLineNames>` -> nullable list).
    /// No upstream default.
    @Nullable
    List<List<String>> gridTemplateRowNames();

    /// Controls how items get placed into the grid for auto-placed items
    default GridAutoFlow gridAutoFlow() {
        return Style.DEFAULT.gridAutoFlow();
    }

    /// How large should the gaps between items in a grid or flex container be?
    default Size<LengthPercentage> gap() {
        return Style.DEFAULT.gap();
    }

    // Alignment properties

    /// How should content contained within this item be aligned in the cross/block axis
    default AlignContent alignContent() {
        return Style.DEFAULT.alignContent();
    }

    /// How should contained within this item be aligned in the main/inline axis
    default AlignContent justifyContent() {
        return Style.DEFAULT.justifyContent();
    }

    /// How this node's children aligned in the cross/block axis?
    default AlignItems alignItems() {
        return Style.DEFAULT.alignItems();
    }

    /// How this node's children should be aligned in the inline axis
    default AlignItems justifyItems() {
        return Style.DEFAULT.justifyItems();
    }

    /// Get a grid item's row or column placement depending on the axis passed
    default @Nullable List<GenericGridTemplateComponent<GridTemplateRepetition>> gridTemplateTracks(AbsoluteAxis axis) {
        return switch (axis) {
            case HORIZONTAL -> gridTemplateColumns();
            case VERTICAL -> gridTemplateRows();
        };
    }

    /// Get a grid container's align-content or justify-content alignment depending on the axis passed
    /// (with `normal` resolved to `stretch`)
    default AlignContent gridAlignContent(AbstractAxis axis) {
        var alignment = switch (axis) {
            case INLINE -> justifyContent();
            case BLOCK -> alignContent();
        };
        return alignment.keyword() == AlignContentKeyword.NORMAL ? AlignContent.STRETCH : alignment;
    }
}
