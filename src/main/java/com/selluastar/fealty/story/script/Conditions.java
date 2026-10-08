package com.selluastar.fealty.story.script;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.dialogue.DialogueCondition;
import com.selluastar.fealty.api.dialogue.DialogueConditionType;
import com.selluastar.fealty.api.dialogue.DialogueContext;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.registry.ModDialogueScripts;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.story.PlayerFlags;
import com.selluastar.fealty.util.Inventories;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

/** Fealty's built-in dialogue conditions ({@code fealty:dialogue_condition}). */
public final class Conditions {
    private Conditions() {
    }

    /** An item id, or {@code {"item": id, "count": n}}. */
    static final Codec<ItemCount> ITEM_COUNT = Codec.withAlternative(
            RecordCodecBuilder.<ItemCount>create(i -> i.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemCount::item),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(ItemCount::count)
            ).apply(i, ItemCount::new)),
            BuiltInRegistries.ITEM.byNameCodec().xmap(item -> new ItemCount(item, 1), ItemCount::item));

    record ItemCount(Item item, int count) {
    }

    /** {@code {"has_item": "minecraft:paper"}} or {@code {"has_item": {"item": "minecraft:emerald", "count": 5}}}. */
    public record HasItem(ItemCount items) implements DialogueCondition {
        public static final Codec<HasItem> CODEC = ITEM_COUNT.xmap(HasItem::new, HasItem::items);

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.HAS_ITEM.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            return Inventories.count(ctx.player(), items.item()) >= items.count();
        }
    }

    /**
     * {@code {"tier_at_least": "fealty:trusted"}}: the player's standing with the NPC's faction (or the village they
     * stand in), or with {@code {"tier": ..., "faction": ...}}.
     */
    public record TierAtLeast(ResourceLocation tier, Optional<ResourceLocation> faction) implements DialogueCondition {
        public static final Codec<TierAtLeast> CODEC = Codec.withAlternative(
                RecordCodecBuilder.<TierAtLeast>create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("tier").forGetter(TierAtLeast::tier),
                        ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(TierAtLeast::faction)
                ).apply(i, TierAtLeast::new)),
                ResourceLocation.CODEC.xmap(tier -> new TierAtLeast(tier, Optional.empty()), TierAtLeast::tier));

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.TIER_AT_LEAST.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            Optional<RepTier> min = TierManager.byId(tier);
            Optional<ResourceLocation> with = faction.or(() -> Scripts.faction(ctx));
            return min.isPresent() && with.isPresent() && RepManager.getTier(ctx.player(), with.get()).rank() >= min.get().rank();
        }
    }

    /** {@code {"flag": "example:met"}} (set at all), or {@code {"flag": {"id": ..., "min": 2, "max": 5}}}. */
    public record Flag(ResourceLocation id, Optional<Integer> min, Optional<Integer> max) implements DialogueCondition {
        public static final Codec<Flag> CODEC = Codec.withAlternative(
                RecordCodecBuilder.<Flag>create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(Flag::id),
                        Codec.INT.optionalFieldOf("min").forGetter(Flag::min),
                        Codec.INT.optionalFieldOf("max").forGetter(Flag::max)
                ).apply(i, Flag::new)),
                ResourceLocation.CODEC.xmap(id -> new Flag(id, Optional.empty(), Optional.empty()), Flag::id));

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.FLAG.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            int value = PlayerFlags.get(ctx.player(), id);
            if (min.isEmpty() && max.isEmpty()) {
                return value != 0;
            }
            return value >= min.orElse(Integer.MIN_VALUE) && value <= max.orElse(Integer.MAX_VALUE);
        }
    }

    /** {@code {"advancement": "minecraft:story/mine_diamond"}}: the player has it. */
    public record Advancement(ResourceLocation id) implements DialogueCondition {
        public static final Codec<Advancement> CODEC = ResourceLocation.CODEC.xmap(Advancement::new, Advancement::id);

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.ADVANCEMENT.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            AdvancementHolder holder = ctx.player().server.getAdvancements().get(id);
            return holder != null && ctx.player().getAdvancements().getOrStartProgress(holder).isDone();
        }
    }

    /** {@code {"dimension": "minecraft:overworld"}}: the player is in that dimension. */
    public record Dimension(ResourceLocation id) implements DialogueCondition {
        public static final Codec<Dimension> CODEC = ResourceLocation.CODEC.xmap(Dimension::new, Dimension::id);

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.DIMENSION.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            return ctx.player().level().dimension().location().equals(id);
        }
    }

    /**
     * {@code {"quest_stage": {"chain": "example:tale", "stage": 1}}}: the player's stage in a quest chain is exactly
     * {@code stage}, or between {@code min} and {@code max}; {@code steps_done} asks for at least that many steps
     * handed in.
     */
    public record QuestStage(ResourceLocation chain, Optional<Integer> stage, Optional<Integer> min, Optional<Integer> max,
                             Optional<Integer> stepsDone) implements DialogueCondition {
        public static final Codec<QuestStage> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("chain").forGetter(QuestStage::chain),
                Codec.INT.optionalFieldOf("stage").forGetter(QuestStage::stage),
                Codec.INT.optionalFieldOf("min").forGetter(QuestStage::min),
                Codec.INT.optionalFieldOf("max").forGetter(QuestStage::max),
                Codec.INT.optionalFieldOf("steps_done").forGetter(QuestStage::stepsDone)
        ).apply(i, QuestStage::new));

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.QUEST_STAGE.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            int current = ChainManager.stage(ctx.player(), chain);
            return stage.map(s -> s == current).orElse(true) && current >= min.orElse(Integer.MIN_VALUE)
                    && current <= max.orElse(Integer.MAX_VALUE) && ChainManager.stepsDone(ctx.player(), chain) >= stepsDone.orElse(0);
        }
    }

    /**
     * {@code {"time_of_day": "day"}} ({@code "night"}), or {@code {"time_of_day": {"min": 0, "max": 6000}}} in ticks of
     * the day (0 is sunrise; a range may wrap past midnight, such as 18000 to 2000).
     */
    public record TimeOfDay(int min, int max) implements DialogueCondition {
        private static final Codec<TimeOfDay> NAMED = Codec.STRING.comapFlatMap(TimeOfDay::named, time -> time.min() == 0 ? "day" : "night");
        public static final Codec<TimeOfDay> CODEC = Codec.withAlternative(
                RecordCodecBuilder.<TimeOfDay>create(i -> i.group(
                        Codec.intRange(0, 24000).fieldOf("min").forGetter(TimeOfDay::min),
                        Codec.intRange(0, 24000).fieldOf("max").forGetter(TimeOfDay::max)
                ).apply(i, TimeOfDay::new)), NAMED);

        private static DataResult<TimeOfDay> named(String name) {
            if (name.equals("day")) {
                return DataResult.success(new TimeOfDay(0, 12000));
            }
            if (name.equals("night")) {
                return DataResult.success(new TimeOfDay(12000, 24000));
            }
            return DataResult.error(() -> "time_of_day is \"day\", \"night\" or {\"min\": ..., \"max\": ...}, not " + name);
        }

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.TIME_OF_DAY.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            long time = Math.floorMod(ctx.player().level().getDayTime(), 24000L);
            return min <= max ? time >= min && time < max : time >= min || time < max;
        }
    }

    /** {@code {"all": [ ... ]}}: every one passes. */
    public record All(List<DialogueCondition> conditions) implements DialogueCondition {
        public static final Codec<All> CODEC = DialogueCondition.CODEC.listOf().xmap(All::new, All::conditions);

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.ALL.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            return conditions.stream().allMatch(c -> Scripts.test(c, ctx));
        }
    }

    /** {@code {"any": [ ... ]}}: at least one passes. */
    public record Any(List<DialogueCondition> conditions) implements DialogueCondition {
        public static final Codec<Any> CODEC = DialogueCondition.CODEC.listOf().xmap(Any::new, Any::conditions);

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.ANY.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            return conditions.stream().anyMatch(c -> Scripts.test(c, ctx));
        }
    }

    /** {@code {"not": { ... }}}. */
    public record Not(DialogueCondition condition) implements DialogueCondition {
        public static final Codec<Not> CODEC = DialogueCondition.CODEC.xmap(Not::new, Not::condition);

        @Override
        public DialogueConditionType<?> type() {
            return ModDialogueScripts.NOT.get();
        }

        @Override
        public boolean test(DialogueContext ctx) {
            return !Scripts.test(condition, ctx);
        }
    }
}
