package com.selluastar.fealty.dialogue;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.entity.QuestGiverEntity;
import com.selluastar.fealty.entity.VillageGuardEntity;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.quest.QuestGivers;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Using any other entity that has something to say through Fealty (a quest giver another mod registered, or the speaker
 * of a conversation from a data pack) opens the dialogue box. Villagers, guards and Fealty's own NPCs open it
 * themselves; crouching leaves the entity alone.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class NpcInteractions {
    private NpcInteractions() {
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND || player.isSecondaryUseActive()
                || player.isSpectator() || !FealtyConfig.VILLAGER_DIALOGUE.get() || target instanceof Villager
                || target instanceof QuestGiverEntity || target instanceof VillageGuardEntity || GuardManager.talksLikeGuard(target)) {
            return;
        }
        if (QuestGivers.forEntity(target).isEmpty() && !Conversations.hasFor(player, target)) {
            return;
        }
        if (DialogueService.open(player, target)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
