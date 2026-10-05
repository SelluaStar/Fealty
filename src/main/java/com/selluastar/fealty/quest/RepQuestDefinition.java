package com.selluastar.fealty.quest;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.registry.FealtyRegistries;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * A quest from {@code data/<ns>/fealty/rep_quests/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "title": {"translate": "quest.fealty.bread_for_the_hall"},
 *   "description": {"translate": "quest.fealty.bread_for_the_hall.desc"},
 *   "pool": "fealty:redemption",
 *   "tiers": { "fealty:distrusted": 10, "fealty:neutral": 10 },
 *   "difficulty": 1,
 *   "reward": { "rep": 6, "loot_table": "fealty:quest_rewards/common" },
 *   "time_limit": 0,
 *   "objective": { "type": "fealty:fetch", "items": [ { "item": "minecraft:bread", "count": 24 } ] }
 * }
 * }</pre>
 * {@code tiers} maps the tiers the quest is offered at to its weight there (higher is more likely). An empty
 * map offers it at every tier with weight 10. Quests in pool {@code fealty:none} are only used by chains.
 */
public record RepQuestDefinition(Component title, Component description, ResourceLocation pool, Map<ResourceLocation, Integer> tiers,
                                 int difficulty, QuestReward reward, int timeLimit, int failRep, QuestObjective objective) {
    public static final ResourceLocation REDEMPTION = Fealty.id("redemption");
    public static final ResourceLocation RESTORE = Fealty.id("restore");
    public static final ResourceLocation NONE = Fealty.id("none");

    public static final Codec<QuestObjective> OBJECTIVE_CODEC = FealtyRegistries.QUEST_TYPES.byNameCodec()
            .dispatch(QuestObjective::type, QuestType::codec);

    public static final Codec<RepQuestDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("title").forGetter(RepQuestDefinition::title),
            ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(RepQuestDefinition::description),
            ResourceLocation.CODEC.optionalFieldOf("pool", REDEMPTION).forGetter(RepQuestDefinition::pool),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(0, 10000)).optionalFieldOf("tiers", Map.of()).forGetter(RepQuestDefinition::tiers),
            Codec.intRange(1, 5).optionalFieldOf("difficulty", 1).forGetter(RepQuestDefinition::difficulty),
            QuestReward.CODEC.optionalFieldOf("reward", QuestReward.NONE).forGetter(RepQuestDefinition::reward),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("time_limit", 0).forGetter(RepQuestDefinition::timeLimit),
            Codec.INT.optionalFieldOf("fail_rep", 0).forGetter(RepQuestDefinition::failRep),
            OBJECTIVE_CODEC.fieldOf("objective").forGetter(RepQuestDefinition::objective)
    ).apply(i, RepQuestDefinition::new));

    /** Weight of this quest at a tier, or 0 if it is not offered there. */
    public int weightAt(ResourceLocation tier) {
        if (tiers.isEmpty()) {
            return 10;
        }
        return tiers.getOrDefault(tier, 0);
    }

    public ResourceLocation typeId() {
        ResourceLocation id = FealtyRegistries.QUEST_TYPES.getKey(objective.type());
        return id != null ? id : Fealty.id("unknown");
    }
}
