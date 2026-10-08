package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.CrimeTrigger;
import com.selluastar.fealty.advancement.FealtyEventTrigger;
import com.selluastar.fealty.advancement.QuestTrigger;
import com.selluastar.fealty.advancement.RenownTrigger;
import com.selluastar.fealty.advancement.RepTierTrigger;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCriteria {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Fealty.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, RepTierTrigger> REP_TIER = TRIGGERS.register("rep_tier", RepTierTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, RenownTrigger> RENOWN = TRIGGERS.register("renown", RenownTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, CrimeTrigger> CRIME = TRIGGERS.register("crime", CrimeTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, QuestTrigger> QUEST = TRIGGERS.register("rep_quest", QuestTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, FealtyEventTrigger> EVENT = TRIGGERS.register("event", FealtyEventTrigger::new);

    private ModCriteria() {
    }
}
