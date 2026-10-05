package com.selluastar.fealty.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config ({@code serverconfig/fealty-server.toml}). Balance numbers that are not per-faction live here. */
public final class FealtyConfig {
    public static final ModConfigSpec SPEC;

    // Reputation
    public static final ModConfigSpec.IntValue REP_MIN;
    public static final ModConfigSpec.IntValue REP_MAX;
    public static final ModConfigSpec.DoubleValue RENOWN_SHARE;
    public static final ModConfigSpec.DoubleValue RENOWN_START_FACTOR;
    public static final ModConfigSpec.IntValue RENOWN_START_CAP;
    public static final ModConfigSpec.BooleanValue NEGATIVE_REP_DECAY;
    public static final ModConfigSpec.IntValue NEGATIVE_REP_DECAY_PER_DAY;
    public static final ModConfigSpec.BooleanValue DISABLE_VANILLA_GOSSIP;
    public static final ModConfigSpec.BooleanValue SHOW_REP_CHANGES;
    public static final ModConfigSpec.BooleanValue GIVE_LEDGER;

    // Witnesses and guards
    public static final ModConfigSpec.IntValue WITNESS_RADIUS;
    public static final ModConfigSpec.IntValue GUARD_ALERT_RADIUS;
    public static final ModConfigSpec.IntValue OUTSIDE_GUARD_RENOWN;
    public static final ModConfigSpec.IntValue GUARD_AGGRO_TICKS;
    public static final ModConfigSpec.IntValue GUARD_WARNING_WINDOW;
    public static final ModConfigSpec.IntValue ESCORT_TICKS;
    public static final ModConfigSpec.BooleanValue FEALTY_GUARDS;
    public static final ModConfigSpec.IntValue VILLAGERS_PER_GUARD;
    public static final ModConfigSpec.IntValue MIN_GUARDS;
    public static final ModConfigSpec.IntValue MAX_GUARDS;
    public static final ModConfigSpec.IntValue EMPTY_VILLAGE_GUARDS;
    public static final ModConfigSpec.IntValue SERGEANT_POPULATION;
    public static final ModConfigSpec.IntValue GUARD_RESPAWN_DAYS;
    public static final ModConfigSpec.IntValue HORN_SUMMON_COUNT;
    public static final ModConfigSpec.IntValue HORN_ARRIVAL_SECONDS;

    // Threats and trade
    public static final ModConfigSpec.IntValue THREAT_COOLDOWN;
    public static final ModConfigSpec.IntValue THREATS_BEFORE_REFUSAL;
    public static final ModConfigSpec.IntValue REFUSAL_TICKS;
    public static final ModConfigSpec.IntValue GIFT_COOLDOWN;
    public static final ModConfigSpec.IntValue HONORED_GIFT_COOLDOWN;
    public static final ModConfigSpec.BooleanValue VILLAGER_DIALOGUE;

    // Villages
    public static final ModConfigSpec.IntValue VILLAGE_MARGIN;
    public static final ModConfigSpec.IntValue BELL_VILLAGE_RADIUS;
    public static final ModConfigSpec.BooleanValue SPAWN_ELDERS;
    public static final ModConfigSpec.BooleanValue PLACE_COFFERS;
    public static final ModConfigSpec.BooleanValue BELL_VILLAGES_HAVE_ELDERS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_VILLAGE_STRUCTURES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_VILLAGE_STRUCTURES;
    public static final ModConfigSpec.BooleanValue ELDERS_ONLY_IN_TAGGED_VILLAGES;
    public static final ModConfigSpec.BooleanValue DETECT_SETTLEMENTS;
    public static final ModConfigSpec.IntValue SETTLEMENT_MIN_HOMES;
    public static final ModConfigSpec.IntValue SETTLEMENT_RADIUS;

    // Quests
    public static final ModConfigSpec.IntValue QUEST_OFFERS;
    public static final ModConfigSpec.IntValue COURIER_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue COURIER_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue RESTORE_SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_QUESTS;
    public static final ModConfigSpec.IntValue ELDER_QUESTS_AT_ONCE;
    public static final ModConfigSpec.BooleanValue FAVORS;
    public static final ModConfigSpec.DoubleValue FAVOR_CHANCE;

    // Lordship
    public static final ModConfigSpec.IntValue MAX_LORDSHIPS;
    public static final ModConfigSpec.IntValue TRIBUTE_INTERVAL_DAYS;
    public static final ModConfigSpec.IntValue TRIBUTE_ROLLS_PER_10_VILLAGERS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> TAX_TRIBUTE_MULTIPLIERS;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> TAX_DAILY_REP;
    public static final ModConfigSpec.IntValue MAX_COMMANDED_GUARDS;

