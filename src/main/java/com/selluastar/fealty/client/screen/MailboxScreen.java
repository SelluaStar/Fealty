package com.selluastar.fealty.client.screen;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.ScrollList;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.network.MailActionPayload;
import com.selluastar.fealty.network.OpenMailboxPayload;
import com.selluastar.fealty.network.OpenMailboxPayload.LetterView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A mailbox: your letters on the left, the open letter on the right, and a desk to write your own. */
public class MailboxScreen extends Screen {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 222;
    private static final int LIST_WIDTH = 132;

    private OpenMailboxPayload data;
    private final ScrollList<LetterView> list = new ScrollList<>(26);
    @Nullable
    private UUID selected;
    private int left;
    private int top;

    public MailboxScreen(OpenMailboxPayload data) {
        super(data.title());
        this.data = data;
        if (!data.letters().isEmpty()) {
            select(data.letters().getFirst());
        }
    }

    public BlockPos pos() {
        return data.pos();
    }

    public void refresh(OpenMailboxPayload payload) {
        this.data = payload;
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send(String action, @Nullable UUID letter) {
        PacketDistributor.sendToServer(new MailActionPayload(data.pos(), action, Optional.ofNullable(letter)));
    }

    private void select(LetterView letter) {
        selected = letter.id();
        if (!letter.read()) {
            send("read", letter.id());
        }
    }

    @Nullable
    private LetterView current() {
        for (LetterView letter : data.letters()) {
            if (letter.id().equals(selected)) {
                return letter;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        list.setBounds(left + 12, top + 34, LIST_WIDTH, HEIGHT - 46 - (hasPostable() ? 26 : 0));
        list.setItems(data.letters());
        for (int i = 0; i < data.letters().size(); i++) {
            if (data.letters().get(i).id().equals(selected)) {
                list.select(i);
            }
        }
        addRenderableWidget(new FealtyButton(left + WIDTH - 112, top + 10, 100, 16, Component.translatable("fealty.mail.write"),
                b -> send("write", null)).icon("scroll").pageSound());
        LetterView letter = current();
        if (letter != null) {
            int bx = left + LIST_WIDTH + 28;
            int by = top + HEIGHT - 30;
            addRenderableWidget(new FealtyButton(bx, by, 90, 20, Component.translatable("fealty.mail.take"),
                    b -> send("take", letter.id())).icon("gift").enabled(!letter.items().isEmpty()));
            addRenderableWidget(new FealtyButton(bx + 96, by, 80, 20, Component.translatable("fealty.mail.delete"),
                    b -> {
                        send("delete", letter.id());
                        selected = null;
                    }).icon("cross").enabled(letter.items().isEmpty())
                    .tooltip(letter.items().isEmpty() ? null : Component.translatable("fealty.mail.delete_hint")));
        }
        if (hasPostable()) {
            addRenderableWidget(new FealtyButton(left + 14, top + HEIGHT - 34, LIST_WIDTH - 4, 18, Component.translatable("fealty.mail.post"),
                    b -> send("post", null)).icon("seal").tooltip(Component.translatable("fealty.mail.post_hint", data.postable())));
        }
    }

    private boolean hasPostable() {
        return !data.postable().getString().isEmpty();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.window(g, left, top, WIDTH, HEIGHT);
    }

    private static String kindIcon(int kind) {
        return switch (kind) {
            case 1 -> "seal";
            case 2 -> "skull";
            default -> "mail";
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Ui.icon(g, "mail", left + 12, top + 9, 16);
        g.drawString(font, Ui.fit(font, title, WIDTH - 150), left + 32, top + 13, Ui.INK, false);
        Ui.panel(g, left + 10, top + 32, LIST_WIDTH + 4, HEIGHT - 42 - (hasPostable() ? 26 : 0));
        if (data.letters().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("fealty.mail.empty"), left + 16, top + 40, LIST_WIDTH - 8, Ui.FADED, 0);
        } else {
            list.render(g, mouseX, mouseY, (gg, letter, index, x, y, w, h, hovered, isSelected) -> {
                if (isSelected) {
                    gg.fill(x, y, x + w, y + h - 1, 0x40B8A27C);
                } else if (hovered) {
                    gg.fill(x, y, x + w, y + h - 1, 0x20B8A27C);
                }
                Ui.icon(gg, kindIcon(letter.kind()), x + 2, y + 5, 14);
                Component subject = letter.read() ? letter.subject() : letter.subject().copy().withStyle(s -> s.withBold(true));
                gg.drawString(font, Ui.fit(font, subject, w - 22 - (letter.items().isEmpty() ? 0 : 10)), x + 19, y + 3, Ui.INK, false);
                Ui.scaled(gg, font, Ui.fit(font, letter.from(), (int) ((w - 22) / 0.75F)), x + 19, y + 14, 0.75F, Ui.FADED, false);
                if (!letter.items().isEmpty()) {
                    Ui.icon(gg, "gift", x + w - 10, y + 3, 8);
                }
                if (!letter.read()) {
                    gg.fill(x + w - 4, y + h - 6, x + w - 1, y + h - 3, 0xFFD4AF37);
                }
            });
        }
        LetterView letter = current();
        int x = left + LIST_WIDTH + 28;
        int y = top + 34;
        int w = WIDTH - LIST_WIDTH - 40;
        if (letter == null) {
            Ui.wrapped(g, font, Component.translatable("fealty.mail.pick"), x, y + 4, w, Ui.FADED, 0);
            return;
        }
        g.drawString(font, Ui.fit(font, letter.subject().copy().withStyle(s -> s.withBold(true)), w), x, y, Ui.INK, false);
        y += 11;
        Component when = letter.minutesAgo() < 1 ? Component.translatable("fealty.mail.just_now")
                : letter.minutesAgo() < 60 ? Component.translatable("fealty.mail.minutes_ago", letter.minutesAgo())
                : Component.translatable("fealty.mail.hours_ago", letter.minutesAgo() / 60);
        Ui.scaled(g, font, Ui.fit(font, Component.translatable("fealty.mail.from_line", letter.from(), when), (int) (w / 0.75F)), x, y,
                0.75F, Ui.FADED, false);
        y += 9;
        Ui.divider(g, x, y, w);
        y += 6;
        Ui.wrapped(g, font, letter.body(), x, y, w, Ui.INK, 11);
        if (!letter.items().isEmpty()) {
            int iy = top + HEIGHT - 56;
            Ui.divider(g, x, iy - 4, w);
            g.drawString(font, Component.translatable("fealty.mail.parcels"), x, iy + 4, Ui.FADED, false);
            int ix = x + font.width(Component.translatable("fealty.mail.parcels")) + 6;
            for (ItemStack stack : letter.items()) {
                Ui.sprite(g, Ui.SLOT, ix - 1, iy - 1, 18, 18);
                g.renderItem(stack, ix, iy);
                g.renderItemDecorations(font, stack, ix, iy);
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    g.renderTooltip(font, stack, mouseX, mouseY);
                }
                ix += 20;
            }
        }
        if (hasPostable()) {
            Ui.wrapped(g, font, Component.translatable("fealty.mail.postable", data.postable()), left + 14, top + HEIGHT - 48, LIST_WIDTH - 4,
                    Ui.GREEN, 1);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (list.mouseClicked(mouseX, mouseY, index -> {
            if (index >= 0 && index < data.letters().size()) {
                select(data.letters().get(index));
                rebuildWidgets();
            }
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
