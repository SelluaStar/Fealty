package com.selluastar.fealty.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.GuardStance;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.RepTiers;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The tier table, loaded from data packs. One table drives villagers, guards and prices for every faction. */
public final class TierManager {
    private static final Map<ResourceLocation, TierData> DEFAULTS = createDefaults();

    private static List<RepTier> tiers = List.of();
    private static Map<ResourceLocation, TierData> data = Map.of();

    static {
        apply(DEFAULTS);
    }

    private TierManager() {
    }

    private static Map<ResourceLocation, TierData> createDefaults() {
        Map<ResourceLocation, TierData> map = new LinkedHashMap<>();
        map.put(RepTiers.HATED, new TierData(Component.translatable("fealty.tier.hated"), -100, -61, 2.0F, 0xC0392B,
                GuardStance.ATTACK_ON_SIGHT, TradePolicy.REFUSE_UNLESS_THREATENED, 0, true, false));
        map.put(RepTiers.DISTRUSTED, new TierData(Component.translatable("fealty.tier.distrusted"), -60, -21, 1.5F, 0xE67E22,
                GuardStance.WATCH, TradePolicy.STANDARD, 5, false, false));
        map.put(RepTiers.NEUTRAL, new TierData(Component.translatable("fealty.tier.neutral"), -20, 20, 1.0F, 0xBDBDBD,
                GuardStance.IGNORE, TradePolicy.STANDARD, 0, false, false));
        map.put(RepTiers.TRUSTED, new TierData(Component.translatable("fealty.tier.trusted"), 21, 60, 0.85F, 0x4CAF50,
                GuardStance.ASSIST, TradePolicy.STANDARD, 0, false, false));
        map.put(RepTiers.HONORED, new TierData(Component.translatable("fealty.tier.honored"), 61, 100, 0.7F, 0xF1C40F,
                GuardStance.ESCORT, TradePolicy.STANDARD, 0, false, true));
        return map;
    }

    /** Replace the table. Missing default ids are filled in so code that relies on them keeps working. */
    public static void apply(Map<ResourceLocation, TierData> loaded) {
        Map<ResourceLocation, TierData> merged = new LinkedHashMap<>(loaded);
        DEFAULTS.forEach(merged::putIfAbsent);
        List<Map.Entry<ResourceLocation, TierData>> sorted = new ArrayList<>(merged.entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getValue().min()));
        List<RepTier> list = new ArrayList<>();
        for (int rank = 0; rank < sorted.size(); rank++) {
            Map.Entry<ResourceLocation, TierData> e = sorted.get(rank);
            list.add(e.getValue().toTier(e.getKey(), rank));
        }
        for (int k = 1; k < list.size(); k++) {
            if (list.get(k).min() != list.get(k - 1).max() + 1) {
                Fealty.LOGGER.warn("Fealty: tiers {} and {} leave a gap or overlap", list.get(k - 1).id(), list.get(k).id());
            }
        }
        tiers = List.copyOf(list);
        data = Map.copyOf(merged);
    }

    public static List<RepTier> tiers() {
        return tiers;
    }

    public static RepTier tierFor(int rep) {
        return tierFor(tiers, rep);
    }

    public static RepTier tierFor(List<RepTier> table, int rep) {
        for (RepTier tier : table) {
            if (tier.contains(rep)) {
                return tier;
            }
        }
        // Outside every range (a gap or beyond the ends): pick the closest tier.
        RepTier best = table.getFirst();
        int bestDistance = Integer.MAX_VALUE;
        for (RepTier tier : table) {
            int distance = rep < tier.min() ? tier.min() - rep : rep - tier.max();
            if (distance < bestDistance) {
                bestDistance = distance;
                best = tier;
            }
        }
        return best;
    }

    public static Optional<RepTier> byId(ResourceLocation id) {
        for (RepTier tier : tiers) {
            if (tier.id().equals(id)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public static RepTier require(ResourceLocation id) {
        return byId(id).orElseThrow(() -> new IllegalStateException("Missing tier " + id));
    }

    public static TierData data(RepTier tier) {
        TierData d = data.get(tier.id());
        return d != null ? d : DEFAULTS.get(RepTiers.NEUTRAL);
    }

    public static RepTier hated() {
        return require(RepTiers.HATED);
    }

    public static RepTier distrusted() {
        return require(RepTiers.DISTRUSTED);
    }

    public static RepTier neutral() {
        return require(RepTiers.NEUTRAL);
    }

    public static RepTier trusted() {
        return require(RepTiers.TRUSTED);
    }

    public static RepTier honored() {
        return require(RepTiers.HONORED);
    }
}