    // Outlaw path
    public static final ModConfigSpec.IntValue BLACK_MARKET_MAX_RENOWN;
    public static final ModConfigSpec.IntValue THIEVES_GUILD_MAX_RENOWN;
    public static final ModConfigSpec.IntValue FOLLOWERS_MAX_RENOWN;
    public static final ModConfigSpec.IntValue MAX_FOLLOWERS;
    public static final ModConfigSpec.IntValue FOLLOWER_COST;
    public static final ModConfigSpec.IntValue HEAT_PER_MINUTE;
    public static final ModConfigSpec.IntValue HEAT_COOL_PER_MINUTE;
    public static final ModConfigSpec.IntValue HEAT_BOUNTY;
    public static final ModConfigSpec.IntValue HEAT_TYRANT;
    public static final ModConfigSpec.IntValue HEAT_MAX;
    public static final ModConfigSpec.IntValue BOUNTY_INTERVAL;
    public static final ModConfigSpec.BooleanValue ENABLE_TYRANT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Reputation scale and Renown").push("reputation");
        REP_MIN = b.comment("Lowest reputation any faction can reach").defineInRange("rep_min", -100, -100000, 0);
        REP_MAX = b.comment("Highest reputation any faction can reach").defineInRange("rep_max", 100, 0, 100000);
        RENOWN_SHARE = b.comment("Share of every rep change that also moves Renown (factions can override)")
                .defineInRange("renown_share", 0.10, 0.0, 1.0);
        RENOWN_START_FACTOR = b.comment("A newly met village starts at this fraction of your Renown (factions can override)")
                .defineInRange("renown_start_factor", 0.25, -1.0, 1.0);
        RENOWN_START_CAP = b.comment("Cap, either way, on the starting rep a new village gives you")
                .defineInRange("renown_start_cap", 25, 0, 100000);
        NEGATIVE_REP_DECAY = b.comment("Whether negative reputation slowly heals by itself (design default: off)")
                .define("negative_rep_decay", false);
        NEGATIVE_REP_DECAY_PER_DAY = b.comment("Rep healed per in-game day when negative_rep_decay is on")
                .defineInRange("negative_rep_decay_per_day", 1, 0, 100);
        DISABLE_VANILLA_GOSSIP = b.comment("Turn off vanilla villager gossip for prices and golems so the two systems do not stack")
                .define("disable_vanilla_gossip", true);
        SHOW_REP_CHANGES = b.comment("Show rep changes in the action bar").define("show_rep_changes", true);
        GIVE_LEDGER = b.comment("Give each player a Fealty Ledger the first time they join").define("give_ledger", true);
        b.pop();

        b.comment("Crimes, witnesses and guards").push("guards");
        WITNESS_RADIUS = b.comment("Blocks within which a villager or guard with line of sight witnesses a crime")
                .defineInRange("witness_radius", 16, 1, 128);
        GUARD_ALERT_RADIUS = b.comment("Blocks within which guards are alerted to a witnessed crime")
                .defineInRange("guard_alert_radius", 32, 1, 256);
        OUTSIDE_GUARD_RENOWN = b.comment("Guards of other villages only answer an alert if your Renown is at or below this")
                .defineInRange("outside_guard_renown", -21, -100000, 100000);
        GUARD_AGGRO_TICKS = b.comment("How long guards stay hostile after a crime (ticks)")
                .defineInRange("guard_aggro_ticks", 2400, 20, 240000);
        GUARD_WARNING_WINDOW = b.comment("A second offence within this many ticks of a warning turns guards hostile")
                .defineInRange("guard_warning_window", 6000, 20, 240000);
        ESCORT_TICKS = b.comment("How long a guard escorts an Honored player (ticks)")
                .defineInRange("escort_ticks", 24000, 20, 240000);
        FEALTY_GUARDS = b.comment("Villages keep a watch of Fealty guards (swordsmen, archers and a sergeant in the village's colours).",
                        "Iron golems and guards from other mods listed in #fealty:guards work either way.")
                .define("fealty_guards", true);
        VILLAGERS_PER_GUARD = b.comment("One guard for every this many villagers").defineInRange("villagers_per_guard", 4, 1, 100);
        MIN_GUARDS = b.comment("Fewest guards a village with villagers keeps").defineInRange("min_guards", 2, 0, 32);
        MAX_GUARDS = b.comment("Most guards a village keeps (not counting the sergeant)").defineInRange("max_guards", 6, 0, 32);
        EMPTY_VILLAGE_GUARDS = b.comment("Guards for a village with no villagers (a castle, say)").defineInRange("empty_village_guards", 3, 0, 32);
        SERGEANT_POPULATION = b.comment("Villages with at least this many villagers also have a sergeant (0 for never)")
                .defineInRange("sergeant_population", 12, 0, 1000);
        GUARD_RESPAWN_DAYS = b.comment("Days before a fallen guard is replaced").defineInRange("guard_respawn_days", 1, 0, 100);
        b.pop();

