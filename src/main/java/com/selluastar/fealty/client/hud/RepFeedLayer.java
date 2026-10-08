package com.selluastar.fealty.client.hud;

import java.util.List;

import com.selluastar.fealty.client.ui.Ui;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Reputation changes as short lines that slide in on the right of the screen and fade away. */
public final class RepFeedLayer {
    private RepFeedLayer() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        List<ClientFeedback.FeedLine> feed = ClientFeedback.feed();
        if (feed.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        int right = g.guiWidth() - 6;
        int y = g.guiHeight() / 3;
        long now = Util.getMillis();
        for (int i = feed.size() - 1; i >= 0; i--) {
            ClientFeedback.FeedLine line = feed.get(i);
            long age = now - line.start;
            float in = Mth.clamp(age / 220.0F, 0.0F, 1.0F);
            float out = Mth.clamp((ClientFeedback.FEED_LIFETIME - age) / 700.0F, 0.0F, 1.0F);
            float alpha = Math.min(in, out);
            int slide = (int) ((1.0F - in) * 40);
            Component amount = Component.literal((line.amount > 0 ? "+" : "") + line.amount);
            int amountColor = line.amount > 0 ? Ui.LIGHT_GREEN : Ui.LIGHT_RED;
            int w = font.width(amount) + 4 + font.width(line.faction) + 26;
            int x = right - w + slide;
            Ui.sprite(g, Ui.HUD_PANEL, x, y, w, 16, alpha);
            Ui.sprite(g, Ui.icon(line.amount > 0 ? "heart" : "skull"), x + 4, y + 3, 10, 10, alpha);
            g.drawString(font, amount, x + 18, y + 4, Ui.withAlpha(amountColor, alpha), true);
            int nameColor = line.color != 0 ? line.color : 0xF4EAD0;
            g.drawString(font, line.faction, x + 22 + font.width(amount), y + 4, Ui.withAlpha(nameColor, alpha), true);
            y += 18;
        }
    }
}
