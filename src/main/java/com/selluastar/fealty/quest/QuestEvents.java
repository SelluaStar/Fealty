package com.selluastar.fealty.quest;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.event.RepQuestEvent;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Feeds game events to the objectives of every quest a player has accepted. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class QuestEvents {
    private QuestEvents() {
    }

    public static void onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            ctx.definition().objective().onBlockBroken(ctx, level, pos, state);
        }
    }

    public static void onBlockPlaced(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            ctx.definition().objective().onBlockPlaced(ctx, level, pos, state);
        }
    }

    public static void onTheft(ServerPlayer player, ResourceLocation village, Map<Item, Integer> stolen, boolean witnessed, boolean coffer) {
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            ctx.definition().objective().onTheft(ctx, village, stolen, witnessed, coffer);
        }
    }

    public static void onPickpocket(ServerPlayer player, ResourceLocation village, boolean witnessed) {
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            ctx.definition().objective().onPickpocket(ctx, village, witnessed);
        }
    }

    public static void onRaidVictory(ServerPlayer player, VillageRecord village) {
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            ctx.definition().objective().onRaidVictory(ctx, village);
        }
    }

    public static boolean onUseItemOnVillager(ServerPlayer player, Villager villager, ItemStack stack) {
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            if (ctx.definition().objective().onUseItemOnVillager(ctx, villager, stack)) {
                return true;
            }
        }
        return false;
    }

    public static void onEntityDeath(LivingEntity victim, @Nullable ServerPlayer killer) {
        if (killer != null) {
            for (QuestContext ctx : QuestManager.activeContexts(killer)) {
                ctx.definition().objective().onKill(ctx, victim);
            }
        }
        if (victim.hasData(ModAttachments.QUEST_TARGET) && victim.getServer() != null) {
            QuestTarget target = victim.getData(ModAttachments.QUEST_TARGET);
            ServerPlayer owner = victim.getServer().getPlayerList().getPlayer(target.owner());
            if (owner != null && owner != killer) {
                QuestManager.byInstance(owner, target.questInstance())
                        .ifPresent(ctx -> ctx.definition().objective().onTargetLost(ctx, victim));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 7) {
            return;
        }
        long now = player.level().getGameTime();
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            int limit = ctx.definition().timeLimit();
            if (limit > 0 && now - ctx.quest().startTime() > limit && !ctx.quest().isReady()) {
                QuestManager.fail(ctx, RepQuestEvent.Reason.EXPIRED);
            } else {
                ctx.definition().objective().tick(ctx);
            }
        }
        QuestSync.syncIfChanged(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        QuestManager.processPending(event.getServer());
    }
}
