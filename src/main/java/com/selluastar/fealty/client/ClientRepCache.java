package com.selluastar.fealty.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.network.Standing;
import com.selluastar.fealty.network.TradeView;

import net.minecraft.resources.ResourceLocation;

/** The client's copy of the local player's standings, kept current by sync payloads. */
public final class ClientRepCache {
    private static List<RepTier> tiers = List.of();
    private static final Map<ResourceLocation, Standing> STANDINGS = new LinkedHashMap<>();
    private static int renown;
    private static List<TradeView> villageTrades = List.of();
    private static final List<Runnable> TRADE_LISTENERS = new ArrayList<>();

    private ClientRepCache() {
    }

    public static void setTiers(List<RepTier> list) {
        tiers = List.copyOf(list);
    }

    public static List<RepTier> tiers() {
        return tiers.isEmpty() ? TierManager.tiers() : tiers;
    }

    public static RepTier tierFor(int rep) {
        return TierManager.tierFor(tiers(), rep);
    }

    public static void update(List<Standing> standings, int newRenown, boolean replace) {
        if (replace) {
            STANDINGS.clear();
        }
        for (Standing s : standings) {
            STANDINGS.put(s.faction(), s);
        }
        renown = newRenown;
    }

    public static void setVillageTrades(List<TradeView> trades) {
        villageTrades = List.copyOf(trades);
        TRADE_LISTENERS.forEach(Runnable::run);
    }

    public static List<TradeView> villageTrades() {
        return villageTrades;
    }

    /** Recipe viewer integrations listen here to refresh when the server sends trades. */
    public static void onVillageTrades(Runnable listener) {
        TRADE_LISTENERS.add(listener);
    }

    public static void clear() {
        STANDINGS.clear();
        tiers = List.of();
        renown = 0;
    }

    public static int renown() {
        return renown;
    }

    /** Villages first (lords' villages on top), then other factions, each sorted by name. */
    public static List<Standing> sortedStandings() {
        List<Standing> list = new ArrayList<>(STANDINGS.values());
        list.sort(Comparator.comparing((Standing s) -> !s.lord())
                .thenComparing(s -> !s.village())
                .thenComparing(s -> s.name().getString()));
        return list;
    }

    public static Standing standing(ResourceLocation faction) {
        return STANDINGS.get(faction);
    }
}
