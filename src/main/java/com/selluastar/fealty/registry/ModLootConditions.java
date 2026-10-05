package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.loot.RepTierCondition;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModLootConditions {
    public static final DeferredRegister<LootItemConditionType> CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, Fealty.MOD_ID);

    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> REP_TIER =
            CONDITIONS.register("rep_tier", () -> new LootItemConditionType(RepTierCondition.CODEC));

    private ModLootConditions() {
    }
}
