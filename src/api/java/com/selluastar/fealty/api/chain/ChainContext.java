package com.selluastar.fealty.api.chain;

import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * One player's run of a quest chain, as its {@link ChainHandler} sees it.
 *
 * @since API 1.3.0
 */
public interface ChainContext {
    ServerPlayer player();

    /** The chain's id ({@code quest_chains/} file). */
    ResourceLocation chain();

    /** The chain's kind ({@code fealty:chain_kind} id). */
    ResourceLocation kind();

    /** The village the run started in. */
    Optional<ResourceLocation> origin();

    int stage();

    /** Move the chain to another stage. Fires {@code ChainStageEvent.Advance}. */
    void setStage(int stage);

    /** How many steps this run has. */
    int stepCount();

    /** The steps handed in so far (indices into the run). */
    Set<Integer> stepsDone();

    default boolean allStepsDone() {
        return stepCount() > 0 && stepsDone().size() >= stepCount();
    }

    /** The quest picked for a step of this run. */
    Optional<ResourceLocation> stepQuest(int step);

    /** How many times the player has completed the chain before this run. */
    int timesCompleted();

    /** Complete the chain now: {@code final_reward}, stage {@link ChainHandler#STAGE_DONE}, {@code ChainStageEvent.Complete}. */
    void complete();
}
