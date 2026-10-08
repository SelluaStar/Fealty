package com.selluastar.fealty.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A parchment toast with an icon or item, a title and one line of detail. */
public class FealtyToast implements Toast {
    private static final long DURATION = 5000L;
    private final String icon;
    private final ItemStack item;
    private final Component title;
    private final Component detail;

    public FealtyToast(String icon, ItemStack item, Component title, Component detail) {
        this.icon = icon == null || icon.isEmpty() ? "scroll" : icon;
        this.item = item;
        this.title = title;
        this.detail = detail;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent component, long timeSinceLastVisible) {
        Font font = Minecraft.getInstance().font;
        Ui.sprite(g, Ui.TOAST, 0, 0, width(), height());
        if (!item.isEmpty()) {
            g.renderFakeItem(item, 8, 8);
        } else {
            Ui.icon(g, icon, 8, 8);
        }
        g.drawString(font, Ui.fit(font, title, width() - 36), 30, 7, Ui.INK, false);
        g.drawString(font, Ui.fit(font, detail, width() - 36), 30, 18, Ui.FADED, false);
        return timeSinceLastVisible >= DURATION * component.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
