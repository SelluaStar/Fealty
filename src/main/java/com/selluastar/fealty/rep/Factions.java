package com.selluastar.fealty.rep;

import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FactionDefinition;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Faction ids and the per-faction settings behind them. */
public final class Factions {
    public static final String VILLAGE_NAMESPACE = "village";
    public static final ResourceLocation VILLAGE_TEMPLATE = Fealty.id("village");
    public static final ResourceLocation WANDERERS = Fealty.id("wanderers");
    public static final ResourceLocation BANDITS = Fealty.id("bandits");
    public static final ResourceLocation THIEVES_GUILD = Fealty.id("thieves_guild");
    /** Pseudo-faction used by commands and FTB Quests to mean the global Renown score. */
    public static final ResourceLocation RENOWN = Fealty.id("renown");

    private Factions() {
    }

    public static boolean isVillage(ResourceLocation faction) {
        return VILLAGE_NAMESPACE.equals(faction.getNamespace());
    }

    /** The definition that governs a faction: its template for villages, or its own entry. */
    public static Optional<FactionDefinition> definition(MinecraftServer server, ResourceLocation faction) {
        if (isVillage(faction)) {
            ResourceLocation template = server == null ? VILLAGE_TEMPLATE
                    : FealtyWorldData.get(server).village(faction).map(VillageRecord::template).orElse(VILLAGE_TEMPLATE);
            return FealtyDataManager.faction(template).or(() -> FealtyDataManager.faction(VILLAGE_TEMPLATE));
        }
        return FealtyDataManager.faction(faction);
    }

    public static double renownShare(MinecraftServer server, ResourceLocation faction) {
        return definition(server, faction).flatMap(FactionDefinition::renownShare).orElse(FealtyConfig.RENOWN_SHARE.get());
    }

    public static double renownStartFactor(MinecraftServer server, ResourceLocation faction) {
        return definition(server, faction).flatMap(FactionDefinition::renownStartFactor).orElse(FealtyConfig.RENOWN_START_FACTOR.get());
    }

    public static Component displayName(MinecraftServer server, ResourceLocation faction) {
        if (RENOWN.equals(faction)) {
            return Component.translatable("fealty.renown");
        }
        if (isVillage(faction)) {
            if (server != null) {
                Optional<VillageRecord> record = FealtyWorldData.get(server).village(faction);
                if (record.isPresent()) {
                    return Component.literal(record.get().name());
                }
            }
            return Component.translatable("fealty.faction.unknown_village");
        }
        return FealtyDataManager.faction(faction).map(FactionDefinition::name)
                .orElseGet(() -> Component.literal(faction.toString()));
    }
}
