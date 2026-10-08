package com.selluastar.fealty.chain;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.registry.ModAttachments;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;

/** Named villagers keep their role through being zombified and cured. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class ChainEvents {
    private ChainEvents() {
    }

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        LivingEntity from = event.getEntity();
        LivingEntity to = event.getOutcome();
        if (from.level().isClientSide() || !from.hasData(ModAttachments.CHAIN_ROLE) || from.getData(ModAttachments.CHAIN_ROLE).isNone()) {
            return;
        }
        to.setData(ModAttachments.CHAIN_ROLE, from.getData(ModAttachments.CHAIN_ROLE));
        if (to instanceof Villager villager) {
            ChainManager.lockProfession(villager);
        }
    }
}
