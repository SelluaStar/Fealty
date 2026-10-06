package com.selluastar.fealty.client.screen;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.client.ClientRepCache;
import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.ScrollList;
import com.selluastar.fealty.client.ui.TabBar;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.network.HallActionPayload;
import com.selluastar.fealty.network.OpenHallPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Village Hall, where a lord runs their village: how it fares, the taxes (with what each level brings in and costs
 * in love), the treasury, the watch, and decrees.
 */
public class LordshipScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 210;
    private static final String[] TAXES = {"none", "light", "fair", "heavy", "crushing"};
    /** Gifts of love, in rose. */
    private static final int LOVE = 0xFFC2185B;

    public enum Page {
        OVERVIEW, TAXES, TREASURY, GUARDS, DECREES
    }

    private OpenHallPayload data;
    private Page page = Page.OVERVIEW;
    private final TabBar tabs;
    private final ScrollList<OpenHallPayload.GuardRow> roster = new ScrollList<>(20);
    private int left;
    private int top;

    public LordshipScreen(OpenHallPayload data) {
        super(Component.translatable("fealty.hall.title", data.name()));
        this.data = data;
        this.tabs = new TabBar(index -> {
            page = Page.values()[index];
            rebuildWidgets();
        });
        tabs.add(Component.translatable("fealty.hall.tab.overview"), "house")
                .add(Component.translatable("fealty.hall.tab.taxes"), "tax")
                .add(Component.translatable("fealty.hall.tab.treasury"), "coin")
                .add(Component.translatable("fealty.hall.tab.guards"), "guard")
                .add(Component.translatable("fealty.hall.tab.decrees"), "seal");
    }

    public String village() {
        return data.village();
    }

    public void refresh(OpenHallPayload payload) {
        this.data = payload;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(String action, int argument) {
        PacketDistributor.sendToServer(new HallActionPayload(data.village(), action, argument));
    }

    private static Component taxName(int level) {
        return Component.translatable("fealty.lord.tax." + TAXES[Mth.clamp(level, 0, 4)]);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2 + 8;
        tabs.setPosition(left + 10, top);
        tabs.select(page.ordinal());
        Component away = Component.translatable("fealty.hall.away");
        switch (page) {
            case TAXES -> {
                int bw = (WIDTH - 28) / 5;
                for (int level = 0; level < 5; level++) {
                    final int chosen = level;
                    addRenderableWidget(new FealtyButton(left + 14 + level * bw, top + 48, bw - 2, 20, taxName(level),
                            b -> send("tax", chosen)).sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F + level * 0.08F));
                }
            }
            case TREASURY -> addRenderableWidget(new FealtyButton(left + WIDTH / 2 - 60, top + HEIGHT - 40, 120, 20,
                    Component.translatable("fealty.hall.collect"), b -> send("collect", 0)).icon("coin")
                    .sound(SoundEvents.PLAYER_LEVELUP, 1.4F).enabled(data.near() && (data.treasury() >= 1.0 || data.gifts() > 0))
                    .tooltip(data.near() ? null : away));
            case GUARDS -> {
                roster.setBounds(left + 14, top + 40, WIDTH - 28, HEIGHT - 80);
                roster.setItems(data.roster());
                addRenderableWidget(new FealtyButton(left + WIDTH - 134, top + HEIGHT - 32, 120, 20,
                        Component.translatable("fealty.hall.recruit", data.recruitCost()), b -> {
                            OpenHallPayload.GuardRow row = roster.selectedItem();
                            if (row != null) {
                                send("recruit", data.roster().indexOf(row));
                            }
                        }).icon("guard").enabled(data.near() && roster.selectedItem() != null && roster.selectedItem().state() == 2
                                && isFealtyGuard(roster.selectedItem()))
                        .tooltip(data.near() ? Component.translatable("fealty.hall.recruit_hint") : away));
            }
            case DECREES -> addRenderableWidget(new FealtyButton(left + 20, top + 92, 130, 20, Component.translatable("fealty.hall.feast"),
                    b -> send("feast", 0)).icon("gift").enabled(data.near() && data.feastCooldown() == 0)
                    .tooltip(!data.near() ? away : data.feastCooldown() > 0
                            ? Component.translatable("fealty.hall.feast_wait", data.feastCooldown()) : null));
            default -> {
            }
        }
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
        // The village's banner colour beside its name.
        g.fill(left + 12, top + 10, left + 16, top + 22, 0xFF000000 | data.color());
        g.drawString(font, Ui.fit(font, title, WIDTH - 40), left + 20, top + 12, Ui.INK, false);
        Ui.divider(g, left + 12, top + 26, WIDTH - 24);
        int x = left + 16;
        int y = top + 34;
        int w = WIDTH - 32;
        switch (page) {
            case OVERVIEW -> renderOverview(g, x, y, w);
            case TAXES -> renderTaxes(g, x, y, w);
            case TREASURY -> renderTreasury(g, x, y, w);
            case GUARDS -> renderGuards(g, mouseX, mouseY);
            case DECREES -> renderDecrees(g, x, y, w);
        }
    }

    private void line(GuiGraphics g, String icon, Component text, int x, int y, int color) {
        Ui.icon(g, icon, x, y - 2, 12);
        g.drawString(font, text, x + 16, y, color, false);
    }

    private void renderOverview(GuiGraphics g, int x, int y, int w) {
        RepTier tier = ClientRepCache.tierFor(data.standing());
        line(g, "crown", Component.translatable("fealty.hall.lord", data.lord(), data.daysRuled()), x, y, Ui.INK);
        line(g, "house", Component.translatable("fealty.hall.population", data.population()), x, y + 16, Ui.INK);
        line(g, "guard", Component.translatable("fealty.hall.watch", data.guardsAlive(), data.guardsTotal()), x, y + 32, Ui.INK);
        line(g, "heart", Component.translatable("fealty.hall.loyalty", tier.displayName().copy().withColor(tier.color()), data.standing()),
                x, y + 48, Ui.INK);
        Ui.bar(g, x + 16, y + 60, w - 16, 6, (data.standing() + 100) / 200.0F, tier.color());
        line(g, "tax", Component.translatable("fealty.hall.taxes_now", taxName(data.taxLevel())), x, y + 76, Ui.INK);
        line(g, "coin", Component.translatable("fealty.hall.treasury_now", (int) Math.floor(data.treasury())), x, y + 92, Ui.INK);
        Ui.wrapped(g, font, mood(), x, y + 112, w, Ui.FADED, 3);
    }

    /** How the village feels about its lord: from love and taxes. */
    private Component mood() {
        int loyalty = data.loyalty().size() > data.taxLevel() ? data.loyalty().get(data.taxLevel()) : 0;
        String key = data.standing() >= 80 ? "devoted" : loyalty < -1 ? "angry" : loyalty < 0 ? "grumbling" : loyalty > 0 ? "happy" : "content";
        return Component.translatable("fealty.hall.mood." + key);
    }

    private void renderTaxes(GuiGraphics g, int x, int y, int w) {
        g.drawString(font, Component.translatable("fealty.hall.taxes_intro"), x, y, Ui.FADED, false);
        int bw = (WIDTH - 28) / 5;
        g.renderOutline(left + 13 + data.taxLevel() * bw, top + 47, bw, 22, Ui.GOLD);
        int ty = y + 44;
        for (int level = 0; level < 5; level++) {
            int cx = left + 14 + level * bw + (bw - 2) / 2;
            float tribute = data.tribute().size() > level ? data.tribute().get(level) : 0.0F;
            int loyalty = data.loyalty().size() > level ? data.loyalty().get(level) : 0;
            Ui.centered(g, font, Component.translatable("fealty.hall.tribute_short", String.format("%.1f", tribute)), cx, ty, Ui.INK, false);
            Component change = Component.literal((loyalty > 0 ? "+" : "") + loyalty);
            Ui.centered(g, font, change, cx, ty + 11, loyalty > 0 ? Ui.GREEN : loyalty < 0 ? Ui.RED : Ui.FADED, false);
            float gift = data.giftChance().size() > level ? data.giftChance().get(level) : 0.0F;
            Ui.centered(g, font, Component.translatable("fealty.hall.gift_short", Math.round(gift * 100)), cx, ty + 22,
                    gift > 0 ? LOVE : Ui.FADED, false);
        }
        Ui.scaled(g, font, Component.translatable("fealty.hall.tribute_legend"), x, ty + 35, 0.75F, Ui.FADED, false);
        ty += 11;
        Ui.divider(g, x, ty + 36, w);
        int level = data.taxLevel();
        float tribute = data.tribute().size() > level ? data.tribute().get(level) : 0.0F;
        int loyalty = data.loyalty().size() > level ? data.loyalty().get(level) : 0;
        Ui.wrapped(g, font, Component.translatable("fealty.hall.taxes_now_detail", taxName(level), String.format("%.1f", tribute),
                (loyalty > 0 ? "+" : "") + loyalty), x, ty + 42, w, Ui.INK, 3);
    }

    private void renderTreasury(GuiGraphics g, int x, int y, int w) {
        int bundles = (int) Math.floor(data.treasury());
        Ui.icon(g, "coin", x, y, 24);
        g.drawString(font, Component.translatable("fealty.hall.treasury_now", bundles).copy().withStyle(s -> s.withBold(true)), x + 30, y + 3,
                Ui.INK, false);
        float perDay = data.tribute().size() > data.taxLevel() ? data.tribute().get(data.taxLevel()) : 0.0F;
        g.drawString(font, Component.translatable("fealty.hall.treasury_rate", String.format("%.1f", perDay)), x + 30, y + 15, Ui.FADED, false);
        if (data.gifts() > 0) {
            Ui.icon(g, "heart", x + 30, y + 26, 10);
            g.drawString(font, Component.translatable("fealty.hall.gifts_waiting", data.gifts()), x + 43, y + 27, LOVE, false);
        }
        Ui.wrapped(g, font, Component.translatable("fealty.hall.treasury_intro"), x, y + 42, w, Ui.FADED, 4);
        if (!data.near()) {
            Ui.wrapped(g, font, Component.translatable("fealty.hall.away"), x, top + HEIGHT - 58, w, Ui.RED, 2);
        }
    }

    /** Fealty's own guards hold a post in the watch; golems and other mods' guards do not (and cannot be recruited). */
    private static boolean isFealtyGuard(OpenHallPayload.GuardRow row) {
        return row.rank().equals("swordsman") || row.rank().equals("archer") || row.rank().equals("sergeant");
    }

    private void renderGuards(GuiGraphics g, int mouseX, int mouseY) {
        if (data.roster().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("fealty.hall.no_watch"), left + 16, top + 40, WIDTH - 32, Ui.FADED, 0);
            return;
        }
        roster.render(g, mouseX, mouseY, (gg, row, index, x, y, w, h, hovered, selected) -> {
            if (selected) {
                gg.fill(x, y, x + w, y + h - 1, 0x40B8A27C);
            } else if (hovered) {
                gg.fill(x, y, x + w, y + h - 1, 0x20B8A27C);
            }
            boolean ours = isFealtyGuard(row);
            String icon = !ours ? (row.rank().equals("golem") ? "shield" : "guard")
                    : row.rank().equals("archer") ? "arrow_up" : row.rank().equals("sergeant") ? "crown" : "sword";
            Ui.icon(gg, icon, x + 2, y + 3, 12);
            Component name;
            if (!ours) {
                // Iron golems and other mods' guards (Guard Villagers, ...): their own name, else what they are.
                Component kind = Component.translatable("fealty.hall.guard.kind." + row.rank());
                name = row.name().isEmpty() ? kind : Component.translatable("fealty.hall.guard.named_other", row.name(), kind);
            } else {
                name = row.name().isEmpty() ? Component.translatable("fealty.hall.unnamed")
                        : Component.translatable("entity.fealty.village_guard." + row.rank() + ".named", row.name());
            }
            gg.drawString(font, Ui.fit(font, name, w - 130), x + 18, y + 5, Ui.INK, false);
            Component state = switch (row.state()) {
                case 1 -> Component.translatable("fealty.hall.guard.with_you");
                case 2 -> !ours ? Component.translatable("fealty.hall.guard.fallen")
                        : row.days() > 0 ? Component.translatable("fealty.hall.guard.fallen_days", row.days())
                        : Component.translatable("fealty.hall.guard.fallen_soon");
                case 3 -> Component.translatable("fealty.hall.guard.unfilled");
                case 4 -> Component.translatable("fealty.hall.guard.away");
                default -> Component.translatable("fealty.hall.guard.on_duty");
            };
            int color = row.state() == 2 ? Ui.RED : row.state() == 1 ? Ui.GREEN : Ui.FADED;
            gg.drawString(font, state, x + w - 4 - font.width(state), y + 5, color, false);
        });
    }

    private void renderDecrees(GuiGraphics g, int x, int y, int w) {
        Ui.icon(g, "gift", x, y, 16);
        g.drawString(font, Component.translatable("fealty.hall.feast_title").copy().withStyle(s -> s.withBold(true)), x + 20, y + 4, Ui.INK, false);
        Ui.wrapped(g, font, Component.translatable("fealty.hall.feast_desc", data.feastFood(), data.feastEmeralds()), x, y + 20, w, Ui.FADED, 4);
        if (data.feastCooldown() > 0) {
            g.drawString(font, Component.translatable("fealty.hall.feast_wait", data.feastCooldown()), x + 140, top + 98, Ui.RED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tabs.mouseClicked(mouseX, mouseY)) {
            return true;
        }
        if (page == Page.GUARDS && roster.mouseClicked(mouseX, mouseY, index -> rebuildWidgets())) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return (page == Page.GUARDS && roster.mouseDragged(mouseY)) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        roster.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return (page == Page.GUARDS && roster.mouseScrolled(mouseX, mouseY, scrollY)) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
