package com.selluastar.fealty.registry;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.chain.ChainHandler;
import com.selluastar.fealty.chain.ChainKinds;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Fealty's own kinds of quest chain ({@code fealty:chain_kind}). */
public final class ModChainKinds {
    public static final DeferredRegister<ChainHandler> KINDS = DeferredRegister.create(ChainHandler.REGISTRY_KEY, Fealty.MOD_ID);

    /** The elder's rumours, a run of named villagers, the hidden hamlet, the Keeper's trial and the Royal Writ. */
    public static final DeferredHolder<ChainHandler, ChainHandler> RARE_VILLAGER = KINDS.register("rare_villager", ChainKinds.RareVillager::new);
    /** The thieves guild fence's jobs, one after another. */
    public static final DeferredHolder<ChainHandler, ChainHandler> GUILD = KINDS.register("guild", ChainKinds.Guild::new);
    /** Steps in order, then the final reward. */
    public static final DeferredHolder<ChainHandler, ChainHandler> STORY = KINDS.register("story", ChainKinds.Story::new);

    private ModChainKinds() {
    }
}
