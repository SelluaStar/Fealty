package com.selluastar.fealty.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.client.ClientRepCache;
import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.ScrollList;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.network.DialogueChoicePayload;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.network.OpenQuestScreenPayload.QuestEntry;
import com.selluastar.fealty.network.QuestActionPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A quest giver's board: their quests (offers and the ones the player has taken) in a list on the left, the chosen
 * quest on the right with its objectives, rewards and the buttons to accept, hand in or abandon it.
 */
public class QuestGiverScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 222;
    private static final int LIST_WIDTH = 124;

    private OpenQuestScreenPayload data;
    private final ScrollList<QuestEntry> list = new ScrollList<>(24);
    private int left;
    private int top;
    private boolean confirmAbandon;

    public QuestGiverScreen(OpenQuestScreenPayload data) {
        super(data.title());
        this.data = data;
    }

    public int entityId() {
        return data.entityId();
    }

    public void refresh(OpenQuestScreenPayload payload) {
        this.data = payload;
        confirmAbandon = false;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The quest giver may go about its day again. */
    @Override
    public void removed() {
        super.removed();
        PacketDistributor.sendToServer(new DialogueChoicePayload(data.entityId(), DialogueService.LEAVE_BOARD));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        list.setBounds(left + 12, top + 50, LIST_WIDTH, HEIGHT - 62);
        list.setItems(data.quests());
        if (list.selected() < 0 && !data.quests().isEmpty()) {
            list.select(0);
        }
        int bx = left + LIST_WIDTH + 30;
        int by = top + HEIGHT - 30;
        addRenderableWidget(new FealtyButton(left + WIDTH - 66, top + 10, 54, 16, Component.translatable("fealty.screen.talk"),
                b -> send("talk", "")).icon("talk"));
        QuestEntry entry = list.selectedItem();
        if (entry != null) {
            switch (entry.status()) {
                case OFFER -> addRenderableWidget(new FealtyButton(bx, by, 92, 20, Component.translatable("fealty.screen.accept"),
                        b -> send(QuestActionPayload.ACCEPT, entry.id().toString())).icon("check").pageSound());
                case READY -> addRenderableWidget(new FealtyButton(bx, by, 92, 20, Component.translatable("fealty.screen.turn_in"),
                        b -> send(QuestActionPayload.TURN_IN, entry.id().toString()))
                        .icon("ready").sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2F));
                case ACTIVE -> addRenderableWidget(new FealtyButton(bx, by, 92, 20, Component.translatable("fealty.screen.turn_in"),
                        b -> { }).icon("ready").enabled(false).tooltip(Component.translatable("fealty.screen.not_ready")));
            }
            if (entry.status() != OpenQuestScreenPayload.Status.OFFER) {
                addRenderableWidget(new FealtyButton(bx + 98, by, 82, 20,
                        Component.translatable(confirmAbandon ? "fealty.screen.abandon_confirm" : "fealty.screen.abandon"), b -> {
                    if (confirmAbandon) {
                        send(QuestActionPayload.ABANDON, entry.id().toString());
                    } else {
                        confirmAbandon = true;
                        rebuildWidgets();
                    }
                }).icon("cross").tooltip(Component.translatable("fealty.screen.abandon_hint")));
            }
        }
        // Actions the old book showed (rumours, the Writ, ...). They are also replies in the dialogue box.
        int ay = top + HEIGHT + 4;
        int ax = left + 8;
        for (ActionEntry action : data.actions()) {
            Component label = action.label();
            int w = Math.min(150, font.width(label) + 16);
            if (ax + w > left + WIDTH) {
                break;
            }
            FealtyButton button = new FealtyButton(ax, ay, w, 16, label, b -> send(action.id(), "")).enabled(action.enabled());
            if (!action.enabled()) {
                button.tooltip(action.hint());
            }
            addRenderableWidget(button);
            ax += w + 4;
        }
    }

    private void send(String action, String argument) {
        PacketDistributor.sendToServer(new QuestActionPayload(data.entityId(), action, argument));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.window(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Ui.fit(font, data.title(), WIDTH - 90), left + 14, top + 12, Ui.INK, false);
        g.drawString(font, Ui.fit(font, data.subtitle(), WIDTH - 90), left + 14, top + 23, Ui.FADED, false);
        if (data.showRep()) {
            RepTier tier = ClientRepCache.tierFor(data.rep());
            Component standing = Component.translatable("fealty.screen.standing", tier.displayName().copy().withColor(tier.color()), data.rep());
            g.drawString(font, standing, left + 14, top + 34, Ui.INK, false);
        }
        Ui.panel(g, left + 10, top + 48, LIST_WIDTH + 4, HEIGHT - 58);
        if (data.quests().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("fealty.screen.no_quests"), left + 16, top + 56, LIST_WIDTH - 8, Ui.FADED, 0);
            return;
        }
        list.render(g, mouseX, mouseY, (gg, entry, index, x, y, w, h, hovered, selected) -> {
            if (selected) {
                gg.fill(x, y, x + w, y + h - 1, 0x40B8A27C);
            } else if (hovered) {
                gg.fill(x, y, x + w, y + h - 1, 0x20B8A27C);
            }
            String icon = switch (entry.status()) {
                case OFFER -> "quest";
                case ACTIVE -> "scroll";
                case READY -> "ready";
            };
            Ui.icon(gg, icon, x + 2, y + 4, 14);
            gg.drawString(font, Ui.fit(font, entry.title(), w - 22), x + 19, y + 3, Ui.INK, false);
            Component status = Component.translatable("fealty.screen.status." + entry.status().name().toLowerCase(java.util.Locale.ROOT));
            gg.drawString(font, status, x + 19, y + 13, entry.status() == OpenQuestScreenPayload.Status.READY ? Ui.GREEN : Ui.FADED, false);
        });
        QuestEntry entry = list.selectedItem();
        if (entry != null) {
            renderDetails(g, entry, left + LIST_WIDTH + 30, top + 48, WIDTH - LIST_WIDTH - 44, mouseX, mouseY);
        }
    }

    private void renderDetails(GuiGraphics g, QuestEntry entry, int x, int y, int w, int mouseX, int mouseY) {
        g.drawString(font, Ui.fit(font, entry.title().copy().withStyle(s -> s.withBold(true)), w - 40), x, y, Ui.INK, false);
        for (int i = 0; i < entry.difficulty(); i++) {
            Ui.icon(g, "seal", x + w - 8 - i * 9, y, 8);
        }
        y += 12;
        Ui.divider(g, x, y, w);
        y += 6;
        y += Ui.wrapped(g, font, entry.description(), x, y, w, Ui.INK, 5) + 4;
        for (Component line : entry.lines()) {
            Ui.icon(g, "seal", x, y - 1, 8);
            y += Ui.wrapped(g, font, Component.literal(line.getString()), x + 11, y, w - 11, Ui.INK, 2);
            if (y > top + HEIGHT - 70) {
                break;
            }
        }
        // Rewards
        int ry = top + HEIGHT - 62;
        Ui.divider(g, x, ry - 4, w);
        g.drawString(font, Component.translatable("fealty.screen.rewards"), x, ry, Ui.FADED, false);
        int rx = x;
        ry += 11;
        Ui.icon(g, "heart", rx, ry - 2, 12);
        Component rep = Component.literal("+" + entry.repReward());
        g.drawString(font, rep, rx + 14, ry, Ui.GREEN, false);
        rx += 20 + font.width(rep);
        OpenQuestScreenPayload.Rewards rewards = entry.rewards();
        if (rewards.renown() != 0) {
            Ui.icon(g, "renown", rx, ry - 2, 12);
            Component renown = Component.literal("+" + rewards.renown());
            g.drawString(font, renown, rx + 14, ry, 0xFFB8860B, false);
            rx += 20 + font.width(renown);
        }
        if (rewards.experience() > 0) {
            Component xp = Component.translatable("fealty.screen.xp", rewards.experience());
            g.drawString(font, xp, rx, ry, 0xFF5B8C2A, false);
            rx += 6 + font.width(xp);
        }
        List<ItemStack> items = new ArrayList<>(rewards.items());
        for (ItemStack stack : items) {
            g.renderItem(stack, rx, ry - 5);
            g.renderItemDecorations(font, stack, rx, ry - 5);
            if (mouseX >= rx && mouseX < rx + 16 && mouseY >= ry - 5 && mouseY < ry + 11) {
                g.renderTooltip(font, stack, mouseX, mouseY);
            }
            rx += 18;
        }
        if (rewards.loot()) {
            Ui.icon(g, "gift", rx, ry - 2, 12);
            if (mouseX >= rx && mouseX < rx + 12 && mouseY >= ry - 2 && mouseY < ry + 10) {
                g.renderTooltip(font, Component.translatable("fealty.screen.loot"), mouseX, mouseY);
            }
        }
        if (rewards.timeLimit() > 0) {
            Ui.icon(g, "clock", x, ry + 12, 10);
            g.drawString(font, Component.translatable("fealty.screen.time_limit", rewards.timeLimit() / 1200), x + 13, ry + 13, Ui.RED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (list.mouseClicked(mouseX, mouseY, index -> {
            confirmAbandon = false;
            rebuildWidgets();
        })) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
