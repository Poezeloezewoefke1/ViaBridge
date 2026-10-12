package dev.texturemixer.core.selection;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo stacks of {@link Selections} snapshots. Snapshots share nothing mutable, so
 * storing them is safe; the stack depth is capped to bound memory.
 */
public final class SelectionHistory {
    private static final int MAX_DEPTH = 200;

    private final Deque<Selections> undo = new ArrayDeque<>();
    private final Deque<Selections> redo = new ArrayDeque<>();

    /** Records {@code before} as the state to return to; clears the redo stack. */
    public void record(Selections before) {
        undo.push(before);
        while (undo.size() > MAX_DEPTH) {
            undo.removeLast();
        }
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Returns the state to restore, given the current one, or {@code null} if nothing to undo. */
    public Selections undo(Selections current) {
        if (undo.isEmpty()) {
            return null;
        }
        redo.push(current);
        return undo.pop();
    }

    public Selections redo(Selections current) {
        if (redo.isEmpty()) {
            return null;
        }
        undo.push(current);
        return redo.pop();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
