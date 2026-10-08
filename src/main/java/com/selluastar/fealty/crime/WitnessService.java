package com.selluastar.fealty.crime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.item.RogueArmorItem;
import com.selluastar.fealty.rep.FactionResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Finds who saw a crime. A witness must belong to the wronged faction, be awake and not blind or in smoke,
 * be within the witness radius (16 by default, halved for a sneaking player in full rogue gear), and either
 * have line of sight to the player and be facing them, or be close enough to notice. Loud crimes (forcing a
 * lock) are heard by everyone in range regardless of sight.
 */
public final class WitnessService {
    /** Within this distance a witness notices the player whichever way they face, unless the player sneaks. */
    private static final double NOTICE_DISTANCE = 2.5;
    /** Cosine of half the witness field of view (about 150 degrees). */
    private static final double FOV_COS = Math.cos(Math.toRadians(75));
    private static final Set<UUID> SNEAKING = new HashSet<>();

    private WitnessService() {
    }

    /**
     * Whether the player counts as sneaking. Opening a screen lets go of the sneak key, so a player who was sneaking
     * when they opened a chest or started picking a lock counts as sneaking until they are done.
     */
    public static boolean sneaking(ServerPlayer player) {
        return player.isCrouching() || SNEAKING.contains(player.getUUID());
    }

    /** Run a witness check as if the player were (or were not) still sneaking. */
    public static <T> T asSneaking(ServerPlayer player, boolean sneaking, Supplier<T> check) {
        if (!sneaking || player.isCrouching()) {
            return check.get();
        }
        SNEAKING.add(player.getUUID());
        try {
            return check.get();
        } finally {
            SNEAKING.remove(player.getUUID());
        }
    }

    public static double radius(ServerPlayer player) {
        double radius = FealtyConfig.WITNESS_RADIUS.get();
        if (sneaking(player) && RogueArmorItem.wearsFullSet(player)) {
            radius *= 0.5;
        }
        return radius;
    }

    public static List<LivingEntity> witnesses(ServerLevel level, ServerPlayer player, BlockPos crimePos, ResourceLocation faction, boolean loud) {
        List<LivingEntity> result = new ArrayList<>();
        double radius = radius(player);
        boolean playerHidden = SmokeClouds.hidden(player) || SmokeClouds.inSmoke(level, Vec3.atCenterOf(crimePos));
        if (playerHidden && !loud) {
            return result;
        }
        AABB box = new AABB(player.position(), player.position()).inflate(radius);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != player)) {
            if (!FactionResolver.canWitness(candidate)) {
                continue;
            }
            Optional<ResourceLocation> candidateFaction = FactionResolver.factionOf(candidate);
            if (candidateFaction.isEmpty() || !candidateFaction.get().equals(faction)) {
                continue;
            }
            if (canWitness(level, candidate, player, radius, loud)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** Whether one entity is in a position to see (or hear) the player. */
    public static boolean canWitness(ServerLevel level, LivingEntity witness, ServerPlayer player, double radius, boolean loud) {
        if (witness.isSleeping() || witness.hasEffect(MobEffects.BLINDNESS) || witness.isDeadOrDying()) {
            return false;
        }
        double distance = witness.distanceTo(player);
        if (distance > radius) {
            return false;
        }
        if (loud) {
            return true;
        }
        if (SmokeClouds.inSmoke(level, witness.getEyePosition())) {
            return false;
        }
        if (!witness.hasLineOfSight(player)) {
            return false;
        }
        if (distance <= NOTICE_DISTANCE && !sneaking(player)) {
            return true;
        }
        return isFacing(witness, player);
    }

    /** Whether the player is inside the witness's field of view. */
    public static boolean isFacing(LivingEntity witness, LivingEntity player) {
        Vec3 look = witness.getViewVector(1.0F).normalize();
        Vec3 toPlayer = player.getEyePosition().subtract(witness.getEyePosition()).normalize();
        return look.dot(toPlayer) >= FOV_COS;
    }
}
