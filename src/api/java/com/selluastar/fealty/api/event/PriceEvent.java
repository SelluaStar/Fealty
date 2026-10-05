package com.selluastar.fealty.api.event;

import com.selluastar.fealty.api.RepTier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.Event;

/** A villager's trade prices are being calculated for a player. Adjust the multiplier freely. */
public class PriceEvent extends Event {
    private final ServerPlayer player;
    private final Villager villager;
    private final ResourceLocation faction;
    private final RepTier tier;
    private final float baseMultiplier;
    private float multiplier;

    public PriceEvent(ServerPlayer player, Villager villager, ResourceLocation faction, RepTier tier, float multiplier) {
        this.player = player;
        this.villager = villager;
        this.faction = faction;
        this.tier = tier;
        this.baseMultiplier = multiplier;
        this.multiplier = multiplier;
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

    public RepTier getTier() {
        return tier;
    }

    /** The multiplier Fealty computed before any listener ran. */
    public float getBaseMultiplier() {
        return baseMultiplier;
    }

    public float getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(float multiplier) {
        this.multiplier = Math.max(0.0F, multiplier);
    }
}
