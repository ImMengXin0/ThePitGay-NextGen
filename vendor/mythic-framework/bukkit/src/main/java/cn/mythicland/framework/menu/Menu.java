package cn.mythicland.framework.menu;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class Menu {
    private final int size;
    private final Map<Integer, AbstractButton> buttons;

    public Menu(int size) {
        if (size <= 0 || size % 9 != 0)
            throw new IllegalArgumentException("Menu size must be a positive multiple of 9");
        this.size = size;
        this.buttons = new HashMap<>(size);
    }

    public Menu setButton(int slot, AbstractButton button) {
        this.checkSlot(slot);
        this.buttons.put(slot, Objects.requireNonNull(button, "button"));
        return this;
    }

    public Menu fillButton(int startIndex, int endIndex, AbstractButton button) {
        Objects.requireNonNull(button, "button");
        if (startIndex > endIndex) throw new IllegalArgumentException("Start slot must be <= end slot");
        for (int slot = startIndex; slot <= endIndex; slot++) {
            this.setButton(slot, button);
        }
        return this;
    }

    public Menu fillBorderButton(AbstractButton button) {
        Objects.requireNonNull(button, "button");
        int rows = this.size / 9;
        for (int column = 0; column < 9; column++) {
            this.setButton(column, button);
            this.setButton(this.size - 9 + column, button);
        }
        for (int row = 1; row < rows - 1; row++) {
            this.setButton(row * 9, button);
            this.setButton(row * 9 + 8, button);
        }
        return this;
    }

    public boolean hasButton(int slot) {
        this.checkSlot(slot);
        return this.buttons.containsKey(slot);
    }

    public int getSize() {
        return this.size;
    }

    public Map<Integer, AbstractButton> getButtons() {
        return Collections.unmodifiableMap(this.buttons);
    }

    public AbstractButton getButton(int slot) {
        this.checkSlot(slot);
        return this.buttons.get(slot);
    }

    /**
     * Clears all button bindings from this menu instance.
     */
    public void clearButtons() {
        this.buttons.clear();
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= this.size) throw new IllegalArgumentException("Slot out of menu bounds: " + slot);
    }
}
