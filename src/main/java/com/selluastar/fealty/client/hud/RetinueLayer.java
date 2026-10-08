package com.selluastar.fealty.client.hud;

import java.util.List;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.config.FealtyClientConfig;
import com.selluastar.fealty.network.RetinuePayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * The guards following the player, at the top centre of the screen below any boss bars: what each one carries, their
 * health, what they were told to do, and a countdown for those still on their way.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID, value = Dist.CLIENT)
public final class RetinueLayer {
    private static final int CELL = 24;
    private static List<RetinuePayload.Member> members = List.of();
    /** Client ticks (game time) when the list arrived, so arrival countdowns tick down between updates. */
    private static long receivedAt;
    /** Bottom of the boss bars drawn this frame. */
    private static int bossBottom;

    private RetinueLayer() {
    }

    public static void set(List<RetinuePayload.Member> list) {
        members = List.copyOf(list);
        Minecraft minecraft = Minecraft.getInstance();
        receivedAt = minecraft.level != null ? minecraft.level.getGameTime() : 0;
    }

    public static void clear() {
        members = List.of();
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBottom = Math.max(bossBottom, event.getY() + event.getIncrement());
    }

    private static ItemStack iconFor(String kind) {
        return switch (kind) {
            case "archer" -> new ItemStack(Items.BOW);
            case "sergeant" -> new ItemStack(Items.GOLDEN_SWORD);
            case "golem" -> new ItemStack(Items.IRON_BLOCK);
            case "other" -> new ItemStack(Items.SHIELD);
            default -> new ItemStack(Items.IRON_SWORD);
        };
    }

    private static String orderIcon(String order) {
        return switch (order) {
            case "hold" -> "shield";
            case "guard" -> "guard";
            case "return" -> "house";
            case "arriving" -> "clock";
            case "escort" -> "crown";
            default -> "";
        };
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        int top = (bossBottom > 0 ? bossBottom : 0) + 4;
        bossBottom = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (members.isEmpty() || minecraft.options.hideGui || minecraft.player == null || minecraft.level == null
                || !FealtyClientConfig.RETINUE_BAR.get() || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        Font font = minecraft.font;
        int count = members.size();
        int width = count * CELL + 8;
        int x = (g.guiWidth() - width) / 2;
        int y = top + 9;
        Ui.scaledCentered(g, font, Component.translatable("fealty.retinue.title"), g.guiWidth() / 2, top, 0.75F, Ui.GOLD_LIGHT, true);
        Ui.hudPanel(g, x, y, width, 32);
        long elapsed = minecraft.level.getGameTime() - receivedAt;
        for (int i = 0; i < count; i++) {
            RetinuePayload.Member member = members.get(i);
            int cx = x + 4 + i * CELL;
            // the village's colour along the top of the cell
            g.fill(cx + 2, y + 3, cx + CELL - 2, y + 4, 0xFF000000 | member.color());
            boolean arriving = "arriving".equals(member.order());
            ItemStack icon = iconFor(member.kind());
            if (arriving) {
                g.setColor(1.0F, 1.0F, 1.0F, 0.45F);
            }
            g.renderItem(icon, cx + 4, y + 5);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            String badge = orderIcon(member.order());
            if (!badge.isEmpty()) {
                Ui.icon(g, badge, cx + CELL - 10, y + 4, 8);
            }
            if (arriving) {
                long ticks = Math.max(0, member.eta() - elapsed);
                Component eta = Component.translatable("fealty.retinue.arriving", (ticks + 19) / 20);
                Ui.scaledCentered(g, font, eta, cx + CELL / 2, y + 23, 0.75F, Ui.CREAM, true);
            } else {
                float fraction = member.maxHealth() > 0 ? member.health() / member.maxHealth() : 0.0F;
                int color = fraction > 0.6F ? 0x66BB6A : fraction > 0.3F ? 0xFFCA28 : 0xEF5350;
                Ui.bar(g, cx + 2, y + 23, CELL - 4, 4, fraction, color);
            }
        }
    }
}
