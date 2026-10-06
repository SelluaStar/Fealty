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
    private static final int THREAT_PIP = 8;
    private static final String[] TAXES = {"none", "light", "fair", "heavy", "crushing"};
    /** Gifts of love, in rose. */
    private static final int LOVE = 0xFFC2185B;

    public enum Page {
        OVERVIEW, TAXES, TREASURY, GUARDS, DECREES, WAR
    }

    private OpenHallPayload data;
    private Page page = Page.OVERVIEW;
    private final TabBar tabs;
    private final ScrollList<OpenHallPayload.GuardRow> roster = new ScrollList<>(20);
    private final ScrollList<OpenHallPayload.StrongholdRow> strongholds = new ScrollList<>(22);
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
                .add(Component.translatable("fealty.hall.tab.decrees"), "seal")
                .add(Component.translatable("fealty.hall.tab.war"), "sword");
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
        tabs.setMaxWidth(WIDTH - 20);
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
            case WAR -> initWar(away);
            default -> {
            }
        }
    }

    /** The War tab: strongholds on the left, scouts and the warband below. */
    private void initWar(Component away) {
        OpenHallPayload.War war = data.war();
        int selected = strongholds.selected();
        strongholds.setBounds(left + 14, top + 40, 178, HEIGHT - 92);
        strongholds.setItems(war.rows());
        if (selected >= 0 && selected < war.rows().size()) {
            strongholds.select(selected);
        }
        boolean scouted = war.scouted();
        addRenderableWidget(new FealtyButton(left + 14, top + HEIGHT - 32, 120, 20, Component.translatable("fealty.hall.war.scout", war.scoutCost()),
                b -> send("scout", 0)).icon("pin").sound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0F)
                .enabled(data.near() && !scouted)
                .tooltip(!data.near() ? away : scouted ? Component.translatable("fealty.hall.war.scouted") : Component.translatable("fealty.hall.war.scout_hint")));
        if (war.phase() > 0) {
            addRenderableWidget(new FealtyButton(left + WIDTH - 134, top + HEIGHT - 32, 120, 20, Component.translatable("fealty.hall.war.call_off"),
                    b -> send("call_off", 0)).icon("cross").tooltip(Component.translatable("fealty.hall.war.call_off_hint")));
            return;
        }
        OpenHallPayload.StrongholdRow row = strongholds.selectedItem();
        Component why = !data.near() ? away
                : row == null ? Component.translatable("fealty.hall.war.pick")
                : row.razedDays() > 0 ? Component.translatable("fealty.hall.war.razed", row.razedDays())
                : war.cooldown() > 0 ? Component.translatable("fealty.war.cooldown", war.cooldown())
                : Component.translatable("fealty.hall.war.raise_hint", row.warband(), row.levy());
        boolean ready = data.near() && row != null && row.razedDays() == 0 && war.cooldown() == 0;
        int cost = row != null ? row.cost() : war.raidCost();
        addRenderableWidget(new FealtyButton(left + WIDTH - 134, top + HEIGHT - 32, 120, 20, Component.translatable("fealty.hall.war.raise", cost),
                b -> {
                    if (strongholds.selected() >= 0) {
                        send("declare", strongholds.selected());
                    }
                }).icon("sword").sound(SoundEvents.RAID_HORN.value(), 1.0F).enabled(ready).tooltip(why));
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
            case WAR -> renderWar(g, mouseX, mouseY);
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

    private static final String[] COMPASS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private static Component compass(int bearing) {
        return Component.translatable("fealty.compass." + COMPASS[Math.floorMod(Math.round(bearing / 45.0F), 8)]);
    }

    /** Skulls for a stronghold's threat, one to five, dark for the threat and faint for the rest when asked. */
    private static void threat(GuiGraphics g, int threat, int x, int y, boolean empties) {
        for (int i = 0; i < 5; i++) {
            if (i < threat) {
                Ui.icon(g, "skull", x + i * THREAT_PIP, y, THREAT_PIP - 1);
            } else if (empties) {
                Ui.sprite(g, Ui.icon("skull"), x + i * THREAT_PIP, y, THREAT_PIP - 1, THREAT_PIP - 1, 0.25F);
            }
        }
    }

    private void renderWar(GuiGraphics g, int mouseX, int mouseY) {
        OpenHallPayload.War war = data.war();
        if (war.rows().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("fealty.hall.war.none"), left + 16, top + 40, 172, Ui.FADED, 0);
        } else {
            strongholds.render(g, mouseX, mouseY, (gg, row, index, x, y, w, h, hovered, selected) -> {
                if (selected) {
                    gg.fill(x, y, x + w, y + h - 1, 0x40B8A27C);
                } else if (hovered) {
                    gg.fill(x, y, x + w, y + h - 1, 0x20B8A27C);
                }
                if (row.target()) {
                    gg.renderOutline(x, y, w, h - 1, Ui.GOLD);
                }
                Ui.icon(gg, row.kind() == 0 ? "skull" : "shield", x + 2, y + 4, 12);
                int nameColor = row.razedDays() > 0 ? Ui.FADED : Ui.INK;
                gg.drawString(font, Ui.fit(font, row.name(), w - 22 - THREAT_PIP * 5), x + 18, y + 3, nameColor, false);
                threat(gg, row.threat(), x + w - 3 - THREAT_PIP * 5, y + 3, false);
                Component where = Component.translatable("fealty.hall.war.where", row.distance(), compass(row.bearing()));
                Ui.scaled(gg, font, where, x + 18, y + 13, 0.7F, Ui.FADED, false);
                Component state = row.razedDays() > 0 ? Component.translatable("fealty.hall.war.razed_short", row.razedDays())
                        : row.captives() > 0 ? Component.translatable("fealty.hall.war.captives_short", row.captives()) : Component.empty();
                Ui.scaled(gg, font, state, x + w - 3 - (int) (font.width(state) * 0.7F), y + 13, 0.7F,
                        row.razedDays() > 0 ? Ui.GREEN : Ui.RED, false);
            });
        }
        int rx = left + 200;
        int rw = WIDTH - 200 - 14;
        int y = top + 40;
        if (war.phase() > 0) {
            Ui.icon(g, "sword", rx, y, 16);
            g.drawString(font, Component.translatable(war.phase() == 2 ? "fealty.hall.war.fighting" : "fealty.hall.war.marching")
                    .copy().withStyle(st -> st.withBold(true)), rx + 20, y + 4, Ui.RED, false);
            Ui.wrapped(g, font, war.campaign(), rx, y + 22, rw, Ui.INK, 2);
            if (war.distance() >= 0) {
                Ui.wrapped(g, font, Component.translatable("fealty.hall.war.distance", war.distance()), rx, y + 44, rw, Ui.FADED, 2);
            }
        } else {
            OpenHallPayload.StrongholdRow row = strongholds.selectedItem();
            if (row == null) {
                Ui.wrapped(g, font, Component.translatable("fealty.hall.war.intro"), rx, y, rw, Ui.FADED, 8);
            } else {
                int dy = y;
                dy += Ui.wrapped(g, font, row.name().copy().withStyle(st -> st.withBold(true)), rx, dy, rw, Ui.INK, 2) + 1;
                threat(g, row.threat(), rx, dy, true);
                boolean traited = !row.trait().equals("none");
                Component trait = Component.translatable("fealty.stronghold.trait." + row.trait());
                Ui.scaled(g, font, Ui.fit(font, trait, (int) ((rw - THREAT_PIP * 5 - 4) / 0.75F)), rx + THREAT_PIP * 5 + 4, dy + 1, 0.75F,
                        traited ? Ui.RED : Ui.FADED, false);
                dy += 11;
                if (traited) {
                    dy += Ui.wrapped(g, font, Component.translatable("fealty.stronghold.trait." + row.trait() + ".desc"), rx, dy, rw, 0.7F,
                            Ui.FADED, 2) + 2;
                }
                dy += Ui.wrapped(g, font, Component.translatable("fealty.hall.war.where_long", row.distance(), compass(row.bearing())), rx, dy, rw,
                        0.75F, Ui.INK, 2) + 2;
                Component held = row.defenders() > 0 ? Component.translatable("fealty.hall.war.defenders", row.defenders())
                        : Component.translatable("fealty.hall.war.defenders_unknown");
                dy += Ui.wrapped(g, font, held, rx, dy, rw, 0.75F, Ui.INK, 2) + 2;
                if (row.captives() > 0) {
                    dy += Ui.wrapped(g, font, Component.translatable("fealty.hall.war.captives", row.captives()), rx, dy, rw, 0.75F, Ui.RED, 2) + 2;
                }
                if (row.razedDays() > 0) {
                    Ui.wrapped(g, font, Component.translatable("fealty.hall.war.razed", row.razedDays()), rx, dy, rw, 0.75F, Ui.GREEN, 2);
                } else {
                    Ui.wrapped(g, font, Component.translatable("fealty.hall.war.rewards", row.spoils(), row.peaceDays()), rx, dy, rw, 0.75F,
                            Ui.GREEN, 3);
                }
            }
        }
        int sy = top + HEIGHT - 48;
        OpenHallPayload.StrongholdRow picked = war.phase() > 0 ? null : strongholds.selectedItem();
        Component band = picked != null ? Component.translatable("fealty.hall.war.warband_for", picked.warband(), picked.levy())
                : Component.translatable("fealty.hall.war.warband", war.ready(), war.levy());
        Ui.scaled(g, font, band, left + 16, sy, 0.75F, Ui.FADED, false);
        if (war.peaceDays() > 0) {
            Component peace = Component.translatable("fealty.hall.war.peace", war.peaceDays());
            Ui.scaled(g, font, peace, left + WIDTH - 16 - (int) (font.width(peace) * 0.75F), sy, 0.75F, Ui.GREEN, false);
        } else if (war.cooldown() > 0) {
            Component rest = Component.translatable("fealty.war.cooldown", war.cooldown());
            Ui.scaled(g, font, rest, left + WIDTH - 16 - (int) (font.width(rest) * 0.75F), sy, 0.75F, Ui.FADED, false);
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
        if (page == Page.WAR && strongholds.mouseClicked(mouseX, mouseY, index -> rebuildWidgets())) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return (page == Page.GUARDS && roster.mouseDragged(mouseY)) || (page == Page.WAR && strongholds.mouseDragged(mouseY))
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        roster.mouseReleased();
        strongholds.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return (page == Page.GUARDS && roster.mouseScrolled(mouseX, mouseY, scrollY))
                || (page == Page.WAR && strongholds.mouseScrolled(mouseX, mouseY, scrollY)) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
