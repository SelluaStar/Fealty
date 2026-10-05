package com.selluastar.fealty.client.screen;

import java.util.List;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.client.ClientRepCache;
import com.selluastar.fealty.network.Standing;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** The Fealty Ledger: Renown and every faction the player has met. */
public class LedgerScreen extends Screen {
    private static final ResourceLocation TEXTURE = Fealty.id("textures/gui/ledger.png");
    private static final int WIDTH = 248;
    private static final int HEIGHT = 200;
    private static final int ROW = 24;
    private static final int LIST_TOP = 52;
    private static final int LIST_HEIGHT = 136;

    private int left;
    private int top;
    private double scroll;

    public LedgerScreen() {
        super(Component.translatable("fealty.ledger.title"));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(TEXTURE, left, top, 0, 0, WIDTH, HEIGHT, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int ink = 0x3B2A1A;
        graphics.drawString(font, title, left + (WIDTH - font.width(title)) / 2, top + 10, ink, false);

        int renown = ClientRepCache.renown();
        RepTier renownTier = ClientRepCache.tierFor(renown);
        Component renownLine = Component.translatable("fealty.ledger.renown", renown,
                renownTier.displayName().copy().withColor(renownTier.color()));
        graphics.drawString(font, renownLine, left + 14, top + 28, ink, false);
        drawBar(graphics, left + 14, top + 40, WIDTH - 28, renown, renownTier);

        List<Standing> standings = ClientRepCache.sortedStandings();
        if (standings.isEmpty()) {
            Component none = Component.translatable("fealty.ledger.empty");
            graphics.drawWordWrap(font, none, left + 14, top + LIST_TOP + 8, WIDTH - 28, 0x6B5A44);
            return;
        }
        int maxScroll = Math.max(0, standings.size() * ROW - LIST_HEIGHT);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        graphics.enableScissor(left + 8, top + LIST_TOP, left + WIDTH - 8, top + LIST_TOP + LIST_HEIGHT);
        int y = top + LIST_TOP - (int) scroll;
        for (Standing standing : standings) {
            if (y + ROW >= top + LIST_TOP && y <= top + LIST_TOP + LIST_HEIGHT) {
                drawRow(graphics, standing, left + 14, y);
            }
            y += ROW;
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int barHeight = Math.max(12, LIST_HEIGHT * LIST_HEIGHT / (standings.size() * ROW));
            int barY = top + LIST_TOP + (int) ((LIST_HEIGHT - barHeight) * (scroll / maxScroll));
            graphics.fill(left + WIDTH - 9, barY, left + WIDTH - 7, barY + barHeight, 0x806B5A44);
        }
    }

    private void drawRow(GuiGraphics graphics, Standing standing, int x, int y) {
        RepTier tier = ClientRepCache.tierFor(standing.rep());
        Component name = standing.lord()
                ? Component.translatable("fealty.ledger.lord_of", standing.name())
                : standing.name();
        graphics.drawString(font, name, x, y + 2, 0x3B2A1A, false);
        Component tierText = Component.literal(tier.displayName().getString() + " (" + standing.rep() + ")").withColor(tier.color());
        graphics.drawString(font, tierText, x + WIDTH - 28 - font.width(tierText), y + 2, tier.color(), false);
        drawBar(graphics, x, y + 13, WIDTH - 28, standing.rep(), tier);
    }

    /** A bar from the lowest to the highest tier with a mark at zero and a fill to the current value. */
    private void drawBar(GuiGraphics graphics, int x, int y, int w, int rep, RepTier tier) {
        List<RepTier> tiers = ClientRepCache.tiers();
        int min = tiers.getFirst().min();
        int max = tiers.getLast().max();
        graphics.fill(x, y, x + w, y + 4, 0x40000000);
        int zero = x + (int) ((0 - min) / (double) (max - min) * w);
        int pos = x + (int) ((Mth.clamp(rep, min, max) - min) / (double) (max - min) * w);
        graphics.fill(Math.min(zero, pos), y, Math.max(zero, pos) + 1, y + 4, 0xFF000000 | tier.color());
        graphics.fill(zero, y - 1, zero + 1, y + 5, 0xFF3B2A1A);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW;
        return true;
    }
}
