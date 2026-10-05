package com.selluastar.fealty.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Tabs drawn along the top edge of a window. */
public class TabBar {
    public record Tab(Component label, String icon) {
    }

    private static final int HEIGHT = 20;
    private final List<Tab> tabs = new ArrayList<>();
    private final IntConsumer onSelect;
    private int selected;
    private int x;
    private int y;

    public TabBar(IntConsumer onSelect) {
        this.onSelect = onSelect;
    }

    public TabBar add(Component label, String icon) {
        tabs.add(new Tab(label, icon));
        return this;
    }

    public void clear() {
        tabs.clear();
    }

    public void setPosition(int x, int bottomY) {
        this.x = x;
        this.y = bottomY - HEIGHT + 4;
    }

    public int selected() {
        return selected;
    }

    public void select(int index) {
        selected = Math.max(0, Math.min(index, tabs.size() - 1));
    }

    private int tabWidth(Font font, Tab tab) {
        return font.width(tab.label()) + 30;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int tx = x;
        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            int w = tabWidth(font, tab);
            boolean active = i == selected;
            boolean hovered = mouseX >= tx && mouseX < tx + w && mouseY >= y && mouseY < y + HEIGHT;
            int ty = active ? y - 2 : y;
            Ui.sprite(g, active ? Ui.TAB_SELECTED : Ui.TAB, tx, ty, w, HEIGHT + (active ? 2 : 0));
            Ui.icon(g, tab.icon(), tx + 6, ty + 4, 12);
            int color = active ? Ui.INK : hovered ? Ui.GOLD_LIGHT : Ui.CREAM;
            g.drawString(font, tab.label(), tx + 22, ty + 7, color, !active);
            tx += w + 2;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int tx = x;
        for (int i = 0; i < tabs.size(); i++) {
            int w = tabWidth(font, tabs.get(i));
            if (mouseX >= tx && mouseX < tx + w && mouseY >= y - 2 && mouseY < y + HEIGHT) {
                if (i != selected) {
                    selected = i;
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                    onSelect.accept(i);
                }
                return true;
            }
            tx += w + 2;
        }
        return false;
    }
}
