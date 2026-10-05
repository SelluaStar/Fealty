package com.selluastar.fealty.quest;

import java.util.List;
import java.util.Map;

import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a quest asks of the player. Progress is kept in {@link ActiveQuest#state()}; mark the quest ready with
 * {@link QuestContext#setReady()} when the objective is met.
 */
public interface QuestObjective {
    QuestType<?> type();

    /** Set the quest up when accepted. Return false (with a message to the player) if it cannot start. */
    boolean start(QuestContext ctx);

    /** Lines describing the objective and progress for the quest screen. */
    List<Component> describe(QuestContext ctx);

    /** Short summary for an offer that has not been accepted yet. */
    List<Component> preview();

    /** Whether the player can hand the quest in now. */
    default boolean canTurnIn(QuestContext ctx) {
        return ctx.quest().isReady();
    }

    /**
     * The player hands the quest in. Take what the quest needs and return {@link TurnIn#COMPLETE}, or
     * {@link TurnIn#PROGRESS} for multi-stage quests that continue, or {@link TurnIn#MISSING}.
     */
    default TurnIn onTurnIn(QuestContext ctx) {
        return ctx.quest().isReady() ? TurnIn.COMPLETE : TurnIn.MISSING;
    }

    enum TurnIn {
        COMPLETE,
        PROGRESS,
        MISSING
    }

    /** Complete as soon as the objective is met, without returning to the giver (courier quests). */
    default boolean completesOnReady() {
        return false;
    }

    /** Undo anything the quest put in the world (spawned targets, rubble) when it ends without success. */
    default void cleanup(QuestContext ctx, boolean success) {
    }

    /** Once a second while the player is online. */
    default void tick(QuestContext ctx) {
    }

    default void onKill(QuestContext ctx, LivingEntity victim) {
    }

    /** A target this quest spawned died to something other than the player. */
    default void onTargetLost(QuestContext ctx, LivingEntity target) {
    }

    default void onBlockPlaced(QuestContext ctx, ServerLevel level, BlockPos pos, BlockState state) {
    }

    default void onBlockBroken(QuestContext ctx, ServerLevel level, BlockPos pos, BlockState state) {
    }

    default void onTheft(QuestContext ctx, ResourceLocation village, Map<Item, Integer> stolen, boolean witnessed, boolean coffer) {
    }

    default void onPickpocket(QuestContext ctx, ResourceLocation village, boolean witnessed) {
    }

    default void onRaidVictory(QuestContext ctx, VillageRecord village) {
    }

    /** The player sneak-used an item on a villager. Return true if the quest consumed the interaction. */
    default boolean onUseItemOnVillager(QuestContext ctx, Villager villager, ItemStack stack) {
        return false;
    }
}
