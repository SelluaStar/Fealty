package com.selluastar.fealty.quest;

import com.mojang.serialization.MapCodec;

/**
 * A kind of quest objective, registered in {@code fealty:quest_type}. Other mods can register their own and
 * use them from {@code fealty/rep_quests/} files with {@code "objective": {"type": "<their id>", ...}}.
 */
public record QuestType<T extends QuestObjective>(MapCodec<T> codec) {
}
