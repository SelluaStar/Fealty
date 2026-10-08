package com.selluastar.fealty.war;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.mixin.MobAccessor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Pillager camp garrisons fight village guards on sight; captives in the cages are left alone by everyone. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class WarDefenders {
    public static final String DEFENDER_TAG = "fealty.defender";

    private WarDefenders() {
    }

    /** Mark a mob as one of a stronghold's defenders (it gets its goals when it joins the world). */
    public static void enlist(Mob mob) {
        mob.addTag(DEFENDER_TAG);
    }

    public static boolean isDefender(Entity entity) {
        return entity.getTags().contains(DEFENDER_TAG);
    }

    /** Give a defender a goal to go for village guards (lost on reload, so given again each time it joins). */
    public static void arm(Mob mob) {
        ((MobAccessor) mob).fealty$getTargetSelector().addGoal(3, new NearestAttackableTargetGoal<>(mob, Mob.class, 10, true, false,
                target -> target instanceof Mob other && GuardManager.isGuard(other)));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob && isDefender(mob)) {
            arm(mob);
        }
    }

    /** Nothing takes aim at a captive. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target != null && Captives.isCaptive(target)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** Stray arrows and blades spare the captives (players can still be cruel). */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (Captives.isCaptive(event.getEntity()) && !(event.getSource().getEntity() instanceof Player)) {
            event.setCanceled(true);
        }
    }
}
