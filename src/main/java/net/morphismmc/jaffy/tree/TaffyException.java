// JAFFY: port of upstream src/tree/taffy_tree.rs — `TaffyError` enum +
// `impl Display`, merged with the Java `Err`-throwing carrier
// (docs/porting/JAVA_API.md): upstream's `enum` variants map to nested
// `final` subclasses of this `sealed` exception — `TaffyError::
// ChildIndexOutOfBounds` -> `TaffyException.ChildIndexOutOfBounds`.
// `TaffyResult<T>` -> `T` + throws `TaffyException` on `Err`
// (user-sanctioned adaptation, DECISIONS.md).
//
// enum -> sealed abstract class of final subclasses (GAP-011; subclasses
// are plain classes — records cannot extend a class). `usize` -> int
// (GAP-014); `NodeId` Debug -> `NodeId(<u64>)` via {@link RustFmt};
// `impl Display` -> the exception message; derive(PartialEq) -> hand-
// written equals/hashCode; derive(Clone) has no counterpart (exceptions
// are not copied). `InvalidParentNode`/`InvalidChildNode`/
// `InvalidInputNode` are never constructed in upstream src/ — same here
// (definitions only).
package net.morphismmc.jaffy.tree;

import java.util.Objects;

import net.morphismmc.just.fmt.RustFmt;


/// An error that occurs while trying to access or modify a node's
/// children by index (`TaffyError`), thrown by {@link TaffyTree}
/// operations that return `Err` upstream.
public sealed abstract class TaffyException extends RuntimeException {

    private TaffyException(String message) {
        super(message);
    }

    /// The parent node does not have a child at `childIndex`. It only has
    /// `childCount` children
    public static final class ChildIndexOutOfBounds extends TaffyException {
        private final long parent;
        private final int childIndex;
        private final int childCount;

        public ChildIndexOutOfBounds(long parent, int childIndex, int childCount) {
            super("Index (is " + childIndex + ") should be < child_count (" + childCount
                    + ") for parent node " + RustFmt.nodeId(parent));
            this.parent = parent;
            this.childIndex = childIndex;
            this.childCount = childCount;
        }

        public long parent() {
            return parent;
        }

        public int childIndex() {
            return childIndex;
        }

        public int childCount() {
            return childCount;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ChildIndexOutOfBounds o
                    && parent == o.parent
                    && childIndex == o.childIndex
                    && childCount == o.childCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), parent, childIndex, childCount);
        }
    }

    /// The parent node was not found in the `TaffyTree` instance.
    public static final class InvalidParentNode extends TaffyException {
        private final long parent;

        public InvalidParentNode(long parent) {
            super("Parent Node " + RustFmt.nodeId(parent) + " is not in the TaffyTree instance");
            this.parent = parent;
        }

        public long parent() {
            return parent;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InvalidParentNode o && parent == o.parent;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), parent);
        }
    }

    /// The child node was not found in the `TaffyTree` instance.
    public static final class InvalidChildNode extends TaffyException {
        private final long child;

        public InvalidChildNode(long child) {
            super("Child Node " + RustFmt.nodeId(child) + " is not in the TaffyTree instance");
            this.child = child;
        }

        public long child() {
            return child;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InvalidChildNode o && child == o.child;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), child);
        }
    }

    /// The supplied node was not found in the `TaffyTree` instance.
    public static final class InvalidInputNode extends TaffyException {
        private final long node;

        public InvalidInputNode(long node) {
            super("Supplied Node " + RustFmt.nodeId(node) + " is not in the TaffyTree instance");
            this.node = node;
        }

        public long node() {
            return node;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InvalidInputNode o && node == o.node;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), node);
        }
    }
}
