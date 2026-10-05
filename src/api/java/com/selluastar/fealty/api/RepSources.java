package com.selluastar.fealty.api;

import net.minecraft.resources.ResourceLocation;

/** Ids of the rep sources Fealty registers. Pass them as the {@code reason} of {@link RepApi#addRep}. */
public final class RepSources {
    // Gains
    public static final ResourceLocation QUEST = FealtyApi.id("quest");
    public static final ResourceLocation DEFEND_RAID = FealtyApi.id("defend_raid");
    public static final ResourceLocation GIFT = FealtyApi.id("gift");
    public static final ResourceLocation TRADE = FealtyApi.id("trade");
    public static final ResourceLocation CURE_VILLAGER = FealtyApi.id("cure_villager");
    public static final ResourceLocation COURIER = FealtyApi.id("courier");
    public static final ResourceLocation LIBERATION = FealtyApi.id("liberation");
    public static final ResourceLocation TAX = FealtyApi.id("tax");
    // Crimes
    public static final ResourceLocation BREAK_BLOCK = FealtyApi.id("break_block");
    public static final ResourceLocation STEAL = FealtyApi.id("steal");
    public static final ResourceLocation VAULT_RAID = FealtyApi.id("vault_raid");
    public static final ResourceLocation PICKPOCKET = FealtyApi.id("pickpocket");
    public static final ResourceLocation THREATEN = FealtyApi.id("threaten");
    public static final ResourceLocation HIT_VILLAGER = FealtyApi.id("hit_villager");
    public static final ResourceLocation KILL_GUARD = FealtyApi.id("kill_guard");
    public static final ResourceLocation KILL_VILLAGER = FealtyApi.id("kill_villager");
    public static final ResourceLocation KILL_ELDER = FealtyApi.id("kill_elder");
    public static final ResourceLocation KILL_MEMBER = FealtyApi.id("kill_member");
    // Other
    public static final ResourceLocation ABANDON_QUEST = FealtyApi.id("abandon_quest");
    public static final ResourceLocation COMMAND = FealtyApi.id("command");
    public static final ResourceLocation API = FealtyApi.id("api");

    private RepSources() {
    }
}
