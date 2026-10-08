package com.selluastar.fealty.compat.ftbquests;

import java.util.Optional;

import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Resolves the faction field shared by the FTB Quests task and reward. */
final class FactionTarget {
    static final String HERE = "here";
    static final String ANY_VILLAGE = "any_village";

    private FactionTarget() {
    }

    /** The faction a field names for this player, or empty when "here" and the player is not in a village. */
    static Optional<ResourceLocation> resolve(ServerPlayer player, String field) {
        if (HERE.equals(field)) {
            return VillageResolver.villageAt(player.serverLevel(), player.blockPosition()).map(VillageRecord::id);
        }
        if (ANY_VILLAGE.equals(field)) {
            ResourceLocation best = null;
            int bestRep = Integer.MIN_VALUE;
            for (var entry : RepManager.data(player).rep().entrySet()) {
                if (Factions.isVillage(entry.getKey()) && entry.getValue() > bestRep) {
                    bestRep = entry.getValue();
                    best = entry.getKey();
                }
            }
            return Optional.ofNullable(best);
        }
        return Optional.ofNullable(ResourceLocation.tryParse(field));
    }

    static net.minecraft.network.chat.Component label(String field) {
        return switch (field) {
            case HERE -> net.minecraft.network.chat.Component.translatable("fealty.ftbquests.faction.here");
            case ANY_VILLAGE -> net.minecraft.network.chat.Component.translatable("fealty.ftbquests.faction.any_village");
            case "fealty:renown" -> net.minecraft.network.chat.Component.translatable("fealty.renown");
            default -> net.minecraft.network.chat.Component.literal(field);
        };
    }

    static int rep(ServerPlayer player, ResourceLocation faction) {
        return Factions.RENOWN.equals(faction) ? RepManager.renown(player) : RepManager.getRep(player, faction);
    }
}
