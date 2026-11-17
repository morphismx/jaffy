// JAFFY: port of upstream src/util/print.rs.
//
// `impl io::Write` -> `Appendable` (StringBuilder/Writer/PrintStream);
// `io::Result<()>` -> `throws IOException`; `writeln!` -> append + '\n';
// `{x:<4}` (left-align, min width 4) -> {@link #pad4}; `{key:?}` (NodeId
// Debug) -> `NodeId(<u64>)`; `content_size` cfg always on (GAP-007).
package net.morphismmc.jaffy.util;

import java.io.IOException;

import net.morphismmc.jaffy.geometry.PointF32;
import net.morphismmc.jaffy.geometry.RectF32;
import net.morphismmc.jaffy.tree.layout.Layout;
import net.morphismmc.just.fmt.RustFmt;

/// Contains functions for printing a debug representation of the tree
public final class PrintTree {
    private PrintTree() {}

    /// Prints a debug representation of the computed layout for a tree of
    /// nodes, starting with the passed root node (`print_tree`).
    public static <Tree extends net.morphismmc.jaffy.tree.traits.PrintTree> void printTree(Tree tree, long root) {
        // Buffer into a string so that output can be captured in tests
        StringBuilder buffer = new StringBuilder();
        try {
            writeTree(buffer, tree, root);
        } catch (IOException e) {
            // `expect("Wrote tree debug representation to Stdout")` — a
            // StringBuilder never fails
            throw new IllegalStateException("Wrote tree debug representation to Stdout", e); // JAFFY(GAP-010)
        }
        System.out.println(buffer); // JAFFY(GAP-016)
    }

    /// Writes a debug representation of the computed layout to the writer,
    /// starting with the passed root node (`write_tree`).
    public static <Tree extends net.morphismmc.jaffy.tree.traits.PrintTree> void writeTree(
            Appendable writer, Tree tree, long root) throws IOException {
        writer.append("TREE\n");
        writeNode(writer, tree, root, false, "");
    }

    /// Recursive function that writes each node in the tree
    private static void writeNode(
            Appendable writer,
            net.morphismmc.jaffy.tree.traits.PrintTree tree,
            long nodeId,
            boolean hasSibling,
            String linesString)
            throws IOException {
        Layout layout = tree.getFinalLayout(nodeId);
        String display = tree.getDebugLabel(nodeId);
        int numChildren = tree.childCount(nodeId);

        String forkString = hasSibling ? "├── " : "└── ";
        PointF32 location = layout.location();
        RectF32 border = layout.border();
        RectF32 padding = layout.padding();
        writer.append(linesString)
                .append(forkString)
                .append(' ')
                .append(display)
                .append(" [x: ")
                .append(pad4(location.x()))
                .append(" y: ")
                .append(pad4(location.y()))
                .append(" w: ")
                .append(pad4(layout.size().width()))
                .append(" h: ")
                .append(pad4(layout.size().height()))
                .append(" overflow: l:")
                .append(RustFmt.display(layout.scrollableOverflowRect().left()))
                .append(" r:")
                .append(RustFmt.display(layout.scrollableOverflowRect().right()))
                .append(" t:")
                .append(RustFmt.display(layout.scrollableOverflowRect().top()))
                .append(" b:")
                .append(RustFmt.display(layout.scrollableOverflowRect().bottom()))
                .append(", border: l:")
                .append(RustFmt.display(border.left()))
                .append(" r:")
                .append(RustFmt.display(border.right()))
                .append(" t:")
                .append(RustFmt.display(border.top()))
                .append(" b:")
                .append(RustFmt.display(border.bottom()))
                .append(", padding: l:")
                .append(RustFmt.display(padding.left()))
                .append(" r:")
                .append(RustFmt.display(padding.right()))
                .append(" t:")
                .append(RustFmt.display(padding.top()))
                .append(" b:")
                .append(RustFmt.display(padding.bottom()))
                .append("] (")
                .append(RustFmt.nodeId(nodeId))
                .append(")\n");

        String bar = hasSibling ? "│   " : "    ";
        String newString = linesString + bar;

        // Recurse into children
        var childIds = tree.childIds(nodeId);
        int index = 0;
        while (childIds.hasNext()) {
            long child = childIds.nextLong();
            writeNode(writer, tree, child, index < numChildren - 1, newString);
            index++;
        }
    }

    /// Rust `{x:<4}` — `Display` for `f32` padded to a minimum width of 4,
    /// left aligned
    private static String pad4(float value) {
        String s = RustFmt.display(value);
        return s.length() >= 4 ? s : s + " ".repeat(4 - s.length());
    }
}
