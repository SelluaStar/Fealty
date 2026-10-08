package com.selluastar.fealty.quest.type;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.village.StructureMatcher;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code fealty:kill}: defeat a number of creatures. {@code targets} is an entity id, a tag ({@code #...}) or a list
 * of them; {@code near_village} only counts kills in and around the quest's village, {@code night_only} only at
 * night. {@code label} names the targets for the player ("zombies", "wolves").
 */
public record KillObjective(List<String> targets, int count, boolean nearVillage, boolean nightOnly, Optional<Component> label)
        implements QuestObjective {
    public static final MapCodec<KillObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StructureMatcher.ENTRIES_CODEC.fieldOf("targets").forGetter(KillObjective::targets),
            Codec.intRange(1, 1000).optionalFieldOf("count", 5).forGetter(KillObjective::count),
            Codec.BOOL.optionalFieldOf("near_village", false).forGetter(KillObjective::nearVillage),
            Codec.BOOL.optionalFieldOf("night_only", false).forGetter(KillObjective::nightOnly),
            ComponentSerialization.CODEC.optionalFieldOf("label").forGetter(KillObjective::label)
    ).apply(i, KillObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.KILL.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return true;
    }

    private boolean matches(EntityType<?> type) {
        Holder<EntityType<?>> holder = type.builtInRegistryHolder();
        for (String target : targets) {
            if (target.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(target.substring(1));
                if (tag != null && holder.is(TagKey.create(Registries.ENTITY_TYPE, tag))) {
                    return true;
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(target);
                if (id != null && id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Component targetName() {
        if (label.isPresent()) {
            return label.get();
        }
        for (String target : targets) {
            ResourceLocation id = ResourceLocation.tryParse(target);
            if (!target.startsWith("#") && id != null) {
                return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityType::getDescription)
                        .orElse(Component.literal(target));
            }
        }
        return Component.translatable("fealty.objective.kill.creatures");
    }

    @Override
    public void onKill(QuestContext ctx, LivingEntity victim) {
        if (ctx.quest().isReady() || !matches(victim.getType())) {
            return;
        }
        if (nightOnly && !victim.level().isNight()) {
            return;
        }
        if (nearVillage && !ctx.village().map(v -> v.dimension().equals(victim.level().dimension())
                && v.bounds().inflatedBy(32).isInside(victim.blockPosition())).orElse(true)) {
            return;
        }
        int done = ctx.getInt("done") + 1;
        ctx.putInt("done", done);
        if (done >= count) {
            ctx.setReady();
        }
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        return List.of(Component.translatable(nearVillage ? "fealty.quest.kill.line_village" : "fealty.quest.kill.line",
                count, targetName(), Math.min(ctx.getInt("done"), count), count));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable(nightOnly ? "fealty.quest.kill.preview_night" : "fealty.quest.kill.preview", count, targetName()));
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.count(Component.translatable("fealty.objective.kill", targetName()), ctx.getInt("done"), count));
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        if (!nearVillage) {
            return Optional.empty();
        }
        return ctx.village().map(v -> new QuestView.Waypoint(v.dimension(), v.center(), Component.literal(v.name())));
    }
}
