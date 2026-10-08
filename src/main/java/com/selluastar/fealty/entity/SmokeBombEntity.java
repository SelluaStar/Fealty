package com.selluastar.fealty.entity;

import com.selluastar.fealty.crime.SmokeClouds;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.registry.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A thrown smoke bomb. Bursts into a cloud that blinds, hides, and covers the thrower's escape. */
public class SmokeBombEntity extends ThrowableItemProjectile {
    private static final double RADIUS = 5.0;
    private static final int DURATION = 300;

    public SmokeBombEntity(EntityType<? extends SmokeBombEntity> type, Level level) {
        super(type, level);
    }

    public SmokeBombEntity(Level level, LivingEntity thrower) {
        super(ModEntities.SMOKE_BOMB.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SMOKE_BOMB.get();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            Vec3 pos = position();
            Entity owner = getOwner();
            SmokeClouds.add(level, pos, RADIUS, DURATION, owner != null ? owner.getUUID() : null);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.5, pos.z, 90, 1.8, 0.9, 1.8, 0.03);
            level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.5, pos.z, 30, 1.0, 0.5, 1.0, 0.08);
            level.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4F, 1.8F);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS))) {
                if (entity != getOwner()) {
                    entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
                }
            }
            discard();
        }
    }
}
