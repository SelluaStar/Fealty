package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepSource;
import com.selluastar.fealty.api.RepSources;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Built-in rep sources with the design document's default amounts. Data packs can retune every one. */
public final class ModRepSources {
    public static final DeferredRegister<RepSource> SOURCES = DeferredRegister.create(RepSource.REGISTRY_KEY, Fealty.MOD_ID);

    static {
        // Gains. Only redemption sources may raise reputation that is below zero.
        register(RepSources.QUEST, RepSource.redemption(10));
        register(RepSources.COURIER, RepSource.redemption(3));
        register(RepSources.LIBERATION, RepSource.redemption(25));
        register(RepSources.DEFEND_RAID, RepSource.action(20));
        register(RepSources.GIFT, RepSource.action(2));
        register(RepSources.TRADE, RepSource.action(1));
        register(RepSources.CURE_VILLAGER, RepSource.action(5));
        register(RepSources.TAX, RepSource.action(0));
        register(RepSources.FAVOR, RepSource.redemption(2));
        // Crimes: need a witness and alert guards.
        register(RepSources.BREAK_BLOCK, RepSource.crime(-5));
        register(RepSources.STEAL, RepSource.crime(-15));
        register(RepSources.VAULT_RAID, RepSource.crime(-20));
        register(RepSources.PICKPOCKET, RepSource.crime(-8));
        register(RepSources.THREATEN, RepSource.crime(-3));
        register(RepSources.HIT_VILLAGER, RepSource.crime(-10));
        register(RepSources.KILL_GUARD, RepSource.crime(-50));
        register(RepSources.KILL_VILLAGER, RepSource.crime(-40));
        register(RepSources.KILL_ELDER, RepSource.crime(-60));
        register(RepSources.KILL_MEMBER, RepSource.crime(-20));
        // Other
        register(RepSources.ABANDON_QUEST, RepSource.action(-2));
        register(RepSources.COMMAND, RepSource.redemption(0));
        register(RepSources.API, RepSource.action(0));
    }

    private ModRepSources() {
    }

    private static void register(ResourceLocation id, RepSource source) {
        SOURCES.register(id.getPath(), () -> source);
    }
}
