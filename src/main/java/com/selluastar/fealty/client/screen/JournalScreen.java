package com.selluastar.fealty.client.screen;

import java.util.List;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.client.ClientQuestCache;
import com.selluastar.fealty.client.ClientRepCache;
import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.ScrollList;
import com.selluastar.fealty.client.ui.TabBar;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.network.Standing;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** The Fealty journal: accepted quests, standings with every faction, and the villages the player rules. */
public class JournalScreen extends Screen {
    public enum Page {
        QUESTS,
        REPUTATION,
        LORDSHIPS
    }

    private static final int WIDTH = 320;
    private static final int HEIGHT = 210;
    private static final int LIST_WIDTH = 116;

    private Page page;
    private int left;
    private int top;
    private final TabBar tabs;
    private final ScrollList<QuestView> questList = new ScrollList<>(22);
    private final ScrollList<Standing> standingList = new ScrollList<>(26);
    private final Runnable listener = this::onQuestsChanged;
    private FealtyButton trackButton;
    private FealtyButton abandonButton;
    private boolean confirmAbandon;

    public JournalScreen(Page page) {
        super(Component.translatable("fealty.journal.title"));
        this.page = page;
        this.tabs = new TabBar(index -> {
            this.page = Page.values()[index];
            rebuildWidgets();
        });
        tabs.add(Component.translatable("fealty.journal.tab.quests"), "scroll")
                .add(Component.translatable("fealty.journal.tab.reputation"), "heart")
                .add(Component.translatable("fealty.journal.tab.lordships"), "crown");
        tabs.select(page.ordinal());
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2 + 8;
        tabs.setPosition(left + 10, top);
        ClientQuestCache.unlisten(listener);
        ClientQuestCache.listen(listener);
        questList.setBounds(left + 12, top + 34, LIST_WIDTH, HEIGHT - 48);
        questList.setItems(ClientQuestCache.quests());
        if (questList.selected() < 0 && !questList.items().isEmpty()) {
            questList.select(0);
        }
        standingList.setBounds(left + 12, top + 52, WIDTH - 24, HEIGHT - 64 - (page == Page.LORDSHIPS ? 26 : 0));
        List<Standing> standings = page == Page.LORDSHIPS
                ? ClientRepCache.sortedStandings().stream().filter(Standing::lord).toList()
                : ClientRepCache.sortedStandings();
        standingList.setItems(standings);

        trackButton = null;
        abandonButton = null;
        if (page == Page.LORDSHIPS && !standings.isEmpty()) {
            if (standingList.selected() < 0) {
                standingList.select(0);
            }
            addRenderableWidget(new FealtyButton(left + WIDTH - 132, top + HEIGHT - 32, 120, 20, Component.translatable("fealty.journal.open_hall"), b -> {
                Standing standing = standingList.selectedItem();
                if (standing != null) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new com.selluastar.fealty.network.HallActionPayload(standing.faction().toString(), "open", 0));
                }
            }).icon("crown").pageSound().tooltip(Component.translatable("fealty.journal.open_hall_hint")));
        }
        if (page == Page.QUESTS) {
            trackButton = addRenderableWidget(new FealtyButton(left + WIDTH - 92, top + HEIGHT - 28, 80, 18,
                    Component.empty(), b -> toggleTrack()).icon("pin").pageSound());
            abandonButton = addRenderableWidget(new FealtyButton(left + WIDTH - 178, top + HEIGHT - 28, 82, 18,
                    Component.empty(), b -> abandon()).icon("cross").tooltip(Component.translatable("fealty.screen.abandon_hint")));
            updateTrackButton();
        }
    }

    @Override
    public void removed() {
        ClientQuestCache.unlisten(listener);
    }

    private void onQuestsChanged() {
        questList.setItems(ClientQuestCache.quests());
        updateTrackButton();
    }

    private void updateTrackButton() {
        if (trackButton == null) {
            return;
        }
        QuestView quest = questList.selectedItem();
        trackButton.visible = quest != null;
        abandonButton.visible = quest != null;
        if (quest != null) {
            trackButton.setMessage(Component.translatable(quest.tracked() ? "fealty.journal.untrack" : "fealty.journal.track"));
        }
        abandonButton.setMessage(Component.translatable(confirmAbandon ? "fealty.screen.abandon_confirm" : "fealty.screen.abandon"));
    }

    private void abandon() {
        QuestView quest = questList.selectedItem();
        if (quest == null) {
            return;
        }
        if (!confirmAbandon) {
            confirmAbandon = true;
            updateTrackButton();
            return;
        }
        confirmAbandon = false;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.selluastar.fealty.network.AbandonQuestPayload(quest.instance()));
    }

    private void toggleTrack() {
        QuestView quest = questList.selectedItem();
        if (quest != null) {
            ClientQuestCache.setTracked(quest.instance(), !quest.tracked());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        tabs.render(g, mouseX, mouseY);
        Ui.window(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        switch (page) {
            case QUESTS -> renderQuests(g, mouseX, mouseY);
            case REPUTATION -> renderStandings(g, mouseX, mouseY, false);
            case LORDSHIPS -> renderStandings(g, mouseX, mouseY, true);
        }
    }

    // ---- Quests ----

    private void renderQuests(GuiGraphics g, int mouseX, int mouseY) {
        Ui.centered(g, font, Component.translatable("fealty.journal.quests_title", ClientQuestCache.quests().size()),
                left + 12 + LIST_WIDTH / 2, top + 18, Ui.INK, false);
        Ui.panel(g, left + 10, top + 32, LIST_WIDTH + 4, HEIGHT - 44);
        if (questList.items().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("fealty.journal.no_quests"), left + 16, top + 40, LIST_WIDTH - 8, Ui.FADED, 0);
            Ui.wrapped(g, font, Component.translatable("fealty.journal.no_quests_hint"), left + LIST_WIDTH + 30, top + 40,
                    WIDTH - LIST_WIDTH - 46, Ui.FADED, 0);
            return;
        }
        questList.render(g, mouseX, mouseY, (gg, quest, index, x, y, w, h, hovered, selected) -> {
            if (selected) {
                gg.fill(x, y, x + w, y + h - 1, 0x40B8A27C);
            } else if (hovered) {
                gg.fill(x, y, x + w, y + h - 1, 0x20B8A27C);
            }
            Ui.icon(gg, quest.ready() ? "ready" : "quest", x + 2, y + 3, 14);
            gg.drawString(font, Ui.fit(font, quest.title(), w - 22), x + 19, y + 3, Ui.INK, false);
            gg.drawString(font, Ui.fit(font, quest.place(), w - 22), x + 19, y + 12, Ui.FADED, false);
            if (quest.tracked()) {
                Ui.icon(gg, "pin", x + w - 9, y + 2, 8);
            }
        });
        QuestView quest = questList.selectedItem();
        if (quest != null) {
            renderQuestDetails(g, quest, left + LIST_WIDTH + 28, top + 16, WIDTH - LIST_WIDTH - 42);
        }
    }

    private void renderQuestDetails(GuiGraphics g, QuestView quest, int x, int y, int w) {
        g.drawString(font, Ui.fit(font, quest.title().copy().withStyle(s -> s.withBold(true)), w), x, y, Ui.INK, false);
        y += 11;
        g.drawString(font, Ui.fit(font, Component.translatable("fealty.journal.from", quest.giver(), quest.place()), w), x, y, Ui.FADED, false);
        y += 12;
        Ui.divider(g, x, y, w);
        y += 6;
        y += Ui.wrapped(g, font, quest.description(), x, y, w, Ui.INK, 4) + 4;
        for (QuestView.Line line : quest.lines()) {
            Ui.icon(g, line.done() ? "check" : "seal", x, y - 1, 9);
            Component text = line.text();
            if (line.need() > 0) {
                text = Component.translatable("fealty.journal.count", text, line.have(), line.need());
            }
            y += Ui.wrapped(g, font, text, x + 12, y, w - 12, line.done() ? Ui.GREEN : Ui.INK, 2);
            if (y > top + HEIGHT - 50) {
                break;
            }
        }
        int bottom = top + HEIGHT - 48;
        if (quest.ready()) {
            Ui.icon(g, "ready", x, bottom - 1, 10);
            g.drawString(font, Component.translatable("fealty.journal.ready", quest.giver()), x + 13, bottom, Ui.GREEN, false);
        } else if (quest.waypoint().isPresent()) {
            Ui.icon(g, "pin", x, bottom - 1, 10);
            g.drawString(font, Ui.fit(font, waypointText(quest.waypoint().get()), w - 13), x + 13, bottom, Ui.FADED, false);
        }
        Ui.icon(g, "heart", x, bottom + 13, 10);
        g.drawString(font, Component.translatable("fealty.journal.reward", quest.repReward()), x + 13, bottom + 14, Ui.INK, false);
    }

    private Component waypointText(QuestView.Waypoint waypoint) {
        if (minecraft == null || minecraft.player == null) {
            return Component.empty();
        }
        if (!minecraft.player.level().dimension().equals(waypoint.dimension())) {
            return Component.translatable("fealty.journal.other_dimension");
        }
        int distance = (int) Math.sqrt(minecraft.player.blockPosition().distSqr(waypoint.pos()));
        return Component.translatable("fealty.journal.waypoint", waypoint.pos().getX(), waypoint.pos().getZ(), distance);
    }

    // ---- Standings ----

    private void renderStandings(GuiGraphics g, int mouseX, int mouseY, boolean lordships) {
        int renown = ClientRepCache.renown();
        RepTier renownTier = ClientRepCache.tierFor(renown);
        Ui.icon(g, "renown", left + 14, top + 14, 14);
        Component renownLine = Component.translatable("fealty.ledger.renown", renown,
                renownTier.displayName().copy().withColor(renownTier.color()));
        g.drawString(font, renownLine, left + 32, top + 17, Ui.INK, false);
        drawRepBar(g, left + 32, top + 29, WIDTH - 46, renown, renownTier);
        Ui.divider(g, left + 12, top + 42, WIDTH - 24);
        if (standingList.items().isEmpty()) {
            Component empty = Component.translatable(lordships ? "fealty.journal.no_lordships" : "fealty.ledger.empty");
            Ui.wrapped(g, font, empty, left + 16, top + 56, WIDTH - 32, Ui.FADED, 0);
            return;
        }
        standingList.render(g, mouseX, mouseY, (gg, standing, index, x, y, w, h, hovered, selected) -> {
            if (lordships && selected) {
                gg.fill(x, y, x + w, y + h - 1, 0x40B8A27C);
            }
            RepTier tier = ClientRepCache.tierFor(standing.rep());
            Ui.icon(gg, standing.lord() ? "crown" : standing.village() ? "house" : "shield", x + 2, y + 4, 14);
            Component name = standing.lord() ? Component.translatable("fealty.ledger.lord_of", standing.name()) : standing.name();
            gg.drawString(font, Ui.fit(font, name, w - 110), x + 20, y + 3, Ui.INK, false);
            Component tierText = Component.literal(tier.displayName().getString() + " (" + standing.rep() + ")");
            gg.drawString(font, tierText, x + w - 4 - font.width(tierText), y + 3, tier.color(), false);
            drawRepBar(gg, x + 20, y + 15, w - 24, standing.rep(), tier);
        });
    }

    /** A bar across the whole rep range with a mark at zero, filled from zero to the current value. */
    private void drawRepBar(GuiGraphics g, int x, int y, int w, int rep, RepTier tier) {
        List<RepTier> tiers = ClientRepCache.tiers();
        int min = tiers.getFirst().min();
        int max = tiers.getLast().max();
        g.fill(x, y, x + w, y + 5, 0xFF1E120A);
        g.fill(x + 1, y + 1, x + w - 1, y + 4, 0xFF8A7656);
        int zero = x + (int) ((0 - min) / (double) (max - min) * w);
        int pos = x + (int) ((Mth.clamp(rep, min, max) - min) / (double) (max - min) * w);
        g.fill(Math.min(zero, pos), y + 1, Math.max(zero, pos) + 1, y + 4, 0xFF000000 | tier.color());
        g.fill(zero, y - 1, zero + 1, y + 6, 0xFF3B2A1A);
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabs.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (page == Page.QUESTS && questList.mouseClicked(mouseX, mouseY, index -> {
            confirmAbandon = false;
            updateTrackButton();
        })) {
            return true;
        }
        if (page != Page.QUESTS && standingList.mouseClicked(mouseX, mouseY, index -> { })) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (questList.mouseDragged(mouseY) || standingList.mouseDragged(mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        questList.mouseReleased();
        standingList.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Page.QUESTS ? questList.mouseScrolled(mouseX, mouseY, scrollY) : standingList.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (com.selluastar.fealty.client.FealtyKeys.JOURNAL.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
