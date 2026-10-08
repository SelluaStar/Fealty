package com.selluastar.fealty.block;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A mailbox: a village's (where its letters are posted) or a player's own. */
public class MailboxBlockEntity extends BlockEntity {
    @Nullable
    private ResourceLocation village;
    @Nullable
    private UUID owner;
    private String ownerName = "";

    public MailboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAILBOX.get(), pos, state);
    }

    @Nullable
    public ResourceLocation village() {
        return village;
    }

    public void setVillage(@Nullable ResourceLocation village) {
        this.village = village;
        setChanged();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(@Nullable UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (village != null) {
            tag.putString("Village", village.toString());
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        village = tag.contains("Village") ? ResourceLocation.tryParse(tag.getString("Village")) : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }
}
