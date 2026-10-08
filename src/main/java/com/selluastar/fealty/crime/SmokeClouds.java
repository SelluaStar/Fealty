package com.selluastar.fealty.crime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Smoke from smoke bombs. Nobody inside a cloud can witness anything or be seen; anything hunting someone in the
 * smoke loses them, and can't pick them out again until a moment after they leave it. Whoever threw the bomb
 * turns invisible and quick on their feet while they stay inside.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class SmokeClouds {
    /** Mobs this far from a cloud still lose track of someone hiding in it. */
    private static final double LOSE_TRACK_RANGE = 32;
    /** How long someone stays hidden after stepping out of the smoke (ticks). */
    private static final int LINGER = 60;
    private static final Map<ResourceKey<Level>, List<Cloud>> CLOUDS = new HashMap<>();
    private static final Map<UUID, Long> HIDDEN_UNTIL = new HashMap<>();

    private SmokeClouds() {
    }

    public static void add(ServerLevel level, Vec3 center, double radius, int durationTicks, @Nullable UUID owner) {
        CLOUDS.computeIfAbsent(level.dimension(), k -> new ArrayList<>())
                .add(new Cloud(center, radius, level.getGameTime() + durationTicks, owner));
    }

    public static boolean inSmoke(ServerLevel level, Vec3 pos) {
        List<Cloud> clouds = CLOUDS.get(level.dimension());
        if (clouds == null) {
            return false;
        }
        long now = level.getGameTime();
        for (Cloud cloud : clouds) {
            if (cloud.until > now && cloud.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the entity is in smoke, or stepped out of it a moment ago. */
    public static boolean hidden(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        Long until = HIDDEN_UNTIL.get(entity.getUUID());
        if (until != null && until > level.getGameTime()) {
            return true;
        }
        return inSmoke(level, entity.getEyePosition()) || inSmoke(level, entity.position());
    }

    /** Nothing can take aim at someone hidden in the smoke. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && !CLOUDS.isEmpty() && hidden(target)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Cloud> clouds = CLOUDS.get(level.dimension());
        if (clouds == null || clouds.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Cloud> it = clouds.iterator();
        while (it.hasNext()) {
            Cloud cloud = it.next();
            if (cloud.until <= now) {
                it.remove();
                continue;
            }
            if (now % 3 == 0) {
                // Thinner as it clears in the last few seconds.
                double r = cloud.radius;
                int thick = cloud.until - now > 60 ? 1 : 0;
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cloud.center.x, cloud.center.y + 0.6, cloud.center.z,
                        4 + 4 * thick, r * 0.55, 0.7, r * 0.55, 0.004);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, cloud.center.x, cloud.center.y + 1.0, cloud.center.z,
                        8 + 10 * thick, r * 0.6, 0.9, r * 0.6, 0.008);
                level.sendParticles(ParticleTypes.CLOUD, cloud.center.x, cloud.center.y + 0.4, cloud.center.z,
                        2 + 4 * thick, r * 0.6, 0.4, r * 0.6, 0.002);
            }
            if (now % 5 == 0) {
                shroud(level, cloud, now);
            }
        }
        if (clouds.isEmpty()) {
            CLOUDS.remove(level.dimension());
        }
    }

    /** Hide everyone in the cloud, break off anyone hunting them, and speed the thrower away. */
    private static void shroud(ServerLevel level, Cloud cloud, long now) {
        AABB box = new AABB(cloud.center, cloud.center).inflate(cloud.radius);
        for (LivingEntity inside : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && cloud.contains(e.position()))) {
            HIDDEN_UNTIL.put(inside.getUUID(), now + LINGER);
            if (inside.getUUID().equals(cloud.owner)) {
                inside.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false, true));
                inside.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false, true));
            }
        }
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box.inflate(LOSE_TRACK_RANGE), m -> m.isAlive() && m.getTarget() != null)) {
            LivingEntity target = mob.getTarget();
            if (target != null && hidden(target)) {
                mob.setTarget(null);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                if (mob instanceof NeutralMob neutral) {
                    neutral.stopBeingAngry();
                }
                mob.getNavigation().stop();
            }
        }
        if (HIDDEN_UNTIL.size() > 256) {
            HIDDEN_UNTIL.entrySet().removeIf(e -> e.getValue() <= now);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CLOUDS.clear();
        HIDDEN_UNTIL.clear();
    }

    private record Cloud(Vec3 center, double radius, long until, @Nullable UUID owner) {
        boolean contains(Vec3 pos) {
            return center.distanceToSqr(pos) <= radius * radius;
        }
    }
}
