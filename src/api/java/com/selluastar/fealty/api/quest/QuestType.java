package com.selluastar.fealty.api.quest;

import com.mojang.serialization.MapCodec;
import com.selluastar.fealty.api.FealtyApi;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * A kind of quest objective, registered in {@code fealty:quest_type}. Register your own with a {@code DeferredRegister}
 * on {@link #REGISTRY_KEY}; data packs then use it from {@code fealty/rep_quests/} files with
 * {@code "objective": {"type": "<your id>", ...}}, and the rest of the file decodes with {@link #codec()}.
 *
 * @since API 1.3.0 (in the main jar before)
 */
public record QuestType<T extends QuestObjective>(MapCodec<T> codec) {
    public static final ResourceKey<Registry<QuestType<?>>> REGISTRY_KEY = ResourceKey.createRegistryKey(FealtyApi.id("quest_type"));
}
