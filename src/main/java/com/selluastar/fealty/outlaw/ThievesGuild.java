package com.selluastar.fealty.outlaw;

import java.util.List;
import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.chain.ChainProgress;
import com.selluastar.fealty.chain.QuestChainDefinition;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.crime.CrimeService;
import com.selluastar.fealty.crime.WitnessService;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.item.RogueArmorItem;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.QuestActionPayload;
import com.selluastar.fealty.quest.QuestEvents;
import com.selluastar.fealty.quest.QuestGiver;
import com.selluastar.fealty.quest.QuestGivers;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.VillagerMemory;
import com.selluastar.fealty.util.Maps;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** The thieves guild: pickpocketing, and a stealth questline from the fence in the bandit camps. */
public final class ThievesGuild {
    public static final ResourceLocation CHAIN = Fealty.id("thieves_guild");
    private static final ResourceLocation JOINED = Fealty.id("guild_joined");
    private static final ResourceKey<LootTable> PICKPOCKET_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("gameplay/pickpocket"));
    public static final QuestGiver FENCE = new FenceGiver();

    private ThievesGuild() {
    }

    /**
     * Sneak up behind a villager with an empty hand to pick their pocket. The victim may notice (less often in
     * full rogue gear); anyone else watching makes it a witnessed crime.
     *
     * @return whether this was a pickpocket attempt (otherwise the player just talks)
     */
    public static boolean tryPickpocket(ServerPlayer player, Villager villager, ResourceLocation faction) {
        if (WitnessService.isFacing(villager, player) || villager.distanceTo(player) > 2.5F) {
            return false;
        }
        long now = player.level().getGameTime();
        VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
        if (now - memory.lastPickpocket < 24000) {
            player.displayClientMessage(Component.translatable("fealty.pickpocket.empty", villager.getDisplayName()), true);
            return true;
        }
        memory.lastPickpocket = now;
        boolean noticed = villager.getRandom().nextFloat() < (RogueArmorItem.wearsFullSet(player) ? 0.05F : 0.2F);
        ServerLevel level = player.serverLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(PICKPOCKET_LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .create(LootContextParamSets.GIFT);
        table.getRandomItems(params).forEach(stack -> Maps.give(player, stack));
        CrimeService.Result result = CrimeService.commit(player, faction, RepSources.PICKPOCKET, villager.blockPosition(),
                noticed ? villager : null, false);
        if (noticed) {
            villager.getLookControl().setLookAt(player);
            player.displayClientMessage(Component.translatable("fealty.pickpocket.noticed", villager.getDisplayName()), true);
        } else if (!result.witnessed()) {
            player.displayClientMessage(Component.translatable("fealty.pickpocket.success", villager.getDisplayName()), true);
        }
        QuestEvents.onPickpocket(player, faction, result.witnessed());
        FealtyEvents.fire(player, FealtyEvents.PICKPOCKETED);
        return true;
    }

    private static boolean welcome(ServerPlayer player) {
        return RepManager.renown(player) <= FealtyConfig.THIEVES_GUILD_MAX_RENOWN.get();
    }

    /** A guild step was handed in: pay out its rewards and open the next step. */
    public static void onStepCompleted(ServerPlayer player, QuestChainDefinition def, ChainProgress progress, int step) {
        if (step < 0 || step >= def.steps().size()) {
            return;
        }
        def.steps().get(step).rewards().forEach(stack -> Maps.give(player, stack.copy()));
        progress.setStage(Math.max(progress.stage(), step + 1));
        if (progress.stage() >= def.steps().size()) {
            def.finalReward().ifPresent(stack -> Maps.give(player, stack.copy()));
            FealtyEvents.fire(player, FealtyEvents.GUILD_COMPLETED);
            player.sendSystemMessage(Component.translatable("fealty.fence.completed"));
        } else {
            player.sendSystemMessage(Component.translatable("fealty.fence.step_done"));
        }
    }

    private static final class FenceGiver implements QuestGiver {
        @Override
        public OpenQuestScreenPayload screen(ServerPlayer player, Entity entity) {
            Component subtitle = Component.translatable("fealty.fence.subtitle");
            Optional<QuestChainDefinition> def = FealtyDataManager.chain(CHAIN);
            if (!welcome(player)) {
                return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle,
                        Component.translatable("fealty.fence.refused"), 0, false, List.of(), List.of());
            }
            int rep = RepManager.getRep(player, Factions.THIEVES_GUILD);
            ChainProgress progress = RepManager.data(player).chains().get(CHAIN);
            int next = progress == null ? 0 : progress.stage();
            if (def.isEmpty() || next >= def.get().steps().size()) {
                return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle,
                        Component.translatable("fealty.fence.done"), rep, true, List.of(), List.of());
            }
            ResourceLocation key = ChainManager.stepKey(CHAIN, next);
            List<OpenQuestScreenPayload.QuestEntry> quests = QuestGivers.entries(player, key, List.of(def.get().steps().get(next).quest()));
            Component greeting = Component.translatable(next == 0 ? "fealty.fence.recruit" : "fealty.fence.next", player.getDisplayName());
            return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), subtitle, greeting, rep, true, quests, List.of());
        }

        @Override
        public void handleAction(ServerPlayer player, Entity entity, String action, String argument) {
            Optional<QuestChainDefinition> def = FealtyDataManager.chain(CHAIN);
            if (!welcome(player) || def.isEmpty()) {
                return;
            }
            PlayerRepData data = RepManager.data(player);
            ChainProgress progress = data.chains().computeIfAbsent(CHAIN, ChainProgress::new);
            int next = progress.stage();
            if (next >= def.get().steps().size()) {
                return;
            }
            ResourceLocation key = ChainManager.stepKey(CHAIN, next);
            switch (action) {
                case QuestActionPayload.ACCEPT -> {
                    RepManager.meet(player, Factions.THIEVES_GUILD);
                    if (QuestManager.accept(player, key, Factions.THIEVES_GUILD, def.get().steps().get(next).quest(), new CompoundTag())
                            && data.setFlag(JOINED)) {
                        FealtyEvents.fire(player, FealtyEvents.GUILD_JOINED);
                    }
                }
                case QuestActionPayload.TURN_IN -> QuestManager.turnIn(player, key);
                case QuestActionPayload.ABANDON -> QuestManager.abandon(player, key, true);
                default -> {
                }
            }
            FealtyWorldData.get(player.server).setDirty();
        }
    }
}
