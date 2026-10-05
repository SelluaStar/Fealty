package com.selluastar.fealty.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.guard.GuardManager;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * Goes after whatever just hurt a villager nearby. Players are left to the crime system, which decides whether
 * hurting a villager was a crime the guards should answer.
 */
public class DefendVillagersGoal extends TargetGoal {
    private static final int RANGE = 16;
    private static final int MEMORY_TICKS = 100;
    private int cooldown;
    @Nullable
    private LivingEntity attacker;

    public DefendVillagersGoal(PathfinderMob guard) {
        super(guard, false, true);
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) {
            return false;
        }
        cooldown = 10;
        attacker = null;
        for (AbstractVillager villager : mob.level().getEntitiesOfClass(AbstractVillager.class, mob.getBoundingBox().inflate(RANGE))) {
            LivingEntity hurtBy = villager.getLastHurtByMob();
            if (hurtBy != null && hurtBy.isAlive() && villager.tickCount - villager.getLastHurtByMobTimestamp() < MEMORY_TICKS
                    && !(hurtBy instanceof Player) && !(hurtBy instanceof AbstractVillager)
                    && !(hurtBy instanceof Mob other && GuardManager.isGuard(other)) && hurtBy != mob) {
                attacker = hurtBy;
                break;
            }
        }
        return attacker != null && canAttack(attacker, TargetingConditions.DEFAULT);
    }

    @Override
    public void start() {
        mob.setTarget(attacker);
        targetMob = attacker;
        super.start();
    }
}
