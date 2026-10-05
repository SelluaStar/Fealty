package com.selluastar.fealty.quest;

import com.selluastar.fealty.network.OpenQuestScreenPayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Something players get quests from: an elder, the Keeper, the guild fence or a named villager. */
public interface QuestGiver {
    OpenQuestScreenPayload screen(ServerPlayer player, Entity entity);

    void handleAction(ServerPlayer player, Entity entity, String action, String argument);
}
