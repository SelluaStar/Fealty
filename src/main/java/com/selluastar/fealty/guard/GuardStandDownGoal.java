package com.selluastar.fealty.guard;

import java.util.EnumSet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Calls off an attack on a player once the village no longer wants them hurt, unless they hit the guard. */
public class GuardStandDownGoal extends Goal {
    private final Mob guard;
    private int cooldown;

    public GuardStandDownGoal(Mob guard) {
        this.guard = guard;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        // (A countdown rather than tickCount % 20: goals are only checked every other tick, so a tick-count test
        // could never pass for half of all guards.)
        if (--cooldown > 0 || !(guard.getTarget() instanceof ServerPlayer player)) {
            return false;
        }
        cooldown = 10;
        boolean provoked = guard.getLastHurtByMob() == player && guard.tickCount - guard.getLastHurtByMobTimestamp() < 200;
        return !provoked && !GuardManager.isHostileTo(guard, player);
    }

    @Override
    public void start() {
        guard.setTarget(null);
        if (guard instanceof NeutralMob neutral) {
            // Iron golems otherwise stay angry and pick the player right back up.
            neutral.stopBeingAngry();
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
