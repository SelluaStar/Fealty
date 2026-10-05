package com.selluastar.fealty.quest;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** A quest a player has accepted. Type-specific progress lives in {@link #state()}. */
public final class ActiveQuest {
    public static final Codec<ActiveQuest> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("instance").forGetter(ActiveQuest::instanceId),
            ResourceLocation.CODEC.fieldOf("quest").forGetter(ActiveQuest::questId),
            ResourceLocation.CODEC.fieldOf("giver").forGetter(ActiveQuest::giver),
            ResourceLocation.CODEC.fieldOf("faction").forGetter(ActiveQuest::faction),
            Codec.LONG.fieldOf("start").forGetter(ActiveQuest::startTime),
            CompoundTag.CODEC.optionalFieldOf("state", new CompoundTag()).forGetter(ActiveQuest::state),
            Codec.BOOL.optionalFieldOf("ready", false).forGetter(ActiveQuest::isReady)
    ).apply(i, ActiveQuest::new));

    private final UUID instanceId;
    private final ResourceLocation questId;
    private final ResourceLocation giver;
    private final ResourceLocation faction;
    private final long startTime;
    private final CompoundTag state;
    private boolean ready;

    public ActiveQuest(UUID instanceId, ResourceLocation questId, ResourceLocation giver, ResourceLocation faction, long startTime,
                       CompoundTag state, boolean ready) {
        this.instanceId = instanceId;
        this.questId = questId;
        this.giver = giver;
        this.faction = faction;
        this.startTime = startTime;
        this.state = state.copy();
        this.ready = ready;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public ResourceLocation questId() {
        return questId;
    }

    /** The faction (or chain step key) the quest is turned in to. */
    public ResourceLocation giver() {
        return giver;
    }

    /** The faction whose reputation the quest's reward raises. */
    public ResourceLocation faction() {
        return faction;
    }

    public long startTime() {
        return startTime;
    }

    public CompoundTag state() {
        return state;
    }

    /** Whether the objective is done and the quest only needs turning in. */
    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }
}
