package com.selluastar.fealty.quest.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.dialogue.DialogueNode;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.village.VillageNames;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * {@code fealty:talk_to}: carry a message to another villager in the same village (of a profession, if given),
 * then report back. The player says {@code message} in the dialogue box and the villager answers {@code reply}.
 */
public record TalkToObjective(Optional<ResourceLocation> profession, Component message, Component reply) implements QuestObjective {
    public static final MapCodec<TalkToObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("profession").forGetter(TalkToObjective::profession),
            ComponentSerialization.CODEC.optionalFieldOf("message", Component.translatable("fealty.objective.talk_to.message"))
                    .forGetter(TalkToObjective::message),
            ComponentSerialization.CODEC.optionalFieldOf("reply", Component.translatable("fealty.objective.talk_to.reply"))
                    .forGetter(TalkToObjective::reply)
    ).apply(i, TalkToObjective::new));

    private static final String OPTION = "tell";

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.TALK_TO.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        Optional<VillageRecord> village = ctx.village();
        if (village.isEmpty() || !village.get().dimension().equals(ctx.level().dimension())) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.talk_to.none"));
            return false;
        }
        UUID giver = ctx.state().hasUUID("giver_uuid") ? ctx.state().getUUID("giver_uuid") : null;
        List<Villager> members = new ArrayList<>();
        List<Villager> candidates = new ArrayList<>();
        for (Villager villager : ctx.level().getEntitiesOfClass(Villager.class, AABB.of(village.get().bounds()),
                v -> v.isAlive() && !v.isBaby() && !v.getUUID().equals(giver))) {
            if (!FactionResolver.factionOf(villager).map(village.get().id()::equals).orElse(false)) {
                continue;
            }
            members.add(villager);
            if (profession.isEmpty()
                    || profession.get().equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()))) {
                candidates.add(villager);
            }
        }
        if (candidates.isEmpty()) {
            // Nobody of that trade lives here: anyone will pass the word on.
            candidates = members;
        }
        if (candidates.isEmpty()) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.talk_to.none"));
            return false;
        }
        Villager target = candidates.get(ctx.level().getRandom().nextInt(candidates.size()));
        if (target.getCustomName() == null) {
            // Give them a name so the player can ask around for them.
            target.setCustomName(Component.literal(VillageNames.personName(target.getRandom())));
        }
        ctx.state().putUUID("target", target.getUUID());
        ctx.state().putString("target_name", target.getDisplayName().getString());
        return true;
    }

    private Optional<UUID> target(QuestContext ctx) {
        return ctx.state().hasUUID("target") ? Optional.of(ctx.state().getUUID("target")) : Optional.empty();
    }

    @Override
    public List<DialogueNode.Option> dialogueOptions(QuestContext ctx, Entity npc) {
        if (ctx.quest().isReady() || !target(ctx).map(npc.getUUID()::equals).orElse(false)) {
            return List.of();
        }
        return List.of(DialogueNode.Option.of(OPTION, message, "talk"));
    }

    @Override
    public Optional<Component> onDialogue(QuestContext ctx, Entity npc, String option) {
        if (!OPTION.equals(option) || !target(ctx).map(npc.getUUID()::equals).orElse(false)) {
            return Optional.empty();
        }
        ctx.setReady();
        return Optional.of(reply);
    }

    @Override
    public List<UUID> markedEntities(QuestContext ctx) {
        return ctx.quest().isReady() ? List.of() : target(ctx).map(List::of).orElse(List.of());
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        return List.of(Component.translatable("fealty.objective.talk_to", ctx.state().getString("target_name")));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.talk_to.preview"));
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.check(Component.translatable("fealty.objective.talk_to", ctx.state().getString("target_name")),
                ctx.quest().isReady()));
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        Optional<UUID> id = target(ctx);
        if (id.isPresent() && ctx.player().level().dimension().equals(ctx.dimension())) {
            Entity entity = ctx.questLevel().getEntity(id.get());
            if (entity != null) {
                return Optional.of(new QuestView.Waypoint(ctx.dimension(), entity.blockPosition(),
                        Component.literal(ctx.state().getString("target_name"))));
            }
        }
        return ctx.village().map(v -> new QuestView.Waypoint(v.dimension(), v.center(), Component.literal(v.name())));
    }
}
