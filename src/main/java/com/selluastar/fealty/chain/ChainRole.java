package com.selluastar.fealty.chain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * Marks a villager as one of the named villagers of a quest chain in their village. The {@code role} (smith, fool,
 * ...) is what players' runs look for; {@code step} is only read from roles saved before roles had names. A
 * {@code fixed} role was given by a data pack or another mod (through the API) and is kept until taken away, rather
 * than dropped once no run needs it.
 */
public record ChainRole(ResourceLocation chain, int step, ResourceLocation village, String role, boolean fixed) {
    public static final Codec<ChainRole> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("chain").forGetter(ChainRole::chain),
            Codec.INT.optionalFieldOf("step", 0).forGetter(ChainRole::step),
            ResourceLocation.CODEC.fieldOf("village").forGetter(ChainRole::village),
            Codec.STRING.optionalFieldOf("role", "").forGetter(ChainRole::role),
            Codec.BOOL.optionalFieldOf("fixed", false).forGetter(ChainRole::fixed)
    ).apply(i, ChainRole::new));

    public static ChainRole of(ResourceLocation chain, ResourceLocation village, String role) {
        return new ChainRole(chain, 0, village, role, false);
    }

    public static ChainRole none() {
        return new ChainRole(ResourceLocation.withDefaultNamespace("none"), -1, ResourceLocation.withDefaultNamespace("none"), "", false);
    }

    public boolean isNone() {
        return step < 0;
    }
}