        b.comment("Threats, trades and gifts").push("trade");
        THREAT_COOLDOWN = b.comment("Cooldown per villager between threats (ticks, 24000 = 1 in-game day)")
                .defineInRange("threat_cooldown", 24000, 0, 2400000);
        THREATS_BEFORE_REFUSAL = b.comment("After this many threats a villager refuses to trade at all")
                .defineInRange("threats_before_refusal", 3, 1, 100);
        REFUSAL_TICKS = b.comment("How long a villager refuses to trade after too many threats (ticks)")
                .defineInRange("refusal_ticks", 24000, 0, 2400000);
        GIFT_COOLDOWN = b.comment("Cooldown per villager between gifts that raise rep (ticks)")
                .defineInRange("gift_cooldown", 24000, 0, 2400000);
        HONORED_GIFT_COOLDOWN = b.comment("Cooldown per villager between gifts villagers give Honored players (ticks)")
                .defineInRange("honored_gift_cooldown", 24000, 0, 2400000);
        VILLAGER_DIALOGUE = b.comment("Right-clicking a villager opens the dialogue box (trade, work, news, gifts).",
                        "Sneak-right-click still trades straight away. Off: right-click trades as in vanilla.")
                .define("villager_dialogue", true);
        b.pop();

        b.comment("Village detection and the trusting elder").push("villages");
        VILLAGE_MARGIN = b.comment("Extra blocks around a village structure that still count as the village")
                .defineInRange("village_margin", 16, 0, 64);
        BELL_VILLAGE_RADIUS = b.comment("Radius of a village found only by its bell (player-built or unknown structure)")
                .defineInRange("bell_village_radius", 48, 8, 160);
        SPAWN_ELDERS = b.comment("Spawn a trusting elder in villages whose structure is in #fealty:elder_villages")
                .define("spawn_elders", true);
        PLACE_COFFERS = b.comment("Place a village coffer next to the elder").define("place_coffers", true);
        BELL_VILLAGES_HAVE_ELDERS = b.comment("Whether villages found only by their bell also get an elder")
                .define("bell_villages_have_elders", false);
        EXTRA_VILLAGE_STRUCTURES = b.comment("More structures that count as villages, on top of the data pack templates.",
                        "Entries are structure ids (\"mymod:castle_town\"), tags (\"#mymod:towns\") or globs (\"mymod:*\", \"*:*keep*\").")
                .defineListAllowEmpty("extra_village_structures", List.of(), () -> "", o -> o instanceof String);
        EXCLUDED_VILLAGE_STRUCTURES = b.comment("Structures that never count as villages, whatever the templates say. Same entry format.")
                .defineListAllowEmpty("excluded_village_structures", List.of(), () -> "", o -> o instanceof String);
        ELDERS_ONLY_IN_TAGGED_VILLAGES = b.comment("Only villages whose structure is in #fealty:elder_villages get an elder.",
                        "Off by default, so every detected village (modded ones included) gets an elder.")
                .define("elders_only_in_tagged_villages", false);
        DETECT_SETTLEMENTS = b.comment("Treat a cluster of villager homes with villagers and a workstation as a village,",
                        "even with no bell and no known structure (for mods that build villages without bells).")
                .define("detect_settlements", true);
        SETTLEMENT_MIN_HOMES = b.comment("Villager beds needed nearby to count as a settlement")
                .defineInRange("settlement_min_homes", 3, 2, 32);
        SETTLEMENT_RADIUS = b.comment("Radius of a village found as a settlement")
                .defineInRange("settlement_radius", 40, 16, 160);
        b.pop();

        b.comment("Redemption quests").push("quests");
        QUEST_OFFERS = b.comment("How many quests an elder offers at once").defineInRange("quest_offers", 3, 1, 6);
        COURIER_MIN_DISTANCE = b.comment("Minimum distance to a courier quest's destination village")
                .defineInRange("courier_min_distance", 200, 0, 100000);
        COURIER_MAX_DISTANCE = b.comment("Maximum distance to a courier quest's destination village")
                .defineInRange("courier_max_distance", 3000, 100, 100000);
        RESTORE_SEARCH_RADIUS = b.comment("Radius in which a neighbouring elder offers to restore a broken village")
                .defineInRange("restore_search_radius", 3000, 100, 100000);
        MAX_ACTIVE_QUESTS = b.comment("Most quests a player can have accepted at once, across all givers")
                .defineInRange("max_active_quests", 8, 1, 32);
        ELDER_QUESTS_AT_ONCE = b.comment("How many of one elder's quests a player can take on at once")
                .defineInRange("elder_quests_at_once", 2, 1, 6);
        FAVORS = b.comment("Ordinary villagers ask small favors (one a day each, some days none)")
                .define("favors", true);
        FAVOR_CHANCE = b.comment("Chance a villager has a favor to ask on a given day")
                .defineInRange("favor_chance", 0.5, 0.0, 1.0);
        b.pop();

