package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.advancement.QuestTrigger;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.item.LetterInfo;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.quest.type.CourierObjective;
import com.selluastar.fealty.registry.ModCriteria;
import com.selluastar.fealty.registry.ModDataComponents;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.NeoForge;

/** Offers, accepts, hands in and rewards quests for every kind of quest giver. */
public final class QuestManager {
    private static final List<PendingCompletion> PENDING = new ArrayList<>();

    private QuestManager() {
    }

    private record PendingCompletion(UUID player, UUID instance) {
    }

    // ---- Lookup ----

    /** The oldest accepted quest with a giver. */
    public static Optional<QuestContext> context(ServerPlayer player, ResourceLocation giver) {
        List<QuestContext> list = contexts(player, giver);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /** Every accepted quest with a giver. */
    public static List<QuestContext> contexts(ServerPlayer player, ResourceLocation giver) {
        QuestLog log = RepManager.data(player).allQuests().get(giver);
        List<QuestContext> list = new ArrayList<>();
        if (log != null) {
            for (ActiveQuest quest : List.copyOf(log.actives())) {
                FealtyDataManager.quest(quest.questId()).ifPresent(def -> list.add(new QuestContext(player, quest, def)));
            }
        }
        return list;
    }

    /** The accepted quest with a giver for a quest id, or the oldest one if the id is null or not found. */
    public static Optional<QuestContext> context(ServerPlayer player, ResourceLocation giver, @org.jetbrains.annotations.Nullable ResourceLocation questId) {
        List<QuestContext> list = contexts(player, giver);
        if (questId != null) {
            for (QuestContext ctx : list) {
                if (ctx.quest().questId().equals(questId)) {
                    return Optional.of(ctx);
                }
            }
        }
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    /** Every quest the player has accepted, as a snapshot safe to iterate while quests change. */
    public static List<QuestContext> activeContexts(ServerPlayer player) {
        List<QuestContext> list = new ArrayList<>();
        for (QuestLog log : RepManager.data(player).allQuests().values()) {
            for (ActiveQuest quest : List.copyOf(log.actives())) {
                FealtyDataManager.quest(quest.questId()).ifPresent(def -> list.add(new QuestContext(player, quest, def)));
            }
        }
        return list;
    }

    public static int activeCount(ServerPlayer player) {
        int count = 0;
        for (QuestLog log : RepManager.data(player).allQuests().values()) {
            count += log.actives().size();
        }
        return count;
    }

    /** Whether the player may take another quest from this giver, under the giver's and the overall limit. */
    public static boolean hasRoom(ServerPlayer player, ResourceLocation giver, int perGiver) {
        return RepManager.data(player).quests(giver).actives().size() < perGiver && activeCount(player) < FealtyConfig.MAX_ACTIVE_QUESTS.get();
    }

    public static Optional<QuestContext> byInstance(ServerPlayer player, UUID instance) {
        for (QuestContext ctx : activeContexts(player)) {
            if (ctx.quest().instanceId().equals(instance)) {
                return Optional.of(ctx);
            }
        }
        return Optional.empty();
    }

    // ---- Offers ----

    /**
     * Today's redemption offers from a village elder, weighted by the player's tier, without repeats until the pool
     * cycles. They are rolled once per Fealty day; accepted offers are gone until tomorrow's roll.
     */
    public static List<ResourceLocation> offers(ServerPlayer player, VillageRecord village) {
        QuestLog log = RepManager.data(player).quests(village.id());
        long day = RepManager.day(player.server);
        RepTier tier = RepManager.getTier(player, village.id());
        if (log.offersDay() != day) {
            log.offers().clear();
            log.offers().addAll(roll(log, RepQuestDefinition.REDEMPTION, tier, player.getRandom(), FealtyConfig.QUEST_OFFERS.get()));
            log.setOffersDay(day);
            FealtyWorldData.get(player.server).setDirty();
        }
        // A tier change during the day hides offers that no longer suit the player.
        List<ResourceLocation> open = new ArrayList<>();
        for (ResourceLocation id : log.offers()) {
            Optional<RepQuestDefinition> def = FealtyDataManager.quest(id);
            if (def.isPresent() && def.get().weightAt(tier.id()) > 0 && log.find(id).isEmpty()) {
                open.add(id);
            }
        }
        return open;
    }

    /** Weighted picks from a pool, skipping quests done since the pool last cycled. */
    public static List<ResourceLocation> roll(QuestLog log, ResourceLocation pool, RepTier tier, RandomSource random, int count) {
        List<Map.Entry<ResourceLocation, RepQuestDefinition>> eligible = new ArrayList<>();
        List<ResourceLocation> poolIds = new ArrayList<>();
        for (Map.Entry<ResourceLocation, RepQuestDefinition> entry : FealtyDataManager.quests().entrySet()) {
            if (entry.getValue().pool().equals(pool) && entry.getValue().weightAt(tier.id()) > 0) {
                poolIds.add(entry.getKey());
                if (!log.completed().contains(entry.getKey())) {
                    eligible.add(entry);
                }
            }
        }
        if (eligible.isEmpty() && !poolIds.isEmpty()) {
            // Everything has been done once: the pool cycles.
            log.completed().removeAll(poolIds);
            for (Map.Entry<ResourceLocation, RepQuestDefinition> entry : FealtyDataManager.quests().entrySet()) {
                if (poolIds.contains(entry.getKey())) {
                    eligible.add(entry);
                }
            }
        }
        List<ResourceLocation> picked = new ArrayList<>();
        while (picked.size() < count && !eligible.isEmpty()) {
            int total = 0;
            for (Map.Entry<ResourceLocation, RepQuestDefinition> entry : eligible) {
                total += entry.getValue().weightAt(tier.id());
            }
            int roll = random.nextInt(Math.max(1, total));
            for (int k = 0; k < eligible.size(); k++) {
                roll -= eligible.get(k).getValue().weightAt(tier.id());
                if (roll < 0) {
                    picked.add(eligible.remove(k).getKey());
                    break;
                }
            }
        }
        return picked;
    }

    /** A screen entry for a quest that has not been accepted yet. */
    public static Optional<com.selluastar.fealty.network.OpenQuestScreenPayload.QuestEntry> offerEntry(ResourceLocation id) {
        return FealtyDataManager.quest(id).map(def -> new com.selluastar.fealty.network.OpenQuestScreenPayload.QuestEntry(id, def.title(),
                def.description(), def.objective().preview(), def.reward().rep(), def.difficulty(),
                com.selluastar.fealty.network.OpenQuestScreenPayload.Status.OFFER, rewards(def)));
    }

    /** The reward preview for a quest. */
    public static com.selluastar.fealty.network.OpenQuestScreenPayload.Rewards rewards(RepQuestDefinition def) {
        QuestReward reward = def.reward();
        List<ItemStack> items = reward.items().stream().map(ItemStack::copy).limit(6).toList();
        return new com.selluastar.fealty.network.OpenQuestScreenPayload.Rewards(items, reward.renown(), reward.experience(),
                reward.lootTable().isPresent(), def.timeLimit());
    }

    // ---- Lifecycle ----

    /** Accept a quest from a giver that allows one quest at a time. */
    public static boolean accept(ServerPlayer player, ResourceLocation giver, ResourceLocation faction, ResourceLocation questId, CompoundTag initialState) {
        return accept(player, giver, faction, questId, initialState, 1);
    }

    /**
     * Accept a quest from a giver.
     *
     * @param faction the faction the reward raises
     * @param perGiver how many quests this giver may have running with the player at once
     */
    public static boolean accept(ServerPlayer player, ResourceLocation giver, ResourceLocation faction, ResourceLocation questId,
                                 CompoundTag initialState, int perGiver) {
        PlayerRepData data = RepManager.data(player);
        QuestLog log = data.quests(giver);
        if (log.find(questId).isPresent()) {
            return false;
        }
        if (log.actives().size() >= perGiver) {
            player.sendSystemMessage(Component.translatable("fealty.quest.already_active"));
            return false;
        }
        if (activeCount(player) >= FealtyConfig.MAX_ACTIVE_QUESTS.get()) {
            player.sendSystemMessage(Component.translatable("fealty.quest.too_many", FealtyConfig.MAX_ACTIVE_QUESTS.get()));
            return false;
        }
        Optional<RepQuestDefinition> def = FealtyDataManager.quest(questId);
        if (def.isEmpty()) {
            return false;
        }
        CompoundTag state = initialState.copy();
        if (!state.contains("dimension")) {
            state.putString("dimension", player.level().dimension().location().toString());
        }
        ActiveQuest quest = new ActiveQuest(UUID.randomUUID(), questId, giver, faction, player.level().getGameTime(), state, false);
        log.add(quest);
        QuestContext ctx = new QuestContext(player, quest, def.get());
        if (!def.get().objective().start(ctx)) {
            log.remove(quest.instanceId());
            return false;
        }
        log.offers().remove(questId);
        FealtyWorldData.get(player.server).setDirty();
        NeoForge.EVENT_BUS.post(new RepQuestEvent.Start(player, faction, questId, def.get().typeId()));
        ModCriteria.QUEST.get().trigger(player, questId, def.get().typeId(), QuestTrigger.Status.STARTED);
        player.sendSystemMessage(Component.translatable("fealty.quest.accepted", def.get().title()).withStyle(ChatFormatting.GOLD));
        Feedback.toast(player, "quest", Component.translatable("fealty.toast.quest_accepted"), def.get().title());
        Feedback.sound(player, SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
        QuestSync.sync(player);
        return true;
    }

    public static boolean turnIn(ServerPlayer player, ResourceLocation giver) {
        return turnIn(player, giver, null);
    }

    /**
     * Hand in a quest with a giver: the one with this quest id, else the first that is ready, else the oldest.
     */
    public static boolean turnIn(ServerPlayer player, ResourceLocation giver, @org.jetbrains.annotations.Nullable ResourceLocation questId) {
        Optional<QuestContext> ctx = questId != null ? context(player, giver, questId)
                : contexts(player, giver).stream().filter(c -> c.definition().objective().canTurnIn(c)).findFirst()
                .or(() -> context(player, giver));
        if (ctx.isEmpty()) {
            return false;
        }
        QuestObjective.TurnIn result = ctx.get().definition().objective().onTurnIn(ctx.get());
        switch (result) {
            case COMPLETE -> complete(ctx.get());
            case PROGRESS -> {
                ctx.get().dirty();
                player.sendSystemMessage(Component.translatable("fealty.quest.progress", ctx.get().definition().title()));
                QuestSync.sync(player);
            }
            case MISSING -> player.displayClientMessage(Component.translatable("fealty.quest.not_ready"), true);
        }
        return result != QuestObjective.TurnIn.MISSING;
    }

    public static boolean abandon(ServerPlayer player, ResourceLocation giver, boolean penalty) {
        return abandon(player, giver, null, penalty);
    }

    public static boolean abandon(ServerPlayer player, ResourceLocation giver, @org.jetbrains.annotations.Nullable ResourceLocation questId,
                                  boolean penalty) {
        Optional<QuestContext> ctx = context(player, giver, questId);
        if (ctx.isEmpty()) {
            return false;
        }
        end(ctx.get(), RepQuestEvent.Reason.ABANDONED, penalty);
        return true;
    }

    /** Abandon from the journal. Quests whose giver punishes abandoning still do. */
    public static boolean abandonInstance(ServerPlayer player, UUID instance) {
        Optional<QuestContext> ctx = byInstance(player, instance);
        if (ctx.isEmpty()) {
            return false;
        }
        ResourceLocation giver = ctx.get().quest().giver();
        boolean penalty = Factions.isVillage(giver) || giver.getPath().startsWith("chain/thieves_guild");
        end(ctx.get(), RepQuestEvent.Reason.ABANDONED, penalty);
        return true;
    }

    public static void fail(QuestContext ctx, RepQuestEvent.Reason reason) {
        end(ctx, reason, true);
    }

    /** End a quest that failed through no fault of the player (the giver died), with no penalty. */
    public static void failQuietly(QuestContext ctx, RepQuestEvent.Reason reason) {
        end(ctx, reason, false);
    }

    /**
     * Whether the person who gave the quest is gone: a village elder's quests end when the village is Broken (except
     * the quest to restore it), and a favor ends when the villager who asked it dies.
     */
    public static boolean giverLost(QuestContext ctx) {
        ResourceLocation giver = ctx.quest().giver();
        if (Factions.isVillage(giver)) {
            return !ctx.definition().pool().equals(RepQuestDefinition.RESTORE)
                    && FealtyWorldData.get(ctx.server()).village(giver).map(VillageRecord::isBroken).orElse(false);
        }
        if (FavorManager.isFavorKey(giver) && ctx.state().hasUUID("giver_uuid")) {
            net.minecraft.world.entity.Entity entity = ctx.questLevel().getEntity(ctx.state().getUUID("giver_uuid"));
            return ctx.state().getBoolean("giver_died") || (entity != null && !entity.isAlive());
        }
        return false;
    }

    private static void end(QuestContext ctx, RepQuestEvent.Reason reason, boolean penalty) {
        ServerPlayer player = ctx.player();
        RepQuestDefinition def = ctx.definition();
        def.objective().cleanup(ctx, false);
        RepManager.data(player).quests(ctx.quest().giver()).remove(ctx.quest().instanceId());
        FealtyWorldData.get(player.server).setDirty();
        if (penalty) {
            if (def.failRep() != 0) {
                RepManager.change(player, ctx.quest().faction(), def.failRep(), RepSources.ABANDON_QUEST);
            } else if (reason == RepQuestEvent.Reason.ABANDONED) {
                RepManager.applySource(player, ctx.quest().faction(), RepSources.ABANDON_QUEST);
            }
        }
        NeoForge.EVENT_BUS.post(new RepQuestEvent.Fail(player, ctx.quest().faction(), ctx.quest().questId(), def.typeId(), reason));
        ChainManager.onQuestEnded(player, ctx.quest());
        ModCriteria.QUEST.get().trigger(player, ctx.quest().questId(), def.typeId(), QuestTrigger.Status.FAILED);
        player.sendSystemMessage(Component.translatable("fealty.quest.failed." + reason.name().toLowerCase(java.util.Locale.ROOT), def.title())
                .withStyle(ChatFormatting.RED));
        Feedback.toast(player, "cross", Component.translatable("fealty.toast.quest_failed"), def.title());
        QuestSync.sync(player);
    }

    public static boolean forceComplete(ServerPlayer player, ResourceLocation giver) {
        Optional<QuestContext> ctx = context(player, giver);
        ctx.ifPresent(QuestManager::complete);
        return ctx.isPresent();
    }

    public static void complete(QuestContext ctx) {
        ServerPlayer player = ctx.player();
        RepQuestDefinition def = ctx.definition();
        ActiveQuest quest = ctx.quest();
        def.objective().cleanup(ctx, true);
        QuestLog log = RepManager.data(player).quests(quest.giver());
        log.remove(quest.instanceId());
        log.completed().add(quest.questId());
        log.incrementCompleted();
        FealtyWorldData.get(player.server).setDirty();

        RepTier before = RepManager.getTier(player, quest.faction());
        QuestReward reward = def.reward();
        if (reward.rep() != 0) {
            RepManager.meet(player, quest.faction());
            if (def.pool().equals(RepQuestDefinition.FAVOR)) {
                RepManager.applyCapped(player, quest.faction(), RepSources.FAVOR, reward.rep());
            } else {
                RepManager.change(player, quest.faction(), reward.rep(), RepSources.QUEST);
            }
        }
        if (reward.renown() != 0) {
            RepManager.change(player, Factions.RENOWN, reward.renown(), RepSources.QUEST);
        }
        giveRewards(player, reward);
        RepTier after = RepManager.getTier(player, quest.faction());
        int neutral = TierManager.neutral().rank();
        if (before.rank() < neutral && after.rank() >= neutral) {
            FealtyEvents.fire(player, FealtyEvents.REDEEMED);
        }

        NeoForge.EVENT_BUS.post(new RepQuestEvent.Complete(player, quest.faction(), quest.questId(), def.typeId(), reward.rep()));
        ModCriteria.QUEST.get().trigger(player, quest.questId(), def.typeId(), QuestTrigger.Status.COMPLETED);
        player.sendSystemMessage(Component.translatable("fealty.quest.completed", def.title()).withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.0F);
        Feedback.banner(player, Component.translatable("fealty.banner.quest_complete"), def.title(), 0xF2D675, "check");
        Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.0F);
        RepManager.data(player).addStat("quests_completed", 1);
        celebrate(player);
        ChainManager.onQuestCompleted(player, quest);
        QuestSync.sync(player);
    }

    /** Nearby villagers cheer when the player completes a quest. */
    private static void celebrate(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        for (net.minecraft.world.entity.npc.Villager villager : level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class,
                player.getBoundingBox().inflate(12), v -> v.isAlive() && !v.isSleeping())) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8,
                    villager.getZ(), 6, 0.3, 0.3, 0.3, 0.0);
            if (villager.onGround() && level.getRandom().nextInt(3) == 0) {
                villager.getJumpControl().jump();
            }
        }
    }

    /** Forget queued completions (server stopping). */
    public static void clearPending() {
        PENDING.clear();
    }

    public static void giveRewards(ServerPlayer player, QuestReward reward) {
        for (ItemStack stack : reward.items()) {
            Maps.give(player, stack.copy());
        }
        if (reward.lootTable().isPresent()) {
            LootTable table = player.server.reloadableRegistries().getLootTable(reward.lootTable().get());
            LootParams params = new LootParams.Builder(player.serverLevel())
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.GIFT);
            table.getRandomItems(params).forEach(stack -> Maps.give(player, stack));
        }
        if (reward.experience() > 0) {
            player.giveExperiencePoints(reward.experience());
        }
    }

    /** Complete a quest after the current event finishes (used by quests that complete on the spot). */
    public static void completeLater(ServerPlayer player, UUID instance) {
        PENDING.add(new PendingCompletion(player.getUUID(), instance));
    }

    static void processPending(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<PendingCompletion> copy = new ArrayList<>(PENDING);
        PENDING.clear();
        for (PendingCompletion pending : copy) {
            ServerPlayer player = server.getPlayerList().getPlayer(pending.player());
            if (player != null) {
                byInstance(player, pending.instance()).filter(ctx -> ctx.quest().isReady()).ifPresent(QuestManager::complete);
            }
        }
    }

    // ---- Couriers ----

    /** Whether the player carries a letter of theirs addressed to this village. */
    public static boolean hasLettersFor(ServerPlayer player, VillageRecord village) {
        for (ItemStack stack : player.getInventory().items) {
            LetterInfo info = stack.get(ModDataComponents.LETTER.get());
            if (info != null && info.to().equals(village.id()) && info.owner().equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** Hand over every letter the player carries for this village. @return whether any were delivered */
    public static boolean tryDeliverLetters(ServerPlayer player, VillageRecord village) {
        boolean delivered = false;
        for (ItemStack stack : player.getInventory().items) {
            LetterInfo info = stack.get(ModDataComponents.LETTER.get());
            if (info == null || !info.to().equals(village.id()) || !info.owner().equals(player.getUUID())) {
                continue;
            }
            Optional<QuestContext> ctx = byInstance(player, info.quest());
            if (ctx.isPresent()) {
                player.sendSystemMessage(Component.translatable("fealty.quest.courier.delivered", info.fromName(), village.name()));
                CourierObjective.deliver(ctx.get(), stack);
                delivered = true;
            } else {
                stack.shrink(1);
            }
        }
        return delivered;
    }
}
