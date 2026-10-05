package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Favors: small requests from ordinary villagers. Each adult villager in a village may ask one favor a day (chosen
 * for their profession and the player's standing), the same for every player. Favors pay a little reputation,
 * capped per day, and can lift negative reputation slowly.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class FavorManager {
    private FavorManager() {
    }

    /** The quest-log key for favors from one villager. */
    public static ResourceLocation key(UUID villager) {
        return Fealty.id("villager/" + villager);
    }

    public static boolean isFavorKey(ResourceLocation key) {
        return key.getNamespace().equals(Fealty.MOD_ID) && key.getPath().startsWith("villager/");
    }

    /** The favor this villager asks the player today, if they have one and the player has not taken it yet. */
    public static Optional<ResourceLocation> todaysFavor(ServerPlayer player, Villager villager) {
        if (!FealtyConfig.FAVORS.get() || villager.isBaby()) {
            return Optional.empty();
        }
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        if (faction.isEmpty() || !Factions.isVillage(faction.get())) {
            return Optional.empty();
        }
        RepTier tier = RepManager.getTier(player, faction.get());
        if (tier.rank() <= TierManager.hated().rank()) {
            return Optional.empty();
        }
        long day = RepManager.day(player.server);
        QuestLog log = RepManager.data(player).allQuests().get(key(villager.getUUID()));
        if (log != null && (log.offersDay() == day || log.hasActive())) {
            return Optional.empty();
        }
        RandomSource random = RandomSource.create(villager.getUUID().getLeastSignificantBits() ^ (day * 0x9E3779B97F4A7C15L));
        if (random.nextDouble() >= FealtyConfig.FAVOR_CHANCE.get()) {
            return Optional.empty();
        }
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        List<Map.Entry<ResourceLocation, RepQuestDefinition>> candidates = new ArrayList<>();
        int total = 0;
        for (Map.Entry<ResourceLocation, RepQuestDefinition> entry : FealtyDataManager.quests().entrySet()) {
            RepQuestDefinition def = entry.getValue();
            if (def.pool().equals(RepQuestDefinition.FAVOR) && def.suits(profession) && def.weightAt(tier.id()) > 0
                    && (log == null || !log.completed().contains(entry.getKey()))) {
                candidates.add(entry);
                total += def.weightAt(tier.id());
            }
        }
        if (candidates.isEmpty() && log != null && !log.completed().isEmpty()) {
            // Every suitable favor has been done for this villager: start over.
            log.completed().clear();
            return todaysFavor(player, villager);
        }
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Map.Entry<ResourceLocation, RepQuestDefinition> entry : candidates) {
            roll -= entry.getValue().weightAt(tier.id());
            if (roll < 0) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** Take on the villager's favor. */
    public static boolean accept(ServerPlayer player, Villager villager, ResourceLocation questId) {
        CompoundTag state = new CompoundTag();
        state.putUUID("giver_uuid", villager.getUUID());
        state.put("giver_pos", NbtUtils.writeBlockPos(villager.blockPosition()));
        state.putString("giver_name", villager.getDisplayName().getString());
        ResourceLocation faction = FactionResolver.factionOf(villager).orElse(Factions.WANDERERS);
        ResourceLocation key = key(villager.getUUID());
        boolean accepted = QuestManager.accept(player, key, faction, questId, state, 1);
        if (accepted) {
            QuestLog log = RepManager.data(player).quests(key);
            log.offers().clear();
            log.setOffersDay(RepManager.day(player.server));
            FealtyWorldData.get(player.server).setDirty();
        }
        return accepted;
    }

    /** A villager who asked favors died: their favors end, without penalty. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.getServer() == null) {
            return;
        }
        ResourceLocation key = key(villager.getUUID());
        for (ServerPlayer player : villager.getServer().getPlayerList().getPlayers()) {
            for (QuestContext ctx : QuestManager.contexts(player, key)) {
                QuestManager.failQuietly(ctx, RepQuestEvent.Reason.GIVER_LOST);
            }
        }
    }
}
