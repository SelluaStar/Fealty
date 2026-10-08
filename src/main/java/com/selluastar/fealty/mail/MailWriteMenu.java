package com.selluastar.fealty.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModMenus;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Writing a letter at a mailbox: three parcel slots and the player's inventory. The text is sent with the letter. */
public class MailWriteMenu extends AbstractContainerMenu {
    public static final int PARCEL_X = 62;
    public static final int PARCEL_Y = 132;
    public static final int INVENTORY_Y = 158;

    /** A village the player can write to. */
    public record VillageChoice(String id, String name) {
    }

    private final Container parcels;
    private final BlockPos mailbox;
    private final ContainerLevelAccess access;
    private final List<VillageChoice> villages;

    /** Client side. */
    public MailWriteMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, new SimpleContainer(Letter.MAX_PARCELS), buf.readBlockPos(), ContainerLevelAccess.NULL, readVillages(buf));
    }

    public MailWriteMenu(int id, Inventory inventory, Container parcels, BlockPos mailbox, ContainerLevelAccess access, List<VillageChoice> villages) {
        super(ModMenus.MAIL_WRITE.get(), id);
        this.parcels = parcels;
        this.mailbox = mailbox;
        this.access = access;
        this.villages = villages;
        for (int i = 0; i < Letter.MAX_PARCELS; i++) {
            addSlot(new Slot(parcels, i, PARCEL_X + i * 18, PARCEL_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    private static List<VillageChoice> readVillages(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 256);
        List<VillageChoice> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new VillageChoice(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));
        }
        return list;
    }

    /** Open the writing desk at a mailbox, offering the villages the player knows as recipients. */
    public static void open(ServerPlayer player, BlockPos mailbox) {
        List<VillageChoice> villages = new ArrayList<>();
        FealtyWorldData data = FealtyWorldData.get(player.server);
        for (ResourceLocation faction : RepManager.data(player).rep().keySet()) {
            if (Factions.isVillage(faction)) {
                data.village(faction).ifPresent(v -> villages.add(new VillageChoice(v.id().toString(), v.name())));
            }
        }
        villages.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MailWriteMenu(id, inventory, new SimpleContainer(Letter.MAX_PARCELS),
                mailbox, ContainerLevelAccess.create(player.level(), mailbox), villages), Component.translatable("fealty.mail.write.title")), buf -> {
            buf.writeBlockPos(mailbox);
            buf.writeVarInt(villages.size());
            for (VillageChoice village : villages) {
                ByteBufCodecs.STRING_UTF8.encode(buf, village.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, village.name());
            }
        });
    }

    public List<VillageChoice> villages() {
        return villages;
    }

    public BlockPos mailbox() {
        return mailbox;
    }

    /** How many parcels are in the slots (each costs an emerald to send). */
    public int parcelCount() {
        int count = 0;
        for (int i = 0; i < parcels.getContainerSize(); i++) {
            if (!parcels.getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Send the letter: on success the parcels go with it and the desk closes. */
    public void send(ServerPlayer player, String type, String recipientValue, String subject, String body) {
        Optional<MailService.Recipient> recipient = MailService.recipient(player, type, recipientValue);
        if (recipient.isEmpty()) {
            player.displayClientMessage(Component.translatable("village".equals(type) ? "fealty.mail.problem.village" : "fealty.mail.problem.player",
                    recipientValue).withStyle(ChatFormatting.RED), true);
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < parcels.getContainerSize(); i++) {
            items.add(parcels.getItem(i).copy());
        }
        Optional<Component> problem = MailService.send(player, mailbox, recipient.get(), subject, body, items);
        if (problem.isPresent()) {
            player.displayClientMessage(problem.get().copy().withStyle(ChatFormatting.RED), true);
            Feedback.sound(player, SoundEvents.VILLAGER_NO, 0.6F, 1.0F);
            return;
        }
        parcels.clearContent();
        player.closeContainer();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int parcelSlots = Letter.MAX_PARCELS;
        if (index < parcelSlots) {
            if (!moveItemStackTo(stack, parcelSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, parcelSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.MAILBOX.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, parcels));
    }
}
