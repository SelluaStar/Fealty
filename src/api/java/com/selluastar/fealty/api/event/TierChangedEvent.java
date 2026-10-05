package com.selluastar.fealty.api.event;

import com.selluastar.fealty.api.RepTier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** A player crossed a tier boundary with a faction. Fired on {@code NeoForge.EVENT_BUS}. */
public class TierChangedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation faction;
    private final RepTier oldTier;
    private final RepTier newTier;

    public TierChangedEvent(ServerPlayer player, ResourceLocation faction, RepTier oldTier, RepTier newTier) {
        this.player = player;
        this.faction = faction;
        this.oldTier = oldTier;
        this.newTier = newTier;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getFaction() {
        return faction;
    }

    public RepTier getOldTier() {
        return oldTier;
    }

    public RepTier getNewTier() {
        return newTier;
    }

    public boolean isRising() {
        return newTier.rank() > oldTier.rank();
    }
}
