package com.selluastar.fealty.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/** Lets Fealty add goals to guards from other mods (and vanilla iron golems). */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("goalSelector")
    GoalSelector fealty$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector fealty$getTargetSelector();
}
