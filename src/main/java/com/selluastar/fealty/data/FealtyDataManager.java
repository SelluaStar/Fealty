package com.selluastar.fealty.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.chain.QuestChainDefinition;
import com.selluastar.fealty.outlaw.BlackMarketOffer;
import com.selluastar.fealty.quest.RepQuestDefinition;
import com.selluastar.fealty.trade.VillageTradeDefinition;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Holds everything loaded from {@code data/<ns>/fealty/...}. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class FealtyDataManager {
    private static Map<ResourceLocation, FactionDefinition> factions = Map.of();
    private static List<Map.Entry<ResourceLocation, FactionDefinition>> villageTemplates = List.of();
    private static Map<ResourceLocation, SourceSettings> repActions = Map.of();
    private static Map<ResourceLocation, SourceSettings> crimes = Map.of();
    private static Map<ResourceLocation, RepQuestDefinition> quests = Map.of();
    private static Map<ResourceLocation, QuestChainDefinition> chains = Map.of();
    private static Map<ResourceLocation, VillageTradeDefinition> villageTrades = Map.of();
    private static Map<ResourceLocation, BlackMarketOffer> blackMarket = Map.of();

    private FealtyDataManager() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        HolderLookup.Provider registries = event.getRegistryAccess();
        event.addListener(new CodecDataLoader<>("fealty/tiers", TierData.CODEC, registries, TierManager::apply));
        event.addListener(new CodecDataLoader<>("fealty/factions", FactionDefinition.CODEC, registries, FealtyDataManager::setFactions));
        event.addListener(new CodecDataLoader<>("fealty/rep_actions", SourceSettings.CODEC, registries, m -> repActions = Map.copyOf(m)));
        event.addListener(new CodecDataLoader<>("fealty/crimes", SourceSettings.CODEC, registries, m -> crimes = Map.copyOf(m)));
        event.addListener(new CodecDataLoader<>("fealty/rep_quests", RepQuestDefinition.CODEC, registries, m -> quests = Map.copyOf(m)));
        event.addListener(new CodecDataLoader<>("fealty/quest_chains", QuestChainDefinition.CODEC, registries, m -> chains = Map.copyOf(m)));
        event.addListener(new CodecDataLoader<>("fealty/village_trades", VillageTradeDefinition.CODEC, registries, m -> villageTrades = Map.copyOf(m)));
        event.addListener(new CodecDataLoader<>("fealty/black_market", BlackMarketOffer.CODEC, registries, m -> blackMarket = Map.copyOf(m)));
        event.addListener(new CodecDataLoader<>("fealty/stronghold_kinds", com.selluastar.fealty.war.StrongholdKind.CODEC, registries,
                com.selluastar.fealty.war.StrongholdKinds::apply));
        event.addListener(new CodecDataLoader<>("fealty/village_layouts", com.selluastar.fealty.village.VillageLayout.CODEC, registries,
                com.selluastar.fealty.village.VillageLayouts::apply));
        event.addListener(new CodecDataLoader<>("fealty/chatter", com.selluastar.fealty.chatter.ChatterTopic.CODEC, registries,
                com.selluastar.fealty.chatter.ChatterTopics::apply));
        event.addListener(new CodecDataLoader<>("fealty/dialogue", com.selluastar.fealty.dialogue.DialogueLine.File.CODEC, registries,
                com.selluastar.fealty.dialogue.DialogueLines::apply));
        event.addListener(new CodecDataLoader<>("fealty/rumours", com.selluastar.fealty.story.RumourDefinition.CODEC, registries,
                com.selluastar.fealty.story.RumourService::apply));
        event.addListener(new CodecDataLoader<>("fealty/conversations", com.selluastar.fealty.dialogue.Conversation.CODEC, registries,
                com.selluastar.fealty.dialogue.Conversations::apply));
    }

    private static void setFactions(Map<ResourceLocation, FactionDefinition> loaded) {
        factions = Map.copyOf(loaded);
        List<Map.Entry<ResourceLocation, FactionDefinition>> templates = new ArrayList<>();
        loaded.forEach((id, def) -> {
            if (def.kind() == FactionDefinition.Kind.VILLAGE) {
                if (def.structures().isEmpty()) {
                    Fealty.LOGGER.warn("Fealty: village faction template {} has no 'structures' and will never match", id);
                }
                templates.add(Map.entry(id, def));
            }
        });
        templates.sort(Comparator.comparingInt((Map.Entry<ResourceLocation, FactionDefinition> e) -> e.getValue().priority()).reversed());
        villageTemplates = List.copyOf(templates);
        com.selluastar.fealty.village.VillageResolver.clearTemplateCache();
    }

    public static Map<ResourceLocation, FactionDefinition> factions() {
        return factions;
    }

    public static Optional<FactionDefinition> faction(ResourceLocation id) {
        return Optional.ofNullable(factions.get(id));
    }

    /** Village templates, highest priority first. */
    public static List<Map.Entry<ResourceLocation, FactionDefinition>> villageTemplates() {
        return villageTemplates;
    }

    /** Settings for a rep source. Crimes take precedence over actions with the same id. */
    public static SourceSettings source(ResourceLocation id) {
        SourceSettings s = crimes.get(id);
        if (s == null) {
            s = repActions.get(id);
        }
        return s != null ? s : SourceSettings.DEFAULT;
    }

    public static Map<ResourceLocation, RepQuestDefinition> quests() {
        return quests;
    }

    public static Optional<RepQuestDefinition> quest(ResourceLocation id) {
        return Optional.ofNullable(quests.get(id));
    }

    public static Map<ResourceLocation, QuestChainDefinition> chains() {
        return chains;
    }

    public static Optional<QuestChainDefinition> chain(ResourceLocation id) {
        return Optional.ofNullable(chains.get(id));
    }

    /** Add (or with null, remove) a quest without a data pack, until the next reload (tests). */
    public static void defineQuest(ResourceLocation id, @Nullable RepQuestDefinition quest) {
        quests = with(quests, id, quest);
    }

    /** Add (or with null, remove) a quest chain without a data pack, until the next reload (tests). */
    public static void defineChain(ResourceLocation id, @Nullable QuestChainDefinition chain) {
        chains = with(chains, id, chain);
    }

    private static <T> Map<ResourceLocation, T> with(Map<ResourceLocation, T> map, ResourceLocation id, @Nullable T value) {
        Map<ResourceLocation, T> copy = new HashMap<>(map);
        if (value == null) {
            copy.remove(id);
        } else {
            copy.put(id, value);
        }
        return Map.copyOf(copy);
    }

    public static Map<ResourceLocation, VillageTradeDefinition> villageTrades() {
        return villageTrades;
    }

    public static Map<ResourceLocation, BlackMarketOffer> blackMarket() {
        return blackMarket;
    }
}
