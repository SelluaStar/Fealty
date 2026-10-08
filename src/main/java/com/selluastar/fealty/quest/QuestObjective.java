package com.selluastar.fealty.quest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.dialogue.DialogueNode;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a quest asks of the player. Progress is kept in {@link ActiveQuest#state()}; mark the quest ready with
 * {@link QuestContext#setReady()} when the objective is met.
 *
 * <p>Fealty's own objectives implement this richer form of the API's
 * {@link com.selluastar.fealty.api.quest.QuestObjective}; objectives other mods register through the API are wrapped
 * in an {@link ApiObjective} when they load.
 */
public interface QuestObjective extends com.selluastar.fealty.api.quest.QuestObjective {
    @Override
    QuestType<?> type();

    /** Set the quest up when accepted. Return false (with a message to the player) if it cannot start. */
    boolean start(QuestContext ctx);

    /** Lines describing the objective and progress for the quest screen. */
    List<Component> describe(QuestContext ctx);

    @Override
    default boolean start(com.selluastar.fealty.api.quest.QuestContext ctx) {
        return start((QuestContext) ctx);
    }

    @Override
    default List<Component> describe(com.selluastar.fealty.api.quest.QuestContext ctx) {
        return describe((QuestContext) ctx);
    }

    /** Short summary for an offer that has not been accepted yet. */
    @Override
    List<Component> preview();

    /**
     * Objective lines with progress for the tracker and journal. By default the screen description without
     * counts; objectives with counts override this.
     */
    default List<QuestView.Line> progress(QuestContext ctx) {
        return describe(ctx).stream().map(line -> QuestView.Line.text(Component.literal(line.getString()))).toList();
    }

    /**
     * Extra replies this quest adds when the player talks to an NPC, for example delivering a message. Option ids only
     * need to be unique within the quest.
     */
    default List<DialogueNode.Option> dialogueOptions(QuestContext ctx, Entity npc) {
        return List.of();
    }

    /** The player picked one of this quest's dialogue replies. Return what the NPC answers, or empty to ignore it. */
    default Optional<Component> onDialogue(QuestContext ctx, Entity npc, String option) {
        return Optional.empty();
    }

    /** Entities this quest wants the player to find, marked above their heads. */
    default List<UUID> markedEntities(QuestContext ctx) {
        return List.of();
    }

    /** Where the player should head while the objective is open (the quest's anchor by default). */
    default Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        return ctx.storedAnchor().map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, Component.empty()));
    }

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
