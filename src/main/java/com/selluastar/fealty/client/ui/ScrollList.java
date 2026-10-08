package com.selluastar.fealty.client.ui;

import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** A clipped, scrollable list of rows with a sprite scrollbar. The owning screen forwards mouse events. */
public class ScrollList<T> {
    @FunctionalInterface
    public interface RowRenderer<T> {
        void render(GuiGraphics g, T item, int index, int x, int y, int width, int height, boolean hovered, boolean selected);
    }

    private List<T> items = List.of();
    private int x;
    private int y;
    private int width;
    private int height;
    private final int rowHeight;
    private double scroll;
    private int selected = -1;
    private boolean dragging;

    public ScrollList(int rowHeight) {
        this.rowHeight = rowHeight;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setItems(List<T> items) {
        this.items = items;
        if (selected >= items.size()) {
            selected = items.isEmpty() ? -1 : items.size() - 1;
        }
        clampScroll();
    }

    public List<T> items() {
        return items;
    }

    public int selected() {
        return selected;
    }

    public T selectedItem() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    public void select(int index) {
        selected = index;
    }

    private int maxScroll() {
        return Math.max(0, items.size() * rowHeight - height);
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private boolean scrollable() {
        return maxScroll() > 0;
    }

    private int rowWidth() {
        return scrollable() ? width - 8 : width;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, RowRenderer<T> renderer) {
        clampScroll();
        g.enableScissor(x, y, x + width, y + height);
        int top = y - (int) scroll;
        for (int i = 0; i < items.size(); i++) {
            int rowY = top + i * rowHeight;
            if (rowY + rowHeight < y || rowY > y + height) {
                continue;
            }
            boolean hovered = isMouseOver(mouseX, mouseY) && mouseX < x + rowWidth() && mouseY >= rowY && mouseY < rowY + rowHeight;
            renderer.render(g, items.get(i), i, x, rowY, rowWidth(), rowHeight, hovered, i == selected);
        }
        g.disableScissor();
        if (scrollable()) {
            int trackX = x + width - 6;
            Ui.sprite(g, Ui.SCROLLER_TRACK, trackX, y, 6, height);
            int handle = Math.max(14, height * height / (items.size() * rowHeight));
            int handleY = y + (int) ((height - handle) * (scroll / maxScroll()));
            Ui.sprite(g, Ui.SCROLLER, trackX, handleY, 6, handle);
        }
    }

    /** Selects the clicked row and reports it. Returns true if the click was inside the list. */
    public boolean mouseClicked(double mouseX, double mouseY, IntConsumer onClick) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (scrollable() && mouseX >= x + width - 6) {
            dragging = true;
            return true;
        }
        int index = (int) ((mouseY - y + scroll) / rowHeight);
        if (index >= 0 && index < items.size()) {
            selected = index;
            onClick.accept(index);
        }
        return true;
    }

    public boolean mouseDragged(double mouseY) {
        if (!dragging) {
            return false;
        }
        double fraction = (mouseY - y) / height;
        scroll = fraction * maxScroll();
        clampScroll();
        return true;
    }

    public void mouseReleased() {
        dragging = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll -= scrollY * rowHeight;
        clampScroll();
        return true;
    }
}
