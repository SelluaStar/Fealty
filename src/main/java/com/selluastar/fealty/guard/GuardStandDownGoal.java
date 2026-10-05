package com.selluastar.fealty.guard;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Calls off an attack on a player once the village no longer wants them hurt, unless they hit the guard. */
public class GuardStandDownGoal extends Goal {
    private final Mob guard;

    public GuardStandDownGoal(Mob guard) {
        this.guard = guard;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (guard.tickCount % 20 != 0 || !(guard.getTarget() instanceof ServerPlayer player)) {
            return false;
        }
        boolean provoked = guard.getLastHurtByMob() == player && guard.tickCount - guard.getLastHurtByMobTimestamp() < 200;
        return !provoked && !GuardManager.isHostileTo(guard, player);
    }

    @Override
    public void start() {
        guard.setTarget(null);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
