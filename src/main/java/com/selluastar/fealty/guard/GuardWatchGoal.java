package com.selluastar.fealty.guard;

import java.util.EnumSet;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.GuardStance;
import com.selluastar.fealty.rep.FactionResolver;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Distrusted players get a shadow: a guard follows a few blocks behind and warns them now and then. */
public class GuardWatchGoal extends Goal {
    private static final int WARN_INTERVAL = 1200;
    private final PathfinderMob guard;
    @Nullable
    private ServerPlayer watched;
    private int check;
    private long lastWarning = Long.MIN_VALUE / 2;

    public GuardWatchGoal(PathfinderMob guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (--check > 0 || guard.getTarget() != null) {
            return false;
        }
        check = 20;
        Optional<ResourceLocation> faction = FactionResolver.factionOf(guard);
        if (faction.isEmpty()) {
            return false;
        }
        watched = null;
        double best = Double.MAX_VALUE;
        for (Player p : guard.level().getEntitiesOfClass(Player.class, guard.getBoundingBox().inflate(12))) {
            if (p instanceof ServerPlayer player && !player.isSpectator() && !player.isCreative()
                    && GuardManager.stance(player, faction.get()) == GuardStance.WATCH && guard.hasLineOfSight(player)) {
                double d = guard.distanceToSqr(player);
                if (d < best) {
                    best = d;
                    watched = player;
                }
            }
        }
        return watched != null;
    }

    @Override
    public boolean canContinueToUse() {
        return watched != null && watched.isAlive() && guard.getTarget() == null && guard.distanceToSqr(watched) < 20 * 20;
    }

    @Override
    public void stop() {
        watched = null;
        guard.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (watched == null) {
            return;
        }
        guard.getLookControl().setLookAt(watched, 30.0F, 30.0F);
        double d = guard.distanceToSqr(watched);
        if (d > 6 * 6) {
            guard.getNavigation().moveTo(watched, 0.8);
        } else if (d < 3 * 3) {
            guard.getNavigation().stop();
        }
        long now = guard.level().getGameTime();
        if (now - lastWarning > WARN_INTERVAL && d < 8 * 8) {
            lastWarning = now;
            watched.displayClientMessage(Component.translatable("fealty.guard.watching." + guard.getRandom().nextInt(3),
                    guard.getDisplayName()), true);
        }
    }
}
