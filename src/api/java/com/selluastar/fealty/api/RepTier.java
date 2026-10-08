package com.selluastar.fealty.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A reputation tier loaded from data packs ({@code data/<ns>/fealty/tiers/}).
 *
 * @param id              tier id, e.g. {@link RepTiers#TRUSTED}
 * @param displayName     name shown to players
 * @param min             lowest reputation in this tier (inclusive)
 * @param max             highest reputation in this tier (inclusive)
 * @param priceMultiplier trade price multiplier villagers apply at this tier
 * @param rank            ordering of tiers, lowest first; compare tiers with {@link #isAtLeast}
 * @param color           RGB colour used in UI
 * @param guardStance     how guards treat players at this tier
 */
public record RepTier(ResourceLocation id, Component displayName, int min, int max, float priceMultiplier,
                      int rank, int color, GuardStance guardStance) {

    public boolean contains(int rep) {
        return rep >= min && rep <= max;
    }

    public boolean isAtLeast(RepTier other) {
        return rank >= other.rank;
    }

    public boolean isAtMost(RepTier other) {
        return rank <= other.rank;
    }

    public boolean is(ResourceLocation tierId) {
        return id.equals(tierId);
    }
}
