package com.selluastar.fealty.api.quest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.api.dialogue.DialogueOption;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a quest asks of the player. Keep progress in {@link QuestContext#state()} and call
 * {@link QuestContext#setReady()} once the objective is met. Only {@link #type()}, {@link #start}, {@link #describe}
 * and {@link #preview()} must be written; everything else has a sensible default.
 *
 * @since API 1.3.0
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
        return ctx.isReady();
    }

    /**
     * The player hands the quest in. Take what the quest needs and return {@link TurnIn#COMPLETE}, or
     * {@link TurnIn#PROGRESS} for quests in several stages that carry on, or {@link TurnIn#MISSING}.
     */
    default TurnIn onTurnIn(QuestContext ctx) {
        return ctx.isReady() ? TurnIn.COMPLETE : TurnIn.MISSING;
    }

    enum TurnIn {
        COMPLETE,
        PROGRESS,
        MISSING
    }

    /** Complete as soon as the objective is met, without returning to the giver. */
    default boolean completesOnReady() {
        return false;
    }

    /** Undo anything the quest put in the world when it ends ({@code success} false: failed or abandoned). */
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

    /** Entities the player should find, marked above their heads. */
    default List<UUID> markedEntities(QuestContext ctx) {
        return List.of();
    }

    /** Where the tracker points while the objective is open; empty to point at the quest's anchor. */
    default Optional<GlobalPos> trackedPosition(QuestContext ctx) {
        return Optional.empty();
    }

    /**
     * Replies this quest adds when the player talks to an NPC. Ids only need to be unique within the quest; the player's
     * pick comes back to {@link #onDialogue}.
     */
    default List<DialogueOption> dialogueReplies(QuestContext ctx, Entity npc) {
        return List.of();
    }

    /** The player picked one of {@link #dialogueReplies}. Return what the NPC answers, or empty to ignore it. */
    default Optional<Component> onDialogue(QuestContext ctx, Entity npc, String option) {
        return Optional.empty();
    }

    /** The player crouch-used an item on a villager. Return true if the quest used the interaction. */
    default boolean onUseItemOnVillager(QuestContext ctx, Villager villager, ItemStack stack) {
        return false;
    }
}
