package com.selluastar.fealty.client.hud;

import com.selluastar.fealty.client.ui.Ui;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** An envelope in the top-left corner while the player has letters they have not read. */
public final class MailLayer {
    private static int unread;
    private static long changedAt;

    private MailLayer() {
    }

    public static void setUnread(int count) {
        if (count > unread) {
            changedAt = Util.getMillis();
        }
        unread = count;
    }

    /** Letters waiting for the player (mailbox flags go up while there are any). */
    public static int unread() {
        return unread;
    }

    public static void clear() {
        unread = 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (unread <= 0 || minecraft.options.hideGui || minecraft.player == null || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        long age = Util.getMillis() - changedAt;
        int bob = age < 2000 ? Math.round(Mth.sin(age / 120.0F) * 2.0F * (1.0F - age / 2000.0F)) : 0;
        Component text = Component.translatable(unread == 1 ? "fealty.mail.hud.one" : "fealty.mail.hud.many", unread);
        int w = minecraft.font.width(text) + 26;
        Ui.hudPanel(g, 4, 4, w, 18);
        Ui.icon(g, "mail", 8, 7 + bob, 12);
        g.drawString(minecraft.font, text, 23, 9, Ui.GOLD_LIGHT, true);
    }
}
