package dev.berikai.BitwigTheme.UI;

import java.util.Stack;

class ColorAction {
    String colorName;
    String oldValue;
    String newValue;

    ColorAction(String name, String oldValue, String newValue) {
        this.colorName = name;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public String getColorName() {
        return colorName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    @Override
    public String toString() {
        return colorName + ": " + oldValue + " → " + newValue;
    }
}

class UndoRedoManager {
    private final Stack<ColorAction> undoStack = new Stack<>();
    private final Stack<ColorAction> redoStack = new Stack<>();
    private final int maxHistorySize;

    public UndoRedoManager(int maxHistorySize) {
        this.maxHistorySize = maxHistorySize;
    }

    public void addAction(ColorAction action) {
        undoStack.push(action);
        redoStack.clear(); // once a new action is made, redo history is cleared

        if (undoStack.size() > maxHistorySize)
            undoStack.remove(0); // remove oldest
    }

    public ColorAction undo() {
        if (undoStack.isEmpty()) return null;
        ColorAction action = undoStack.pop();
        redoStack.push(action);
        return action;
    }

    public ColorAction redo() {
        if (redoStack.isEmpty()) return null;
        ColorAction action = redoStack.pop();
        undoStack.push(action);
        return action;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void printHistory() {
        System.out.println("Undo Stack: " + undoStack);
        System.out.println("Redo Stack: " + redoStack);
    }
}
