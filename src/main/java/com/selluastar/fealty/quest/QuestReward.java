package com.selluastar.fealty.quest;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/** What finishing a quest gives: reputation with the quest's faction, Renown, a loot table roll and items. */
public record QuestReward(int rep, int renown, Optional<ResourceKey<LootTable>> lootTable, List<ItemStack> items, int experience) {
    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("rep", 10).forGetter(QuestReward::rep),
            Codec.INT.optionalFieldOf("renown", 0).forGetter(QuestReward::renown),
            ResourceKey.codec(Registries.LOOT_TABLE).optionalFieldOf("loot_table").forGetter(QuestReward::lootTable),
            ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestReward::items),
            Codec.intRange(0, 100000).optionalFieldOf("experience", 0).forGetter(QuestReward::experience)
    ).apply(i, QuestReward::new));

    public static final QuestReward NONE = new QuestReward(0, 0, Optional.empty(), List.of(), 0);
}
