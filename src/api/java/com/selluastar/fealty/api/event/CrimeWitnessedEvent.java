package com.selluastar.fealty.api.event;

import java.util.List;

import com.selluastar.fealty.api.Severity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A villager or guard saw a crime. Cancel it to let the player get away with it, or change its severity.
 * Fired on {@code NeoForge.EVENT_BUS} before any reputation changes.
 */
public class CrimeWitnessedEvent extends Event implements ICancellableEvent {
    private final ServerPlayer player;
    private final ResourceLocation faction;
    private final ResourceLocation crime;
    private final BlockPos pos;
    private final List<LivingEntity> witnesses;
    private Severity severity;

    public CrimeWitnessedEvent(ServerPlayer player, ResourceLocation faction, ResourceLocation crime, BlockPos pos,
                               List<LivingEntity> witnesses, Severity severity) {
        this.player = player;
        this.faction = faction;
        this.crime = crime;
        this.pos = pos;
        this.witnesses = List.copyOf(witnesses);
        this.severity = severity;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public ResourceLocation getFaction() {
        return faction;
    }

    /** The crime's rep source id, e.g. {@code fealty:steal}. */
    public ResourceLocation getCrime() {
        return crime;
    }

    public BlockPos getPos() {
        return pos;
    }

    /** Everyone who saw it. Never empty. */
    public List<LivingEntity> getWitnesses() {
        return witnesses;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }
}
