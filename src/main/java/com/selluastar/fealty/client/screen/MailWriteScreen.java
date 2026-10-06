package com.selluastar.fealty.client.screen;

import java.util.List;

import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.mail.MailWriteMenu;
import com.selluastar.fealty.network.MailSendPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/** Writing a letter: who to (a village you know, or a player by name), a subject, the letter, and up to three parcels. */
public class MailWriteScreen extends AbstractContainerScreen<MailWriteMenu> {
    private int recipient;
    private EditBox playerName;
    private EditBox subject;
    private MultiLineEditBox body;
    private FealtyButton recipientButton;

    public MailWriteScreen(MailWriteMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 240;
        inventoryLabelY = MailWriteMenu.INVENTORY_Y - 11;
        titleLabelY = 6;
    }

    private List<MailWriteMenu.VillageChoice> villages() {
        return menu.villages();
    }

    /** The last choice is "a player"; the others are villages. */
    private boolean toPlayer() {
        return recipient >= villages().size();
    }

    private Component recipientLabel() {
        return toPlayer() ? Component.translatable("fealty.mail.to_player") : Component.literal(villages().get(recipient).name());
    }

    @Override
    protected void init() {
        super.init();
        String keepName = playerName != null ? playerName.getValue() : "";
        String keepSubject = subject != null ? subject.getValue() : "";
        String keepBody = body != null ? body.getValue() : "";
        recipientButton = addRenderableWidget(new FealtyButton(leftPos + 30, topPos + 17, 138, 16, recipientLabel(), b -> {
            recipient = (recipient + 1) % (villages().size() + 1);
            b.setMessage(recipientLabel());
            playerName.visible = toPlayer();
        }).icon("mail").tooltip(Component.translatable("fealty.mail.to_hint")));
        playerName = addRenderableWidget(new EditBox(font, leftPos + 30, topPos + 36, 138, 14, Component.translatable("fealty.mail.player_name")));
        playerName.setMaxLength(16);
        playerName.setHint(Component.translatable("fealty.mail.player_name"));
        playerName.setValue(keepName);
        playerName.visible = toPlayer();
        subject = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 53, 160, 14, Component.translatable("fealty.mail.subject")));
        subject.setMaxLength(MailService.MAX_SUBJECT);
        subject.setHint(Component.translatable("fealty.mail.subject"));
        subject.setValue(keepSubject);
        body = addRenderableWidget(new MultiLineEditBox(font, leftPos + 8, topPos + 70, 160, 54, Component.translatable("fealty.mail.body_hint"),
                Component.translatable("fealty.mail.body")));
        body.setCharacterLimit(MailService.MAX_BODY);
        body.setValue(keepBody);
        addRenderableWidget(new FealtyButton(leftPos + 122, topPos + MailWriteMenu.PARCEL_Y - 1, 46, 18, Component.translatable("fealty.mail.send"),
                b -> send()).icon("seal").pageSound());
        setInitialFocus(subject);
    }

    private void send() {
        String type = toPlayer() ? "player" : "village";
        String value = toPlayer() ? playerName.getValue() : villages().get(recipient).id();
        PacketDistributor.sendToServer(new MailSendPayload(type, value, subject.getValue(), body.getValue()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        // While typing, keys go to the text (so E does not close the screen).
        if (getFocused() instanceof EditBox box && box.isFocused()) {
            return box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (getFocused() instanceof MultiLineEditBox area && area.isFocused()) {
            return area.keyPressed(keyCode, scanCode, modifiers) || true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.sprite(g, Ui.SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, Ui.INK, false);
        g.drawString(font, Component.translatable("fealty.mail.to"), 8, 21, Ui.FADED, false);
        g.drawString(font, Component.translatable("fealty.mail.parcels"), 8, MailWriteMenu.PARCEL_Y + 4, Ui.FADED, false);
        int parcels = menu.parcelCount();
        Component postage = parcels == 0 ? Component.translatable("fealty.mail.postage.paper")
                : Component.translatable("fealty.mail.postage.parcels", parcels);
        Ui.scaled(g, font, postage, 8, MailWriteMenu.PARCEL_Y + 19, 0.75F, Ui.FADED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.FADED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
