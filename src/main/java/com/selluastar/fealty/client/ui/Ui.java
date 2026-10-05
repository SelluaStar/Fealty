package com.selluastar.fealty.client.ui;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.selluastar.fealty.Fealty;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Fealty's GUI kit: sprite ids, colours and drawing helpers shared by every screen and HUD layer. */
public final class Ui {
    public static final ResourceLocation WINDOW = Fealty.id("window");
    public static final ResourceLocation PANEL = Fealty.id("panel");
    public static final ResourceLocation HUD_PANEL = Fealty.id("hud_panel");
    public static final ResourceLocation TOAST = Fealty.id("toast");
    public static final ResourceLocation BUTTON = Fealty.id("button");
    public static final ResourceLocation BUTTON_HIGHLIGHTED = Fealty.id("button_highlighted");
    public static final ResourceLocation BUTTON_DISABLED = Fealty.id("button_disabled");
    public static final ResourceLocation TAB = Fealty.id("tab");
    public static final ResourceLocation TAB_SELECTED = Fealty.id("tab_selected");
    public static final ResourceLocation SCROLLER = Fealty.id("scroller");
    public static final ResourceLocation SCROLLER_TRACK = Fealty.id("scroller_track");
    public static final ResourceLocation SLOT = Fealty.id("slot");
    public static final ResourceLocation DIVIDER = Fealty.id("divider");
    public static final ResourceLocation BANNER = Fealty.id("banner");
    public static final ResourceLocation BAR_BACKGROUND = Fealty.id("bar_background");
    public static final ResourceLocation BAR_FILL = Fealty.id("bar_fill");

    /** Text on parchment. */
    public static final int INK = 0xFF3B2A1A;
    /** Secondary text on parchment. */
    public static final int FADED = 0xFF6B5A44;
    /** Text on leather, wood and dark HUD panels. */
    public static final int CREAM = 0xFFF4EAD0;
    public static final int GOLD = 0xFFD4AF37;
    public static final int GOLD_LIGHT = 0xFFF2D675;
    public static final int GREEN = 0xFF2E7D32;
    public static final int LIGHT_GREEN = 0xFF81C784;
    public static final int RED = 0xFFB71C1C;
    public static final int LIGHT_RED = 0xFFE57373;

    private Ui() {
    }

    public static ResourceLocation icon(String name) {
        return Fealty.id("icon/" + name);
    }

    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        sprite(g, WINDOW, x, y, w, h);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        sprite(g, PANEL, x, y, w, h);
    }

    public static void hudPanel(GuiGraphics g, int x, int y, int w, int h) {
        sprite(g, HUD_PANEL, x, y, w, h);
    }

    public static void divider(GuiGraphics g, int x, int y, int w) {
        sprite(g, DIVIDER, x, y, w, 3);
    }

    public static void icon(GuiGraphics g, String name, int x, int y) {
        sprite(g, icon(name), x, y, 16, 16);
    }

    public static void icon(GuiGraphics g, String name, int x, int y, int size) {
        sprite(g, icon(name), x, y, size, size);
    }

    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blitSprite(sprite, x, y, w, h);
    }

    /** A sprite drawn with transparency, for fading HUD elements. */
    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, float alpha) {
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        sprite(g, sprite, x, y, w, h);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** A framed bar filled to {@code fraction} in {@code color}. */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, 0xFF1E120A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xC02A1C12);
        int fill = Mth.floor((w - 2) * Mth.clamp(fraction, 0.0F, 1.0F));
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, 0xFF000000 | color);
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, 0x40FFFFFF);
        }
    }

    public static void centered(GuiGraphics g, Font font, Component text, int centerX, int y, int color, boolean shadow) {
        g.drawString(font, text, centerX - font.width(text) / 2, y, color, shadow);
    }

    /** Draws text scaled around its top-left corner. */
    public static void scaled(GuiGraphics g, Font font, Component text, int x, int y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    public static void scaledCentered(GuiGraphics g, Font font, Component text, int centerX, int y, float scale, int color, boolean shadow) {
        int w = Mth.ceil(font.width(text) * scale);
        scaled(g, font, text, centerX - w / 2, y, scale, color, shadow);
    }

    /** Draws wrapped text and returns the height used. Stops at {@code maxLines} if positive. */
    public static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int count = maxLines > 0 ? Math.min(maxLines, lines.size()) : lines.size();
        for (int i = 0; i < count; i++) {
            g.drawString(font, lines.get(i), x, y + i * (font.lineHeight + 1), color, false);
        }
        return count * (font.lineHeight + 1);
    }

    /** Cuts text to fit a width, ending with an ellipsis. */
    public static Component fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        String cut = font.plainSubstrByWidth(text.getString(), width - font.width(ellipsis));
        return Component.literal(cut + ellipsis).withStyle(text.getStyle());
    }

    public static int withAlpha(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 4, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
