package com.selluastar.fealty.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.entity.BanditEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;

/** A hired bandit keeps up with the player who pays them, teleporting like a pet when left far behind. */
public class FollowMasterGoal extends Goal {
    private final BanditEntity bandit;
    private final double speed;
    private final float startDistance;
    private final float stopDistance;
    @Nullable
    private LivingEntity master;
    private int repath;

    public FollowMasterGoal(BanditEntity bandit, double speed, float startDistance, float stopDistance) {
        this.bandit = bandit;
        this.speed = speed;
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity owner = bandit.masterEntity();
        if (owner == null || owner.isSpectator() || bandit.getTarget() != null
                || bandit.distanceToSqr(owner) < startDistance * startDistance) {
            return false;
        }
        master = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return master != null && master.isAlive() && bandit.getTarget() == null && !bandit.getNavigation().isDone()
                && bandit.distanceToSqr(master) > stopDistance * stopDistance;
    }

    @Override
    public void start() {
        repath = 0;
        bandit.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        master = null;
        bandit.getNavigation().stop();
        bandit.setPathfindingMalus(PathType.WATER, 8.0F);
    }

    @Override
    public void tick() {
        if (master == null) {
            return;
        }
        bandit.getLookControl().setLookAt(master, 10.0F, bandit.getMaxHeadXRot());
        if (--repath > 0) {
            return;
        }
        repath = 10;
        if (bandit.distanceToSqr(master) >= 24 * 24) {
            teleportNear(master.blockPosition());
        } else {
            bandit.getNavigation().moveTo(master, speed);
        }
    }

    private void teleportNear(BlockPos target) {
        for (int i = 0; i < 10; i++) {
            int dx = bandit.getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = bandit.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            BlockPos pos = target.offset(dx, 0, dz);
            if (bandit.level().getBlockState(pos.below()).isSolid() && bandit.level().getBlockState(pos).isAir()
                    && bandit.level().getBlockState(pos.above()).isAir()) {
                bandit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, bandit.getYRot(), bandit.getXRot());
                bandit.getNavigation().stop();
                return;
            }
        }
    }
}
