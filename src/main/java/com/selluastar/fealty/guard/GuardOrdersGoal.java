package com.selluastar.fealty.guard;

import java.util.EnumSet;

import com.selluastar.fealty.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Carries out escort and lord's-horn orders: follow a player or hold a position. */
public class GuardOrdersGoal extends Goal {
    private final PathfinderMob guard;
    private int repath;

    public GuardOrdersGoal(PathfinderMob guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private GuardOrders orders() {
        return guard.getData(ModAttachments.GUARD_ORDERS);
    }

    @Override
    public boolean canUse() {
        if (!guard.hasData(ModAttachments.GUARD_ORDERS) || guard.getTarget() != null) {
            return false;
        }
        GuardOrders orders = orders();
        long now = guard.level().getGameTime();
        if (orders.mode() == GuardOrders.Mode.NONE || orders.mode() == GuardOrders.Mode.DEFEND) {
            return false;
        }
        if (!orders.isActive(now)) {
            expire(orders);
            return false;
        }
        return switch (orders.mode()) {
            case ESCORT, FOLLOW -> {
                Player leader = leader(orders);
                yield leader != null && guard.distanceToSqr(leader) > 9;
            }
            case HOLD -> orders.holdPos() != null && guard.blockPosition().distSqr(orders.holdPos()) > 4;
            default -> false;
        };
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && !guard.getNavigation().isDone();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        guard.getNavigation().stop();
    }

    @Override
    public void tick() {
        GuardOrders orders = orders();
        if (--repath > 0) {
            return;
        }
        repath = 10;
        if (orders.mode() == GuardOrders.Mode.HOLD && orders.holdPos() != null) {
            BlockPos hold = orders.holdPos();
            guard.getNavigation().moveTo(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5, 1.0);
            return;
        }
        Player leader = leader(orders);
        if (leader == null) {
            return;
        }
        guard.getLookControl().setLookAt(leader, 10.0F, guard.getMaxHeadXRot());
        double distance = guard.distanceToSqr(leader);
        if (distance > 32 * 32 && leader.onGround()) {
            // Keep up with the player the way tamed animals do.
            guard.moveTo(leader.getX(), leader.getY(), leader.getZ(), guard.getYRot(), guard.getXRot());
            guard.getNavigation().stop();
        } else {
            guard.getNavigation().moveTo(leader, distance > 100 ? 1.2 : 1.0);
        }
    }

    private Player leader(GuardOrders orders) {
        if (orders.leader() == null) {
            return null;
        }
        Player leader = guard.level().getPlayerByUUID(orders.leader());
        return leader != null && leader.isAlive() && !leader.isSpectator() ? leader : null;
    }

    private void expire(GuardOrders orders) {
        if (orders.mode() == GuardOrders.Mode.ESCORT && leader(orders) instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("fealty.guard.escort_end", guard.getDisplayName()), true);
        }
        orders.clear();
    }
}
