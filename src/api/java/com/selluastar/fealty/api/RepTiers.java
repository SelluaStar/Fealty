package com.selluastar.fealty.api;

import net.minecraft.resources.ResourceLocation;

/** Ids of the default tiers. Data packs may rename or re-range them but these ids are always present. */
public final class RepTiers {
    public static final ResourceLocation HATED = FealtyApi.id("hated");
    public static final ResourceLocation DISTRUSTED = FealtyApi.id("distrusted");
    public static final ResourceLocation NEUTRAL = FealtyApi.id("neutral");
    public static final ResourceLocation TRUSTED = FealtyApi.id("trusted");
    public static final ResourceLocation HONORED = FealtyApi.id("honored");

    private RepTiers() {
    }
}
