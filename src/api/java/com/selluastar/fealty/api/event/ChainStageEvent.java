package com.selluastar.fealty.api.event;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A player's progress through a quest chain ({@code fealty/quest_chains/}). Fired on {@code NeoForge.EVENT_BUS}; since
 * API 1.3.0. Stages are described on {@code ChainHandler}.
 */
public abstract class ChainStageEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation chain;
    private final int stage;

    protected ChainStageEvent(ServerPlayer player, ResourceLocation chain, int stage) {
        this.player = player;
        this.chain = chain;
        this.stage = stage;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getChain() {
        return chain;
    }

    /** The chain's stage (after the change, for {@link Advance}). */
    public int getStage() {
        return stage;
    }

    /** A chain is about to start for the player (stage 1). Cancel to stop it. */
    public static class Start extends ChainStageEvent implements ICancellableEvent {
        private final Optional<ResourceLocation> origin;

        public Start(ServerPlayer player, ResourceLocation chain, Optional<ResourceLocation> origin) {
            super(player, chain, 1);
            this.origin = origin;
        }

        /** The village it starts in. */
        public Optional<ResourceLocation> getOrigin() {
            return origin;
        }
    }

    /** A step was handed in, or the chain moved to another stage. */
    public static class Advance extends ChainStageEvent {
        /** {@link #getStep()} when the stage moved without a step being handed in. */
        public static final int NO_STEP = Integer.MIN_VALUE;
        /** {@link #getStep()} for the chain's trial. */
        public static final int TRIAL = -1;

        private final int previousStage;
        private final int step;

        public Advance(ServerPlayer player, ResourceLocation chain, int previousStage, int stage, int step) {
            super(player, chain, stage);
            this.previousStage = previousStage;
            this.step = step;
        }

        public int getPreviousStage() {
            return previousStage;
        }

        /** The step handed in (its index in the run), {@link #TRIAL}, or {@link #NO_STEP}. */
        public int getStep() {
            return step;
        }
    }

    /** The player completed the chain. */
    public static class Complete extends ChainStageEvent {
        private final int timesCompleted;

        public Complete(ServerPlayer player, ResourceLocation chain, int stage, int timesCompleted) {
            super(player, chain, stage);
            this.timesCompleted = timesCompleted;
        }

        /** How many times the player had completed it before this run. */
        public int getTimesCompleted() {
            return timesCompleted;
        }
    }
}
