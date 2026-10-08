package com.selluastar.fealty.quest.type;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestTarget;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModQuestTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code fealty:hunt}: kill a named monster that has been terrorising the area. The monster appears when the
 * player gets close to where it was last seen.
 */
public record HuntObjective(List<Target> targets, int minDistance, int maxDistance) implements QuestObjective {
    private static final String[] NAMES = {"Grimfang", "Old Mawkin", "Blackmoor", "Gallowmere", "Rotgut", "Sallowjaw", "Hollow Tom",
            "the Widowmaker", "Cinderhide", "Mother Thorn"};
    private static final String[] TITLES = {"the Ravager", "the Cruel", "of the Fen", "the Unburied", "the Gnawer", "the Shrike"};

    public static final MapCodec<HuntObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Target.CODEC.listOf().fieldOf("targets").forGetter(HuntObjective::targets),
            Codec.intRange(0, 10000).optionalFieldOf("min_distance", 80).forGetter(HuntObjective::minDistance),
            Codec.intRange(0, 10000).optionalFieldOf("max_distance", 200).forGetter(HuntObjective::maxDistance)
    ).apply(i, HuntObjective::new));

    /** A monster the hunt can pick. */
    public record Target(EntityType<?> entity, Optional<Component> name, float health, float damage, float armor,
                         Map<EquipmentSlot, ItemStack> equipment) {
        public static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Target::entity),
                ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Target::name),
                Codec.floatRange(0.1F, 100F).optionalFieldOf("health_multiplier", 3.0F).forGetter(Target::health),
                Codec.floatRange(0F, 100F).optionalFieldOf("damage_bonus", 3.0F).forGetter(Target::damage),
                Codec.floatRange(0F, 30F).optionalFieldOf("armor_bonus", 4.0F).forGetter(Target::armor),
                Codec.unboundedMap(EquipmentSlot.CODEC, ItemStack.CODEC).optionalFieldOf("equipment", Map.of()).forGetter(Target::equipment)
        ).apply(i, Target::new));
    }

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.HUNT.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        if (targets.isEmpty()) {
            return false;
        }
        RandomSource random = ctx.level().getRandom();
        int index = random.nextInt(targets.size());
        Target target = targets.get(index);
        BlockPos anchor = ctx.anchor();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = minDistance + (maxDistance > minDistance ? random.nextInt(maxDistance - minDistance + 1) : 0);
        BlockPos lair = anchor.offset(Mth.floor(Math.cos(angle) * distance), 0, Mth.floor(Math.sin(angle) * distance));
        Component name = target.name()
                .orElseGet(() -> Component.literal(NAMES[random.nextInt(NAMES.length)] + " " + TITLES[random.nextInt(TITLES.length)]));
        ctx.state().putInt("target", index);
        ctx.state().put("lair", NbtUtils.writeBlockPos(lair));
        ctx.state().putString("name", Component.Serializer.toJson(name, ctx.level().registryAccess()));
        ctx.state().putBoolean("spawned", false);
        return true;
    }

    private static Component name(QuestContext ctx) {
        Component name = Component.Serializer.fromJson(ctx.state().getString("name"), ctx.level().registryAccess());
        return name != null ? name : Component.literal("?");
    }

    private static Optional<BlockPos> lair(QuestContext ctx) {
        return NbtUtils.readBlockPos(ctx.state(), "lair");
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        if (ctx.quest().isReady()) {
            return List.of(Component.translatable("fealty.quest.hunt.done", name(ctx)));
        }
        BlockPos lair = lair(ctx).orElse(BlockPos.ZERO);
        return List.of(Component.translatable("fealty.quest.hunt.line", name(ctx), lair.getX(), lair.getZ()));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.hunt.preview"));
    }

    @Override
    public void tick(QuestContext ctx) {
        if (ctx.quest().isReady() || ctx.state().getBoolean("spawned")) {
            return;
        }
        Optional<BlockPos> lair = lair(ctx);
        ServerLevel level = ctx.level();
        if (lair.isEmpty() || !ctx.village().map(v -> v.dimension().equals(level.dimension())).orElse(true)) {
            return;
        }
        BlockPos pos = lair.get();
        double dx = ctx.player().getX() - pos.getX();
        double dz = ctx.player().getZ() - pos.getZ();
        if (dx * dx + dz * dz > 56 * 56 || !level.isLoaded(pos)) {
            return;
        }
        Entity entity = spawn(ctx, level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos));
        if (entity != null) {
            ctx.state().putBoolean("spawned", true);
            ctx.state().putUUID("entity", entity.getUUID());
            ctx.dirty();
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.hunt.near", name(ctx)));
        }
    }

    private Entity spawn(QuestContext ctx, ServerLevel level, BlockPos pos) {
        int index = Mth.clamp(ctx.state().getInt("target"), 0, targets.size() - 1);
        Target target = targets.get(index);
        Entity entity = target.entity().create(level);
        if (entity == null) {
            return null;
        }
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
            target.equipment().forEach((slot, stack) -> {
                mob.setItemSlot(slot, stack.copy());
                mob.setDropChance(slot, 0.2F);
            });
        }
        if (entity instanceof LivingEntity living) {
            modify(living, Attributes.MAX_HEALTH, "hunt_health", target.health() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            modify(living, Attributes.ATTACK_DAMAGE, "hunt_damage", target.damage(), AttributeModifier.Operation.ADD_VALUE);
            modify(living, Attributes.ARMOR, "hunt_armor", target.armor(), AttributeModifier.Operation.ADD_VALUE);
            living.setHealth(living.getMaxHealth());
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
        entity.setCustomName(name(ctx));
        entity.setCustomNameVisible(true);
        entity.setData(ModAttachments.QUEST_TARGET, new QuestTarget(ctx.player().getUUID(), ctx.quest().instanceId()));
        level.addFreshEntityWithPassengers(entity);
        return entity;
    }

    private static void modify(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                               String id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && amount != 0) {
            instance.addPermanentModifier(new AttributeModifier(Fealty.id(id), amount, operation));
        }
    }

    private static boolean isTarget(QuestContext ctx, Entity entity) {
        if (!entity.hasData(ModAttachments.QUEST_TARGET)) {
            return false;
        }
        QuestTarget target = entity.getData(ModAttachments.QUEST_TARGET);
        return target.questInstance().equals(ctx.quest().instanceId());
    }

    @Override
    public void onKill(QuestContext ctx, LivingEntity victim) {
        if (isTarget(ctx, victim)) {
            ctx.setReady();
        }
    }

    @Override
    public void onTargetLost(QuestContext ctx, LivingEntity target) {
        if (isTarget(ctx, target) && !ctx.quest().isReady()) {
            // It died to something else; it will turn up again near its lair.
            ctx.state().putBoolean("spawned", false);
            ctx.dirty();
        }
    }

    @Override
    public void cleanup(QuestContext ctx, boolean success) {
        if (success || !ctx.state().hasUUID("entity")) {
            return;
        }
        UUID id = ctx.state().getUUID("entity");
        Entity entity = ctx.questLevel().getEntity(id);
        if (entity != null) {
            entity.discard();
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.check(Component.translatable("fealty.objective.hunt", name(ctx)), ctx.quest().isReady()));
    }

    @Override
    public java.util.Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        return lair(ctx).map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, name(ctx)));
    }

}
