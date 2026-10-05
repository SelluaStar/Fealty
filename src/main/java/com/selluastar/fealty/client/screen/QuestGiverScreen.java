package com.selluastar.fealty.client.screen;

import java.util.List;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.client.ClientRepCache;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.OpenQuestScreenPayload.ActionEntry;
import com.selluastar.fealty.network.OpenQuestScreenPayload.QuestEntry;
import com.selluastar.fealty.network.QuestActionPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** A quest giver's book: quests and actions on the left page, the chosen quest on the right. */
public class QuestGiverScreen extends Screen {
    private static final ResourceLocation TEXTURE = Fealty.id("textures/gui/quest_book.png");
    private static final int WIDTH = 300;
    private static final int HEIGHT = 210;
    private static final int INK = 0x3B2A1A;
    private static final int FADED = 0x6B5A44;

    private OpenQuestScreenPayload data;
    private int selected;
    private int left;
    private int top;

    public QuestGiverScreen(OpenQuestScreenPayload data) {
        super(data.title());
        this.data = data;
    }

    public int entityId() {
        return data.entityId();
    }

    public void refresh(OpenQuestScreenPayload payload) {
        this.data = payload;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        List<QuestEntry> quests = data.quests();
        if (selected >= quests.size()) {
            selected = Math.max(0, quests.size() - 1);
        }
        int y = top + 82;
        for (int i = 0; i < quests.size() && i < 4; i++) {
            final int index = i;
            QuestEntry entry = quests.get(i);
            Component label = Component.literal(trim(entry.title().getString(), 17)).append(statusMark(entry.status()));
            Button button = Button.builder(label, b -> {
                selected = index;
                rebuildWidgets();
            }).bounds(left + 12, y, 106, 18).build();
            button.active = index != selected;
            addRenderableWidget(button);
            y += 20;
        }
        for (ActionEntry action : data.actions()) {
            if (y > top + HEIGHT - 26) {
                break;
            }
            Button button = Button.builder(Component.literal(trim(action.label().getString(), 18)),
                    b -> send(action.id(), "")).bounds(left + 12, y, 106, 18).build();
            button.active = action.enabled();
            Component tip = action.enabled() ? action.label() : action.label().copy().append("\n").append(action.hint());
            button.setTooltip(Tooltip.create(tip));
            addRenderableWidget(button);
            y += 20;
        }
        if (!quests.isEmpty()) {
            QuestEntry entry = quests.get(selected);
            int bx = left + 128;
            int by = top + HEIGHT - 28;
            switch (entry.status()) {
                case OFFER -> addRenderableWidget(Button.builder(Component.translatable("fealty.screen.accept"),
                        b -> send(QuestActionPayload.ACCEPT, entry.id().toString())).bounds(bx, by, 76, 20).build());
                case READY -> addRenderableWidget(Button.builder(Component.translatable("fealty.screen.turn_in"),
                        b -> send(QuestActionPayload.TURN_IN, entry.id().toString())).bounds(bx, by, 76, 20).build());
                case ACTIVE -> {
                    Button turnIn = Button.builder(Component.translatable("fealty.screen.turn_in"),
                            b -> send(QuestActionPayload.TURN_IN, entry.id().toString())).bounds(bx, by, 76, 20).build();
                    turnIn.active = false;
                    addRenderableWidget(turnIn);
                }
            }
            if (entry.status() != OpenQuestScreenPayload.Status.OFFER) {
                addRenderableWidget(Button.builder(Component.translatable("fealty.screen.abandon"),
                        b -> send(QuestActionPayload.ABANDON, entry.id().toString())).bounds(bx + 82, by, 76, 20).build());
            }
        }
    }

    private static Component statusMark(OpenQuestScreenPayload.Status status) {
        return switch (status) {
            case OFFER -> Component.empty();
            case ACTIVE -> Component.literal(" …");
            case READY -> Component.literal(" ✔");
        };
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private void send(String action, String argument) {
        PacketDistributor.sendToServer(new QuestActionPayload(data.entityId(), action, argument));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(TEXTURE, left, top, 0, 0, WIDTH, HEIGHT, 512, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        centered(graphics, data.title(), top + 9, INK);
        centered(graphics, data.subtitle(), top + 21, FADED);
        if (data.showRep()) {
            RepTier tier = ClientRepCache.tierFor(data.rep());
            Component standing = Component.translatable("fealty.screen.standing",
                    tier.displayName().copy().withColor(tier.color()), data.rep());
            centered(graphics, standing, top + 33, INK);
        }
        int y = top + 47;
        for (FormattedCharSequence line : font.split(data.greeting(), WIDTH - 32)) {
            if (y > top + 74) {
                break;
            }
            graphics.drawString(font, line, left + 16, y, FADED, false);
            y += 10;
        }

        List<QuestEntry> quests = data.quests();
        int x = left + 128;
        y = top + 82;
        if (quests.isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.translatable("fealty.screen.no_quests"), 158)) {
                graphics.drawString(font, line, x, y, FADED, false);
                y += 10;
            }
            return;
        }
        QuestEntry entry = quests.get(selected);
        for (FormattedCharSequence line : font.split(entry.title().copy().withStyle(s -> s.withBold(true)), 158)) {
            graphics.drawString(font, line, x, y, INK, false);
            y += 10;
        }
        y += 2;
        for (FormattedCharSequence line : font.split(entry.description(), 158)) {
            if (y > top + HEIGHT - 64) {
                break;
            }
            graphics.drawString(font, line, x, y, FADED, false);
            y += 9;
        }
        y += 3;
        for (Component objective : entry.lines()) {
            for (FormattedCharSequence line : font.split(Component.literal("• ").append(objective), 158)) {
                if (y > top + HEIGHT - 44) {
                    break;
                }
                graphics.drawString(font, line, x, y, INK, false);
                y += 9;
            }
        }
        Component reward = Component.translatable("fealty.screen.reward", entry.repReward(), "★".repeat(Math.max(1, entry.difficulty())));
        graphics.drawString(font, reward, x, top + HEIGHT - 40, FADED, false);
    }

    private void centered(GuiGraphics graphics, Component text, int y, int color) {
        graphics.drawString(font, text, left + (WIDTH - font.width(text)) / 2, y, color, false);
    }
}
