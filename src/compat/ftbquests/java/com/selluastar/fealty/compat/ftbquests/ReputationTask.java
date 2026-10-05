package com.selluastar.fealty.compat.ftbquests;

import java.util.Optional;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.data.TierManager;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Complete when the player's standing with a faction reaches a tier (and optionally a minimum rep).
 * Faction: a faction id, {@code fealty:renown}, {@code here} (the village you stand in) or {@code any_village}.
 */
public class ReputationTask extends AbstractBooleanTask {
    private String faction = FactionTarget.HERE;
    private String minTier = "fealty:trusted";
    private int minRep = -100000;

    public ReputationTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbQuestsCompat.REPUTATION_TASK;
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 20;
    }

    @Override
    public boolean canSubmit(TeamData teamData, ServerPlayer player) {
        Optional<ResourceLocation> target = FactionTarget.resolve(player, faction);
        if (target.isEmpty()) {
            return false;
        }
        int rep = FactionTarget.rep(player, target.get());
        if (rep < minRep) {
            return false;
        }
        ResourceLocation tierId = ResourceLocation.tryParse(minTier);
        Optional<RepTier> tier = tierId == null ? Optional.empty() : TierManager.byId(tierId);
        return tier.isEmpty() || TierManager.tierFor(rep).rank() >= tier.get().rank();
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("faction", faction);
        nbt.putString("min_tier", minTier);
        nbt.putInt("min_rep", minRep);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        faction = nbt.contains("faction") ? nbt.getString("faction") : FactionTarget.HERE;
        minTier = nbt.contains("min_tier") ? nbt.getString("min_tier") : "fealty:trusted";
        minRep = nbt.contains("min_rep") ? nbt.getInt("min_rep") : -100000;
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(faction);
        buffer.writeUtf(minTier);
        buffer.writeVarInt(minRep);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        faction = buffer.readUtf();
        minTier = buffer.readUtf();
        minRep = buffer.readVarInt();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("faction", faction, v -> faction = v, FactionTarget.HERE);
        config.addString("min_tier", minTier, v -> minTier = v, "fealty:trusted");
        config.addInt("min_rep", minRep, v -> minRep = v, -100000, -100000, 100000);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public MutableComponent getAltTitle() {
        ResourceLocation tierId = ResourceLocation.tryParse(minTier);
        Component tier = tierId == null ? Component.literal(minTier)
                : TierManager.byId(tierId).map(RepTier::displayName).orElse(Component.literal(minTier));
        return Component.translatable("fealty.ftbquests.task", tier, FactionTarget.label(faction));
    }
}
