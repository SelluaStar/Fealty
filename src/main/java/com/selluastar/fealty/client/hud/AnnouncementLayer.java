package com.selluastar.fealty.client.hud;

import com.selluastar.fealty.client.ui.Ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/** A ribbon banner across the upper middle of the screen for tier changes, completed quests and lordship. */
public final class AnnouncementLayer {
    private AnnouncementLayer() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        ClientFeedback.Banner banner = ClientFeedback.banner();
        if (banner == null) {
            return;
        }
        long age = ClientFeedback.bannerAge();
        float in = Mth.clamp(age / 350.0F, 0.0F, 1.0F);
        float out = Mth.clamp((ClientFeedback.BANNER_LIFETIME - age) / 600.0F, 0.0F, 1.0F);
        float alpha = Math.min(in, out);
        float grow = 0.6F + 0.4F * Mth.sin(in * Mth.HALF_PI);

        Font font = minecraft.font;
        int centerX = g.guiWidth() / 2;
        int y = g.guiHeight() / 4 - 10;
        float titleScale = 1.6F;
        int titleWidth = (int) (font.width(banner.title()) * titleScale);
        int detailWidth = font.width(banner.detail());
        int w = (int) (Math.max(titleWidth + 56, Math.max(detailWidth + 40, 180)) * grow);
        Ui.sprite(g, Ui.BANNER, centerX - w / 2, y, w, 30, alpha);
        if (!banner.icon().isEmpty()) {
            Ui.sprite(g, Ui.icon(banner.icon()), centerX - titleWidth / 2 - 22, y + 7, 16, 16, alpha);
        }
        int titleColor = banner.color() != 0 ? banner.color() : 0xF2D675;
        Ui.scaledCentered(g, font, banner.title(), centerX, y + 9, titleScale, Ui.withAlpha(titleColor, alpha), true);
        if (!banner.detail().getString().isEmpty()) {
            Ui.centered(g, font, banner.detail(), centerX, y + 34, Ui.withAlpha(0xF4EAD0, alpha), true);
        }
    }
}
