package com.selluastar.fealty.api.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** A player threatens a villager with a drawn weapon. Cancel to stop the threat. */
public class ThreatEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final Villager villager;
    private final ResourceLocation faction;

    public ThreatEvent(ServerPlayer player, Villager villager, ResourceLocation faction) {
        this.player = player;
        this.villager = villager;
        this.faction = faction;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Villager getVillager() {
        return villager;
    }

    public ResourceLocation getFaction() {
        return faction;
    }
}
