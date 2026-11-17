// JAFFY: upstream/src/util/front_back_vec_builder.rs -> net.morphismmc.jaffy.util.FrontBackVecBuilder
//
// Rust item correspondence:
//   struct FrontBackVecBuilder<T>   -> FrontBackVecBuilder<T>
//   fn with_capacity                -> withCapacity
//   fn push_front                   -> pushFront
//   fn push_back                    -> pushBack
//   fn into_vec -> (Vec<T>, usize)  -> intoVec -> IntoVec<T>
//
// The Rust original writes into the spare capacity of a `Vec` whose length stays
// zero until `into_vec`; here a null-filled `ArrayList` of `capacity` plays that
// role. Observable behavior is identical: writes past `capacity` throw
// (Rust bounds-check panic -> IndexOutOfBoundsException, GAP-010), and writes
// into an occupied slot overwrite it just like the Rust comment describes.
package net.morphismmc.jaffy.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FrontBackVecBuilder<T> {

    /// `into_vec`'s `(Vec<T>, usize)` tuple.
    public record IntoVec<T>(List<T> vec, int frontLen) {}

    /// The underlying storage. Its length is kept at zero until `intoVec` is called,
    /// and its allocated capacity is at least `capacity`.
    private final List<T> vec;
    /// The number of items pushed to the front. The slots `0..frontLen` are initialized.
    private int frontLen;
    /// The number of items pushed to the back. The slots `(capacity - backLen)..capacity` are initialized.
    private int backLen;

    /// `with_capacity`
    private FrontBackVecBuilder(int capacity) {
        this.vec = new ArrayList<>(Collections.nCopies(capacity, (T) null));
    }

    /// `with_capacity`
    public static <T> FrontBackVecBuilder<T> withCapacity(int capacity) {
        return new FrontBackVecBuilder<>(capacity);
    }

    /// `push_front` — pushing more than `capacity` items is not checked here: it either
    /// throws or overwrites a previously pushed item, then causes `intoVec` to throw.
    public void pushFront(T value) {
        vec.set(frontLen, value);
        frontLen += 1;
    }

    /// `push_back`
    public void pushBack(T value) {
        vec.set(vec.size() - backLen - 1, value);
        backLen += 1;
    }

    /// `into_vec` — throws `AssertionError` unless exactly `capacity` items were pushed
    /// (GAP-010: `assert_eq!` -> `AssertionError`; the std-generated framing around the
    /// custom message is not reproduced).
    public IntoVec<T> intoVec() {
        if (frontLen + backLen != vec.size()) {
            throw new AssertionError("the number of items pushed to a FrontBackVecBuilder must equal its capacity");
        }
        return new IntoVec<>(vec, frontLen);
    }
}
