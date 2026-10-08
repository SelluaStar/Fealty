package com.selluastar.fealty.registry;

import com.selluastar.fealty.api.RepSource;
import com.selluastar.fealty.api.chain.ChainHandler;
import com.selluastar.fealty.api.dialogue.DialogueConditionType;
import com.selluastar.fealty.api.dialogue.DialogueEffectType;
import com.selluastar.fealty.api.quest.QuestGiver;
import com.selluastar.fealty.api.quest.QuestType;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Fealty's own registries. Other mods add entries with a DeferredRegister on these keys. */
public final class FealtyRegistries {
    public static final ResourceKey<Registry<QuestType<?>>> QUEST_TYPE_KEY = QuestType.REGISTRY_KEY;

    /** Ways to gain or lose reputation ({@code fealty:rep_source}). */
    public static final Registry<RepSource> REP_SOURCES = new RegistryBuilder<>(RepSource.REGISTRY_KEY).sync(false).create();
    /** Redemption quest types ({@code fealty:quest_type}), used by {@code fealty/rep_quests/} files. */
    public static final Registry<QuestType<?>> QUEST_TYPES = new RegistryBuilder<>(QUEST_TYPE_KEY).sync(false).create();
    /** Kinds of quest chain ({@code fealty:chain_kind}), named by {@code fealty/quest_chains/} files. */
    public static final Registry<ChainHandler> CHAIN_KINDS = new RegistryBuilder<>(ChainHandler.REGISTRY_KEY).sync(false).create();
    /** Conversation, rumour and chain step conditions ({@code fealty:dialogue_condition}). */
    public static final Registry<DialogueConditionType<?>> DIALOGUE_CONDITIONS =
            new RegistryBuilder<>(DialogueConditionType.REGISTRY_KEY).sync(false).create();
    /** Conversation and rumour effects ({@code fealty:dialogue_effect}). */
    public static final Registry<DialogueEffectType<?>> DIALOGUE_EFFECTS = new RegistryBuilder<>(DialogueEffectType.REGISTRY_KEY).sync(false).create();
    /** Quest givers other mods add for their own NPCs ({@code fealty:quest_giver}). */
    public static final Registry<QuestGiver> QUEST_GIVERS = new RegistryBuilder<>(QuestGiver.REGISTRY_KEY).sync(false).create();

    private FealtyRegistries() {
    }

    static void onNewRegistry(NewRegistryEvent event) {
        event.register(REP_SOURCES);
        event.register(QUEST_TYPES);
        event.register(CHAIN_KINDS);
        event.register(DIALOGUE_CONDITIONS);
        event.register(DIALOGUE_EFFECTS);
        event.register(QUEST_GIVERS);
    }
}
