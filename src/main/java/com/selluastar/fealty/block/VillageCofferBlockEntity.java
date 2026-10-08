package com.selluastar.fealty.block;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class VillageCofferBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    @Nullable
    private ResourceLocation village;

    public VillageCofferBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VILLAGE_COFFER.get(), pos, state);
    }

    public void setVillage(ResourceLocation village) {
        this.village = village;
        setChanged();
    }

    @Nullable
    public ResourceLocation village() {
        return village;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.fealty.village_coffer");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public boolean isEmpty() {
        unpackLootTable(null);
        return super.isEmpty();
    }

    /** Put tribute in; whatever does not fit is returned. */
    public List<ItemStack> deposit(List<ItemStack> stacks) {
        unpackLootTable(null);
        List<ItemStack> leftovers = new java.util.ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack remaining = stack.copy();
            for (int i = 0; i < items.size() && !remaining.isEmpty(); i++) {
                ItemStack slot = items.get(i);
                if (slot.isEmpty()) {
                    items.set(i, remaining);
                    remaining = ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(slot, remaining) && slot.getCount() < slot.getMaxStackSize()) {
                    int move = Math.min(remaining.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(move);
                    remaining.shrink(move);
                }
            }
            if (!remaining.isEmpty()) {
                leftovers.add(remaining);
            }
        }
        setChanged();
        return leftovers;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items, registries);
        }
        village = tag.contains("FealtyVillage") ? ResourceLocation.tryParse(tag.getString("FealtyVillage")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items, registries);
        }
        if (village != null) {
            tag.putString("FealtyVillage", village.toString());
        }
    }
}
