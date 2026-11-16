// JAFFY: port of upstream src/style/grid.rs — `GridTemplateAreas<CustomIdent>`.
// `CustomIdent: CheapCloneStr` -> `String` (GAP-017); `GridTrackVec` = `Vec`
// -> `List`; `u16` -> `int` (GAP-014). Implements `Iterable<GridTemplateArea>`
// so it can satisfy `GridContainerStyle#gridTemplateAreas` (the upstream
// `GridTemplateAreas<'a>: IntoIterator<Item = GridTemplateArea>` GAT).
package net.morphismmc.jaffy.style.grid;

import java.util.Iterator;
import java.util.List;

/// Defines the value of the `grid-template-areas` property: the named areas plus the overall
/// size (in tracks) of the area template.
///
/// The template may be larger than the extents of the named areas due to unnamed (`.`) cells,
/// so the size is stored explicitly.
public record GridTemplateAreas(List<GridTemplateArea> areas, int rowCount, int columnCount)
        implements Iterable<GridTemplateArea> {

    @Override
    public Iterator<GridTemplateArea> iterator() {
        return areas.iterator();
    }
}
