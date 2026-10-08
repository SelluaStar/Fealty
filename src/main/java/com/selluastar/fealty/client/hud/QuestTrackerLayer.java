package com.selluastar.fealty.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.math.Axis;
import com.selluastar.fealty.client.ClientQuestCache;
import com.selluastar.fealty.client.FealtyKeys;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.config.FealtyClientConfig;
import com.selluastar.fealty.config.FealtyClientConfig.TrackerMode;
import com.selluastar.fealty.network.QuestView;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The quest tracker in the top-left corner: tracked quests with their objectives, progress, the way to the
 * next waypoint and any time left. The tracker key cycles it between expanded, compact and hidden.
 */
public final class QuestTrackerLayer {
    private static final int WIDTH = 170;
    private static final int PAD = 5;

    private QuestTrackerLayer() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        TrackerMode mode = FealtyClientConfig.TRACKER_MODE.get();
        if (mode == TrackerMode.HIDDEN || minecraft.options.hideGui || minecraft.player == null
                || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        List<QuestView> tracked = ClientQuestCache.tracked();
        if (tracked.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        Player player = minecraft.player;
        int max = FealtyClientConfig.TRACKER_MAX_QUESTS.get();
        int x = FealtyClientConfig.TRACKER_X.get();
        int y = FealtyClientConfig.TRACKER_Y.get();
        boolean compact = mode == TrackerMode.COMPACT;

        List<Block> blocks = new ArrayList<>();
        int height = PAD + 12;
        for (int i = 0; i < Math.min(max, tracked.size()); i++) {
            Block block = layout(font, tracked.get(i), compact);
            blocks.add(block);
            height += block.height;
        }
        int more = tracked.size() - blocks.size();
        if (more > 0) {
            height += 10;
        }
        height += PAD - 2;

        Ui.hudPanel(g, x, y, WIDTH, height);
        Component header = Component.translatable("fealty.tracker.header", ClientQuestCache.quests().size());
        Ui.icon(g, "scroll", x + PAD, y + PAD - 1, 10);
        g.drawString(font, header, x + PAD + 13, y + PAD, Ui.GOLD, true);
        Component hint = Component.literal("[" + FealtyKeys.TOGGLE_TRACKER.getTranslatedKeyMessage().getString() + "]");
        g.drawString(font, hint, x + WIDTH - PAD - font.width(hint), y + PAD, 0xFF8A7E6E, false);

        int cy = y + PAD + 12;
        for (Block block : blocks) {
            drawBlock(g, font, player, block, x + PAD, cy, compact);
            cy += block.height;
        }
        if (more > 0) {
            g.drawString(font, Component.translatable("fealty.tracker.more", more), x + PAD, cy, 0xFF8A7E6E, false);
        }
    }

    private record Block(QuestView quest, List<FormattedCharSequence> title, List<List<FormattedCharSequence>> lines, int height) {
    }

    private static Block layout(Font font, QuestView quest, boolean compact) {
        int inner = WIDTH - PAD * 2 - 12;
        List<FormattedCharSequence> title = font.split(quest.title(), inner);
        if (title.size() > 2) {
            title = title.subList(0, 2);
        }
        int h = title.size() * 10 + 2;
        List<List<FormattedCharSequence>> lines = new ArrayList<>();
        if (!compact) {
            if (quest.ready()) {
                lines.add(font.split(Component.translatable("fealty.tracker.return", quest.giver()), inner - 10));
            } else {
                for (QuestView.Line line : quest.lines()) {
                    lines.add(font.split(lineText(line), inner - 10));
                }
            }
            for (List<FormattedCharSequence> l : lines) {
                h += Math.min(2, l.size()) * 9;
            }
            if (quest.waypoint().isPresent() || quest.expiresAt() > 0) {
                h += 10;
            }
        }
        return new Block(quest, title, lines, h + 3);
    }

    private static Component lineText(QuestView.Line line) {
        String text = line.text().getString();
        if (line.need() > 0) {
            text = text + "  " + line.have() + "/" + line.need();
        }
        return Component.literal(text);
    }

    private static void drawBlock(GuiGraphics g, Font font, Player player, Block block, int x, int y, boolean compact) {
        QuestView quest = block.quest();
        Ui.icon(g, quest.ready() ? "ready" : "quest", x - 1, y, 10);
        int ty = y;
        for (FormattedCharSequence line : block.title()) {
            g.drawString(font, line, x + 11, ty + 1, quest.ready() ? Ui.LIGHT_GREEN : Ui.GOLD_LIGHT, true);
            ty += 10;
        }
        if (compact) {
            return;
        }
        ty += 1;
        int index = 0;
        for (List<FormattedCharSequence> wrapped : block.lines()) {
            boolean done = quest.ready() || (index < quest.lines().size() && quest.lines().get(index).done());
            Ui.icon(g, done ? "check" : "seal", x + 10, ty, 8);
            for (int i = 0; i < Math.min(2, wrapped.size()); i++) {
                g.drawString(font, wrapped.get(i), x + 21, ty + i * 9, done ? 0xFF9CCC9C : Ui.CREAM, false);
            }
            ty += Math.min(2, wrapped.size()) * 9;
            index++;
        }
        if (quest.waypoint().isPresent() || quest.expiresAt() > 0) {
            int wx = x + 10;
            if (quest.waypoint().isPresent()) {
                QuestView.Waypoint waypoint = quest.waypoint().get();
                if (player.level().dimension().equals(waypoint.dimension())) {
                    double dx = waypoint.pos().getX() + 0.5 - player.getX();
                    double dz = waypoint.pos().getZ() + 0.5 - player.getZ();
                    float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                    float relative = Mth.wrapDegrees(targetYaw - player.getYRot());
                    g.pose().pushPose();
                    g.pose().rotateAround(Axis.ZP.rotationDegrees(relative), wx + 4, ty + 4, 0);
                    Ui.icon(g, "arrow_up", wx, ty, 8);
                    g.pose().popPose();
                    int distance = (int) Math.sqrt(dx * dx + dz * dz);
                    Component text = Component.literal(distance + "m");
                    g.drawString(font, text, wx + 11, ty, 0xFFC9B58C, false);
                    wx += 15 + font.width(text);
                } else {
                    Component text = Component.translatable("fealty.tracker.elsewhere");
                    g.drawString(font, text, wx, ty, 0xFFC9B58C, false);
                    wx += 6 + font.width(text);
                }
            }
            if (quest.expiresAt() > 0) {
                long left = Math.max(0, quest.expiresAt() - player.level().getGameTime()) / 20;
                Component time = Component.literal(String.format(Locale.ROOT, "%d:%02d", left / 60, left % 60));
                Ui.icon(g, "clock", wx, ty, 8);
                g.drawString(font, time, wx + 10, ty, left < 120 ? Ui.LIGHT_RED : 0xFFC9B58C, false);
            }
        }
    }
}
