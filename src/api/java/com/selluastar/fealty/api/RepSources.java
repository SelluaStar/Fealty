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
    /** Small favors done for ordinary villagers. */
    public static final ResourceLocation FAVOR = FealtyApi.id("favor");
    /** A lord's feast: the village's thanks to the lord and to everyone who comes. */
    public static final ResourceLocation FEAST = FealtyApi.id("feast");
    /** Killing a monster in a village. */
    public static final ResourceLocation KILL_MONSTER = FealtyApi.id("kill_monster");
    /** Killing something that was attacking a villager. */
    public static final ResourceLocation DEFEND_VILLAGER = FealtyApi.id("defend_villager");
    /** Healing a hurt villager with a potion. */
    public static final ResourceLocation HEAL_VILLAGER = FealtyApi.id("heal_villager");
    /** Ringing the bell to warn of monsters or raiders. */
    public static final ResourceLocation RING_BELL = FealtyApi.id("ring_bell");
    /** Mending a village iron golem. */
    public static final ResourceLocation REPAIR_GOLEM = FealtyApi.id("repair_golem");
    /** Lighting dark corners of a village. */
    public static final ResourceLocation LIGHT_VILLAGE = FealtyApi.id("light_village");
    /** Giving a village a new bed (a new home). */
    public static final ResourceLocation PLACE_BED = FealtyApi.id("place_bed");
    /** Planting or bonemealing a village's crops. */
    public static final ResourceLocation TEND_CROPS = FealtyApi.id("tend_crops");
    /** Giving emeralds to a village coffer (per 8). */
    public static final ResourceLocation DONATE = FealtyApi.id("donate");
    /** Killing a bandit near a village. */
    public static final ResourceLocation KILL_BANDIT = FealtyApi.id("kill_bandit");
    /** Killing raiders near a village. */
    public static final ResourceLocation KILL_RAIDER = FealtyApi.id("kill_raider");
    /** Paying a fine to the elder or a guard. */
    public static final ResourceLocation FINE = FealtyApi.id("fine");
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
    /** Trampling a village's farmland. */
    public static final ResourceLocation TRAMPLE_CROPS = FealtyApi.id("trample_crops");
    /** Harvesting a village's crops (per 8). */
    public static final ResourceLocation HARVEST_CROPS = FealtyApi.id("harvest_crops");
    /** Killing a village's animals. */
    public static final ResourceLocation KILL_ANIMAL = FealtyApi.id("kill_animal");
    /** Killing a village cat. */
    public static final ResourceLocation KILL_CAT = FealtyApi.id("kill_cat");
    /** Setting fire, or pouring lava, in a village. */
    public static final ResourceLocation ARSON = FealtyApi.id("arson");
    /** Blowing up part of a village. */
    public static final ResourceLocation EXPLOSION = FealtyApi.id("explosion");
    /** Entering villagers' homes at night. */
    public static final ResourceLocation TRESPASS = FealtyApi.id("trespass");
    /** Sleeping in a villager's bed. */
    public static final ResourceLocation SLEEP_IN_BED = FealtyApi.id("sleep_in_bed");
    /** Hitting a village child. */
    public static final ResourceLocation HIT_CHILD = FealtyApi.id("hit_child");
    /** Killing a village child. */
    public static final ResourceLocation KILL_CHILD = FealtyApi.id("kill_child");
    /** Killing a wandering trader. */
    public static final ResourceLocation KILL_TRADER = FealtyApi.id("kill_trader");
    /** Breaking a village's bell, coffer or mailbox. */
    public static final ResourceLocation BREAK_FIXTURE = FealtyApi.id("break_fixture");
    /** Attacking a village guard. */
    public static final ResourceLocation HIT_GUARD = FealtyApi.id("hit_guard");
    /** Being heard picking a village lock. */
    public static final ResourceLocation LOCKPICKING = FealtyApi.id("lockpicking");
    /** Word of a crime reaching other villages. */
    public static final ResourceLocation WORD_TRAVELS = FealtyApi.id("word_travels");
    // Other
    public static final ResourceLocation ABANDON_QUEST = FealtyApi.id("abandon_quest");
    public static final ResourceLocation COMMAND = FealtyApi.id("command");
    public static final ResourceLocation API = FealtyApi.id("api");

    private RepSources() {
    }
}
