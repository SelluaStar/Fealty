package com.selluastar.fealty.guard;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.GuardStance;
import com.selluastar.fealty.rep.FactionResolver;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/** Trusted players get help in fights; Honored players get guards joining fights from further away. */
public class GuardAssistGoal extends TargetGoal {
    private static final int MEMORY_TICKS = 100;
    private int cooldown;
    @Nullable
    private LivingEntity candidate;

    public GuardAssistGoal(Mob guard) {
        super(guard, false);
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0) {
            return false;
        }
        cooldown = 10;
        Optional<ResourceLocation> faction = FactionResolver.factionOf(mob);
        if (faction.isEmpty()) {
            return false;
        }
        candidate = null;
        for (Player p : mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(24))) {
            if (!(p instanceof ServerPlayer player) || player.isSpectator()) {
                continue;
            }
            GuardStance stance = GuardManager.stance(player, faction.get());
            if (!stance.assists() || (stance == GuardStance.ASSIST && mob.distanceToSqr(player) > 16 * 16)) {
                continue;
            }
            LivingEntity attacker = player.getLastHurtByMob();
            if (isValidFoe(attacker) && player.tickCount - player.getLastHurtByMobTimestamp() < MEMORY_TICKS) {
                candidate = attacker;
                break;
            }
            LivingEntity victim = player.getLastHurtMob();
            if (victim instanceof Enemy && isValidFoe(victim) && player.tickCount - player.getLastHurtMobTimestamp() < MEMORY_TICKS) {
                candidate = victim;
                break;
            }
        }
        return candidate != null && canAttack(candidate, TargetingConditions.DEFAULT);
    }

    private boolean isValidFoe(@Nullable LivingEntity entity) {
        return entity != null && entity.isAlive() && entity != mob && !(entity instanceof Player)
                && !(entity instanceof AbstractVillager) && !(entity instanceof Creeper)
                && !(entity instanceof Mob m && GuardManager.isGuard(m));
    }

    @Override
    public void start() {
        mob.setTarget(candidate);
        targetMob = candidate;
        super.start();
    }
}
