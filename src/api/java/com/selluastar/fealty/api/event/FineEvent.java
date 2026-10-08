package com.selluastar.fealty.api.event;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fines for crimes. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class FineEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation faction;

    protected FineEvent(ServerPlayer player, ResourceLocation faction) {
        this.player = player;
        this.faction = faction;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getFaction() {
        return faction;
    }

    /** A guard is about to demand a fine. Cancel it (the guard just warns), or change what it costs. */
    public static class Issued extends FineEvent implements ICancellableEvent {
        private final Entity guard;
        private final ResourceLocation crime;
        private int cost;

        public Issued(ServerPlayer player, ResourceLocation faction, Entity guard, ResourceLocation crime, int cost) {
            super(player, faction);
            this.guard = guard;
            this.crime = crime;
            this.cost = cost;
        }

        public Entity getGuard() {
            return guard;
        }

        public ResourceLocation getCrime() {
            return crime;
        }

        /** In emeralds. */
        public int getCost() {
            return cost;
        }

        public void setCost(int cost) {
            this.cost = Math.max(1, cost);
        }
    }

    /** A fine was paid, to a guard or to the elder (clearing the player's name). */
    public static class Paid extends FineEvent {
        @Nullable
        private final Entity receiver;
        private final int cost;
        private final boolean toElder;

        public Paid(ServerPlayer player, ResourceLocation faction, @Nullable Entity receiver, int cost, boolean toElder) {
            super(player, faction);
            this.receiver = receiver;
            this.cost = cost;
            this.toElder = toElder;
        }

        @Nullable
        public Entity getReceiver() {
            return receiver;
        }

        public int getCost() {
            return cost;
        }

        public boolean isToElder() {
            return toElder;
        }
    }

    /** The player refused to pay, or ran: the guards come for them. */
    public static class Refused extends FineEvent {
        public Refused(ServerPlayer player, ResourceLocation faction) {
            super(player, faction);
        }
    }
}
