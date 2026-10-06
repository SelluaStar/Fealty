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
    public static final ModConfigSpec.BooleanValue NEGLECT;
    public static final ModConfigSpec.IntValue NEGLECT_GRACE_DAYS;
    public static final ModConfigSpec.IntValue NEGLECT_FIRST;
    public static final ModConfigSpec.IntValue NEGLECT_DAILY;
    public static final ModConfigSpec.IntValue NEGLECT_FLOOR;
    public static final ModConfigSpec.IntValue GUARDS_FORGIVE_AT;
    public static final ModConfigSpec.IntValue LORD_GRACE_DAYS;
    public static final ModConfigSpec.IntValue WAR_RANGE;
    public static final ModConfigSpec.IntValue WARBAND_SIZE;
    public static final ModConfigSpec.IntValue LEVY_PER_VILLAGERS;
    public static final ModConfigSpec.IntValue MAX_LEVY;
    public static final ModConfigSpec.IntValue RAID_COST_EMERALDS;
    public static final ModConfigSpec.IntValue SCOUT_COST_EMERALDS;
    public static final ModConfigSpec.IntValue CAMPAIGN_COOLDOWN_DAYS;
    public static final ModConfigSpec.IntValue CAMPAIGN_DAYS;
    public static final ModConfigSpec.IntValue RAZE_DAYS;
    public static final ModConfigSpec.IntValue PEACE_DAYS;
    public static final ModConfigSpec.IntValue WAR_SPOILS;
    public static final ModConfigSpec.BooleanValue PILLAGER_MENACE;
    public static final ModConfigSpec.IntValue MENACE_RANGE;
    public static final ModConfigSpec.IntValue MENACE_MIN_DAYS;
    public static final ModConfigSpec.IntValue MENACE_MAX_DAYS;
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
    public static final ModConfigSpec.BooleanValue FINES;
    public static final ModConfigSpec.IntValue GUARD_FINE_SECONDS;
    public static final ModConfigSpec.DoubleValue WORD_TRAVELS_SHARE;
    public static final ModConfigSpec.IntValue WORD_TRAVELS_RADIUS;
    public static final ModConfigSpec.BooleanValue HATED_ARRIVAL;
    public static final ModConfigSpec.BooleanValue LOCKED_VILLAGE_CHESTS;
    public static final ModConfigSpec.IntValue HORN_SUMMON_COUNT;
    public static final ModConfigSpec.IntValue HORN_ARRIVAL_SECONDS;
    public static final ModConfigSpec.BooleanValue VILLAGE_MAILBOXES;
    public static final ModConfigSpec.IntValue MAIL_BASE_DELAY;
    public static final ModConfigSpec.IntValue MAIL_DELAY_PER_100;
    public static final ModConfigSpec.IntValue MAIL_MAX_DELAY;

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
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> GIFT_CHANCE_BY_TAX;
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
    public static final ModConfigSpec.IntValue CAMP_EXCLUSION_RADIUS;
    public static final ModConfigSpec.BooleanValue BANDIT_RAIDS;
    public static final ModConfigSpec.IntValue RAID_RANGE;
    public static final ModConfigSpec.IntValue RAID_MIN_DAYS;
    public static final ModConfigSpec.IntValue RAID_MAX_DAYS;

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
        NEGLECT = b.comment("Standing with a village fades while you stay away from it").define("neglect", true);
        NEGLECT_GRACE_DAYS = b.comment("Days away from a village before its standing starts to fade")
                .defineInRange("neglect_grace_days", 2, 1, 1000);
        NEGLECT_FIRST = b.comment("Rep lost on the first day the grace runs out").defineInRange("neglect_first", 10, 0, 1000);
        NEGLECT_DAILY = b.comment("Rep lost on each later day away").defineInRange("neglect_daily", 5, 0, 1000);
        NEGLECT_FLOOR = b.comment("Staying away never takes standing below this (0: fades to Neutral at worst)")
                .defineInRange("neglect_floor", 0, -100000, 100000);
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
        GUARDS_FORGIVE_AT = b.comment("A player whose rep with a village was at least this before a crime is not attacked or fined for it;",
                        "the crime only costs rep (61: Honored players). Below it, guards warn or fine first and attack for severe",
                        "crimes, repeat offences or a refused fine.")
                .defineInRange("guards_forgive_at_rep", 61, -100000, 100000);
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
        FINES = b.comment("Guards demand a fine for minor and moderate crimes, and elders let players pay to clear their name")
                .define("fines", true);
        GUARD_FINE_SECONDS = b.comment("Seconds a player has to pay a guard's fine before the watch turns hostile")
                .defineInRange("guard_fine_seconds", 60, 5, 3600);
        WORD_TRAVELS_SHARE = b.comment("Share of a witnessed crime's reputation loss that reaches other villages the next day (0 to turn off)")
                .defineInRange("word_travels_share", 0.25, 0.0, 1.0);
        WORD_TRAVELS_RADIUS = b.comment("Villages within this many blocks of a crime hear of it")
                .defineInRange("word_travels_radius", 1000, 0, 100000);
        HATED_ARRIVAL = b.comment("When a Hated player walks into a village, the bell tolls and villagers run for their homes")
                .define("hated_arrival", true);
        LOCKED_VILLAGE_CHESTS = b.comment("Village chests and barrels are locked to players the village does not yet trust; a lockpick opens them.",
                        "The village coffer is always locked to everyone but the lord.")
                .define("locked_village_chests", true);
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

        b.comment("War: pillager camps and the lord's raids on them").push("war");
        WAR_RANGE = b.comment("How far from the village (blocks) a lord's warband will march, and its scouts search")
                .defineInRange("war_range", 1200, 100, 10000);
        WARBAND_SIZE = b.comment("Most of the village's guards that march with the lord").defineInRange("warband_size", 6, 1, 32);
        LEVY_PER_VILLAGERS = b.comment("One villager is called up as militia for every this many villagers")
                .defineInRange("levy_per_villagers", 8, 1, 1000);
        MAX_LEVY = b.comment("Most militia called up for one raid").defineInRange("max_levy", 4, 0, 32);
        RAID_COST_EMERALDS = b.comment("Emeralds to raise the warband (arms and food)").defineInRange("raid_cost_emeralds", 12, 0, 640);
        SCOUT_COST_EMERALDS = b.comment("Emeralds to send scouts out (once a day per village)").defineInRange("scout_cost_emeralds", 8, 0, 640);
        CAMPAIGN_COOLDOWN_DAYS = b.comment("Days a village rests between raids").defineInRange("campaign_cooldown_days", 3, 0, 1000);
        CAMPAIGN_DAYS = b.comment("Days a lord has to win a raid once it is declared").defineInRange("campaign_days", 3, 1, 1000);
        RAZE_DAYS = b.comment("Days a razed stronghold stays empty").defineInRange("raze_days", 10, 0, 10000);
        PEACE_DAYS = b.comment("Days of peace (no bandit raids, no pillager patrols) a victory wins the village")
                .defineInRange("peace_days", 7, 0, 10000);
        WAR_SPOILS = b.comment("Rolls of the tribute table a victory adds to the village treasury").defineInRange("war_spoils", 2, 0, 100);
        PILLAGER_MENACE = b.comment("Pillager camps and outposts left standing send raids against villages near them")
                .define("pillager_menace", true);
        MENACE_RANGE = b.comment("How far (blocks) a stronghold's menace reaches").defineInRange("menace_range", 600, 50, 10000);
        MENACE_MIN_DAYS = b.comment("Fewest days between a stronghold's raids on one village").defineInRange("menace_min_days", 5, 1, 1000);
        MENACE_MAX_DAYS = b.comment("Most days between a stronghold's raids on one village").defineInRange("menace_max_days", 8, 1, 1000);
        b.pop();

        b.comment("Village lordship").push("lordship");
        MAX_LORDSHIPS = b.comment("How many villages one player may rule at once").defineInRange("max_lordships", 2, 1, 100);
        LORD_GRACE_DAYS = b.comment("Days a lord may stay below Honored with their village before it renounces them")
                .defineInRange("lord_grace_days", 2, 0, 1000);
        TRIBUTE_INTERVAL_DAYS = b.comment("Days between tribute reports sent to the lord by letter (tribute itself is gathered daily)")
                .defineInRange("tribute_interval_days", 3, 1, 100);
        TRIBUTE_ROLLS_PER_10_VILLAGERS = b.comment("Tribute loot rolls gathered each day per 10 villagers at the 'fair' tax level")
                .defineInRange("tribute_rolls_per_10_villagers", 4, 0, 64);
        TAX_TRIBUTE_MULTIPLIERS = b.comment("Tribute multiplier for tax levels none, light, fair, heavy, crushing")
                .defineList("tribute_by_tax_level", List.of(0.1, 0.4, 1.0, 2.0, 3.5), () -> 1.0, o -> o instanceof Double d && d >= 0);
        GIFT_CHANCE_BY_TAX = b.comment("Daily chance of a gift of love from a village that loves its lord, for tax levels none, light, fair,",
                        "heavy, crushing (halved for a lord who is only Trusted, and more likely for one the village adores)")
                .defineList("gift_chance_by_tax_level", List.of(0.15, 0.10, 0.05, 0.01, 0.0), () -> 0.0,
                        o -> o instanceof Double d && d >= 0 && d <= 1);
        TAX_DAILY_REP = b.comment("Daily rep change for the lord at tax levels none, light, fair, heavy, crushing")
                .defineList("tax_daily_rep", List.of(2, 1, 0, -1, -3), () -> 0, o -> o instanceof Integer);
        MAX_COMMANDED_GUARDS = b.comment("Most guards a Lord's Horn can command at once").defineInRange("max_commanded_guards", 8, 1, 64);
        HORN_SUMMON_COUNT = b.comment("How many of the village's guards answer the horn's call, wherever the lord is")
                .defineInRange("horn_summon_count", 4, 0, 32);
        HORN_ARRIVAL_SECONDS = b.comment("Seconds before called guards arrive from the village")
                .defineInRange("horn_arrival_seconds", 5, 0, 600);
        b.pop();

        b.comment("Mail: mailboxes, letters and parcels").push("mail");
        VILLAGE_MAILBOXES = b.comment("Every village gets a mailbox when first visited (put back once a day if it goes missing)")
                .define("village_mailboxes", true);
        MAIL_BASE_DELAY = b.comment("Seconds every letter takes to arrive").defineInRange("base_delay_seconds", 30, 0, 86400);
        MAIL_DELAY_PER_100 = b.comment("Extra seconds for every 100 blocks a letter travels").defineInRange("seconds_per_100_blocks", 6, 0, 3600);
        MAIL_MAX_DELAY = b.comment("Longest a letter takes, in seconds").defineInRange("max_delay_seconds", 600, 0, 86400);
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
        CAMP_EXCLUSION_RADIUS = b.comment("Only one bandit camp within this many blocks is manned; the others stand empty")
                .defineInRange("camp_exclusion_radius", 768, 0, 100000);
        BANDIT_RAIDS = b.comment("Manned bandit camps raid nearby villages while a player is there").define("bandit_raids", true);
        RAID_RANGE = b.comment("Camps raid villages within this many blocks").defineInRange("raid_range", 640, 16, 100000);
        RAID_MIN_DAYS = b.comment("Fewest days between raids on one village").defineInRange("raid_min_days", 3, 1, 1000);
        RAID_MAX_DAYS = b.comment("Most days between raids on one village (while a player is there)").defineInRange("raid_max_days", 5, 1, 1000);
        b.pop();

        SPEC = b.build();
    }

    private FealtyConfig() {
    }

    public static double taxTributeMultiplier(int level) {
        List<? extends Double> list = TAX_TRIBUTE_MULTIPLIERS.get();
        return level >= 0 && level < list.size() ? list.get(level) : 1.0;
    }

    public static double giftChance(int level) {
        List<? extends Double> list = GIFT_CHANCE_BY_TAX.get();
        return level >= 0 && level < list.size() ? list.get(level) : 0.0;
    }

    public static int taxDailyRep(int level) {
        List<? extends Integer> list = TAX_DAILY_REP.get();
        return level >= 0 && level < list.size() ? list.get(level) : 0;
    }
}
