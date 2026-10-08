package com.selluastar.fealty.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.TierData;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * Called from {@code VillagerMixin}. Replaces vanilla's gossip-based prices with Fealty's tier multiplier, locks
 * high-level trades for distrusted players and adds village-exclusive trades for trusted ones, then puts the
 * villager's real offers back when trading ends.
 */
public final class TradeHooks {
    private static final Map<Villager, Session> SESSIONS = new WeakHashMap<>();

    private TradeHooks() {
    }

    private record Exclusive(String key, MerchantOffer offer) {
    }

    private record Session(Map<Integer, MerchantOffer> originals, List<Exclusive> exclusives) {
    }

    public static boolean gossipDisabled() {
        return FealtyConfig.SPEC.isLoaded() && FealtyConfig.DISABLE_VANILLA_GOSSIP.get();
    }

    /** @return true if Fealty set the prices and vanilla's calculation should be skipped */
    public static boolean updatePrices(Villager villager, ServerPlayer player) {
        restore(villager);
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        if (faction.isEmpty()) {
            return false;
        }
        float multiplier = PricingService.multiplierFor(player, villager, true);
        RepTier tier = RepManager.getTier(player, faction.get());
        TierData data = TierManager.data(tier);
        MerchantOffers offers = villager.getOffers();

        Map<Integer, MerchantOffer> originals = new HashMap<>();
        if (data.lockedTradeLevel() > 0) {
            // Villagers learn two trades per level, so level N's trades start at index (N - 1) * 2.
            for (int i = (data.lockedTradeLevel() - 1) * 2; i < offers.size(); i++) {
                MerchantOffer original = offers.get(i);
                MerchantOffer locked = original.copy();
                locked.setToOutOfStock();
                originals.put(i, original);
                offers.set(i, locked);
            }
        }

        List<Exclusive> exclusives = new ArrayList<>();
        long day = RepManager.day(player.server);
        VillagerMemory memory = villager.getData(ModAttachments.VILLAGER_MEMORY);
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        for (Map.Entry<ResourceLocation, VillageTradeDefinition> entry : FealtyDataManager.villageTrades().entrySet()) {
            VillageTradeDefinition def = entry.getValue();
            if (!def.professions().isEmpty() && !def.professions().contains(profession)) {
                continue;
            }
            if (villager.getVillagerData().getLevel() < def.minLevel()) {
                continue;
            }
            Optional<RepTier> min = TierManager.byId(def.minTier());
            if (min.isEmpty() || tier.rank() < min.get().rank()) {
                continue;
            }
            for (int k = 0; k < def.offers().size(); k++) {
                String key = entry.getKey() + "#" + k;
                MerchantOffer offer = def.offers().get(k).create();
                int used = Math.min(memory.exclusiveUses(key, day), offer.getMaxUses());
                for (int u = 0; u < used; u++) {
                    offer.increaseUses();
                }
                offers.add(offer);
                exclusives.add(new Exclusive(key, offer));
            }
        }

        for (MerchantOffer offer : offers) {
            int diff = Math.round(offer.getBaseCostA().getCount() * (multiplier - 1.0F));
            if (diff != 0) {
                offer.addToSpecialPriceDiff(diff);
            }
        }
        MobEffectInstance hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (hero != null) {
            double discount = 0.3 + 0.0625 * hero.getAmplifier();
            for (MerchantOffer offer : offers) {
                int k = (int) Math.floor(discount * offer.getBaseCostA().getCount());
                offer.addToSpecialPriceDiff(-Math.max(k, 1));
            }
        }
        SESSIONS.put(villager, new Session(originals, exclusives));
        return true;
    }

    /** Trading ended: remember exclusive trade uses and put the real offers back. */
    public static void restore(Villager villager) {
        Session session = SESSIONS.remove(villager);
        if (session == null) {
            return;
        }
        MerchantOffers offers = villager.getOffers();
        VillagerMemory memory = villager.getData(ModAttachments.VILLAGER_MEMORY);
        for (Exclusive exclusive : session.exclusives()) {
            memory.setExclusiveUses(exclusive.key(), exclusive.offer().getUses());
            offers.removeIf(o -> o == exclusive.offer());
        }
        for (Map.Entry<Integer, MerchantOffer> entry : session.originals().entrySet()) {
            if (entry.getKey() < offers.size()) {
                offers.set(entry.getKey(), entry.getValue());
            }
        }
    }

    /** The offers to save while a session is open, without temporary changes. Null when nothing is open. */
    @Nullable
    public static MerchantOffers offersForSaving(Villager villager) {
        Session session = SESSIONS.get(villager);
        if (session == null) {
            return null;
        }
        MerchantOffers copy = new MerchantOffers();
        MerchantOffers offers = villager.getOffers();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer offer = offers.get(i);
            final MerchantOffer current = offer;
            if (session.exclusives().stream().anyMatch(e -> e.offer() == current)) {
                continue;
            }
            MerchantOffer original = session.originals().getOrDefault(i, offer).copy();
            original.resetSpecialPriceDiff();
            copy.add(original);
        }
        return copy;
    }

    /** Vanilla reputation events Fealty cares about. Zombie curing becomes a rep source. */
    public static void onReputationEvent(Villager villager, ReputationEventType type, Entity target) {
        if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED && target instanceof ServerPlayer player) {
            FactionResolver.factionOf(villager).ifPresent(faction -> RepManager.applySource(player, faction, RepSources.CURE_VILLAGER));
        }
    }
}