        b.comment("Village lordship").push("lordship");
        MAX_LORDSHIPS = b.comment("How many villages one player may rule at once").defineInRange("max_lordships", 2, 1, 100);
        TRIBUTE_INTERVAL_DAYS = b.comment("In-game days between tribute payments").defineInRange("tribute_interval_days", 3, 1, 100);
        TRIBUTE_ROLLS_PER_10_VILLAGERS = b.comment("Tribute loot rolls per 10 villagers at the 'fair' tax level")
                .defineInRange("tribute_rolls_per_10_villagers", 4, 0, 64);
        TAX_TRIBUTE_MULTIPLIERS = b.comment("Tribute multiplier for tax levels none, light, fair, heavy, crushing")
                .defineList("tax_tribute_multipliers", List.of(0.0, 0.5, 1.0, 1.5, 2.0), () -> 1.0, o -> o instanceof Double d && d >= 0);
        TAX_DAILY_REP = b.comment("Daily rep change for the lord at tax levels none, light, fair, heavy, crushing")
                .defineList("tax_daily_rep", List.of(2, 1, 0, -1, -3), () -> 0, o -> o instanceof Integer);
        MAX_COMMANDED_GUARDS = b.comment("Most guards a Lord's Horn can command at once").defineInRange("max_commanded_guards", 8, 1, 64);
        HORN_SUMMON_COUNT = b.comment("How many of the village's guards answer the horn's call, wherever the lord is")
                .defineInRange("horn_summon_count", 4, 0, 32);
        HORN_ARRIVAL_SECONDS = b.comment("Seconds before called guards arrive from the village")
                .defineInRange("horn_arrival_seconds", 5, 0, 600);
        b.pop();

        b.comment("Outlaw path: Renown gates, followers and wanted escalation").push("outlaw");
        BLACK_MARKET_MAX_RENOWN = b.comment("Black market vendors trade with players at or below this Renown (Distrusted)")
                .defineInRange("black_market_max_renown", -21, -100000, 100000);
        THIEVES_GUILD_MAX_RENOWN = b.comment("The thieves guild works with players at or below this Renown (Distrusted)")
                .defineInRange("thieves_guild_max_renown", -21, -100000, 100000);
        FOLLOWERS_MAX_RENOWN = b.comment("Bandits join players at or below this Renown (Hated)")
                .defineInRange("followers_max_renown", -61, -100000, 100000);
        MAX_FOLLOWERS = b.comment("Most bandit followers one player can hire").defineInRange("max_followers", 3, 0, 32);
        FOLLOWER_COST = b.comment("Emeralds to hire one bandit").defineInRange("follower_cost", 12, 0, 64);
        HEAT_PER_MINUTE = b.comment("Heat gained per minute spent Hated by a faction").defineInRange("heat_per_minute", 2, 0, 1000);
        HEAT_COOL_PER_MINUTE = b.comment("Heat lost per minute when above Hated").defineInRange("heat_cool_per_minute", 4, 0, 1000);
        HEAT_BOUNTY = b.comment("Heat at which bounty hunters are sent (wanted level 1)").defineInRange("heat_bounty", 20, 1, 100000);
        HEAT_TYRANT = b.comment("Heat at which the Tyrant Lord event starts (wanted level 2)").defineInRange("heat_tyrant", 60, 1, 100000);
        HEAT_MAX = b.comment("Heat cap").defineInRange("heat_max", 100, 1, 100000);
        BOUNTY_INTERVAL = b.comment("Ticks between bounty hunter parties while wanted").defineInRange("bounty_interval", 12000, 200, 2400000);
        ENABLE_TYRANT = b.comment("Enable the Tyrant Lord boss event").define("enable_tyrant", true);
        b.pop();

        SPEC = b.build();
    }

    private FealtyConfig() {
    }

    public static double taxTributeMultiplier(int level) {
        List<? extends Double> list = TAX_TRIBUTE_MULTIPLIERS.get();
        return level >= 0 && level < list.size() ? list.get(level) : 1.0;
    }

    public static int taxDailyRep(int level) {
        List<? extends Integer> list = TAX_DAILY_REP.get();
        return level >= 0 && level < list.size() ? list.get(level) : 0;
    }
}
