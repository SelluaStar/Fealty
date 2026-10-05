package com.selluastar.fealty.guard;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/** Targets players the guard's village is hostile to: Hated, wanted, or recently caught committing a crime. */
public class GuardHostilePlayerGoal extends NearestAttackableTargetGoal<Player> {
    public GuardHostilePlayerGoal(Mob guard) {
        super(guard, Player.class, 10, true, false, p -> p instanceof ServerPlayer sp && GuardManager.isHostileTo(guard, sp));
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target instanceof ServerPlayer player && !GuardManager.isHostileTo(mob, player)) {
            return false;
        }
        return super.canContinueToUse();
    }
}
