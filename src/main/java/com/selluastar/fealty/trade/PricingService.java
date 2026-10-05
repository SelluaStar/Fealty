package com.selluastar.fealty.trade;

import java.util.Optional;

import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.event.PriceEvent;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.NeoForge;

/** Works out the price multiplier a villager applies to a player. */
public final class PricingService {
    private PricingService() {
    }

    /**
     * @param forTrade whether this is for an actual trading session (a pending threat price is used up then)
     */
    public static float multiplierFor(ServerPlayer player, Villager villager, boolean forTrade) {
        Optional<ResourceLocation> faction = FactionResolver.factionOf(villager);
        if (faction.isEmpty()) {
            return 1.0F;
        }
        RepTier tier = RepManager.getTier(player, faction.get());
        float multiplier = tier.priceMultiplier();
        if (villager.hasData(ModAttachments.VILLAGER_MEMORY)) {
            VillagerMemory.Entry memory = villager.getData(ModAttachments.VILLAGER_MEMORY).of(player.getUUID());
            if (memory.threatPrice) {
                // A threatened villager gives the Neutral price, once, even if the player is hated.
                multiplier = Math.min(multiplier, TierManager.neutral().priceMultiplier());
                if (forTrade) {
                    memory.threatPrice = false;
                }
            }
        }
        PriceEvent event = NeoForge.EVENT_BUS.post(new PriceEvent(player, villager, faction.get(), tier, multiplier));
        return event.getMultiplier();
    }
}
