package com.selluastar.fealty.api.chain;

import com.selluastar.fealty.api.FealtyApi;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * A kind of quest chain, registered in {@code fealty:chain_kind}. A {@code fealty/quest_chains/} file names its kind
 * with {@code "kind"}; Fealty ships {@code fealty:rare_villager} (the elder's rumours, named villagers, the hidden
 * hamlet), {@code fealty:guild} (the thieves guild fence) and {@code fealty:story} (steps in order, then the final
 * reward). Register your own with a {@code DeferredRegister} on {@link #REGISTRY_KEY}.
 *
 * <p>Fealty does the bookkeeping every kind shares: it lays out a run of steps when the chain starts, gives the
 * steps to their villagers ({@code "giver": "per_step"}) or to one villager ({@code "giver": "single"}), marks a step
 * done and pays its {@code rewards} when it is handed in, and gives {@code final_reward} when the chain completes. A
 * handler adds what is special to its kind. Every method has a default, so {@code new ChainHandler() {}} is a chain
 * that completes when its last step is handed in.
 *
 * <p>Stages ({@link ChainContext#stage()}): {@link #STAGE_NONE} before the chain starts, {@link #STAGE_STEPS} once it
 * has, {@link #STAGE_DONE} when complete. Stages in between are the kind's own; rumours with a {@code "stage"} above 1
 * move a running chain on to them when {@link #canAdvance} allows it.
 *
 * @since API 1.3.0
 */
public interface ChainHandler {
    ResourceKey<Registry<ChainHandler>> REGISTRY_KEY = ResourceKey.createRegistryKey(FealtyApi.id("chain_kind"));

    int STAGE_NONE = 0;
    int STAGE_STEPS = 1;
    int STAGE_DONE = 5;

    /**
     * A player starts a chain of this kind (from a rumour, a conversation, the elder or the API). Fealty has already
     * laid out the run and set the stage to {@link #STAGE_STEPS}; return false to refuse, and Fealty puts everything
     * back as it was.
     */
    default boolean start(ChainContext ctx) {
        return true;
    }

    /**
     * A step was handed in. Fealty has already marked it done and paid the step's {@code rewards}. {@code step} is
     * the step's index in the run, or -1 for the chain's trial ({@code trial_quests}). Return true if the chain is now
     * complete; Fealty then gives {@code final_reward} and calls {@link #onChainComplete}.
     */
    default boolean onStepComplete(ChainContext ctx, int step) {
        return step >= 0 && ctx.allStepsDone();
    }

    /** The chain was completed. Fealty has already given {@code final_reward} and set the stage to {@link #STAGE_DONE}. */
    default void onChainComplete(ChainContext ctx) {
    }

    /** Whether a rumour may move a running chain of this kind on to {@code stage} (above {@link #STAGE_STEPS}). */
    default boolean canAdvance(ChainContext ctx, int stage) {
        return stage > ctx.stage() && stage < STAGE_DONE;
    }
}
