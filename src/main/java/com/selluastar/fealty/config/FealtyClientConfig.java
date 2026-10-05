package com.selluastar.fealty.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config ({@code config/fealty-client.toml}): HUD layout and visual preferences. */
public final class FealtyClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<TrackerMode> TRACKER_MODE;
    public static final ModConfigSpec.IntValue TRACKER_X;
    public static final ModConfigSpec.IntValue TRACKER_Y;
    public static final ModConfigSpec.IntValue TRACKER_MAX_QUESTS;
    public static final ModConfigSpec.BooleanValue REP_FEED;
    public static final ModConfigSpec.BooleanValue ANNOUNCEMENTS;
    public static final ModConfigSpec.BooleanValue RETINUE_BAR;
    public static final ModConfigSpec.BooleanValue SPEECH_BUBBLES;
    public static final ModConfigSpec.BooleanValue QUEST_MARKERS;
    public static final ModConfigSpec.IntValue TYPEWRITER_SPEED;

    /** How much of the quest tracker is shown. The tracker key cycles through these. */
    public enum TrackerMode {
        EXPANDED,
        COMPACT,
        HIDDEN;

        public TrackerMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("hud");
        TRACKER_MODE = b.comment("Quest tracker: EXPANDED, COMPACT (titles only) or HIDDEN. The tracker key cycles it.")
                .defineEnum("tracker_mode", TrackerMode.EXPANDED);
        TRACKER_X = b.comment("Quest tracker distance from the left edge of the screen, in GUI pixels.")
                .defineInRange("tracker_x", 6, 0, 2000);
        TRACKER_Y = b.comment("Quest tracker distance from the top of the screen, in GUI pixels.")
                .defineInRange("tracker_y", 40, 0, 2000);
        TRACKER_MAX_QUESTS = b.comment("Most quests the tracker shows at once.")
                .defineInRange("tracker_max_quests", 4, 1, 10);
        REP_FEED = b.comment("Show reputation changes as fading lines on the right of the screen.")
                .define("rep_feed", true);
        ANNOUNCEMENTS = b.comment("Show banners for tier changes, quest completions and lordship.")
                .define("announcements", true);
        RETINUE_BAR = b.comment("Show the guards following you at the top of the screen.")
                .define("retinue_bar", true);
        b.pop();
        b.push("world");
        SPEECH_BUBBLES = b.comment("Show speech bubbles above villagers, guards and other NPCs.")
                .define("speech_bubbles", true);
        QUEST_MARKERS = b.comment("Show ! and ? markers above NPCs with quests for you.")
                .define("quest_markers", true);
        b.pop();
        b.push("dialogue");
        TYPEWRITER_SPEED = b.comment("Characters per tick revealed in the dialogue box. 0 shows text at once.")
                .defineInRange("typewriter_speed", 2, 0, 20);
        b.pop();
        SPEC = b.build();
    }

    private FealtyClientConfig() {
    }

    /** Store a new tracker mode and save it to disk. */
    public static void setTrackerMode(TrackerMode mode) {
        TRACKER_MODE.set(mode);
        SPEC.save();
    }
}
