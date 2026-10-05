package com.selluastar.fealty.compat.ftbquests;

import java.util.Optional;

import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.rep.RepManager;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Change the player's reputation with a faction (or the village they stand in, or Renown). */
public class ReputationReward extends Reward {
    private String faction = FactionTarget.HERE;
    private int amount = 10;

    public ReputationReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FtbQuestsCompat.REPUTATION_REWARD;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify) {
        Optional<ResourceLocation> target = FactionTarget.resolve(player, faction);
        target.ifPresent(f -> {
            RepManager.meet(player, f);
            RepManager.change(player, f, amount, RepSources.QUEST, true);
        });
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("faction", faction);
        nbt.putInt("amount", amount);
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        faction = nbt.contains("faction") ? nbt.getString("faction") : FactionTarget.HERE;
        amount = nbt.getInt("amount");
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(faction);
        buffer.writeVarInt(amount);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        faction = buffer.readUtf();
        amount = buffer.readVarInt();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString("faction", faction, v -> faction = v, FactionTarget.HERE);
        config.addInt("amount", amount, v -> amount = v, 10, -1000, 1000);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public MutableComponent getAltTitle() {
        return Component.translatable("fealty.ftbquests.reward", (amount > 0 ? "+" : "") + amount,
                FactionTarget.label(faction));
    }
}
