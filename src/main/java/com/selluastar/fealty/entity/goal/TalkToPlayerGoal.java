package com.selluastar.fealty.entity.goal;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.dialogue.DialogueService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/** While a player talks to this NPC (or has its quest board open), it stands still and faces them. */
public class TalkToPlayerGoal extends Goal {
    private final Mob mob;
    @Nullable
    private ServerPlayer listener;

    public TalkToPlayerGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Danger comes first: a fight or a fright breaks off the conversation.
        if (mob.getTarget() != null || mob.getLastHurtByMob() != null) {
            return false;
        }
        listener = DialogueService.listener(mob).orElse(null);
        return listener != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (listener != null) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(listener, 30.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        listener = null;
    }
}
