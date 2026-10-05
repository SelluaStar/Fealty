package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepSource;
import com.selluastar.fealty.quest.QuestType;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Fealty's own registries. Other mods add entries with a DeferredRegister on these keys. */
public final class FealtyRegistries {
    public static final ResourceKey<Registry<QuestType<?>>> QUEST_TYPE_KEY = ResourceKey.createRegistryKey(Fealty.id("quest_type"));

    /** Ways to gain or lose reputation ({@code fealty:rep_source}). */
    public static final Registry<RepSource> REP_SOURCES = new RegistryBuilder<>(RepSource.REGISTRY_KEY).sync(false).create();
    /** Redemption quest types ({@code fealty:quest_type}), used by {@code fealty/rep_quests/} files. */
    public static final Registry<QuestType<?>> QUEST_TYPES = new RegistryBuilder<>(QUEST_TYPE_KEY).sync(false).create();

    private FealtyRegistries() {
    }

    static void onNewRegistry(NewRegistryEvent event) {
        event.register(REP_SOURCES);
        event.register(QUEST_TYPES);
    }
}
