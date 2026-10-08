package com.selluastar.fealty.story.script;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.dialogue.DialogueContext;
import com.selluastar.fealty.api.dialogue.DialogueEffect;
import com.selluastar.fealty.api.dialogue.DialogueEffectType;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.quest.FavorManager;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.registry.ModDialogueScripts;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.story.PlayerFlags;
import com.selluastar.fealty.util.Inventories;
import com.selluastar.fealty.util.Maps;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Fealty's built-in dialogue effects ({@code fealty:dialogue_effect}). */
public final class Effects {
    private Effects() {
    }

    /** {@code {"take_item": "minecraft:paper"}} or {@code {"take_item": {"item": ..., "count": n}}} (as many as the player has, up to n). */
    public record TakeItem(Conditions.ItemCount items) implements DialogueEffect {
        public static final Codec<TakeItem> CODEC = Conditions.ITEM_COUNT.xmap(TakeItem::new, TakeItem::items);

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.TAKE_ITEM.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            int count = Math.min(items.count(), Inventories.count(ctx.player(), items.item()));
            if (count > 0) {
                Inventories.take(ctx.player(), items.item(), count);
            }
        }
    }

    /** {@code {"give_item": "minecraft:paper"}} or a full item stack ({@code {"id": ..., "count": n, "components": {...}}}). */
    public record GiveItem(ItemStack stack) implements DialogueEffect {
        public static final Codec<GiveItem> CODEC = Codec.withAlternative(ItemStack.CODEC,
                BuiltInRegistries.ITEM.byNameCodec().<ItemStack>xmap(ItemStack::new, ItemStack::getItem)).xmap(GiveItem::new, GiveItem::stack);

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.GIVE_ITEM.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            Maps.give(ctx.player(), stack.copy());
        }
    }

    /** {@code {"set_flag": "example:met"}} (to 1) or {@code {"set_flag": {"id": ..., "value": 3}}}. */
    public record SetFlag(ResourceLocation id, int value) implements DialogueEffect {
        public static final Codec<SetFlag> CODEC = Codec.withAlternative(
                RecordCodecBuilder.<SetFlag>create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(SetFlag::id),
                        Codec.INT.optionalFieldOf("value", 1).forGetter(SetFlag::value)
                ).apply(i, SetFlag::new)),
                ResourceLocation.CODEC.xmap(id -> new SetFlag(id, 1), SetFlag::id));

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.SET_FLAG.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            PlayerFlags.set(ctx.player(), id, value);
        }
    }

    /** {@code {"clear_flag": "example:met"}}. */
    public record ClearFlag(ResourceLocation id) implements DialogueEffect {
        public static final Codec<ClearFlag> CODEC = ResourceLocation.CODEC.xmap(ClearFlag::new, ClearFlag::id);

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.CLEAR_FLAG.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            PlayerFlags.clear(ctx.player(), id);
        }
    }

    /**
     * {@code {"add_rep": 5}} with the NPC's faction (or the village the player stands in), or
     * {@code {"add_rep": {"amount": 5, "faction": ..., "reason": "fealty:quest"}}}. The reason decides whether it may
     * raise reputation below zero; the default, {@code fealty:dialogue}, may not.
     */
    public record AddRep(int amount, Optional<ResourceLocation> faction, ResourceLocation reason) implements DialogueEffect {
        public static final Codec<AddRep> CODEC = Codec.withAlternative(
                RecordCodecBuilder.<AddRep>create(i -> i.group(
                        Codec.INT.fieldOf("amount").forGetter(AddRep::amount),
                        ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(AddRep::faction),
                        ResourceLocation.CODEC.optionalFieldOf("reason", RepSources.DIALOGUE).forGetter(AddRep::reason)
                ).apply(i, AddRep::new)),
                Codec.INT.xmap(amount -> new AddRep(amount, Optional.empty(), RepSources.DIALOGUE), AddRep::amount));

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.ADD_REP.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            faction.or(() -> Scripts.faction(ctx)).ifPresent(id -> {
                RepManager.meet(ctx.player(), id);
                RepManager.change(ctx.player(), id, amount, reason);
            });
        }
    }

    /**
     * {@code {"start_quest": "example:find_the_ring"}}: the player takes up a quest ({@code rep_quests/} file). It is
     * handed in to the villager who gave it (as a favor), or to the NPC's faction (a village's elder); give
     * {@code {"quest": ..., "giver": "<key>"}} to file it somewhere else.
     */
    public record StartQuest(ResourceLocation quest, Optional<ResourceLocation> giver) implements DialogueEffect {
        public static final Codec<StartQuest> CODEC = Codec.withAlternative(
                RecordCodecBuilder.<StartQuest>create(i -> i.group(
                        ResourceLocation.CODEC.fieldOf("quest").forGetter(StartQuest::quest),
                        ResourceLocation.CODEC.optionalFieldOf("giver").forGetter(StartQuest::giver)
                ).apply(i, StartQuest::new)),
                ResourceLocation.CODEC.xmap(quest -> new StartQuest(quest, Optional.empty()), StartQuest::quest));

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.START_QUEST.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            ResourceLocation faction = Scripts.faction(ctx).orElse(Factions.WANDERERS);
            Optional<Entity> npc = ctx.npc();
            ResourceLocation key = giver.orElseGet(() -> npc.filter(Villager.class::isInstance).map(e -> FavorManager.key(e.getUUID()))
                    .orElse(faction));
            CompoundTag state = new CompoundTag();
            npc.ifPresent(e -> {
                state.putUUID("giver_uuid", e.getUUID());
                state.put("giver_pos", NbtUtils.writeBlockPos(e.blockPosition()));
                state.putString("giver_name", e.getDisplayName().getString());
            });
            QuestManager.accept(ctx.player(), key, faction, quest, state);
        }
    }

    /** {@code {"grant_advancement": "example:met_the_stranger"}}: every criterion of it. */
    public record GrantAdvancement(ResourceLocation id) implements DialogueEffect {
        public static final Codec<GrantAdvancement> CODEC = ResourceLocation.CODEC.xmap(GrantAdvancement::new, GrantAdvancement::id);

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.GRANT_ADVANCEMENT.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            ServerPlayer player = ctx.player();
            AdvancementHolder holder = player.server.getAdvancements().get(id);
            if (holder == null) {
                return;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            for (String criterion : progress.getRemainingCriteria()) {
                player.getAdvancements().award(holder, criterion);
            }
        }
    }

    /** {@code {"run_function": "example:story/reward"}}: runs a function as the player, at permission level 2. */
    public record RunFunction(ResourceLocation id) implements DialogueEffect {
        public static final Codec<RunFunction> CODEC = ResourceLocation.CODEC.xmap(RunFunction::new, RunFunction::id);

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.RUN_FUNCTION.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            MinecraftServer server = ctx.player().server;
            CommandSourceStack source = ctx.player().createCommandSourceStack().withSuppressedOutput().withPermission(2);
            server.getFunctions().get(id).ifPresent(function -> server.getFunctions().execute(function, source));
        }
    }

    /**
     * {@code {"start_chain": "example:tale"}}: starts a quest chain in the NPC's village (or the one the player stands
     * in), as a rumour would.
     */
    public record StartChain(ResourceLocation chain) implements DialogueEffect {
        public static final Codec<StartChain> CODEC = ResourceLocation.CODEC.xmap(StartChain::new, StartChain::chain);

        @Override
        public DialogueEffectType<?> type() {
            return ModDialogueScripts.START_CHAIN.get();
        }

        @Override
        public void apply(DialogueContext ctx) {
            Scripts.village(ctx).ifPresent(village -> ChainManager.start(ctx.player(), chain, village));
        }
    }
}
