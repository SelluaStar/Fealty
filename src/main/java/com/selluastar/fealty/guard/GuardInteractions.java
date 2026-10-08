package com.selluastar.fealty.guard;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.entity.VillageGuardEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Using another mod's guard (Guard Villagers' guards, say) is like using a villager: it opens the dialogue box, where
 * a fine is paid, an Honored player asks for an escort and a lord gives orders. Crouching leaves the guard to its own
 * mod, as does a golem, which has nothing to say. Fealty's own guards open their dialogue themselves.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class GuardInteractions {
    private GuardInteractions() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                || player.isSecondaryUseActive() || player.isSpectator() || !FealtyConfig.VILLAGER_DIALOGUE.get()
                || !(event.getTarget() instanceof Mob guard) || guard instanceof VillageGuardEntity
                || !GuardManager.talksLikeGuard(guard) || guard.getTarget() == player) {
            return;
        }
        if (DialogueService.open(player, guard)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
