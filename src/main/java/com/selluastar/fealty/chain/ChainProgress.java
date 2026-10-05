package com.selluastar.fealty.chain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** A player's progress through one quest chain (the rare villager chain or the thieves guild). */
public final class ChainProgress {
    public static final Codec<ChainProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("chain").forGetter(ChainProgress::chainId),
            ResourceLocation.CODEC.optionalFieldOf("origin").forGetter(c -> Optional.ofNullable(c.origin)),
            Codec.INT.optionalFieldOf("stage", 0).forGetter(ChainProgress::stage),
            Codec.INT.listOf().optionalFieldOf("steps_done", List.of()).forGetter(c -> List.copyOf(c.stepsDone)),
            BlockPos.CODEC.optionalFieldOf("target").forGetter(c -> Optional.ofNullable(c.target)),
            Codec.INT.optionalFieldOf("times_completed", 0).forGetter(ChainProgress::timesCompleted),
            RunStep.CODEC.listOf().optionalFieldOf("run", List.of()).forGetter(c -> List.copyOf(c.run)),
            ResourceLocation.CODEC.optionalFieldOf("trial").forGetter(c -> Optional.ofNullable(c.trial))
    ).apply(i, (chain, origin, stage, steps, target, times, run, trial) -> {
        ChainProgress p = new ChainProgress(chain);
        p.origin = origin.orElse(null);
        p.stage = stage;
        p.stepsDone.addAll(steps);
        p.target = target.orElse(null);
        p.timesCompleted = times;
        p.run.addAll(run);
        p.trial = trial.orElse(null);
        return p;
    }));

    /**
     * One step of the current run: the role whose villager gives it, and the quest variant picked for it.
     */
    public record RunStep(String role, ResourceLocation quest) {
        public static final Codec<RunStep> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("role").forGetter(RunStep::role),
                ResourceLocation.CODEC.fieldOf("quest").forGetter(RunStep::quest)
        ).apply(i, RunStep::new));
    }

    private final ResourceLocation chainId;
    @Nullable
    private ResourceLocation origin;
    private int stage;
    private final Set<Integer> stepsDone = new HashSet<>();
    @Nullable
    private BlockPos target;
    private int timesCompleted;
    private final List<RunStep> run = new ArrayList<>();
    @Nullable
    private ResourceLocation trial;

    public ChainProgress(ResourceLocation chainId) {
        this.chainId = chainId;
    }

    public ResourceLocation chainId() {
        return chainId;
    }

    /** The village the chain started in; its tier is what the Keeper checks. */
    @Nullable
    public ResourceLocation origin() {
        return origin;
    }

    public void setOrigin(@Nullable ResourceLocation origin) {
        this.origin = origin;
    }

    public int stage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public Set<Integer> stepsDone() {
        return stepsDone;
    }

    @Nullable
    public BlockPos target() {
        return target;
    }

    public void setTarget(@Nullable BlockPos target) {
        this.target = target;
    }

    public int timesCompleted() {
        return timesCompleted;
    }

    /** The steps of the current run, in order; the index is the step number in quest keys and {@link #stepsDone()}. */
    public List<RunStep> run() {
        return run;
    }

    /** The index of the run step this role gives, or -1. */
    public int indexOf(String role) {
        for (int i = 0; i < run.size(); i++) {
            if (run.get(i).role().equals(role)) {
                return i;
            }
        }
        return -1;
    }

    /** The trial the Keeper set for this run. */
    @Nullable
    public ResourceLocation trial() {
        return trial;
    }

    public void setTrial(@Nullable ResourceLocation trial) {
        this.trial = trial;
    }

    public void reset() {
        stage = 0;
        stepsDone.clear();
        target = null;
        run.clear();
        trial = null;
        timesCompleted++;
    }
}
