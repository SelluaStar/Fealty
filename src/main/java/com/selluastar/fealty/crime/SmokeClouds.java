package com.selluastar.fealty.crime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.selluastar.fealty.Fealty;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Smoke from smoke bombs. Nobody inside a cloud can witness anything, and nobody can be seen inside one. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class SmokeClouds {
    private static final Map<ResourceKey<Level>, List<Cloud>> CLOUDS = new HashMap<>();

    private SmokeClouds() {
    }

    public static void add(ServerLevel level, Vec3 center, double radius, int durationTicks) {
        CLOUDS.computeIfAbsent(level.dimension(), k -> new ArrayList<>())
                .add(new Cloud(center, radius, level.getGameTime() + durationTicks));
    }

    public static boolean inSmoke(ServerLevel level, Vec3 pos) {
        List<Cloud> clouds = CLOUDS.get(level.dimension());
        if (clouds == null) {
            return false;
        }
        long now = level.getGameTime();
        for (Cloud cloud : clouds) {
            if (cloud.until > now && cloud.center.distanceToSqr(pos) <= cloud.radius * cloud.radius) {
                return true;
            }
        }
        return false;
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
            } else if (now % 4 == 0) {
                double r = cloud.radius;
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cloud.center.x, cloud.center.y + 0.5, cloud.center.z,
                        6, r * 0.5, 0.6, r * 0.5, 0.005);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, cloud.center.x, cloud.center.y + 1.0, cloud.center.z,
                        10, r * 0.6, 0.8, r * 0.6, 0.01);
            }
        }
    }

    private record Cloud(Vec3 center, double radius, long until) {
    }
}
