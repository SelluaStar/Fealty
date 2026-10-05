package com.selluastar.fealty.chain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Marks a villager as one of the named villagers of a quest chain in their village. */
public record ChainRole(ResourceLocation chain, int step, ResourceLocation village) {
    public static final Codec<ChainRole> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("chain").forGetter(ChainRole::chain),
            Codec.INT.fieldOf("step").forGetter(ChainRole::step),
            ResourceLocation.CODEC.fieldOf("village").forGetter(ChainRole::village)
    ).apply(i, ChainRole::new));

    public static ChainRole none() {
        return new ChainRole(ResourceLocation.withDefaultNamespace("none"), -1, ResourceLocation.withDefaultNamespace("none"));
    }

    public boolean isNone() {
        return step < 0;
    }
}
