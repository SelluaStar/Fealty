package com.selluastar.fealty.trade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierData;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.entity.BlackMarketeerEntity;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.quest.QuestEvents;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/** How villagers behave around a player because of their tier: hiding, gifts, trade rep and raid thanks. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class VillagerBehaviors {
    private static final ResourceKey<LootTable> GENERIC_GIFT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("gameplay/honored_gift"));
    private static final Map<UUID, Long> LAST_RAID_REWARD = new HashMap<>();
    private static final Map<UUID, Long> LAST_GREETED = new HashMap<>();

    private VillagerBehaviors() {
    }

    /** Called once a second for a player standing in a village. */
    public static void tickNearPlayer(ServerPlayer player, VillageRecord village) {
        GuardManager.tickPlayer(player, village);
        if (player.isSpectator()) {
            return;
        }
        greet(player, village);
        if (player.isCreative()) {
            return;
        }
        RepTier tier = RepManager.getTier(player, village.id());
        TierData data = TierManager.data(tier);
        if (!data.villagersHide() && !data.villagerGifts()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(data.villagersHide() ? 20 : 5),
                v -> v.isAlive() && !v.isBaby() && !v.isSleeping());
        long now = level.getGameTime();
        for (Villager villager : villagers) {
            Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
            if (faction.isEmpty() || !faction.get().equals(village.id())) {
                continue;
            }
            if (data.villagersHide()) {
                if (DialogueService.listener(villager).isPresent()) {
                    continue; // already talking to someone: they hear them out
                }
                // Same signal as a rung bell: villagers run home and hide.
                villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
            } else if (data.villagerGifts() && villager.hasLineOfSight(player) && villager.distanceToSqr(player) < 25) {
                VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
                if (now - memory.lastGiftGiven >= FealtyConfig.HONORED_GIFT_COOLDOWN.get()) {
                    memory.lastGiftGiven = now;
                    giveGift(level, villager, player);
                }
            }
        }
    }

    /** Now and then a villager near the player says hello (or tells them to go away), as a speech bubble. */
    private static void greet(ServerPlayer player, VillageRecord village) {
        long now = player.level().getGameTime();
        Long last = LAST_GREETED.get(player.getUUID());
        if ((last != null && now - last < 240) || player.getRandom().nextInt(3) != 0) {
            return;
        }
        Villager nearest = null;
        double best = 36.0;
        for (Villager villager : player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(6),
                v -> v.isAlive() && !v.isSleeping() && !v.isTrading() && v.hasLineOfSight(player))) {
            double d = villager.distanceToSqr(player);
            if (d < best && Speech.sinceLastSpoke(villager) > 600) {
                best = d;
                nearest = villager;
            }
        }
        if (nearest == null || !FactionResolver.factionOf(nearest).map(village.id()::equals).orElse(false)) {
            return;
        }
        Villager speaker = nearest;
        Speech.bark(speaker, speaker.isBaby() ? "greet_child" : "greet", player, 600).ifPresent(line -> {
            LAST_GREETED.put(player.getUUID(), now);
            speaker.getLookControl().setLookAt(player);
        });
    }

    private static void giveGift(ServerLevel level, Villager villager, ServerPlayer player) {
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        ResourceKey<LootTable> vanilla = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.withDefaultNamespace("gameplay/hero_of_the_village/" + profession.getPath() + "_gift"));
        LootTable table = level.getServer().reloadableRegistries().getLootTable(vanilla);
        if (table == LootTable.EMPTY) {
            table = level.getServer().reloadableRegistries().getLootTable(GENERIC_GIFT);
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .create(LootContextParamSets.GIFT);
        List<ItemStack> items = table.getRandomItems(params);
        if (items.isEmpty()) {
            return;
        }
        villager.getLookControl().setLookAt(player);
        for (ItemStack stack : items) {
            BehaviorUtils.throwItem(villager, stack, player.position());
        }
        player.displayClientMessage(Component.translatable("fealty.gift.received", villager.getDisplayName()), true);
        Speech.bark(villager, "gift_give", player, 0);
        FealtyEvents.fire(player, FealtyEvents.GIFT_RECEIVED);
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getAbstractVillager() instanceof BlackMarketeerEntity) {
            FealtyEvents.fire(player, FealtyEvents.BLACK_MARKET_TRADE);
            return;
        }
        if (event.getAbstractVillager() instanceof Villager villager) {
            FactionResolver.factionOf(villager).ifPresent(faction -> {
                RepManager.meet(player, faction);
                RepManager.applySource(player, faction, RepSources.TRADE);
            });
        }
    }

    /** Vanilla gives Hero of the Village to everyone who helped win a raid; that is our raid-defence signal. */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getEffectInstance().getEffect().value() != MobEffects.HERO_OF_THE_VILLAGE.value()) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_RAID_REWARD.get(player.getUUID());
        if (last != null && now - last < 2400) {
            return;
        }
        LAST_RAID_REWARD.put(player.getUUID(), now);
        Optional<VillageRecord> village = VillageResolver.nearestVillage(player.serverLevel(), player.blockPosition(), 96);
        if (village.isEmpty()) {
            return;
        }
        RepManager.meet(player, village.get().id());
        RepManager.applySource(player, village.get().id(), RepSources.DEFEND_RAID);
        FealtyEvents.fire(player, FealtyEvents.RAID_DEFENDED);
        QuestEvents.onRaidVictory(player, village.get());
    }
}
