package com.selluastar.fealty.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired on {@code NeoForge.EVENT_BUS} around every reputation change. */
public abstract class RepChangeEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation faction;
    private final ResourceLocation reason;
    private final int oldRep;

    protected RepChangeEvent(ServerPlayer player, ResourceLocation faction, ResourceLocation reason, int oldRep) {
        this.player = player;
        this.faction = faction;
        this.reason = reason;
        this.oldRep = oldRep;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getFaction() {
        return faction;
    }

    /** The rep source id that caused the change. */
    public ResourceLocation getReason() {
        return reason;
    }

    public int getOldRep() {
        return oldRep;
    }

    /** A change is about to apply. Cancel it, or change the amount. */
    public static class Pre extends RepChangeEvent implements ICancellableEvent {
        private int amount;

        public Pre(ServerPlayer player, ResourceLocation faction, ResourceLocation reason, int oldRep, int amount) {
            super(player, faction, reason, oldRep);
            this.amount = amount;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    /** A change has applied. {@link #getNewRep()} is already clamped to the configured range. */
    public static class Post extends RepChangeEvent {
        private final int newRep;

        public Post(ServerPlayer player, ResourceLocation faction, ResourceLocation reason, int oldRep, int newRep) {
            super(player, faction, reason, oldRep);
            this.newRep = newRep;
        }

        public int getNewRep() {
            return newRep;
        }

        public int getDelta() {
            return newRep - getOldRep();
        }
    }
}
