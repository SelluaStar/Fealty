package com.selluastar.fealty.client.screen;

import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.ScrollList;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.guard.HornOrder;
import com.selluastar.fealty.network.HornCommandPayload;
import com.selluastar.fealty.network.OpenHornPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Lord's Horn: pick which village's guards to command and what to tell them. */
public class HornScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 222;
    private static final int LIST_WIDTH = 150;

    private final OpenHornPayload data;
    private final ScrollList<OpenHornPayload.Village> list = new ScrollList<>(30);
    private HornOrder order;
    private int left;
    private int top;

    public HornScreen(OpenHornPayload data) {
        super(Component.translatable("fealty.horn.title"));
        this.data = data;
        this.order = HornOrder.byIndex(data.order());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        list.setBounds(left + 12, top + 40, LIST_WIDTH, HEIGHT - 52);
        list.setItems(data.villages());
        if (list.selected() < 0) {
            for (int i = 0; i < data.villages().size(); i++) {
                if (data.villages().get(i).id().equals(data.selected())) {
                    list.select(i);
                }
            }
            if (list.selected() < 0 && !data.villages().isEmpty()) {
                list.select(0);
            }
        }
        int bx = left + LIST_WIDTH + 24;
        int bw = WIDTH - LIST_WIDTH - 36;
        int by = top + 40;
        for (HornOrder option : HornOrder.values()) {
            FealtyButton button = new FealtyButton(bx, by, bw, 20, Component.translatable("fealty.horn.order." + option.id()), b -> {
                order = option;
            }).icon(option.icon()).tooltip(hint(option));
            addRenderableWidget(button);
            by += 23;
        }
        boolean any = !data.villages().isEmpty();
        addRenderableWidget(new FealtyButton(bx, top + HEIGHT - 52, bw, 20, Component.translatable("fealty.horn.blow"), b -> send(true))
                .icon("horn").sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F).enabled(any));
        addRenderableWidget(new FealtyButton(bx, top + HEIGHT - 28, bw, 16, Component.translatable("fealty.horn.set"), b -> send(false))
                .enabled(any));
    }

    private Component hint(HornOrder option) {
        return option == HornOrder.CALL ? Component.translatable("fealty.horn.hint.call", data.summon())
                : Component.translatable("fealty.horn.hint." + option.id());
    }

    private void send(boolean blow) {
        OpenHornPayload.Village village = list.selectedItem();
        PacketDistributor.sendToServer(new HornCommandPayload(village != null ? village.id() : "", order.id(), blow));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.window(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Ui.icon(g, "horn", left + 12, top + 10, 16);
        g.drawString(font, title, left + 32, top + 14, Ui.INK, false);
        g.drawString(font, Component.translatable("fealty.horn.villages"), left + 14, top + 29, Ui.FADED, false);
        g.drawString(font, Component.translatable("fealty.horn.orders"), left + LIST_WIDTH + 26, top + 29, Ui.FADED, false);
        Ui.panel(g, left + 10, top + 38, LIST_WIDTH + 4, HEIGHT - 48);
        if (data.villages().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("fealty.horn.none"), left + 16, top + 46, LIST_WIDTH - 8, Ui.FADED, 0);
            return;
        }
        list.render(g, mouseX, mouseY, (gg, village, index, x, y, w, h, hovered, selected) -> {
            if (selected) {
                gg.fill(x, y, x + w, y + h - 1, 0x40B8A27C);
            } else if (hovered) {
                gg.fill(x, y, x + w, y + h - 1, 0x20B8A27C);
            }
            gg.fill(x + 2, y + 3, x + 6, y + h - 4, 0xFF000000 | village.color());
            gg.drawString(font, Ui.fit(font, Component.literal(village.name()), w - 14), x + 10, y + 3, Ui.INK, false);
            Component guards = Component.translatable("fealty.horn.guards", village.alive(), village.total());
            if (village.following() > 0) {
                guards = Component.empty().append(guards).append(" · ").append(Component.translatable("fealty.horn.with_you", village.following()));
            }
            Ui.scaled(gg, font, Ui.fit(font, guards, (int) ((w - 12) / 0.75F)), x + 10, y + 13, 0.75F, Ui.FADED, false);
            Component where = village.distance() >= 0 ? Component.translatable("fealty.horn.distance", village.distance())
                    : Component.translatable("fealty.horn.elsewhere");
            Ui.scaled(gg, font, where, x + 10, y + 21, 0.75F, Ui.FADED, false);
        });
        // The chosen order: outlined, and described under the buttons.
        int hx = left + LIST_WIDTH + 24;
        int bw = WIDTH - LIST_WIDTH - 36;
        g.renderOutline(hx - 1, top + 40 + order.ordinal() * 23 - 1, bw + 2, 22, Ui.GOLD);
        int hy = top + 40 + HornOrder.values().length * 23 + 2;
        Ui.wrapped(g, font, hint(order), hx, hy, bw, Ui.FADED, 3);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (list.mouseClicked(mouseX, mouseY, index -> { })) {
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
