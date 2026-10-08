package com.selluastar.fealty.quest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.api.dialogue.DialogueOption;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.dialogue.DialogueNode;
import com.selluastar.fealty.network.QuestView;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** An objective another mod registered through the API, seen as one of Fealty's own. */
public record ApiObjective(com.selluastar.fealty.api.quest.QuestObjective delegate) implements QuestObjective {
    /** Fealty's own objectives as they are; anything else wrapped. */
    public static QuestObjective wrap(com.selluastar.fealty.api.quest.QuestObjective objective) {
        return objective instanceof QuestObjective own ? own : new ApiObjective(objective);
    }

    public static com.selluastar.fealty.api.quest.QuestObjective unwrap(QuestObjective objective) {
        return objective instanceof ApiObjective api ? api.delegate() : objective;
    }

    @Override
    public QuestType<?> type() {
        return delegate.type();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return delegate.start(ctx);
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        return delegate.describe(ctx);
    }

    @Override
    public List<Component> preview() {
        return delegate.preview();
    }

    @Override
    public List<DialogueNode.Option> dialogueOptions(QuestContext ctx, Entity npc) {
        return delegate.dialogueReplies(ctx, npc).stream().map(ApiObjective::option).toList();
    }

    private static DialogueNode.Option option(DialogueOption option) {
        return new DialogueNode.Option(option.id(), option.label(), option.icon(), option.enabled(), option.hint());
    }

    @Override
    public Optional<Component> onDialogue(QuestContext ctx, Entity npc, String option) {
        return delegate.onDialogue((com.selluastar.fealty.api.quest.QuestContext) ctx, npc, option);
    }

    @Override
    public List<UUID> markedEntities(QuestContext ctx) {
        return delegate.markedEntities((com.selluastar.fealty.api.quest.QuestContext) ctx);
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        Optional<QuestView.Waypoint> own = delegate.trackedPosition(ctx)
                .map(pos -> new QuestView.Waypoint(pos.dimension(), pos.pos(), Component.empty()));
        return own.isPresent() ? own : QuestObjective.super.waypoint(ctx);
    }

    @Override
    public boolean canTurnIn(QuestContext ctx) {
        return delegate.canTurnIn((com.selluastar.fealty.api.quest.QuestContext) ctx);
    }

    @Override
    public TurnIn onTurnIn(QuestContext ctx) {
        return delegate.onTurnIn((com.selluastar.fealty.api.quest.QuestContext) ctx);
    }

    @Override
    public boolean completesOnReady() {
        return delegate.completesOnReady();
    }

    @Override
    public void cleanup(QuestContext ctx, boolean success) {
        delegate.cleanup((com.selluastar.fealty.api.quest.QuestContext) ctx, success);
    }

    @Override
    public void tick(QuestContext ctx) {
        delegate.tick((com.selluastar.fealty.api.quest.QuestContext) ctx);
    }

    @Override
    public void onKill(QuestContext ctx, LivingEntity victim) {
        delegate.onKill((com.selluastar.fealty.api.quest.QuestContext) ctx, victim);
    }

    @Override
    public void onTargetLost(QuestContext ctx, LivingEntity target) {
        delegate.onTargetLost((com.selluastar.fealty.api.quest.QuestContext) ctx, target);
    }

    @Override
    public void onBlockPlaced(QuestContext ctx, ServerLevel level, BlockPos pos, BlockState state) {
        delegate.onBlockPlaced((com.selluastar.fealty.api.quest.QuestContext) ctx, level, pos, state);
    }

    @Override
    public void onBlockBroken(QuestContext ctx, ServerLevel level, BlockPos pos, BlockState state) {
        delegate.onBlockBroken((com.selluastar.fealty.api.quest.QuestContext) ctx, level, pos, state);
    }

    @Override
    public boolean onUseItemOnVillager(QuestContext ctx, Villager villager, ItemStack stack) {
        return delegate.onUseItemOnVillager((com.selluastar.fealty.api.quest.QuestContext) ctx, villager, stack);
    }
}
