package com.selluastar.fealty.entity;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.quest.ElderGiver;
import com.selluastar.fealty.quest.QuestGiver;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.village.ElderManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The trusting villager: one per castle village, in a distinct robe and name tag, living near the village hall.
 * The only reliable way to raise reputation. If killed, the village is Broken until restored.
 */
public class VillageElderEntity extends QuestGiverEntity {
    @Nullable
    private ResourceLocation village;

    public VillageElderEntity(EntityType<? extends VillageElderEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseAttributes();
    }

    public void bindTo(VillageRecord record, BlockPos home, String name) {
        this.village = record.id();
        setData(ModAttachments.FACTION, record.id());
        setHome(home);
        setCustomName(Component.literal(name));
        setCustomNameVisible(true);
    }

    @Nullable
    public ResourceLocation village() {
        return village;
    }

    @Override
    public QuestGiver giver() {
        return ElderGiver.INSTANCE;
    }

    @Override
    protected void onInteract(ServerPlayer player) {
        com.selluastar.fealty.advancement.FealtyEvents.fire(player, com.selluastar.fealty.advancement.FealtyEvents.MET_ELDER);
        super.onInteract(player);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel) {
            ElderManager.onElderDeath(this, source);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (village != null) {
            tag.putString("FealtyVillage", village.toString());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("FealtyVillage")) {
            village = ResourceLocation.tryParse(tag.getString("FealtyVillage"));
        }
    }
}
