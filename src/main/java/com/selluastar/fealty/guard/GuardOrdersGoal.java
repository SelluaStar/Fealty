package com.selluastar.fealty.guard;

import java.util.EnumSet;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.entity.VillageGuardEntity;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Carries out escort orders and a lord's orders: follow a player, hold a position, or keep watch over an area. A
 * lord's orders lapse when they stop being the village's lord, and a guard left without its leader for a while
 * stops waiting.
 */
public class GuardOrdersGoal extends Goal {
    /** Goal checks (one every other tick) without the leader in reach before orders lapse. */
    private static final int PATIENCE = 300;
    private final PathfinderMob guard;
    private int repath;
    private int lordCheck;
    private int leaderMissing;

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
        switch (orders.mode()) {
            case NONE, DEFEND -> {
                return false;
            }
            case RETURN -> {
                // Fealty guards walk off on their own; other guards simply go back to their usual rounds.
                if (!(guard instanceof VillageGuardEntity)) {
                    orders.clear();
                }
                return false;
            }
            default -> {
            }
        }
        if (!orders.isActive(now)) {
            expire(orders);
            return false;
        }
        if (orders.isLordsOrder() && --lordCheck <= 0) {
            lordCheck = 20;
            if (!stillLord(orders)) {
                orders.clear();
                return false;
            }
        }
        return switch (orders.mode()) {
            case ESCORT, FOLLOW -> {
                Player leader = leader(orders);
                if (leader == null) {
                    if (++leaderMissing > PATIENCE && !(guard instanceof VillageGuardEntity)) {
                        orders.clear();
                    }
                    yield false;
                }
                leaderMissing = 0;
                yield guard.distanceToSqr(leader) > 9;
            }
            case HOLD -> orders.holdPos() != null && guard.blockPosition().distSqr(orders.holdPos()) > 4;
            case GUARD -> orders.holdPos() != null && guard.blockPosition().distSqr(orders.holdPos()) > 8 * 8;
            default -> false;
        };
    }

    /** A lord's orders only hold while the one who gave them still rules the guard's village. */
    private boolean stillLord(GuardOrders orders) {
        if (orders.leader() == null || !(guard.level() instanceof ServerLevel level)) {
            return orders.leader() == null;
        }
        return FactionResolver.factionOf(guard)
                .flatMap(id -> FealtyWorldData.get(level.getServer()).village(id))
                .map(VillageRecord::lord)
                .map(lord -> lord.isLord(orders.leader()))
                .orElse(false);
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
        if ((orders.mode() == GuardOrders.Mode.HOLD || orders.mode() == GuardOrders.Mode.GUARD) && orders.holdPos() != null) {
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

    @Nullable
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
