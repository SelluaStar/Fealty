package com.selluastar.fealty.quest;

import java.util.Optional;

import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Everything a quest objective needs to know about one accepted quest. */
public record QuestContext(ServerPlayer player, ActiveQuest quest, RepQuestDefinition definition) {
    public CompoundTag state() {
        return quest.state();
    }

    public ServerLevel level() {
        return player.serverLevel();
    }

    /** The level the quest takes place in (its village's dimension), falling back to the player's. */
    public ServerLevel questLevel() {
        ServerLevel level = player.server.getLevel(dimension());
        return level != null ? level : player.serverLevel();
    }

    public MinecraftServer server() {
        return player.server;
    }

    public void dirty() {
        FealtyWorldData.get(player.server).setDirty();
    }

    /** The objective is met. Quests that complete on the spot do so now; others wait for hand-in. */
    public void setReady() {
        if (quest.isReady()) {
            return;
        }
        quest.setReady(true);
        dirty();
        if (definition.objective().completesOnReady()) {
            QuestManager.completeLater(player, quest.instanceId());
        } else {
            player.sendSystemMessage(Component.translatable("fealty.quest.ready", definition.title()).withStyle(ChatFormatting.GREEN));
            com.selluastar.fealty.network.Feedback.toast(player, "ready", Component.translatable("fealty.toast.quest_ready"), definition.title());
            com.selluastar.fealty.network.Feedback.sound(player, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME, 0.7F, 1.4F);
            QuestSync.sync(player);
        }
    }

    /** The village that gave or rewards the quest, if it is a village. */
    public Optional<VillageRecord> village() {
        FealtyWorldData data = FealtyWorldData.get(player.server);
        if (Factions.isVillage(quest.giver())) {
            return data.village(quest.giver());
        }
        if (Factions.isVillage(quest.faction())) {
            return data.village(quest.faction());
        }
        return Optional.empty();
    }

    /** The explicit anchor an objective stored, if any. */
    public Optional<BlockPos> storedAnchor() {
        return state().contains("anchor") ? NbtUtils.readBlockPos(state(), "anchor") : Optional.empty();
    }

    /** The dimension the quest takes place in: its village's, else the player's current one. */
    public ResourceKey<Level> dimension() {
        if (state().contains("dimension")) {
            ResourceLocation id = ResourceLocation.tryParse(state().getString("dimension"));
            if (id != null) {
                return ResourceKey.create(Registries.DIMENSION, id);
            }
        }
        return village().map(VillageRecord::dimension).orElse(player.level().dimension());
    }

    /** Where the quest is centred: an explicit anchor, else the village, else the player. */
    public BlockPos anchor() {
        if (state().contains("anchor")) {
            Optional<BlockPos> pos = NbtUtils.readBlockPos(state(), "anchor");
            if (pos.isPresent()) {
                return pos.get();
            }
        }
        return village().map(VillageRecord::center).orElse(player.blockPosition());
    }

    public int getInt(String key) {
        return state().getInt(key);
    }

    public void putInt(String key, int value) {
        state().putInt(key, value);
        dirty();
    }
}
