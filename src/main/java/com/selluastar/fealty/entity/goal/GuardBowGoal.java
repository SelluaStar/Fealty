package com.selluastar.fealty.entity.goal;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;

/**
 * Shooting with a bow, the way skeletons and pillagers do (keep at range, strafe, draw, loose), for mobs that are not
 * monsters, which the vanilla bow goal requires.
 */
public class GuardBowGoal extends Goal {
    private final PathfinderMob mob;
    private final RangedAttackMob shooter;
    private final double speed;
    private final int interval;
    private final float radiusSqr;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public <T extends PathfinderMob & RangedAttackMob> GuardBowGoal(T mob, double speed, int interval, float radius) {
        this.mob = mob;
        this.shooter = mob;
        this.speed = speed;
        this.interval = interval;
        this.radiusSqr = radius * radius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean holdingBow() {
        return mob.isHolding(Items.BOW);
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.getTarget().isAlive() && holdingBow();
    }

    @Override
    public boolean canContinueToUse() {
        return (canUse() || !mob.getNavigation().isDone()) && holdingBow();
    }

    @Override
    public void start() {
        super.start();
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        mob.setAggressive(false);
        seeTime = 0;
        attackTime = -1;
        mob.stopUsingItem();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        double distance = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = mob.getSensing().hasLineOfSight(target);
        if (canSee != seeTime > 0) {
            seeTime = 0;
        }
        seeTime += canSee ? 1 : -1;
        if (distance <= radiusSqr && seeTime >= 20) {
            mob.getNavigation().stop();
            strafingTime++;
        } else {
            mob.getNavigation().moveTo(target, speed);
            strafingTime = -1;
        }
        if (strafingTime >= 20) {
            if (mob.getRandom().nextFloat() < 0.3F) {
                strafingClockwise = !strafingClockwise;
            }
            if (mob.getRandom().nextFloat() < 0.3F) {
                strafingBackwards = !strafingBackwards;
            }
            strafingTime = 0;
        }
        if (strafingTime > -1) {
            if (distance > radiusSqr * 0.75F) {
                strafingBackwards = false;
            } else if (distance < radiusSqr * 0.25F) {
                strafingBackwards = true;
            }
            mob.getMoveControl().strafe(strafingBackwards ? -0.5F : 0.5F, strafingClockwise ? 0.5F : -0.5F);
            mob.lookAt(target, 30.0F, 30.0F);
        } else {
            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (mob.isUsingItem()) {
            if (!canSee && seeTime < -60) {
                mob.stopUsingItem();
            } else if (canSee) {
                int drawn = mob.getTicksUsingItem();
                if (drawn >= 20) {
                    mob.stopUsingItem();
                    shooter.performRangedAttack(target, BowItem.getPowerForTime(drawn));
                    attackTime = interval;
                }
            }
        } else if (--attackTime <= 0 && seeTime >= -60) {
            mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(mob, Items.BOW));
        }
    }
}
