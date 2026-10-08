package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.dialogue.DialogueConditionType;
import com.selluastar.fealty.api.dialogue.DialogueEffectType;
import com.selluastar.fealty.story.script.Conditions;
import com.selluastar.fealty.story.script.Effects;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Fealty's built-in dialogue conditions and effects, for conversations, rumours and chain steps. */
public final class ModDialogueScripts {
    public static final DeferredRegister<DialogueConditionType<?>> CONDITIONS =
            DeferredRegister.create(DialogueConditionType.REGISTRY_KEY, Fealty.MOD_ID);
    public static final DeferredRegister<DialogueEffectType<?>> EFFECTS = DeferredRegister.create(DialogueEffectType.REGISTRY_KEY, Fealty.MOD_ID);

    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.HasItem>> HAS_ITEM =
            CONDITIONS.register("has_item", () -> new DialogueConditionType<>(Conditions.HasItem.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.TierAtLeast>> TIER_AT_LEAST =
            CONDITIONS.register("tier_at_least", () -> new DialogueConditionType<>(Conditions.TierAtLeast.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.Flag>> FLAG =
            CONDITIONS.register("flag", () -> new DialogueConditionType<>(Conditions.Flag.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.Advancement>> ADVANCEMENT =
            CONDITIONS.register("advancement", () -> new DialogueConditionType<>(Conditions.Advancement.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.Dimension>> DIMENSION =
            CONDITIONS.register("dimension", () -> new DialogueConditionType<>(Conditions.Dimension.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.QuestStage>> QUEST_STAGE =
            CONDITIONS.register("quest_stage", () -> new DialogueConditionType<>(Conditions.QuestStage.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.TimeOfDay>> TIME_OF_DAY =
            CONDITIONS.register("time_of_day", () -> new DialogueConditionType<>(Conditions.TimeOfDay.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.All>> ALL =
            CONDITIONS.register("all", () -> new DialogueConditionType<>(Conditions.All.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.Any>> ANY =
            CONDITIONS.register("any", () -> new DialogueConditionType<>(Conditions.Any.CODEC));
    public static final DeferredHolder<DialogueConditionType<?>, DialogueConditionType<Conditions.Not>> NOT =
            CONDITIONS.register("not", () -> new DialogueConditionType<>(Conditions.Not.CODEC));

    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.TakeItem>> TAKE_ITEM =
            EFFECTS.register("take_item", () -> new DialogueEffectType<>(Effects.TakeItem.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.GiveItem>> GIVE_ITEM =
            EFFECTS.register("give_item", () -> new DialogueEffectType<>(Effects.GiveItem.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.SetFlag>> SET_FLAG =
            EFFECTS.register("set_flag", () -> new DialogueEffectType<>(Effects.SetFlag.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.ClearFlag>> CLEAR_FLAG =
            EFFECTS.register("clear_flag", () -> new DialogueEffectType<>(Effects.ClearFlag.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.AddRep>> ADD_REP =
            EFFECTS.register("add_rep", () -> new DialogueEffectType<>(Effects.AddRep.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.StartQuest>> START_QUEST =
            EFFECTS.register("start_quest", () -> new DialogueEffectType<>(Effects.StartQuest.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.GrantAdvancement>> GRANT_ADVANCEMENT =
            EFFECTS.register("grant_advancement", () -> new DialogueEffectType<>(Effects.GrantAdvancement.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.RunFunction>> RUN_FUNCTION =
            EFFECTS.register("run_function", () -> new DialogueEffectType<>(Effects.RunFunction.CODEC));
    public static final DeferredHolder<DialogueEffectType<?>, DialogueEffectType<Effects.StartChain>> START_CHAIN =
            EFFECTS.register("start_chain", () -> new DialogueEffectType<>(Effects.StartChain.CODEC));

    private ModDialogueScripts() {
    }
}
