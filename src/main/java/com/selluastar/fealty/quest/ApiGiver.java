package com.selluastar.fealty.quest;

import java.util.List;

import com.selluastar.fealty.api.quest.QuestGiver.Board;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.QuestActionPayload;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** A quest giver another mod registered through the API: Fealty runs its board. */
public record ApiGiver(com.selluastar.fealty.api.quest.QuestGiver delegate) implements QuestGiver {
    @Override
    public OpenQuestScreenPayload screen(ServerPlayer player, Entity entity) {
        Board board = delegate.board(player, entity);
        List<OpenQuestScreenPayload.QuestEntry> quests = QuestGivers.entries(player, board.key(), board.offers(), board.perGiver());
        List<OpenQuestScreenPayload.ActionEntry> actions = board.actions().stream()
                .map(a -> new OpenQuestScreenPayload.ActionEntry(a.id(), a.label(), a.enabled(), a.hint())).toList();
        RepManager.meet(player, board.faction());
        return new OpenQuestScreenPayload(entity.getId(), entity.getDisplayName(), board.subtitle(), board.greeting(),
                RepManager.getRep(player, board.faction()), true, quests, actions);
    }

    @Override
    public void handleAction(ServerPlayer player, Entity entity, String action, String argument) {
        Board board = delegate.board(player, entity);
        ResourceLocation quest = ResourceLocation.tryParse(argument);
        switch (action) {
            case QuestActionPayload.ACCEPT -> {
                if (quest != null && board.offers().contains(quest)) {
                    QuestManager.accept(player, board.key(), board.faction(), quest, ElderGiver.giverState(entity), board.perGiver());
                }
            }
            case QuestActionPayload.TURN_IN -> QuestManager.turnIn(player, board.key(), quest);
            case QuestActionPayload.ABANDON -> QuestManager.abandon(player, board.key(), quest, false);
            default -> {
                if (board.actions().stream().anyMatch(a -> a.id().equals(action) && a.enabled())) {
                    delegate.onAction(player, entity, action);
                }
            }
        }
    }
}
