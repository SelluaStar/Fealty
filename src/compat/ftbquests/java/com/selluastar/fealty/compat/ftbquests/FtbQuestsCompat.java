package com.selluastar.fealty.compat.ftbquests;

import com.selluastar.fealty.Fealty;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;

/**
 * FTB Quests integration: a {@code fealty:reputation} task (reach a tier or rep with a faction, the village you
 * stand in, any village, or Renown) and a {@code fealty:reputation} reward (change rep).
 */
public final class FtbQuestsCompat {
    public static TaskType REPUTATION_TASK;
    public static RewardType REPUTATION_REWARD;

    private FtbQuestsCompat() {
    }

    public static void init(IEventBus modBus) {
        REPUTATION_TASK = TaskTypes.register(Fealty.id("reputation"), ReputationTask::new, () -> ItemIcon.getItemIcon(Items.EMERALD));
        REPUTATION_REWARD = RewardTypes.register(Fealty.id("reputation"), ReputationReward::new, () -> ItemIcon.getItemIcon(Items.EMERALD));
    }
}
