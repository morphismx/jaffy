// JAFFY: upstream/src/util/debug.rs -> net.morphismmc.jaffy.util.DebugLogger
// Ported at L0 per WORKFLOW.md §2 cycle note (only the Features wiring remains L9).
//
// Rust item correspondence:
//   struct DebugLogger { stack: Mutex<Vec<String>> }  -> DebugLogger, List<String> guarded by synchronized
//   static EMPTY_STRING                                -> EMPTY_STRING
//   const fn new                                       -> constructor
//   fn push_node                                       -> pushNode (NodeId = long, GAP-015)
//   fn pop_node                                        -> popNode (empty-stack pop is a no-op, like Vec::pop)
//   fn log / labelled_log / debug_log / labelled_debug_log -> log / labelledLog / debugLog / labelledDebugLog
//   static NODE_LOGGER                                 -> NODE_LOGGER (shared singleton, not thread-local — GAP-007)
//   macro debug_log!                                   -> call sites write `if (Features.DEBUG) NODE_LOGGER.<m>(...)`
//   macro debug_log_node!                              -> debugLogNode — TODO: added when LayoutInput lands (L3)
//   macro debug_push_node!                             -> debugPushNode
//   macro debug_pop_node!                              -> debugPopNode
//   macro time!                                        -> time
//
// `impl Display`/`impl Debug` message parameters arrive pre-formatted as String
// (callers produce Rust-identical text via toString()/RustFmt — GAP-016).
// The feature check lives at the call site for single-call macros (GAP-007); the
// multi-call macros below carry their own cfg checks, matching the Rust expansion.
package net.morphismmc.jaffy.util;

import java.util.ArrayList;
import java.util.List;
import net.morphismmc.jaffy.Features;

public final class DebugLogger {
    private static final String EMPTY_STRING = "";

    private final List<String> stack = new ArrayList<>();

    private DebugLogger() {}

    public static final DebugLogger NODE_LOGGER = new DebugLogger();

    /// `push_node`
    public void pushNode(long nodeId) {
        synchronized (stack) {
            String keyString = "NodeId(" + Long.toUnsignedString(nodeId) + ")"; // JAFFY(GAP-016)
            stack.add(keyString);
        }
    }

    /// `pop_node`
    public void popNode() {
        synchronized (stack) {
            if (!stack.isEmpty()) {
                stack.remove(stack.size() - 1);
            }
        }
    }

    /// `log` — `{space:level$}{key}: {message}` with `level = stack.len() * 4` and `\n`.
    public void log(String message) {
        synchronized (stack) {
            String key = stack.isEmpty() ? EMPTY_STRING : stack.get(stack.size() - 1);
            int level = stack.size() * 4;
            System.out.print(" ".repeat(level) + key + ": " + message + "\n"); // JAFFY(GAP-016)
        }
    }

    /// `labelled_log`
    public void labelledLog(String label, String message) {
        synchronized (stack) {
            String key = stack.isEmpty() ? EMPTY_STRING : stack.get(stack.size() - 1);
            int level = stack.size() * 4;
            System.out.print(" ".repeat(level) + key + ": " + label + " " + message + "\n"); // JAFFY(GAP-016)
        }
    }

    /// `debug_log` — identical framing to `log`; the message is the caller's `{:?}` output.
    public void debugLog(String message) {
        synchronized (stack) {
            String key = stack.isEmpty() ? EMPTY_STRING : stack.get(stack.size() - 1);
            int level = stack.size() * 4;
            System.out.print(" ".repeat(level) + key + ": " + message + "\n"); // JAFFY(GAP-016)
        }
    }

    /// `labelled_debug_log`
    public void labelledDebugLog(String label, String message) {
        synchronized (stack) {
            String key = stack.isEmpty() ? EMPTY_STRING : stack.get(stack.size() - 1);
            int level = stack.size() * 4;
            System.out.print(" ".repeat(level) + key + ": " + label + " " + message + "\n"); // JAFFY(GAP-016)
        }
    }

    /// `debug_push_node!` — push under (debug || profile), then the empty-label log under debug.
    public void debugPushNode(long nodeId) {
        if (Features.DEBUG || Features.PROFILE) {
            pushNode(nodeId);
        }
        if (Features.DEBUG) {
            log("");
        }
    }

    /// `debug_pop_node!`
    public void debugPopNode() {
        if (Features.DEBUG || Features.PROFILE) {
            popNode();
        }
    }

    /// `time!` — times `code` and logs under profile; otherwise runs `code` unmeasured.
    /// `time!` currently has no call sites upstream (1abd8eea).
    public void time(String label, Runnable code) {
        if (Features.PROFILE) {
            long start = System.nanoTime();
            code.run();
            long duration = System.nanoTime() - start;
            log("Performed " + label + " in " + duration / 1_000_000 + "ms");
        } else {
            code.run();
        }
    }
}
